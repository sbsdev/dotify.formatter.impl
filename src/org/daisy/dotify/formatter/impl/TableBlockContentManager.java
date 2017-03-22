package org.daisy.dotify.formatter.impl;

import java.util.List;

class TableBlockContentManager extends AbstractBlockContentManager {
	private final List<RowImpl> rows;
	private final int forceCount;

	TableBlockContentManager(int flowWidth, int minWidth, int forceCount, List<RowImpl> rows, RowDataProperties rdp, FormatterContext fcontext) {
		super(flowWidth, rdp, fcontext, minWidth);
		this.rows = rows;
		this.forceCount = forceCount;
	}
	
	@Override
	boolean supportsVariableWidth() {
		return false;
	}

	@Override
	int getRowCount() {
		return rows.size();
	}
	
	@Override
	RowImpl get(int i) {
		return rows.get(i);
	}

	@Override
	int getForceBreakCount() {
		return forceCount;
	}

}
