package org.daisy.dotify.formatter.impl;

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

	void setCurrentSequence(PageSequenceBuilder2 seq) {
		currentSeq = seq;
	}

	int getCurrentPageOffset() {
		return currentSeq!=null?currentSeq.getCurrentPageOffset():0;
	}

	int getPageCount() {
		return pageCount;
	}
	
	void increasePageCount() {
		pageCount++;
	}

}