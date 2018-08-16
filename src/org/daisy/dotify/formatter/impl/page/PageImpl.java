package org.daisy.dotify.formatter.impl.page;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.daisy.dotify.api.formatter.FieldList;
import org.daisy.dotify.api.formatter.Marker;
import org.daisy.dotify.api.formatter.PageAreaProperties;
import org.daisy.dotify.api.translator.BrailleTranslator;
import org.daisy.dotify.api.writer.Row;
import org.daisy.dotify.formatter.impl.core.BorderManager;
import org.daisy.dotify.formatter.impl.core.FormatterContext;
import org.daisy.dotify.formatter.impl.core.HeightCalculator;
import org.daisy.dotify.formatter.impl.core.LayoutMaster;
import org.daisy.dotify.formatter.impl.core.PageTemplate;
import org.daisy.dotify.formatter.impl.core.PaginatorException;
import org.daisy.dotify.formatter.impl.datatype.VolumeKeepPriority;
import org.daisy.dotify.formatter.impl.row.RowImpl;
import org.daisy.dotify.formatter.impl.search.PageDetails;
import org.daisy.dotify.formatter.impl.writer.Page;


//FIXME: scope spread is currently implemented using document wide scope, i.e. across volume boundaries. This is wrong, but is better than the previous sequence scope.
/**
 * Provides a page object.
 * 
 * @author Joel Håkansson
 */
public class PageImpl implements Page {
	private final FieldResolver fieldResolver;
	private final PageDetails details;
	private final LayoutMaster master;
	private final FormatterContext fcontext;
	private final PageAreaContent pageAreaTemplate;
    private ArrayList<RowImpl> pageArea;
    private Iterator<FieldList> header;
    private Iterator<FieldList> footer;
    private final ArrayList<String> anchors;
    private final ArrayList<String> identifiers;
	private final int flowHeight;
	private final PageTemplate template;
	private final int pageMargin;
	private final BorderManager finalRows;

	private boolean hasRows;
	private boolean isVolBreakAllowed;
	private int keepPreviousSheets;
	private VolumeKeepPriority volumeBreakAfterPriority;
	private final BrailleTranslator filter;
	
	public PageImpl(FieldResolver fieldResolver, PageDetails details, LayoutMaster master, FormatterContext fcontext, PageAreaContent pageAreaTemplate) {
		this.fieldResolver = fieldResolver;
		this.details = details;
		this.master = master;
		this.fcontext = fcontext;
		this.pageAreaTemplate = pageAreaTemplate;

		this.pageArea = new ArrayList<>();
		this.anchors = new ArrayList<>();
		this.identifiers = new ArrayList<>();
		this.template = master.getTemplate(details.getPageNumber());
		this.header = template.getHeader().iterator();
		this.footer = template.getFooter().iterator();
        this.flowHeight = master.getFlowHeight(template);
		this.isVolBreakAllowed = true;
		this.keepPreviousSheets = 0;
		this.volumeBreakAfterPriority = VolumeKeepPriority.empty();
		this.pageMargin = ((details.getPageId().getOrdinal() % 2 == 0) ? master.getInnerMargin() : master.getOuterMargin());
		this.finalRows = new BorderManager(master, fcontext, pageMargin);
		this.hasRows = false;
		this.filter = fcontext.getDefaultTranslator();
	}
	
	public PageImpl(PageImpl template) {
		this.fieldResolver = template.fieldResolver;
		this.details = template.details;
		this.master = template.master;
		this.fcontext = template.fcontext;
		this.pageAreaTemplate = template.pageAreaTemplate;
		this.pageArea = pageArea==null ? null : new ArrayList<>(template.pageArea);
		{
			ArrayList<FieldList> list = new ArrayList<>();
			template.header.forEachRemaining(list::add);
			template.header = list.iterator();
			this.header = list.iterator();
		}
		{
			ArrayList<FieldList> list = new ArrayList<>();
			template.footer.forEachRemaining(list::add);
			template.footer = list.iterator();
			this.footer = list.iterator();
		}
	    this.anchors = new ArrayList<>(template.anchors);
	    this.identifiers = new ArrayList<>(template.identifiers);
		this.flowHeight = template.flowHeight;
		this.template = template.template;
		this.pageMargin = template.pageMargin;
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
		if (pageArea == null) {
			throw new IllegalStateException("Page area must be added before adding rows.");
		}
		pageArea.addAll(block);
	}
	
	void newRow(RowImpl r) {
		float pos = finalRows.getOffsetHeight();
		float headerHeight = template.getHeaderHeight();
		float flowIntoHeaderHeight = fieldResolver.getAllowFlowInHeader() ? template.validateAndAnalyzeHeader() : 0;
		while (pos < headerHeight - flowIntoHeaderHeight) {
			if (!header.hasNext()) {
				throw new RuntimeException("Coding error");
			}
			finalRows.addRow(fieldResolver.renderFields(getDetails(), header.next(), filter));
			pos = finalRows.getOffsetHeight();
		}
		while (header.hasNext()) {
			if (hasTopPageArea()) {
				finalRows.addRow(fieldResolver.renderFields(getDetails(), header.next(), filter));
				pos = finalRows.getOffsetHeight();
			} else {
				if (!hasRows) {
					getDetails().startsContentMarkers();
				}
				finalRows.addRow(fieldResolver.renderFields(getDetails(), header.next(), filter, r));
				hasRows = true;
				getDetails().getMarkers().addAll(r.getMarkers());
				anchors.addAll(r.getAnchors());
				identifiers.addAll(r.getIdentifiers());
				return;
			}
		}
		if (hasTopPageArea()) {
			addPageArea();
		}
		if (!hasRows) {
			getDetails().startsContentMarkers();
		}
		float flowIntoFooterHeight = template.validateAndAnalyzeFooter();
		if (pos < headerHeight - flowIntoHeaderHeight + getFlowHeight() - flowIntoFooterHeight) {
			finalRows.addRow(r);
		} else {
			if (!footer.hasNext()) {
				throw new RuntimeException(); // page full
			}
			if (hasBottomPageArea()) {
				throw new RuntimeException(); // page full
			}
			finalRows.addRow(fieldResolver.renderFields(getDetails(), footer.next(), filter, r));
		}
		hasRows = true;
		getDetails().getMarkers().addAll(r.getMarkers());
		anchors.addAll(r.getAnchors());
		identifiers.addAll(r.getIdentifiers());
	}
	
	void addMarkers(List<Marker> m) {
		getDetails().getMarkers().addAll(m);
	}
	
	public List<String> getAnchors() {
		return anchors;
	}
	
	void addIdentifiers(List<String> ids) {
		identifiers.addAll(ids);
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
	static float rowsNeeded(Collection<? extends Row> rows, float defSpacing) {
		HeightCalculator c = new HeightCalculator(defSpacing);
		c.addRows(rows);
		return c.getCurrentHeight();
	}

	// vertical position of the next body row, measured from the top edge of the page body area
	// negative if the row flows into the header
	float currentPosition() {
		float pos = finalRows.getOffsetHeight();
		float headerHeight = template.getHeaderHeight();
		float flowIntoHeaderHeight = fieldResolver.getAllowFlowInHeader() ? template.validateAndAnalyzeHeader() : 0;
		if (pos < headerHeight - flowIntoHeaderHeight) {
			if (hasTopPageArea()) {
				return pageAreaSpaceNeeded();
			} else {
				return - flowIntoHeaderHeight;
			}
		} else {
			return pos - headerHeight;
		}
	}
	
	// vertical position of the next body row, measured from the top edge of the page body area,
	// possibly extended into the header
	float currentPositionInFlow() {
		float pos = finalRows.getOffsetHeight();
		float headerHeight = template.getHeaderHeight();
		float flowIntoHeaderHeight = fieldResolver.getAllowFlowInHeader() ? template.validateAndAnalyzeHeader() : 0;
		if (pos < headerHeight - flowIntoHeaderHeight) {
			if (hasTopPageArea()) {
				return pageAreaSpaceNeeded() + flowIntoHeaderHeight;
			} else {
				return 0;
			}
		} else {
			return pos - headerHeight + flowIntoHeaderHeight;
		}
	}
	
	// space available for body rows
	// - this excludes space used for page-area
	// - this includes available space in headers and footers that allow text to flow into them
	float spaceAvailableInFlow() {
		float available = getFlowHeight() - pageAreaSpaceNeeded();
		float headerHeight = template.getHeaderHeight();
		float flowIntoHeaderHeight = fieldResolver.getAllowFlowInHeader() ? template.validateAndAnalyzeHeader() : 0;
		float flowIntoFooterHeight = template.validateAndAnalyzeFooter();
		float pos = finalRows.getOffsetHeight();
		if (pos < headerHeight - flowIntoHeaderHeight) {
			if (hasTopPageArea()) {
				available -= flowIntoHeaderHeight;
			} else if (hasBottomPageArea()) {
				available -= flowIntoFooterHeight;
			}
		} else {
			available -= (pos - headerHeight + flowIntoHeaderHeight);
			if (hasBottomPageArea()) {
				available -= flowIntoFooterHeight;
			}
		}
		return available;
	}
	
	private float staticAreaSpaceNeeded() {
		return rowsNeeded(pageAreaTemplate.getBefore(), master.getRowSpacing()) + rowsNeeded(pageAreaTemplate.getAfter(), master.getRowSpacing());
	}
	
	float pageAreaSpaceNeeded() {
		return ((pageArea != null && !pageArea.isEmpty()) ? staticAreaSpaceNeeded() + rowsNeeded(pageArea, master.getRowSpacing()) : 0);
	}
	
	private boolean hasTopPageArea() {
		return pageArea != null
			&& !pageArea.isEmpty()
			&& master.getPageArea() != null
			&& master.getPageArea().getAlignment() == PageAreaProperties.Alignment.TOP;
	}

	private boolean hasBottomPageArea() {
		return pageArea != null
			&& !pageArea.isEmpty()
			&& master.getPageArea() != null
			&& master.getPageArea().getAlignment() == PageAreaProperties.Alignment.BOTTOM;
	}
	
	private void addPageArea() {
		finalRows.addAll(pageAreaTemplate.getBefore());
		finalRows.addAll(pageArea);
		finalRows.addAll(pageAreaTemplate.getAfter());
		pageArea = null;
	}

	/*
	 * The assumption is made that by now all pages have been added to the parent sequence and volume scopes
	 * have been set on the page struct.
	 */
	@Override
	public List<Row> getRows() {
		try {
			if (!finalRows.isClosed()) {
				if (header.hasNext()) {
					finalRows.addAll(fieldResolver.renderFields(getDetails(), header, filter));
				}
				if (hasTopPageArea()) {
					addPageArea();
				}
				if (footer.hasNext() || finalRows.hasBorder() || hasBottomPageArea()) {
					float headerHeight = template.getHeaderHeight();
					float flowIntoHeaderHeight = fieldResolver.getAllowFlowInHeader() ? template.validateAndAnalyzeHeader() : 0;
					float flowIntoFooterHeight = template.validateAndAnalyzeFooter();
					float areaSize = hasBottomPageArea() ? pageAreaSpaceNeeded() : 0;
					float pos = finalRows.getOffsetHeight();
					while (pos < headerHeight - flowIntoHeaderHeight + getFlowHeight() - flowIntoFooterHeight - areaSize) {
						finalRows.addRow(new RowImpl());
						pos = finalRows.getOffsetHeight();
					}
					if (areaSize > 0) {
						addPageArea();
					}
					finalRows.addAll(fieldResolver.renderFields(getDetails(), footer, filter));
				}
			}
			return finalRows.getRows();
		} catch (PaginatorException e) {
			throw new RuntimeException("Pagination failed.", e);
		}
	}

	
	/**
	 * Get the page number, one based.
	 * 
	 * @return returns the page number
	 */
	public int getPageNumber() {
		return details.getPageNumber();
	}

	/**
	 * Gets the flow height for this page, i.e. the number of rows available for text flow
	 * @return returns the flow height
	 */
	int getFlowHeight() {
		return fieldResolver.getAllowFlowInHeader() ? flowHeight : flowHeight - template.validateAndAnalyzeHeader();
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
	
	public VolumeKeepPriority getAvoidVolumeBreakAfter() {
		return volumeBreakAfterPriority;
	}
	
	void setAvoidVolumeBreakAfter(VolumeKeepPriority value) {
		this.volumeBreakAfterPriority = value;
	}

	public PageDetails getDetails() {
		return details;
	}

}
