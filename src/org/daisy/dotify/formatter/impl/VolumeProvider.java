package org.daisy.dotify.formatter.impl;

import java.util.ArrayList;
import java.util.List;

import org.daisy.dotify.common.layout.SplitPoint;
import org.daisy.dotify.common.layout.SplitPointCost;
import org.daisy.dotify.common.layout.SplitPointHandler;
import org.daisy.dotify.formatter.impl.DefaultContext.Space;

/**
 * Provides contents for a volume 
 * @author Joel Håkansson
 *
 */
public class VolumeProvider {
	private final Iterable<BlockSequence> blocks;
	private final FormatterContext fcontext;
	private final  CrossReferenceHandler crh;
	private final SheetGroupManager groups;
	private final SplitPointHandler<Sheet> volSplitter;
	
	private PageStructBuilder contentPaginator;
	private int pageIndex = 0;
	private int currentVolumeNumber=0;
	
	private final SplitterLimit splitterLimit;

	public VolumeProvider(Iterable<BlockSequence> blocks, SplitterLimit splitterLimit, FormatterContext fcontext, CrossReferenceHandler crh) {
		this.blocks = blocks;
		this.splitterLimit = splitterLimit;
		this.fcontext = fcontext;
		this.crh = crh;
		this.volSplitter = new SplitPointHandler<>();
		
		this.groups = new SheetGroupManager(splitterLimit);
		init();
	}
	
	private void init() {
		//FIXME: delete the following try/catch
		//This code is here for compatibility with regression tests and can be removed once
		//differences have been checked and accepted
		try {
			// make a preliminary calculation based on a contents only
			List<List<Sheet>> allUnits = new PageStructBuilder(fcontext, blocks, crh).paginateGrouped(new DefaultContext.Builder().space(Space.BODY).build());
			int volCount = 0;
			for (int i=0; i<allUnits.size(); i++) {
				SheetGroup g = groups.add();
				g.setUnits(allUnits.get(i));
				g.getSplitter().updateSheetCount(allUnits.get(i).size());
				volCount += g.getSplitter().getVolumeCount();
			}
			crh.setVolumeCount(volCount);
		} catch (PaginatorException e) {
			throw new RuntimeException("Error while formatting.", e);
		}
	}
	
	void prepare() {
		contentPaginator = new PageStructBuilder(fcontext, blocks, crh);
		try {
			List<List<Sheet>> allUnits = contentPaginator.paginateGrouped(new DefaultContext.Builder().space(Space.BODY).build());
			for (int i=0; i<allUnits.size(); i++) {
				groups.atIndex(i).setUnits(allUnits.get(i));
			}
		} catch (PaginatorException e) {
			throw new RuntimeException("Error while reformatting.", e);
		}
		pageIndex = 0;
		currentVolumeNumber=0;

		groups.resetAll();
	}
	
	List<Sheet> nextVolume(final int overhead, ArrayList<AnchorData> ad) {
		currentVolumeNumber++;
		groups.currentGroup().setOverheadCount(groups.currentGroup().getOverheadCount() + overhead);
		final int splitterMax = splitterLimit.getSplitterLimit(currentVolumeNumber);
		final int targetSheetsInVolume = (groups.lastInGroup()?splitterMax:groups.sheetsInCurrentVolume());
		volSplitter.setCost(new SplitPointCost<Sheet>(){
			@Override
			public double getCost(List<Sheet> units, int index) {
				int contentSheetTarget = targetSheetsInVolume - overhead;
				if (units.size()>index+1 && units.get(index+1).shouldStartNewVolume()) { 
					// The closer to 0 index is, the better. 
					// By giving it a negative cost, it is always preferred over the options below.
					return index-units.size();
				} else {
					Sheet lastSheet = units.get(index);
					double priorityPenalty = 0;
					int sheetCount = index + 1;
					// Calculates a maximum offset based on the maximum possible number of sheets
					double range = splitterMax * 0.2;
					if (!units.isEmpty()) {
						Integer avoid = lastSheet.getAvoidVolumeBreakAfterPriority();
						if (avoid!=null) {
							// Reverses 1-9 to 9-1 with bounds control and normalizes that to [1/9, 1]
							double normalized = ((10 - Math.max(1, Math.min(avoid, 9)))/9d);
							// Calculates a number of sheets that a high priority can beat
							priorityPenalty = range * normalized;
						}
					}
					// sets the preferred value to targetSheetsInVolume, where cost will be 0
					// including a small preference for bigger volumes
					double distancePenalty = Math.abs(contentSheetTarget - sheetCount) + (contentSheetTarget-sheetCount)*0.001;
					int unbreakablePenalty = lastSheet.isBreakable()?0:100;
					return distancePenalty + priorityPenalty + unbreakablePenalty;
				}
			}});
		SplitPoint<Sheet> sp = getSplitPoint(splitterMax-overhead);
		groups.currentGroup().setUnits(sp.getTail());
		List<Sheet> contents = sp.getHead();
		int pageCount = countPages(contents);
		// TODO: In a volume-by-volume scenario, how can we make this work
		contentPaginator.setVolumeScope(currentVolumeNumber, pageIndex, pageIndex+pageCount); 
		pageIndex += pageCount;
		for (Sheet sheet : contents) {
			for (PageImpl p : sheet.getPages()) {
				for (String id : p.getIdentifiers()) {
					crh.setVolumeNumber(id, currentVolumeNumber);
				}
				if (p.getAnchors().size()>0) {
					ad.add(new AnchorData(p.getPageIndex(), p.getAnchors()));
				}
			}
		}
		groups.currentGroup().setSheetCount(groups.currentGroup().getSheetCount() + contents.size());
		groups.nextVolume();
		return contents;
	}
	
	void update() {
		groups.updateAll();
	}
	
	void adjustVolumeCount() {
		groups.adjustVolumeCount();
	}
	
	int getVolumeCount() {
		return groups.getVolumeCount();
	}
	
	int countTotalSheets() {
		return groups.countTotalSheets();
	}
	
	int countRemainingPages() {
		return groups.countRemainingPages();
	}
	
	int countRemainingSheets() {
		return groups.countRemainingSheets();
	}
	
	/**
	 * The total number of pages provided so far
	 * @return the number of pages
	 */
	int getTotalPageCount() {
		return pageIndex;
	}
	
	private SplitPoint<Sheet> getSplitPoint(int contentSheets) {
		return volSplitter.split(contentSheets, true, groups.currentGroup().getUnits());
	}
	
	boolean hasNext() {
		return groups.hasNext();
	}

	static int countPages(List<Sheet> sheets) {
		int ret = 0;
		for (Sheet s : sheets) {
			ret += s.getPages().size();
		}
		return ret;
	}

}
