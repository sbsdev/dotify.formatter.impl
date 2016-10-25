package org.daisy.dotify.formatter.impl;

import java.util.List;

public class SheetGroup {
	private List<Sheet> units;
	private final VolumeSplitter splitter;
	private int overheadCount;
	private int sheetCount;

	public SheetGroup(VolumeSplitter splitter) {
		this.splitter = splitter;
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
