package org.daisy.dotify.formatter.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import org.daisy.dotify.api.formatter.FormattingTypes;
import org.daisy.dotify.api.formatter.Marker;
import org.daisy.dotify.api.formatter.PageAreaProperties;
import org.daisy.dotify.api.translator.BrailleTranslator;
import org.daisy.dotify.api.translator.TextBorderStyle;
import org.daisy.dotify.api.writer.Row;
import org.daisy.dotify.common.text.StringTools;
import org.daisy.dotify.formatter.impl.search.PageDetails;
import org.daisy.dotify.writer.impl.Page;


//FIXME: scope spread is currently implemented using document wide scope, i.e. across volume boundaries. This is wrong, but is better than the previous sequence scope.
/**
 * Provides a page object.
 * 
 * @author Joel Håkansson
 */
public class PageImpl implements Page {
	private static final Pattern trailingWs = Pattern.compile("\\s*\\z");
	private final FieldResolver fieldResolver;
	private final PageDetails details;
	private final LayoutMaster master;
	private final FormatterContext fcontext;
	private final List<RowImpl> before;
	private final List<RowImpl> after;
    private final ArrayList<RowImpl> pageArea;
    private final ArrayList<String> anchors;
    private final ArrayList<String> identifiers;
	private final int pagenum;
	private final int flowHeight;
	private final PageTemplate template;
	private final int pageMargin;
	private final TextBorderStyle borderStyle;
	private final TextBorder border;
	private final PageTemplate pageTemplate;
	private final BorderManager finalRows;

	private boolean hasRows;
	private boolean isVolBreakAllowed;
	private int keepPreviousSheets;
	private Integer volumeBreakAfterPriority;
	private final BrailleTranslator filter;
	
	public PageImpl(FieldResolver fieldResolver, PageDetails details, LayoutMaster master, FormatterContext fcontext, List<RowImpl> before, List<RowImpl> after) {
		this.fieldResolver = fieldResolver;
		this.details = details;
		this.master = master;
		this.fcontext = fcontext;
		this.before = before;
		this.after = after;

		this.pageArea = new ArrayList<>();
		this.anchors = new ArrayList<>();
		this.identifiers = new ArrayList<>();
		this.pagenum = details.getPageNumberIndex() + 1;
		this.template = master.getTemplate(pagenum);
        this.flowHeight = master.getFlowHeight(template);
		this.isVolBreakAllowed = true;
		this.keepPreviousSheets = 0;
		this.volumeBreakAfterPriority = null;
		this.pageMargin = ((pagenum % 2 == 0) ? master.getOuterMargin() : master.getInnerMargin());
		this.borderStyle = master.getBorder()!=null?master.getBorder():TextBorderStyle.NONE;
		this.border = buildBorder();
		this.pageTemplate = master.getTemplate(pagenum);
		this.finalRows = new BorderManager(master.getRowSpacing());
		this.hasRows = false;
		this.filter = fcontext.getDefaultTranslator();
	}
	
	public PageImpl(PageImpl template) {
		this.fieldResolver = template.fieldResolver;
		this.details = template.details;
		this.master = template.master;
		this.fcontext = template.fcontext;
		this.before = new ArrayList<>(template.before);
		this.after = new ArrayList<>(template.after);
	    this.pageArea = new ArrayList<>(template.pageArea);
	    this.anchors = new ArrayList<>(template.anchors);
	    this.identifiers = new ArrayList<>(template.identifiers);
		this.pagenum = template.pagenum;
		this.flowHeight = template.flowHeight;
		this.template = template.template;
		this.pageMargin = template.pageMargin;
		this.borderStyle = template.borderStyle;
		this.border = template.border;
		this.pageTemplate = template.pageTemplate;
		this.finalRows = new BorderManager(template.finalRows);

		this.hasRows = template.hasRows;
		this.isVolBreakAllowed = template.isVolBreakAllowed;
		this.keepPreviousSheets = template.keepPreviousSheets;
		this.volumeBreakAfterPriority = template.volumeBreakAfterPriority;
		this.filter = template.filter;
	}
	
	public static PageImpl copyUnlessNull(PageImpl page) {
		return page==null?null:new PageImpl(page);
	}

	void addToPageArea(List<RowImpl> block) {
		if (hasRows) {
			throw new IllegalStateException("Page area must be added before adding rows.");
		}
		pageArea.addAll(block);
	}
	
	void newRow(RowImpl r) {
		if (!hasRows) {
			//add the header
	        finalRows.addAll(fieldResolver.renderFields(getDetails(), pageTemplate.getHeader(), filter));
	        //add the top page area
			addTopPageArea();
			getDetails().startsContentMarkers();
			hasRows = true;
		}
		finalRows.addRow(r);
		getDetails().getMarkers().addAll(r.getMarkers());
		anchors.addAll(r.getAnchors());
	}
	
	void addMarkers(List<Marker> m) {
		getDetails().getMarkers().addAll(m);
	}
	
	public List<String> getAnchors() {
		return anchors;
	}
	
	void addIdentifier(String id) {
		identifiers.add(id);
	}
	
	public List<String> getIdentifiers() {
		return identifiers;
	}
	
	/**
	 * Gets the page space needed to render the rows. 
	 * @param rows
	 * @param defSpacing a value >= 1.0
	 * @return returns the space, in rows
	 */
	private static float rowsNeeded(Collection<? extends Row> rows, float defSpacing) {
		HeightCalculator c = new HeightCalculator(defSpacing);
		c.addRows(rows);
		return c.getCurrentHeight();
	}
	
	private static class HeightCalculator {
		private final float defSpacing;
		private float ret;
		private HeightCalculator(float defSpacing) {
			this.defSpacing = defSpacing < 1 ? 1 : defSpacing;
			this.ret = 0;
		}
		
		private HeightCalculator(HeightCalculator template) {
			this.defSpacing = template.defSpacing;
			this.ret = template.ret;
		}
		
		float getRowSpacing(Row r) {
			if (r.getRowSpacing()!=null && r.getRowSpacing()>=1) {
				return r.getRowSpacing();
			} else {
				return defSpacing;
			}
		}
		
		void addRow(Row r) {
			ret += getRowSpacing(r);
		}
		
		void addRows(Collection<? extends Row> rows) {
			ret += rows.stream().mapToDouble(this::getRowSpacing).sum();
		}

		float getCurrentHeight() {
			return ret;
		}
	}
	
	private float spaceNeeded() {
		return 	pageAreaSpaceNeeded() +
				finalRows.getOffsetHeight();
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
	
	private TextBorder buildBorder() {
		int fsize = borderStyle.getLeftBorder().length() + borderStyle.getRightBorder().length();
		int w = master.getFlowWidth() + fsize + pageMargin;
		return new TextBorder.Builder(w, fcontext.getSpaceCharacter()+"")
				.style(borderStyle)
				.outerLeftMargin(pageMargin)
				.padToSize(!TextBorderStyle.NONE.equals(borderStyle))
				.build();
	}
	
	private void addTopPageArea() {
        if (master.getPageArea()!=null && master.getPageArea().getAlignment()==PageAreaProperties.Alignment.TOP && !pageArea.isEmpty()) {
			finalRows.addAll(before);
			finalRows.addAll(pageArea);
			finalRows.addAll(after);
		}
	}

	/*
	 * The assumption is made that by now all pages have been added to the parent sequence and volume scopes
	 * have been set on the page struct.
	 */
	@Override
	public List<Row> getRows() {
		try {
			if (!finalRows.closed) {
				if (!hasRows) { // the header hasn't been added yet 
					//add the header
			        finalRows.addAll(fieldResolver.renderFields(getDetails(), pageTemplate.getHeader(), filter));
			      //add top page area
					addTopPageArea();
				}
		        float headerHeight = pageTemplate.getHeaderHeight();
		        if (!pageTemplate.getFooter().isEmpty() || borderStyle != TextBorderStyle.NONE || (master.getPageArea()!=null && master.getPageArea().getAlignment()==PageAreaProperties.Alignment.BOTTOM && !pageArea.isEmpty())) {
		            float areaSize = (master.getPageArea()!=null && master.getPageArea().getAlignment()==PageAreaProperties.Alignment.BOTTOM ? pageAreaSpaceNeeded() : 0);
		            while (Math.ceil(finalRows.getOffsetHeight() + areaSize) < getFlowHeight() + headerHeight) {
						finalRows.addRow(new RowImpl());
					}
					if (master.getPageArea()!=null && master.getPageArea().getAlignment()==PageAreaProperties.Alignment.BOTTOM && !pageArea.isEmpty()) {
						finalRows.addAll(before);
						finalRows.addAll(pageArea);
						finalRows.addAll(after);
					}
		            finalRows.addAll(fieldResolver.renderFields(getDetails(), pageTemplate.getFooter(), filter));
				}
			}
			return finalRows.getRows();
		} catch (PaginatorException e) {
			throw new RuntimeException("Pagination failed.", e);
		}
	}
	
	private class BorderManager {
		private HeightCalculator hc;
		private List<Row> ret2;
		private DistributedRowSpacing rs = null;
		private RowImpl r2 = null;
		private boolean closed;
		//This variable is used to compensate for the fact that the top border was calculated outside of the main logic before
        //and can be removed once the logic has been updated.
		private final float offsetHeight;
        
        private BorderManager(float defRowSpacing) {
        	this.hc = new HeightCalculator(defRowSpacing);
        	this.ret2 = new ArrayList<>();
        	this.closed = false;
        	if (!TextBorderStyle.NONE.equals(borderStyle)) {
        		addTopBorder();
        	}
        	this.offsetHeight = hc.getCurrentHeight();
        }
        
        private BorderManager(BorderManager template) {
        	this.hc = new HeightCalculator(template.hc);
        	this.ret2 = new ArrayList<>(template.ret2);
        	this.rs = template.rs;
        	this.closed = template.closed;
        	this.offsetHeight = template.offsetHeight;
        }

        //This method is used to compensate for the fact that the top border was calculated outside of the main logic before
        //and can be removed once the logic has been updated.        
        private float getOffsetHeight() {
        	return hc.getCurrentHeight() - offsetHeight;
        }

		private void addTopBorder() {
			RowImpl r = new RowImpl(border.getTopBorder());
			DistributedRowSpacing rs = master.distributeRowSpacing(master.getRowSpacing(), true);
			r.setRowSpacing(rs.spacing);
			addRowInner(r);
		}
		
		private void addAll(Collection<? extends RowImpl> rows) {
			for (RowImpl r : rows) {
				addRow(r);
			}
		}

		private void addRow(RowImpl row) {
			if (closed) {
				throw new IllegalStateException("Cannot add rows when closed.");
			}
			//does the previously added row require additional processing?
            if (rs!=null) {
				RowImpl s = null;
				for (int i = 0; i < rs.lines-1; i++) {
					s = new RowImpl(border.addBorderToRow(r2.getLeftMargin().getContent(), r2.getRightMargin().getContent()));
					s.setRowSpacing(rs.spacing);
					addRowInner(s);
				}
			}

			r2 = addBorders(row);
			addRowInner(r2);
			Float rs2 = row.getRowSpacing();
			if (!TextBorderStyle.NONE.equals(borderStyle)) {
				//distribute row spacing
				rs = master.distributeRowSpacing(rs2, true);
				r2.setRowSpacing(rs.spacing);
			} else {
				r2.setRowSpacing(rs2);
			}			
		}

		private void close() {
			if (closed) {
				return;
			}
			closed = true;
			if (!TextBorderStyle.NONE.equals(borderStyle)) {
                addRowInner(new RowImpl(border.getBottomBorder()));
			}
			if (ret2.size()>0) {
                RowImpl last = ((RowImpl)ret2.get(ret2.size()-1));
				if (master.getRowSpacing()!=1) {
					//set row spacing on the last row to 1.0
					last.setRowSpacing(1f);
				} else if (last.getRowSpacing()!=null) {
					//ignore row spacing on the last row if overall row spacing is 1.0
					last.setRowSpacing(null);
				}
			}
		}
		
		/**
		 * Only use from inside this class!!
		 * @param r the row to add
		 */
		private void addRowInner(Row r) {
			ret2.add(r);
			hc.addRow(r);
		}

		List<Row> getRows() {
			if (!closed) {
				close();
			}
			return ret2;
		}

	}
	
	private RowImpl addBorders(RowImpl row) {
		String res = "";
		if (row.getChars().length() > 0) {
			// remove trailing whitespace
			String chars = trailingWs.matcher(row.getChars()).replaceAll("");
			res = border.addBorderToRow(
					padLeft(master.getFlowWidth(), chars, row.getLeftMargin(), row.getRightMargin(), row.getAlignment(), fcontext.getSpaceCharacter()), 
					"");
		} else {
			if (!TextBorderStyle.NONE.equals(borderStyle)) {
				res = border.addBorderToRow(row.getLeftMargin().getContent(), row.getRightMargin().getContent());
			} else {
				if (!row.getLeftMargin().isSpaceOnly() || !row.getRightMargin().isSpaceOnly()) {
					res = TextBorder.addBorderToRow(
						master.getFlowWidth(), row.getLeftMargin().getContent(), "", row.getRightMargin().getContent(), fcontext.getSpaceCharacter()+"");
				} else {
					res = "";
				}
			}
		}
		int rowWidth = StringTools.length(res) + pageMargin;
		if (rowWidth > master.getPageWidth()) {
			throw new PaginatorException("Row is too long (" + rowWidth + "/" + master.getPageWidth() + ") '" + res + "'");
		}
		return new RowImpl(res);
	}
	
	static String padLeft(int w, RowImpl row, char space) {
		String chars = trailingWs.matcher(row.getChars()).replaceAll("");
		return padLeft(w, chars, row.getLeftMargin(), row.getRightMargin(), row.getAlignment(), space);
	}
	
	private static String padLeft(int w, String text, MarginProperties leftMargin, MarginProperties rightMargin, FormattingTypes.Alignment align, char space) {
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
	public int getPageIndex() {
		return details.getPageNumberIndex();
	}

	/**
	 * Gets the flow height for this page, i.e. the number of rows available for text flow
	 * @return returns the flow height
	 */
	int getFlowHeight() {
		return flowHeight;
	}
	
	void setKeepWithPreviousSheets(int value) {
		keepPreviousSheets = Math.max(value, keepPreviousSheets);
	}
	
	void setAllowsVolumeBreak(boolean value) {
		this.isVolBreakAllowed = value;
	}

	public boolean allowsVolumeBreak() {
		return isVolBreakAllowed;
	}

	public int keepPreviousSheets() {
		return keepPreviousSheets;
	}

	PageTemplate getPageTemplate() {
		return template;
	}
	
	public Integer getAvoidVolumeBreakAfter() {
		return volumeBreakAfterPriority;
	}
	
	void setAvoidVolumeBreakAfter(Integer value) {
		this.volumeBreakAfterPriority = value;
	}

	public PageDetails getDetails() {
		return details;
	}

}
