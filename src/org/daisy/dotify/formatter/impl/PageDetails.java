package org.daisy.dotify.formatter.impl;

class PageDetails {
	private final boolean duplex;
	private final int ordinal;
	private final int globalStartIndex;
	
	PageDetails(boolean duplex, int ordinal, int globalStartIndex) {
		this.duplex = duplex;
		this.ordinal = ordinal;
		this.globalStartIndex = globalStartIndex;
	}

	boolean duplex() {
		return duplex;
	}
	
	int getGlobalStartIndex() {
		return globalStartIndex;
	}
	
	int getOrdinal() {
		return ordinal;
	}
	
	boolean isWithinSpreadScope(int offset, PageDetails other) {
		if (other==null) { 
			return ((offset == 1 && getOrdinal() % 2 == 1) ||
					(offset == -1 && getOrdinal() % 2 == 0));
		} else {
			return (
					(offset == 1 && getOrdinal() % 2 == 1 && duplex()==true) ||
					(offset == -1 && getOrdinal() % 2 == 0 && other.duplex()==true && other.getOrdinal() % 2 == 1)
				);
		}
	}

	
}
