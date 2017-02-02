package org.daisy.dotify.formatter.impl;

import java.util.List;

class View<T> {
	private final int fromIndex;
	protected final List<T> items;
	protected int toIndex;

	View(List<T> items, int fromIndex) {
		this(items, fromIndex, fromIndex);
	}
	
	View(List<T> items, int fromIndex, int toIndex) {
		this.items = items;
		this.fromIndex = fromIndex;
		this.toIndex = toIndex;
	}
	
	/**
	 * Gets the number of items in this sequence
	 * @return returns the number of items in this sequence
	 */
	public int size() {
		return toIndex-fromIndex;
	}

	public T get(int index) {
		if (index<0 || index>=size()) {
			throw new ArrayIndexOutOfBoundsException(index);
		}
		return items.get(index+fromIndex);
	}

	public List<T> getItems() {
		return items.subList(fromIndex, toIndex);
	}

	boolean isSequenceEmpty() {
		return toIndex-fromIndex == 0;
	}
	
	T peek() {
		return items.get(toIndex-1);
	}
	
	int toLocalIndex(int globalIndex) {
		return globalIndex-fromIndex;
	}
	
	/**
	 * Gets the index for the first item in this sequence, counting all preceding items in the document, zero-based. 
	 * @return returns the first index
	 */
	int getGlobalStartIndex() {
		return fromIndex;
	}

}
