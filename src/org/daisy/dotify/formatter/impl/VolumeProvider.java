package org.daisy.dotify.formatter.impl;

import java.util.ArrayList;
import java.util.List;

import org.daisy.dotify.common.split.SplitPoint;
import org.daisy.dotify.common.split.SplitPointCost;
import org.daisy.dotify.common.split.SplitPointDataSource;
import org.daisy.dotify.common.split.SplitPointHandler;
import org.daisy.dotify.common.split.StandardSplitOption;
import org.daisy.dotify.formatter.impl.DefaultContext.Space;

/**
 * Provides contents in volumes.
 *  
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

	/**
	 * Creates a new volume provider with the specifed parameters
	 * @param blocks the block sequences
	 * @param splitterLimit the splitter limit
	 * @param fcontext the formatter context
	 * @param crh the cross reference handler
	 */
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
		// make a preliminary calculation based on a contents only
		Iterable<SplitPointDataSource<Sheet>> allUnits = new PageStructBuilder(fcontext, blocks, crh).paginateGrouped(new DefaultContext.Builder().space(Space.BODY).build());
		int volCount = 0;
		for (SplitPointDataSource<Sheet> data : allUnits) {
			SheetGroup g = groups.add();
			g.setUnits(data);
			g.getSplitter().updateSheetCount(data.getRemaining().size());
			volCount += g.getSplitter().getVolumeCount();
		}
		crh.setVolumeCount(volCount);
		/*catch (PaginatorException e) {
			throw new RuntimeException("Error while formatting.", e);
		}*/
	}
	
	/**
	 * Resets the volume provider to its initial state (with some information preserved). 
	 */
	void prepare() {
		contentPaginator = new PageStructBuilder(fcontext, blocks, crh);
		Iterable<SplitPointDataSource<Sheet>> allUnits = contentPaginator.paginateGrouped(new DefaultContext.Builder().space(Space.BODY).build());
		int i=0;
		for (SplitPointDataSource<Sheet> unit : allUnits) {
			groups.atIndex(i).setUnits(unit);
			i++;
		}
		/* catch (PaginatorException e) {
			throw new RuntimeException("Error while reformatting.", e);
		}*/
		pageIndex = 0;
		currentVolumeNumber=0;

		groups.resetAll();
	}
	
	/**
	 * Gets the contents of the next volume
	 * @param overhead the number of sheets in this volume that's not part of the main body of text
	 * @param ad the anchor data
	 * @return returns the contents of the next volume
	 */
	List<Sheet> nextVolume(final int overhead, ArrayList<AnchorData> ad) {
		currentVolumeNumber++;
		groups.currentGroup().setOverheadCount(groups.currentGroup().getOverheadCount() + overhead);
		final int splitterMax = splitterLimit.getSplitterLimit(currentVolumeNumber);
		final int targetSheetsInVolume = (groups.lastInGroup()?splitterMax:groups.sheetsInCurrentVolume());
		//Not using lambda for now, because it's noticeably slower.
		SplitPointCost<Sheet> cost = new SplitPointCost<Sheet>(){
			@Override
			public double getCost(SplitPointDataSource<Sheet> units, int index, int breakpoint) {
				int contentSheetTarget = targetSheetsInVolume - overhead;
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
			}};
		SplitPoint<Sheet> sp = volSplitter.split(splitterMax-overhead, groups.currentGroup().getUnits(), cost, StandardSplitOption.ALLOW_FORCE);
		groups.currentGroup().setUnits(sp.getTail());
		List<Sheet> contents = sp.getHead();
		int pageCount = Sheet.countPages(contents);
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
	
	/**
	 * Informs the volume provider that the caller has finished requesting volumes.
	 * <b>Note: only use after all volumes have been calculated.</b>  
	 */
	void update() {
		groups.updateAll();
		//TODO: call adjustVolumeCount from here and remove it from the caller's responsibility
	}
	
	/**
	 * Informs the volume provider to adjust its volume calculation.
	 * <b>Note: only use after all volumes have been calculated.</b>
	 */
	void adjustVolumeCount() {
		groups.adjustVolumeCount();
	}
	
	/**
	 * Gets the total number of volumes.
	 * @return returns the number of volumes
	 */
	int getVolumeCount() {
		return groups.getVolumeCount();
	}
	
	/**
	 * Counts the total number of sheets.
	 * <b>Note: only use after all volumes have been calculated.</b>
	 * @return returns the total number of sheets.
	 */
	int countTotalSheets() {
		return groups.countTotalSheets();
	}
	
	/**
	 * Counts the remaining pages. 
	 * <b>Note: only use after all volumes have been calculated.</b>
	 * @return returns the number of remaining pages
	 */
	int countRemainingPages() {
		return groups.countRemainingPages();
	}
	
	/**
	 * Counts the remaining sheets.
	 * <b>Note: only use after all volumes have been calculated.</b>
	 * @return returns the number of remaining sheets
	 */
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

	/**
	 * Returns true if there is content left or left behind.
	 * @return returns true if there is more content, false otherwise
	 */
	boolean hasNext() {
		return groups.hasNext();
	}

}