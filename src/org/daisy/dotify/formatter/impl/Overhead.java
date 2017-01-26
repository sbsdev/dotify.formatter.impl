package org.daisy.dotify.formatter.impl;

final class Overhead {
	private final int preContentSize;
	private final int postContentSize;
	
	/**
	 * @param preContentSize
	 * @param postContentSize
	 */
	Overhead(int preContentSize, int postContentSize) {
		super();
		this.preContentSize = preContentSize;
		this.postContentSize = postContentSize;
	}
	
	Overhead withPreContentSize(int s) {
		return new Overhead(s, getPostContentSize());
	}
	
	Overhead withPostContentSize(int s) {
		return new Overhead(getPreContentSize(), s);
	}

	int getPreContentSize() {
		return preContentSize;
	}
	int getPostContentSize() {
		return postContentSize;
	}
	
	int total() {
		return preContentSize + postContentSize;
	}
	

}
