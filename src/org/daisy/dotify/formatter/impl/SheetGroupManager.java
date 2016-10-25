package org.daisy.dotify.formatter.impl;

import java.util.ArrayList;
import java.util.List;

class SheetGroupManager {
	private final SplitterLimit splitterLimit;
	private final List<SheetGroup> groups;
	private int indexInGroup = 0;
	private int index = 0;
	
	SheetGroupManager(SplitterLimit splitterLimit) {
		this.groups = new ArrayList<>();
		this.splitterLimit = splitterLimit;
	}

	SheetGroup add() {
		SheetGroup ret = new SheetGroup();
		ret.setSplitter(new EvenSizeVolumeSplitter(new SplitterLimit() {
			private final int groupIndex = groups.size();
			
			@Override
			public int getSplitterLimit(int volume) {
				int offset = 0;
				for (int i=0; i<groupIndex; i++) {
					offset += groups.get(i).getSplitter().getVolumeCount();
				}
				return splitterLimit.getSplitterLimit(volume + offset);
			}
		}));
		groups.add(ret);
		return ret;
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
	
	void nextVolume() {
		if (indexInGroup+1>=currentGroup().getSplitter().getVolumeCount()) {
			nextGroup();
			indexInGroup = 0;
		} else {
			indexInGroup++;
		}
	}
	
	boolean lastInGroup() {
		return indexInGroup==currentGroup().getSplitter().getVolumeCount();
	}

	int sheetsInCurrentVolume() {
		return currentGroup().getSplitter().sheetsInVolume(1+indexInGroup);
	}
	
	void nextGroup() {
		index++;
		if (groups.size()<index) {
			throw new IllegalStateException("No more groups.");
		}
	}
	
	void resetAll() {
		for (SheetGroup g : groups) {
			g.reset();
		}
		index = 0;
		indexInGroup = 0;
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
