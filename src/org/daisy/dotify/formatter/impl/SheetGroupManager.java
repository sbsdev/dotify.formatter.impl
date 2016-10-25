package org.daisy.dotify.formatter.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SheetGroupManager {
	private final List<SheetGroup> groups;
	private int index = 0;

	public SheetGroupManager(List<SheetGroup> groups) {
		this.groups = new ArrayList<>(groups);
	}
	
	public SheetGroupManager(SheetGroup ... groups) {
		this.groups = Arrays.asList(groups);
	}
	
	SheetGroup atIndex(int index) {
		return groups.get(index);
	}
	
	int size() {
		return groups.size();
	}
	
	SheetGroup currentGroup() {
		return groups.get(index);
	}
	
	void nextGroup() {
		index++;
	}
	
	void resetAll() {
		for (SheetGroup g : groups) {
			g.reset();
		}
		index = 0;
	}
	
	boolean hasNext() {
		for (SheetGroup g : groups) {
			if (g.hasNext()) {
				return true;
			}
		}
		return false;
	}
	
	void updateAll() {
		for (SheetGroup g : groups) {
			g.getSplitter().updateSheetCount(g.countTotalSheets());
		}
	}
	
	void adjustVolumeCount() {
		for (SheetGroup g : groups) {
			if (g.hasNext()) {
				g.getSplitter().adjustVolumeCount(g.countTotalSheets());
			}
		}
	}
	
	int countTotalSheets() {
		int ret = 0;
		for (SheetGroup g : groups) {
			ret += g.countTotalSheets();
		}
		return ret;
	}
	
	int countRemainingSheets() {
		int ret = 0;
		for (SheetGroup g : groups) {
			ret += g.getUnits().size();
		}
		return ret;
	}
	
	int countRemainingPages() {
		int ret = 0;
		for (SheetGroup g : groups) {
			ret += VolumeProvider.countPages(g.getUnits());
		}
		return ret;
	}
	
	int getVolumeCount() {
		int ret = 0;
		for (SheetGroup g : groups) {
			ret += g.getSplitter().getVolumeCount();
		}
		return ret;
	}

}
