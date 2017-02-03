package org.daisy.dotify.formatter.impl;

import org.daisy.dotify.formatter.impl.DefaultContext.Space;

class SequenceId {
	private final int ordinal;
	private final Space space;
	
	SequenceId(int ordinal, Space space) {
		this.ordinal = ordinal;
		this.space = space;
	}
	
	int getOrdinal() {
		return ordinal;
	}
	
	Space getSpace() {
		return space;
	}

}
