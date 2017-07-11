package org.daisy.dotify.formatter.impl.page;

/**
 * Provides state needed for a text flow.
 * 
 * @author Joel Håkansson
 */
public class PageStruct {
	private PageSequenceBuilder2 currentSeq;
	private int pageCount;

	public PageStruct() {
		currentSeq = null;
		pageCount = 0;
	}

	public PageStruct(PageStruct template) {
		this.currentSeq = template.currentSeq; //Copy not needed, since the only use for this is to check identity
		this.pageCount = template.pageCount;
	}

	public void setCurrentSequence(PageSequenceBuilder2 seq) {
		currentSeq = seq;
	}

	public int getCurrentPageOffset() {
		return currentSeq!=null?currentSeq.getCurrentPageOffset():0;
	}

	public int getPageCount() {
		return pageCount;
	}
	
	public void increasePageCount() {
		pageCount++;
	}

}