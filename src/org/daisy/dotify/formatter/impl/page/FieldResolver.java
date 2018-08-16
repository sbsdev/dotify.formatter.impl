package org.daisy.dotify.formatter.impl.page;

import java.util.ArrayList;
import java.util.function.Supplier;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.daisy.dotify.api.formatter.CompoundField;
import org.daisy.dotify.api.formatter.CurrentPageField;
import org.daisy.dotify.api.formatter.Field;
import org.daisy.dotify.api.formatter.FieldList;
import org.daisy.dotify.api.formatter.MarkerReferenceField;
import org.daisy.dotify.api.formatter.NoField;
import org.daisy.dotify.api.translator.BrailleTranslator;
import org.daisy.dotify.api.translator.DefaultTextAttribute;
import org.daisy.dotify.api.translator.Translatable;
import org.daisy.dotify.api.translator.TranslationException;
import org.daisy.dotify.common.text.StringTools;
import org.daisy.dotify.formatter.impl.core.BorderManager;
import org.daisy.dotify.formatter.impl.core.FormatterContext;
import org.daisy.dotify.formatter.impl.core.LayoutMaster;
import org.daisy.dotify.formatter.impl.core.PageTemplate;
import org.daisy.dotify.formatter.impl.core.PaginatorException;
import org.daisy.dotify.formatter.impl.row.RowImpl;
import org.daisy.dotify.formatter.impl.search.CrossReferenceHandler;
import org.daisy.dotify.formatter.impl.search.PageDetails;

class FieldResolver implements PageShape, Cloneable {
	private static final Pattern softHyphen = Pattern.compile("\u00ad");
	private final LayoutMaster master;
	private final FormatterContext fcontext;
	private final Supplier<CrossReferenceHandler> crh;
	private final PageDetails detailsTemplate;
	private boolean allowFlowInHeader;

	FieldResolver(LayoutMaster master, FormatterContext fcontext, Supplier<CrossReferenceHandler> crh, PageDetails detailsTemplate) {
		this.master = master;
		this.fcontext = fcontext;
		this.crh = crh;
		this.detailsTemplate = detailsTemplate;
		allowFlowInHeader = true;
	}
	
	// FIXME: make immutable
	void setAllowFlowInHeader(boolean allow) {
		allowFlowInHeader = allow;
	}
	
	boolean getAllowFlowInHeader() {
		return allowFlowInHeader;
	}
	
	List<RowImpl> renderFields(PageDetails p, Iterable<FieldList> fields, BrailleTranslator translator) throws PaginatorException {
		return renderFields(p, fields.iterator(), translator);
	}
	
	List<RowImpl> renderFields(PageDetails p, Iterator<FieldList> fields, BrailleTranslator translator) throws PaginatorException {
		ArrayList<RowImpl> ret = new ArrayList<>();
		while (fields.hasNext()) {
			ret.add(renderFields(p, fields.next(), translator));
		}
		return ret;
	}
	
	RowImpl renderFields(PageDetails p, FieldList fields, BrailleTranslator translator) throws PaginatorException {
		return renderFields(p, fields, translator, null);
	}
	
	RowImpl renderFields(PageDetails p, FieldList fields, BrailleTranslator translator, RowImpl bodyRow) throws PaginatorException {
		int width = master.getFlowWidth();
		char space = fcontext.getSpaceCharacter();
		List<String> distributedRow; {
			String padding = space+"";
			try {
				distributedRow = distribute(p, fields, width, padding, translator);
			} catch (PaginatorToolsException e) {
				throw new PaginatorException("Error while rendering header/footer", e);
			}
		}
		int length = 0;
		for (String s : distributedRow) {
			if (s != null) {
				length += s.length();
			}
		}
		StringBuffer sb = new StringBuffer();
		for (String s : distributedRow) {
			if (s != null) {
				sb.append(s);
			} else {
				float rowSpacing = (bodyRow != null && bodyRow.getRowSpacing() != null)
					? bodyRow.getRowSpacing()
					: master.getRowSpacing();
				if (rowSpacing != 1.0f) {
					throw new RuntimeException("Text can only flow in <field allow-text-flow=\"true\"/> when row-spacing of text is '1'.");
				}
				String chars = bodyRow != null
					? BorderManager.padLeft(width - length,
					                        bodyRow.getChars(),
					                        bodyRow.getLeftMargin(),
					                        bodyRow.getRightMargin(),
					                        bodyRow.getAlignment(),
					                        space)
					: "";
				if (chars.length() + length > width) {
					throw new RuntimeException("Row does not fit in empty field in header/footer");
				}
				sb.append(chars);
				if (chars.length() + length <= width) {
					sb.append(StringTools.fill(space, width - length - chars.length()));
				}
				bodyRow = null;
			}
		}
		if (bodyRow != null) {
			throw new RuntimeException("Coding error");
		}
		return new RowImpl.Builder(sb.toString()).rowSpacing(fields.getRowSpacing()).build();
	}
	
	private List<String> distribute(PageDetails p, FieldList chunks, int width, String padding, BrailleTranslator translator)
			throws PaginatorToolsException {
		List<String> chunksResolved = resolveFields(p, chunks, width, padding, translator);
		List<String> chunksWithOutFlow = new ArrayList<>();
		List<Boolean> flowPositions = new ArrayList<>();
		for (String f : chunksResolved) {
			if (f == null) {
				f = "";
				flowPositions.add(true);
			} else {
				flowPositions.add(false);
			}
			chunksWithOutFlow.add(f);
		}
		List<String> chunksWithPadding = PaginatorTools.distributeRetain(
			chunksWithOutFlow, width, padding,
			fcontext.getConfiguration().isAllowingTextOverflowTrimming()
				? PaginatorTools.DistributeMode.EQUAL_SPACING_TRUNCATE
				: PaginatorTools.DistributeMode.EQUAL_SPACING);
		List<String> ret = new ArrayList<>(); {
			// copy chunks
			int i = 0;
			for (String s : chunksWithPadding) {
				if (i % 2 == 0) {
					if (flowPositions.get(i/2)) {
						ret.add(null);
					} else {
						ret.add(s);
					}
				}
				i++;
			}
			// add padding
			i = 0;
			for (String s : chunksWithPadding) {
				if (i % 2 == 1) {
					if (ret.get((i-1)/2) == null || ret.get((i+1)/2) == null) {
						// drop padding before and after flow positions
					} else {
						// append to following chunk
						ret.set((i+1)/2, s + ret.get((i+1)/2));
					}
				}
				i++;
			}
		}
		return ret;
	}
	
	private List<String> resolveFields(PageDetails p, FieldList chunks, int width, String padding, BrailleTranslator translator)
			throws PaginatorToolsException {
		List<String> chunkF = new ArrayList<>();
		for (Field f : chunks.getFields()) {
			DefaultTextAttribute.Builder b = new DefaultTextAttribute.Builder(null);
			String resolved = resolveField(f, p, b);
			if (resolved != null) {
				resolved = softHyphen.matcher(resolved).replaceAll("");
				Translatable.Builder tr = Translatable
					.text(fcontext.getConfiguration().isMarkingCapitalLetters()?resolved:resolved.toLowerCase())
					.hyphenate(false);
				if (resolved.length()>0) {
					tr.attributes(b.build(resolved.length()));
				}
				try {
					resolved = translator.translate(tr.build()).getTranslatedRemainder();
				} catch (TranslationException e) {
					throw new PaginatorToolsException(e);
				}
			}
			chunkF.add(resolved);
		}
		return chunkF;
	}
	
	/*
	 * Note that the result of this function is not constant because getPageInSequenceWithOffset(),
	 * getPageInVolumeWithOffset() and shouldAdjustOutOfBounds() are not constant.
	 */
	private String resolveField(Field field, PageDetails p, DefaultTextAttribute.Builder b) {
		if (field instanceof NoField) {
			return null;
		}
		String ret;
		DefaultTextAttribute.Builder b2 = new DefaultTextAttribute.Builder(field.getTextStyle());
		if (field instanceof CompoundField) {
			ret = resolveCompoundField((CompoundField)field, p, b2);
		} else if (field instanceof MarkerReferenceField) {
			ret = crh.get().findMarker(p.getPageId(), (MarkerReferenceField)field);
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

	private String resolveCompoundField(CompoundField f, PageDetails p, DefaultTextAttribute.Builder b) {
		return f.stream().map(f2 -> resolveField(f2, p, b)).collect(Collectors.joining());
	}
	
	private static String resolveCurrentPageField(CurrentPageField f, PageDetails p) {
		int pagenum = p.getPageNumber();
		return f.getNumeralStyle().format(pagenum);
	}

	@Override
	public int getWidth(int pagenum, int rowOffset) {
		while (true) {
			// Iterates until rowOffset is less than the height of the page.
			// Since each page could potentially have a different flow height we cannot
			// simply divide, we have to retrieve the page template for each page
			// and look at the actual value...
			PageTemplate p = master.getTemplate(pagenum);
			int flowHeight = master.getFlowHeight(p);
			if (rowOffset>flowHeight) {
				if (flowHeight<=0) {
					throw new RuntimeException("Error in code.");
				}
				// subtract the height of the page we're on
				rowOffset-=flowHeight;
				// move to the next page
				pagenum++;
			} else {
				break;
			}
		}
		return getWidth(detailsTemplate.with(pagenum-1), rowOffset);
	}

	private int getWidth(PageDetails details, int rowOffset) {
		PageTemplate p = master.getTemplate(details.getPageNumber());
		int flowHeight = master.getFlowHeight(p);
		int flowHeader = p.validateAndAnalyzeHeader();
		if (!allowFlowInHeader) {
			flowHeight -= flowHeader;
			flowHeader = 0;
		}
		int flowFooter = p.validateAndAnalyzeFooter();
		if (flowHeader+flowFooter>0) {
			rowOffset = rowOffset % flowHeight;
			if (rowOffset<flowHeader) {
				//this is a shared row
				int start = p.getHeader().size()-flowHeader;
				return getAvailableForNoField(details, p.getHeader().get(start+rowOffset));
			} else if (rowOffset>=flowHeight-flowFooter) {
				//this is a shared row
				int rowsLeftOnPage = flowHeight-rowOffset;
				return getAvailableForNoField(details, p.getFooter().get(flowFooter-rowsLeftOnPage));
			} else {
				return master.getFlowWidth();
			}
		} else {
			return master.getFlowWidth();
		}
	}

	private int getAvailableForNoField(PageDetails details, FieldList list) {
		try {
			List<String> parts = resolveFields(details, list, master.getFlowWidth(), fcontext.getSpaceCharacter()+"", fcontext.getDefaultTranslator());
			int size = parts.stream().mapToInt(str -> str == null ? 0 : str.length()).sum();
			return master.getFlowWidth()-size;
		} catch (PaginatorToolsException e) {
			throw new RuntimeException("", e);
		}
	}
	
	@Override
	public FieldResolver clone() {
		FieldResolver clone;
		try {
			clone = (FieldResolver)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new InternalError("coding error");
		}
		return clone;
	}
}
