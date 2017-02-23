package org.daisy.dotify.formatter.impl;

/**
 * Provides a page oriented structure
 * 
 * @author Joel Håkansson
 */
class PageStruct {
	private PageSequenceBuilder2 currentSeq;
	private int pageCount;

	PageStruct() {
		currentSeq = null;
		pageCount = 0;
	}

	void setCurrentSequence(PageSequenceBuilder2 seq) {
		currentSeq = seq;
	}

	int getCurrentPageOffset() {
		return currentSeq!=null?currentSeq.getCurrentPageOffset():0;
	}

	int size() {
		return pageCount;
	}
	
	void addPage(PageImpl p) {
		pageCount++;
	}

}