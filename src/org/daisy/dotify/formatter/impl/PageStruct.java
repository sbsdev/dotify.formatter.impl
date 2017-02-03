package org.daisy.dotify.formatter.impl;

import java.util.Iterator;
import java.util.List;
import java.util.Stack;

/**
 * Provides a page oriented structure
 * 
 * @author Joel Håkansson
 */
class PageStruct implements Iterable<PageSequence> {
	private final Stack<PageSequence> seqs;
	private final Stack<PageImpl> pages;

	PageStruct() {
		seqs = new Stack<>();
		pages = new Stack<>();
	}

	static String toString(List<Sheet> units) {
		StringBuilder debug = new StringBuilder();
		for (Sheet s : units) {
			debug.append("s");
			if (s.isBreakable()) {
				debug.append("-");
			}
		}
		return debug.toString();
	}

	boolean add(PageSequence seq) {
		return seqs.add(seq);
	}

	boolean empty() {
		return seqs.empty();
	}

	PageSequence peek() {
		return seqs.peek();
	}

	int size() {
		return seqs.size();
	}

	Stack<PageImpl> getPages() {
		return pages;
	}

	@Override
	public Iterator<PageSequence> iterator() {
		return seqs.iterator();
	}

}