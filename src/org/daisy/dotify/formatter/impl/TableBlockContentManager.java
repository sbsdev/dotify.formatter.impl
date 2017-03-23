package org.daisy.dotify.formatter.impl;

import java.util.Collections;
import java.util.List;

class TableBlockContentManager extends AbstractBlockContentManager {
	private final List<RowImpl> rows;
	private final int forceCount;
    private int rowIndex;

	TableBlockContentManager(int flowWidth, int minWidth, int forceCount, List<RowImpl> rows, RowDataProperties rdp, FormatterContext fcontext) {
		super(flowWidth, rdp, fcontext, minWidth);
		this.rows = Collections.unmodifiableList(rows);
		this.forceCount = forceCount;
        initFields();
	}
	
	TableBlockContentManager(TableBlockContentManager template) {
		super(template);
		this.rows = template.rows;
		this.forceCount = template.forceCount;
		this.rowIndex = template.rowIndex;
	}
	
    private void initFields() {
    	rowIndex = 0;
    }
	
	@Override
	AbstractBlockContentManager copy() {
		return new TableBlockContentManager(this);
	}
	
	@Override
	boolean supportsVariableWidth() {
		return false;
	}

	@Override
	public int getRowCount() {
		return rows.size();
	}

    @Override
    void reset() {
    	super.reset();
    	initFields();
    }
	
    @Override
    RowImpl getNext() {
    	RowImpl ret = rows.get(rowIndex);
    	rowIndex++;
        return ret;
    }

    @Override
    boolean hasNext() {
        return rowIndex<rows.size();
    }

	@Override
	public int getForceBreakCount() {
		return forceCount;
	}

}
