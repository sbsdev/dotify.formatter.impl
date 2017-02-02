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
	
	int getPageId() {
		return globalStartIndex + ordinal;
	}
	
	/*
	 * This method is unused at the moment, but could be activated once additional scopes are added to the API,
	 * namely SPREAD_WITHIN_SEQUENCE
	 */
	@SuppressWarnings("unused") 
	private boolean isWithinSequenceSpreadScope(int offset) {
		return 	offset==0 ||
				(
					duplex() && 
					(
						(offset == 1 && getOrdinal() % 2 == 1) ||
						(offset == -1 && getOrdinal() % 2 == 0)
					)
				);
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
