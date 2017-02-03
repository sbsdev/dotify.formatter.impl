package org.daisy.dotify.formatter.impl;

import java.util.List;

import org.daisy.dotify.api.writer.SectionProperties;
import org.daisy.dotify.writer.impl.Page;
import org.daisy.dotify.writer.impl.Section;

/**
 * Provides a sequence of pages.
 * 
 * @author Joel Håkansson
 */
class PageSequence extends View<PageImpl> implements Section {
	private final LayoutMaster master;
	private final int pageOffset;
	
	PageSequence(List<PageImpl> items, int fromIndex, LayoutMaster master, int pageOffset) { //, int pageOffset, FormatterFactory formatterFactory) {
		super(items, fromIndex);
		this.master = master;
		this.pageOffset = pageOffset;
	}
	
	void addPage(PageImpl p) {
		items.add(p);
		toIndex++;
	}

	/**
	 * Gets the layout master for this sequence
	 * @return returns the layout master for this sequence
	 */
	LayoutMaster getLayoutMaster() {
		return master;
	}

	
	int currentPageNumber() {
		return peek().getPageIndex()+1;
	}
	
	public int getPageNumberOffset() {
		return pageOffset;
	}

	@Override
	public SectionProperties getSectionProperties() {
		return master;
	}

	@Override
	public List<? extends Page> getPages() {
		return getItems();
	}

}
