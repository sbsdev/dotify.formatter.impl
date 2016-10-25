package org.daisy.dotify.formatter.impl;

import java.util.List;

/**
 * Provides a list of sheets.
 * @author Joel Håkansson
 *
 */
class SheetGroup {
	private List<Sheet> units;
	private VolumeSplitter splitter;
	private int overheadCount;
	private int sheetCount;

	SheetGroup() {
		reset();
	}
	
	void reset() {
		this.overheadCount = 0;
		this.sheetCount =  0;
	}
	
	int getOverheadCount() {
		return overheadCount;
	}
	
	void setOverheadCount(int value) {
		this.overheadCount = value;
	}
	
	int getSheetCount() {
		return sheetCount;
	}
	
	void setSheetCount(int value) {
		this.sheetCount = value;
	}

	List<Sheet> getUnits() {
		return units;
	}

	void setUnits(List<Sheet> units) {
		this.units = units;
	}
	
	void setSplitter(VolumeSplitter splitter) {
		this.splitter = splitter;
	}

	VolumeSplitter getSplitter() {
		return splitter;
	}
	
	int countTotalSheets() {
		return getOverheadCount() + getSheetCount() + getUnits().size();
	}
	
	boolean hasNext() {
		return !getUnits().isEmpty();
	}

}
