package org.daisy.dotify.formatter.impl.page;

/**
 * Provides state needed for a text flow.
 * 
 * @author Joel Håkansson
 */
public class PageStruct {
	private int pageOffset;
	private int pageCount;

	public PageStruct() {
		pageOffset = 0;
		pageCount = 0;
	}

	public PageStruct(PageStruct template) {
		this.pageOffset = template.pageOffset;
		this.pageCount = template.pageCount;
	}
	
	/**
	 * Copies this object unless it is the same as the other object,
	 * in which case no copy is created and the object is returned.
	 * @param other
	 * @return
	 */
	public PageStruct copyUnlessSameObject(PageStruct other) {
		return this==other?this:new PageStruct(this); 
	}

	public void setDefaultPageOffset(int value) {
		pageOffset = value;
	}

	public int getDefaultPageOffset() {
		return pageOffset;
	}

	/**
	 * This is used for searching and MUST be continuous. Do not use for page numbers.
	 * @return returns the page count
	 */
	public int getPageCount() {
		return pageCount;
	}
	
	/**
	 * Advance to the next page.
	 */
	public void increasePageCount() {
		pageCount++;
	}

}