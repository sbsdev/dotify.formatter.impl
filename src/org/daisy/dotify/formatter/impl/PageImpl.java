package org.daisy.dotify.formatter.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.regex.Pattern;

import org.daisy.dotify.api.formatter.CompoundField;
import org.daisy.dotify.api.formatter.CurrentPageField;
import org.daisy.dotify.api.formatter.Field;
import org.daisy.dotify.api.formatter.FieldList;
import org.daisy.dotify.api.formatter.FormattingTypes;
import org.daisy.dotify.api.formatter.Marker;
import org.daisy.dotify.api.formatter.MarkerReferenceField;
import org.daisy.dotify.api.formatter.MarkerReferenceField.MarkerSearchDirection;
import org.daisy.dotify.api.formatter.MarkerReferenceField.MarkerSearchScope;
import org.daisy.dotify.api.formatter.NoField;
import org.daisy.dotify.api.formatter.PageAreaProperties;
import org.daisy.dotify.api.translator.BrailleTranslator;
import org.daisy.dotify.api.translator.DefaultTextAttribute;
import org.daisy.dotify.api.translator.TextBorderStyle;
import org.daisy.dotify.api.translator.Translatable;
import org.daisy.dotify.api.translator.TranslationException;
import org.daisy.dotify.api.writer.Row;
import org.daisy.dotify.common.text.StringTools;
import org.daisy.dotify.formatter.impl.UnwriteableAreaInfo.UnwriteableArea;
import org.daisy.dotify.writer.impl.Page;


//FIXME: scope spread is currently implemented using document wide scope, i.e. across volume boundaries. This is wrong, but is better than the previous sequence scope.
/**
 * Provides a page object.
 * 
 * @author Joel Håkansson
 */
class PageImpl implements Page, Cloneable {
	private static final Pattern trailingWs = Pattern.compile("\\s*\\z");
	private static final Pattern softHyphen = Pattern.compile("\u00ad");
	private final CrossReferenceHandler crh;
	private final PageDetails details;
	private final PageSequence parent;
	private final LayoutMaster master;
	private final FormatterContext fcontext;
	private final UnwriteableAreaInfo unwriteableAreaInfo;
	private final List<RowImpl> before;
	private final List<RowImpl> after;
	private ArrayList<RowImpl> bodyRows;
	private ArrayList<RowImpl> pageArea;
	private ArrayList<RowImpl> pageRows;
	private ArrayList<String> anchors;
	private ArrayList<String> identifiers;
	private final int pageIndex;
	private final int textFlowIntoHeaderHeight;
	private final int textFlowIntoFooterHeight;
	private final int headerHeight;
	private final int footerHeight;
	private final int flowHeight;
	private final PageTemplate template;

	private boolean isVolBreakAllowed;
	private int keepPreviousSheets;
	private Integer volumeBreakAfterPriority;
	private int volumeNumber;
	
	
	public PageImpl(CrossReferenceHandler crh, PageDetails details,PageSequence parent, LayoutMaster master, FormatterContext fcontext, int pageIndex, List<RowImpl> before, List<RowImpl> after,
	                UnwriteableAreaInfo unwriteableAreaInfo) {
		this.crh = crh;
		this.details = details;
		this.parent = parent;
		this.master = master;
		this.fcontext = fcontext;
		this.bodyRows = new ArrayList<>();
		this.before = before;
		this.after = after; 
		this.unwriteableAreaInfo = unwriteableAreaInfo;

		this.pageArea = new ArrayList<>();
		this.anchors = new ArrayList<>();
		this.identifiers = new ArrayList<>();
		this.pageIndex = pageIndex;
		this.template = master.getTemplate(pageIndex+1);
		this.isVolBreakAllowed = true;
		this.keepPreviousSheets = 0;
		this.volumeBreakAfterPriority = null;
		this.volumeNumber = 0;
		
		// validate/analyze header and footer
		this.textFlowIntoHeaderHeight = validateAndAnalyzeHeaderFooter(master, template, true);
		this.textFlowIntoFooterHeight = validateAndAnalyzeHeaderFooter(master, template, false);
		
		this.headerHeight = (int)Math.ceil(getHeight(template.getHeader(), master.getRowSpacing()));
		this.footerHeight = (int)Math.ceil(getHeight(template.getFooter(), master.getRowSpacing()));
		
		// Maximum flow height, effective height could be lower
		this.flowHeight = master.getPageHeight()
				- this.headerHeight + this.textFlowIntoHeaderHeight
				- this.footerHeight + this.textFlowIntoFooterHeight
				- (master.getBorder() != null ? (int)Math.ceil(distributeRowSpacing(null, false).spacing*2) : 0);
	}
	
	private static int validateAndAnalyzeHeaderFooter(LayoutMaster master, PageTemplate template, boolean header) {
		List<FieldList> rows;
		if (header) {
			rows = new ArrayList<>(template.getHeader());
			Collections.reverse(rows);
		} else {
			rows = template.getFooter();
		}
		int j = 0;
		int height = 0;
		for (FieldList row : rows) {
			int k = 0;
			boolean hasEmptyField = false;
			for (Field f : row.getFields()) {
				if (f instanceof NoField) {
					if (hasEmptyField) {
						throw new RuntimeException("At most one empty <field/> allowed.");
					} else if (k > 0) {
						throw new RuntimeException("Empty <field/> only allowed on the left.");
					} else {
						hasEmptyField = true;
					}
				}
				k++;
			}
			if (hasEmptyField) {
				if (k == 1) {
					throw new RuntimeException("Empty <field/> does not make sense as single child.");
				} else if (k > 2) {
					throw new RuntimeException("Empty <field/> only allowed in combination with a single non-empty <field/>.");
				}
				float rowSpacing;
				if (row.getRowSpacing() != null) {
					rowSpacing = row.getRowSpacing();
				} else {
					rowSpacing = master.getRowSpacing();
				}
				if (rowSpacing != 1.0f) {
					throw new RuntimeException("Empty <field/> only allowed when row-spacing is '1'.");
				}
				if (height == j) {
					height++;
				} else {
					throw new RuntimeException("Empty <field/> only allowed if all "
						                           + (header ? "<header/> below" : "<footer/> above")
						                           + " have an empty <field/> as well.");
				}
			}
			j++;
		}
		return height;
	}
	
	static float getHeight(List<FieldList> list, float def) {
		float ret = 0;
		for (FieldList f : list) {
			if (f.getRowSpacing()!=null) {
				ret += f.getRowSpacing();
			} else {
				ret += def;
			}
		}
		return ret;
	}

	void addToPageArea(List<RowImpl> block) {
		pageArea.addAll(block);
	}
	
	void newRow(RowImpl r) throws PageFullException {
		if (rowsOnPage()==0) {
			details.contentMarkersBegin = details.markers.size();
		}
		float spaceUsed = spaceNeeded();
		bodyRows.add(r);
		int markerCountBefore = details.markers.size();
		details.markers.addAll(r.getMarkers());
		if (Math.ceil(spaceUsed) >= flowHeight - textFlowIntoHeaderHeight - textFlowIntoFooterHeight) {
			try {
				if (textFlowIntoHeaderHeight + textFlowIntoFooterHeight == 0) {
					throw new PaginatorException("Too many rows for page");
				}
				TextBorderStyle border = master.getBorder();
				if (border == null) {
					border = TextBorderStyle.NONE;
				}
				pageRows = buildPageRows(border);
			} catch (PageFullException e) {
				bodyRows.remove(bodyRows.size() - 1);
				details.markers.subList(markerCountBefore, details.markers.size()).clear();
				throw e;
			} catch (PaginatorException e) {
				throw new RuntimeException("Pagination failed.", e);
			}
		}
		anchors.addAll(r.getAnchors());
	}
	
	/**
	 * Gets the number of rows on this page
	 * @return returns the number of rows on this page
	 */
	private int rowsOnPage() {
		return bodyRows.size();
	}
	
	void addMarkers(List<Marker> m) {
		details.markers.addAll(m);
	}
	
	List<String> getAnchors() {
		return anchors;
	}
	
	void addIdentifier(String id) {
		identifiers.add(id);
	}
	
	List<String> getIdentifiers() {
		return identifiers;
	}
	
	/**
	 * Gets the page space needed to render the rows. 
	 * @param rows
	 * @param defSpacing a value >= 1.0
	 * @return returns the space, in rows
	 */
	static float rowsNeeded(Iterable<? extends Row> rows, float defSpacing) {
		float ret = 0;
		if (defSpacing < 1) {
			defSpacing = 1;
		}
		for (Row r : rows) {
			if (r.getRowSpacing()!=null && r.getRowSpacing()>=1) {
				ret += r.getRowSpacing();
			} else {
				ret += defSpacing;
			}
		}
		return ret;
	}
	
	float spaceNeeded() {
		return 	pageAreaSpaceNeeded() +
				rowsNeeded(bodyRows, master.getRowSpacing());
	}
	
	float staticAreaSpaceNeeded() {
		return rowsNeeded(before, master.getRowSpacing()) + rowsNeeded(after, master.getRowSpacing());
	}
	
	float pageAreaSpaceNeeded() {
		return (!pageArea.isEmpty() ? staticAreaSpaceNeeded() + rowsNeeded(pageArea, master.getRowSpacing()) : 0);
	}
	
	int spaceUsedOnPage(int offs) {
		return (int)Math.ceil(spaceNeeded()) + offs;
	}
	
	private ArrayList<RowImpl> buildPageRows(TextBorderStyle border) throws PaginatorException, PageFullException {
		ArrayList<RowImpl> ret = new ArrayList<>();
		{
			LayoutMaster lm = master;
			int pagenum = getPageIndex() + 1;
			PageTemplate t = lm.getTemplate(pagenum);
			BrailleTranslator filter = fcontext.getDefaultTranslator();
			boolean hasTopArea = lm.getPageArea()!=null && lm.getPageArea().getAlignment()==PageAreaProperties.Alignment.TOP && !pageArea.isEmpty();
			ListIterator<RowImpl> rows = bodyRows.listIterator();
			ret.addAll(renderFields(lm, t.getHeader(), filter, true, !hasTopArea, rows));
			if (hasTopArea) {
				ret.addAll(before);
				ret.addAll(pageArea);
				ret.addAll(after);
			}
			float bottomAreaSize = (lm.getPageArea()!=null && lm.getPageArea().getAlignment()==PageAreaProperties.Alignment.BOTTOM ? pageAreaSpaceNeeded() : 0);
			while (Math.ceil(rowsNeeded(ret, lm.getRowSpacing()) + bottomAreaSize)
			        < headerHeight - textFlowIntoHeaderHeight + flowHeight - textFlowIntoFooterHeight
			       && rows.hasNext()) {
				ret.add(rows.next());
			}
			if (!t.getFooter().isEmpty() || border != TextBorderStyle.NONE || bottomAreaSize > 0) {
				while (Math.ceil(rowsNeeded(ret, lm.getRowSpacing()) + bottomAreaSize)
				        < headerHeight - textFlowIntoHeaderHeight + flowHeight - textFlowIntoFooterHeight) {
					ret.add(new RowImpl());
				}
				if (lm.getPageArea()!=null && lm.getPageArea().getAlignment()==PageAreaProperties.Alignment.BOTTOM && !pageArea.isEmpty()) {
					ret.addAll(before);
					ret.addAll(pageArea);
					ret.addAll(after);
				}
				ret.addAll(renderFields(lm, t.getFooter(), filter, false, bottomAreaSize == 0, rows));
			}
			if (rows.hasNext()) {
				int remaining = 0;
				int remainingNotSpaceOnly = 0;
				while (rows.hasNext()) {
					RowImpl r = rows.next();
					remaining++;
					String chars = trailingWs.matcher(r.getChars()).replaceAll("");
					if (bottomAreaSize > 0 || chars.length() > 0 || !r.getLeftMargin().isSpaceOnly() || !r.getRightMargin().isSpaceOnly()) {
						remainingNotSpaceOnly = remaining;
					}
				}
				if (remainingNotSpaceOnly > 0) {
					if (Math.ceil(spaceNeeded()) <= flowHeight) {
						throw new PageFullException(flowHeight - remainingNotSpaceOnly, false);
					} else {
						throw new PaginatorException("Too many rows for page");
					}
				}
			}
		}
		return ret;
	}

	/*
	 * The assumption is made that by now all pages have been added to the parent sequence and volume scopes
	 * have been set on the page struct.
	 */
	@Override
	public List<Row> getRows() {

		try {
			TextBorderStyle border = master.getBorder();
			if (border == null) {
				border = TextBorderStyle.NONE;
			}
			if (pageRows == null) {
				try {
					pageRows = buildPageRows(border);
				} catch (PageFullException e) {
					throw new RuntimeException("Coding error");
				}
			}
			
			LayoutMaster lm = master;
			ArrayList<Row> ret = new ArrayList<>();
			{
				final int pagenum = getPageIndex() + 1;
				TextBorder tb = null;

				int fsize = border.getLeftBorder().length() + border.getRightBorder().length();
				final int pageMargin = ((pagenum % 2 == 0) ? lm.getOuterMargin() : lm.getInnerMargin());
				int w = master.getFlowWidth() + fsize + pageMargin;

				tb = new TextBorder.Builder(w, fcontext.getSpaceCharacter()+"")
						.style(border)
						.outerLeftMargin(pageMargin)
						.padToSize(!TextBorderStyle.NONE.equals(border))
						.build();
				if (!TextBorderStyle.NONE.equals(border)) {
					RowImpl r = new RowImpl(tb.getTopBorder());
					DistributedRowSpacing rs = distributeRowSpacing(lm.getRowSpacing(), true);
					r.setRowSpacing(rs.spacing);
					ret.add(r);
				}
				String res;

				for (RowImpl row : pageRows) {
					res = "";
					if (row.getChars().length() > 0) {
						// remove trailing whitespace
						String chars = trailingWs.matcher(row.getChars()).replaceAll("");
						//if (!TextBorderStyle.NONE.equals(frame)) {
							res = tb.addBorderToRow(
									padLeft(master.getFlowWidth(), chars, row.getLeftMargin(), row.getRightMargin(), row.getAlignment(), fcontext.getSpaceCharacter()), 
									"");
						//} else {
						//	res = StringTools.fill(getMarginCharacter(), pageMargin + row.getLeftMargin()) + chars;
						//}
					} else {
						if (!TextBorderStyle.NONE.equals(border)) {
							res = tb.addBorderToRow(row.getLeftMargin().getContent(), row.getRightMargin().getContent());
						} else {
							if (!row.getLeftMargin().isSpaceOnly() || !row.getRightMargin().isSpaceOnly()) {
								res = TextBorder.addBorderToRow(
									lm.getFlowWidth(), row.getLeftMargin().getContent(), "", row.getRightMargin().getContent(), fcontext.getSpaceCharacter()+"");
							} else {
								res = "";
							}
						}
					}
					int rowWidth = StringTools.length(res) + pageMargin;
					String r = res;
					if (rowWidth > master.getPageWidth()) {
						throw new PaginatorException("Row is too long (" + rowWidth + "/" + master.getPageWidth() + ") '" + res + "'");
					}
					RowImpl r2 = new RowImpl(r);
					ret.add(r2);
					Float rs2 = row.getRowSpacing();
					if (!TextBorderStyle.NONE.equals(border)) {
						DistributedRowSpacing rs = distributeRowSpacing(rs2, true);
						r2.setRowSpacing(rs.spacing);
						//don't add space to the last line
						if (row!=pageRows.get(pageRows.size()-1)) {
							RowImpl s = null;
							for (int i = 0; i < rs.lines-1; i++) {
								s = new RowImpl(tb.addBorderToRow(row.getLeftMargin().getContent(), row.getRightMargin().getContent()));
								s.setRowSpacing(rs.spacing);
								ret.add(s);
							}
						}
					} else {
						r2.setRowSpacing(rs2);
					}
					
				}
				if (!TextBorderStyle.NONE.equals(border)) {
					ret.add(new RowImpl(tb.getBottomBorder()));
				}
			}
			if (ret.size()>0) {
				RowImpl last = ((RowImpl)ret.get(ret.size()-1));
				if (lm.getRowSpacing()!=1) {
					//set row spacing on the last row to 1.0
					last.setRowSpacing(1f);
				} else if (last.getRowSpacing()!=null) {
					//ignore row spacing on the last row if overall row spacing is 1.0
					last.setRowSpacing(null);
				}
			}
			return ret;
		} catch (PaginatorException e) {
			throw new RuntimeException("Pagination failed.", e);
		}
	}
	
	static String padLeft(int w, RowImpl row, char space) {
		String chars = trailingWs.matcher(row.getChars()).replaceAll("");
		return padLeft(w, chars, row.getLeftMargin(), row.getRightMargin(), row.getAlignment(), space);
	}
	
	static String padLeft(int w, String text, MarginProperties leftMargin, MarginProperties rightMargin, FormattingTypes.Alignment align, char space) {
		if ("".equals(text) && leftMargin.isSpaceOnly() && rightMargin.isSpaceOnly()) {
			return "";
		} else {
			String r = leftMargin.getContent() + StringTools.fill(space, align.getOffset(w - (leftMargin.getContent().length() + rightMargin.getContent().length() + text.length()))) + text;
			if (rightMargin.isSpaceOnly()) {
				return r;
			} else {
				return r + StringTools.fill(space, w - r.length() - rightMargin.getContent().length()) + rightMargin.getContent();
			}
		}
	}

	/**
	 * Get the page index, offset included, zero based. Don't assume
	 * getPageIndex() % 2 == getPageOrdinal() % 2
	 * 
	 * @return returns the page index in the sequence (zero based)
	 */
	int getPageIndex() {
		return pageIndex;
	}

	/**
	 * Gets the ordinal number for the page in the page sequence list
	 * @return returns the ordinal number for the page
	 */
	private int getPageOrdinal() {
		return pageIndex-getSequenceParent().getPageNumberOffset();
	}
	
	private int getPageId() {
		return getSequenceParent().getGlobalStartIndex()+getPageOrdinal();
	}

	private PageSequence getSequenceParent() {
		return parent;
	}
	
	/**
	 * Gets the flow height for this page, i.e. the number of rows available for text flow
	 * @return returns the flow height
	 */
	int getFlowHeight() {
		return flowHeight;
	}

	private List<RowImpl> renderFields(LayoutMaster lm, List<FieldList> fields, BrailleTranslator translator, boolean headerOrFooter,
	                                   boolean allowTextFlow, ListIterator<RowImpl> bodyRows)
			throws PaginatorException, PageFullException {
		List<RowImpl> ret = new ArrayList<>();
		int width = lm.getFlowWidth();
		char space = fcontext.getSpaceCharacter();
		int i = 0;
		for (FieldList row : fields) {
			List<String> distributedRow; {
				try {
					distributedRow = distribute(row, width, space+"", translator);
				} catch (PaginatorToolsException e) {
					throw new PaginatorException("Error while rendering header", e);
				}
			}
			int length = 0;
			for (String s : distributedRow) {
				if (s != null) {
					length += s.length();
				}
			}
			int k = 0;
			boolean someFlowed = false;
			StringBuffer sb = new StringBuffer();
			for (String s : distributedRow) {
				if (s != null) {
					sb.append(s);
				} else if (k != 0) {
					throw new RuntimeException("Coding error");
				} else if (allowTextFlow && bodyRows.hasNext()) {
					if (headerOrFooter && (headerHeight - i - 1) >= textFlowIntoHeaderHeight
					    || !headerOrFooter && i >= textFlowIntoFooterHeight ) {
						throw new RuntimeException("Coding error");
					}
					RowImpl bodyRow = bodyRows.next();
					float rowSpacing; {
						if (bodyRow.getRowSpacing() != null) {
							rowSpacing = bodyRow.getRowSpacing();
						} else {
							rowSpacing = lm.getRowSpacing();
						}
					}
					if (rowSpacing != 1.0f) {
						throw new RuntimeException("Text can only flow in empty <field/> when row-spacing of text is '1'.");
					}
					String chars;
					try {
						chars = padLeft(width - length,
						                bodyRow.getChars(),
						                bodyRow.getLeftMargin(),
						                bodyRow.getRightMargin(),
						                bodyRow.getAlignment(),
						                space);
					} catch (NegativeArraySizeException e) { // thrown by StringTools.fill if length < 0
						chars = null;
					}
					if (chars == null || chars.length() + length > width) {
						if (bodyRow.block != null) {
							unwriteableAreaInfo.setUnwriteableArea(bodyRow.block,
							                                       bodyRow.positionInBlock,
							                                       new UnwriteableArea(UnwriteableArea.Side.RIGHT, length));
							throw new PageFullException(flowHeight, headerOrFooter);
						}
						sb.append(StringTools.fill(space, width - length));
						bodyRows.previous();
						if (!headerOrFooter || someFlowed) {
							allowTextFlow = false;
						}
					} else {
						sb.append(chars);
						someFlowed = true;
						if (chars.length() + length <= width) {
							sb.append(StringTools.fill(space, width - length - chars.length()));
						}
					}
				} else {
					sb.append(StringTools.fill(space, width - length));
				}
				k++;
			}
			RowImpl r = new RowImpl(sb.toString());
			r.setRowSpacing(row.getRowSpacing());
			ret.add(r);
			i++;
		}
		return ret;
	}
	
	private List<String> distribute(FieldList chunks, int width, String padding, BrailleTranslator translator) throws PaginatorToolsException {
		ArrayList<String> chunkF = new ArrayList<>();
		ArrayList<Boolean> flowPositions = new ArrayList<>();
		for (Field f : chunks.getFields()) {
			DefaultTextAttribute.Builder b = new DefaultTextAttribute.Builder(null);
			String resolved = resolveField(f, this, b);
			if (resolved == null) {
				chunkF.add("");
				flowPositions.add(true);
				continue;
			} else {
				flowPositions.add(false);
			}
			resolved = softHyphen.matcher(resolved).replaceAll("");
			Translatable.Builder tr = Translatable.text(fcontext.getConfiguration().isMarkingCapitalLetters()?resolved:resolved.toLowerCase()).
										hyphenate(false);
			if (resolved.length()>0) {
				tr.attributes(b.build(resolved.length()));
			}
			try {
				chunkF.add(translator.translate(tr.build()).getTranslatedRemainder());
			} catch (TranslationException e) {
				throw new PaginatorToolsException(e);
			}
		}
		List<String> chunksWithPadding = PaginatorTools.distributeRetain(
			chunkF, width, padding,
			fcontext.getConfiguration().isAllowingTextOverflowTrimming()?
			PaginatorTools.DistributeMode.EQUAL_SPACING_TRUNCATE:
			PaginatorTools.DistributeMode.EQUAL_SPACING
		);
		List<String> rv = new ArrayList<>(); {
			// copy chunks
			int i = 0;
			for (String s : chunksWithPadding) {
				if (i % 2 == 0) {
					if (flowPositions.get(i/2)) {
						rv.add(null);
					} else {
						rv.add(s);
					}
				}
				i++;
			}
			// add padding
			i = 0;
			for (String s : chunksWithPadding) {
				if (i % 2 == 1) {
					if (rv.get((i-1)/2) == null || rv.get((i+1)/2) == null) {
						// drop
					} else {
						// append to following chunk
						rv.set((i+1)/2, s + rv.get((i+1)/2));
					}
				}
				i++;
			}
		}
		return rv;
	}
	
	/*
	 * Note that the result of this function is not constant because getPageInSequenceWithOffset(),
	 * getPageInVolumeWithOffset() and shouldAdjustOutOfBounds() are not constant.
	 */
	private static String resolveField(Field field, PageImpl p, DefaultTextAttribute.Builder b) {
		if (field instanceof NoField) {
			return null;
		}
		String ret;
		DefaultTextAttribute.Builder b2 = new DefaultTextAttribute.Builder(field.getTextStyle());
		if (field instanceof CompoundField) {
			ret = resolveCompoundField((CompoundField)field, p, b2);
		} else if (field instanceof MarkerReferenceField) {
			MarkerReferenceField f2 = (MarkerReferenceField)field;
			PageImpl start;
			if (f2.getSearchScope()==MarkerSearchScope.SPREAD ||
				f2.getSearchScope()==MarkerSearchScope.SPREAD_CONTENT) {
				start = getPageInVolumeWithOffset(p, f2.getOffset(), p.shouldAdjustOutOfBounds(f2));
			} else {
				start = p.getPageInScope(p.getSequenceParent(), f2.getOffset(), p.shouldAdjustOutOfBounds(f2));
			}
			ret = findMarker(start, f2);
		} else if (field instanceof CurrentPageField) {
			ret = resolveCurrentPageField((CurrentPageField)field, p);
		} else {
			ret = field.toString();
		}
		if (ret.length()>0) {
			b.add(b2.build(ret.length()));
		}
		return ret;
	}

	private static String resolveCompoundField(CompoundField f, PageImpl p, DefaultTextAttribute.Builder b) {
		if (f.size() == 1) {
			return resolveField(f.get(0), p, b);
		}
		StringBuffer sb = new StringBuffer();
		for (Field f2 : f) {
			String res = resolveField(f2, p, b);
			sb.append(res);
		}
		return sb.toString();
	}

	/*
	 * Note that the result of this function is not constant because getPageInSequenceWithOffset(),
	 * getPageInVolumeWithOffset() and isWithinVolumeSpreadScope() are not constant.
	 */
	private static String findMarker(PageImpl page, MarkerReferenceField markerRef) {
		if (page==null) {
			return "";
		}
		if (markerRef.getSearchScope()==MarkerSearchScope.VOLUME || markerRef.getSearchScope()==MarkerSearchScope.DOCUMENT) {
			throw new RuntimeException("Marker reference scope not implemented: " + markerRef.getSearchScope());
		}
		int dir = 1;
		int index = 0;
		int count = 0;
		List<Marker> m;
		boolean skipLeading = false;
		if (markerRef.getSearchScope() == MarkerReferenceField.MarkerSearchScope.PAGE_CONTENT) {
			skipLeading = true;
		} else if (markerRef.getSearchScope() == MarkerReferenceField.MarkerSearchScope.SPREAD_CONTENT) {
			PageImpl prevPageInVolume = getPageInVolumeWithOffset(page, -1, false);
			if (prevPageInVolume == null || !page.details.isWithinSpreadScope(-1, prevPageInVolume.details==null?null:prevPageInVolume.details)) {
				skipLeading = true;
			}
		}
		if (skipLeading) {
			m = page.details.getContentMarkers();
		} else {
			m = page.details.getMarkers();
		}
		if (markerRef.getSearchDirection() == MarkerReferenceField.MarkerSearchDirection.BACKWARD) {
			dir = -1;
			index = m.size()-1;
		}
		while (count < m.size()) {
			Marker m2 = m.get(index);
			if (m2.getName().equals(markerRef.getName())) {
				return m2.getValue();
			}
			index += dir; 
			count++;
		}
		PageImpl next = null;
		if (markerRef.getSearchScope() == MarkerReferenceField.MarkerSearchScope.SEQUENCE ||
			markerRef.getSearchScope() == MarkerSearchScope.SHEET && page.isWithinSheetScope(dir) //||
			//markerRef.getSearchScope() == MarkerSearchScope.SPREAD && page.isWithinSequenceSpreadScope(dir)
			) {
			next = page.getPageInScope(page.getSequenceParent(), dir, false);
		} //else if (markerRef.getSearchScope() == MarkerSearchScope.SPREAD && page.isWithinDocumentSpreadScope(dir)) {
		  else if (markerRef.getSearchScope() == MarkerSearchScope.SPREAD ||
		           markerRef.getSearchScope() == MarkerSearchScope.SPREAD_CONTENT) {
			if (page.isWithinVolumeSpreadScope(dir)) {
				next = getPageInVolumeWithOffset(page, dir, false);
			}
		}
		if (next!=null) {
			return findMarker(next, markerRef);
		} else {
			return "";
		}
	}
	
	/*
	 * Note that the result of this function is not constant because isWithinVolumeSpreadScope() is not
	 * constant.
	 */
	private boolean shouldAdjustOutOfBounds(MarkerReferenceField markerRef) {
		if (markerRef.getSearchDirection()==MarkerSearchDirection.FORWARD && markerRef.getOffset()>=0 ||
			markerRef.getSearchDirection()==MarkerSearchDirection.BACKWARD && markerRef.getOffset()<=0) {
			return false;
		} else {
			switch(markerRef.getSearchScope()) {
			case PAGE_CONTENT: case PAGE:
				return false;
			case SEQUENCE: case VOLUME: case DOCUMENT:
				return true;
			case SPREAD_CONTENT: case SPREAD:
				//return  isWithinSequenceSpreadScope(markerRef.getOffset());				
				//return  isWithinDocumentSpreadScope(markerRef.getOffset());
				return isWithinVolumeSpreadScope(markerRef.getOffset());
			case SHEET:
				return isWithinSheetScope(markerRef.getOffset()) && 
						markerRef.getSearchDirection()==MarkerSearchDirection.BACKWARD;
			default:
				throw new RuntimeException("Error in code. Missing implementation for value: " + markerRef.getSearchScope());
			}
		}
	}

	/*
	 * This method is unused at the moment, but could be activated if additional scopes are added to the API,
	 * namely SPREAD_WITHIN_DOCUMENT
	 *
	 * Note that the result of this function is not constant because getPageInDocumentWithOffset() is not
	 * constant.
	 */
	@SuppressWarnings("unused")
	private boolean isWithinDocumentSpreadScope(int offset) {
		if (offset==0) {
			return true;
		} else {
			PageImpl n = getPageInDocumentWithOffset(this, offset, false);
			return details.isWithinSpreadScope(offset, (n==null?null:n.details));
		}
	}
	
	/*
	 * Note that the result of this function is not constant because getPageInVolumeWithOffset() is not
	 * constant.
	 */
	private boolean isWithinVolumeSpreadScope(int offset) {
		if (offset==0) {
			return true;
		} else {
			PageImpl n = getPageInVolumeWithOffset(this, offset, false);
			return details.isWithinSpreadScope(offset, (n==null?null:n.details));
		}
	}
	
	
	private boolean isWithinSheetScope(int offset) {
		return 	offset==0 || 
				(
					getSequenceParent().getLayoutMaster().duplex() &&
					(
						(offset == 1 && getPageOrdinal() % 2 == 0) ||
						(offset == -1 && getPageOrdinal() % 2 == 1)
					)
				);
	}
	

	
	/*
	 * Note that the result of this function is not constant because getPageInScope() and
	 * getSequenceParent().getParent().getContentsInVolume() are not constant because PageSequence and
	 * PageStruct are mutable.
	 */
	private static PageImpl getPageInVolumeWithOffset(PageImpl base, int offset, boolean adjustOutOfBounds) {
		if (offset==0) {
			return base;
		} else {
			return base.getPageInScope(base.getSequenceParent().getParent().getContentsInVolume(base.getVolumeNumber()), offset, adjustOutOfBounds);
		}
	}

	/*
	 * Note that the result of this function is not constant because getPageInScope() is not constant.
	 */
	private static PageImpl getPageInDocumentWithOffset(PageImpl base, int offset, boolean adjustOutOfBounds) {
		if (offset==0) {
			return base;
		} else {
			return base.getPageInScope(base.getSequenceParent().getParent().getPageView(), offset, adjustOutOfBounds);
		}
	}
	
	/*
	 * Note that the result of this function is not constant because
	 * getSequenceParent().getParent().getPageView().getPages() is not constant because PageSequence is
	 * mutable.
	 */
	private PageImpl getPageInScope(View<PageImpl> pageView, int offset, boolean adjustOutOfBounds) {
		if (offset==0) {
			return this;
		} else {
			if (pageView!=null) {
				int next = pageView.toLocalIndex(getPageId())+offset;
				int size = pageView.size();
				if (adjustOutOfBounds) {
					next = Math.min(size-1, Math.max(0, next));
				}
				if (next < size && next >= 0) {
					return pageView.get(next);
				}
			}
			return null;
		}
	}
	
	private static String resolveCurrentPageField(CurrentPageField f, PageImpl p) {
		int pagenum = p.getPageIndex() + 1;
		return f.getNumeralStyle().format(pagenum);
	}
	
	/**
	 * Divide a row-spacing value into several rows with a row-spacing < 2.
	 * <p>E.g. A row spacing of 2.5 will return:</p>
	 * <dl>
	 * 	<dt>RowSpacing.spacing</dt><dd>1.25</dd> 
	 *  <dt>RowSpacing.lines</dt><dd>2</dd>
	 * </dl>
	 * @param rs
	 * @return
	 */
	private DistributedRowSpacing distributeRowSpacing(Float rs, boolean nullIfEqualToDefault) {
		if (rs == null) {
			//use default
			rs = this.master.getRowSpacing();
		}
		int ins = Math.max((int)Math.floor(rs), 1);
		Float spacing = rs / ins;
		if (nullIfEqualToDefault && spacing.equals(this.master.getRowSpacing())) {
			return new DistributedRowSpacing(null, ins);
		} else {
			return new DistributedRowSpacing(spacing, ins);
		}
	}
	
	private class DistributedRowSpacing {
		private final Float spacing;
		private final int lines;
		DistributedRowSpacing(Float s, int l) {
			this.spacing = s;
			this.lines = l;
		}
	}
	
	void setKeepWithPreviousSheets(int value) {
		keepPreviousSheets = Math.max(value, keepPreviousSheets);
	}
	
	void setAllowsVolumeBreak(boolean value) {
		this.isVolBreakAllowed = value;
	}

	boolean allowsVolumeBreak() {
		return isVolBreakAllowed;
	}

	int keepPreviousSheets() {
		return keepPreviousSheets;
	}

	PageTemplate getPageTemplate() {
		return template;
	}

	int getVolumeNumber() {
		return volumeNumber;
	}

	void setVolumeNumber(int volumeNumber) {
		this.volumeNumber = volumeNumber;
	}
	
	Integer getAvoidVolumeBreakAfter() {
		return volumeBreakAfterPriority;
	}
	
	void setAvoidVolumeBreakAfter(Integer value) {
		this.volumeBreakAfterPriority = value;
	}

	@SuppressWarnings("unchecked")
	public Object clone() {
		PageImpl clone; {
			try {
				clone = (PageImpl)super.clone();
			} catch (CloneNotSupportedException e) {
				throw new InternalError("coding error");
			}
		}
		clone.bodyRows = (ArrayList<RowImpl>)bodyRows.clone();
		clone.pageArea = (ArrayList<RowImpl>)pageArea.clone();
		clone.details.markers = (ArrayList<Marker>)details.markers.clone();
		clone.anchors = (ArrayList<String>)anchors.clone();
		clone.identifiers = (ArrayList<String>)identifiers.clone();
		if (pageRows != null) {
			clone.pageRows = (ArrayList<RowImpl>)pageRows.clone();
		}
		return clone;
	}
}
