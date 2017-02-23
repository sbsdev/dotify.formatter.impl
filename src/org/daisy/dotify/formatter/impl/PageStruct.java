package org.daisy.dotify.formatter.impl;

import java.util.Stack;

/**
 * Provides a page oriented structure
 * 
 * @author Joel Håkansson
 */
class PageStruct {
	private final Stack<PageImpl> pages;
	private PageSequenceBuilder2 currentSeq;

	PageStruct() {
		pages = new Stack<>();
		currentSeq = null;
	}

	void setCurrentSequence(PageSequenceBuilder2 seq) {
		currentSeq = seq;
	}

	int getCurrentPageOffset() {
		return currentSeq!=null?currentSeq.getCurrentPageOffset():0;
	}

	Stack<PageImpl> getPages() {
		return pages;
	}

}