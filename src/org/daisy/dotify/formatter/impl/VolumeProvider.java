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
	private List<Sheet> units;
	private final SplitPointHandler<Sheet> volSplitter;
	
	private PageStructBuilder contentPaginator;
	private int pageIndex = 0;
	private int i=0;
	
	private final SplitterLimit splitterLimit;
	private final VolumeSplitter splitter;
	private int totalOverheadCount = 0;
	private int sheetCount = 0;

	public VolumeProvider(Iterable<BlockSequence> blocks, SplitterLimit splitterLimit, FormatterContext fcontext, CrossReferenceHandler crh) {
		this.blocks = blocks;
		this.splitterLimit = splitterLimit;
		this.fcontext = fcontext;
		this.crh = crh;
		this.volSplitter = new SplitPointHandler<>();
		this.splitter = new EvenSizeVolumeSplitter(crh, splitterLimit);
		
		//FIXME: delete the following try/catch
		//This code is here for compatibility with regression tests and can be removed once
		//differences have been checked and accepted
		try {
			// make a preliminary calculation based on a contents only
			List<Sheet> ps = new PageStructBuilder(fcontext, blocks, crh).paginate(new DefaultContext.Builder().space(Space.BODY).build());
			splitter.updateSheetCount(ps.size());
		} catch (PaginatorException e) {
			throw new RuntimeException("Error while formatting.", e);
		}
	}
	
	void prepare() {
		contentPaginator = new PageStructBuilder(fcontext, blocks, crh);
		try {
			units = contentPaginator.paginate(new DefaultContext.Builder().space(Space.BODY).build());
		} catch (PaginatorException e) {
			throw new RuntimeException("Error while reformatting.", e);
		}
		pageIndex = 0;
		i=0;
		totalOverheadCount = 0;
		sheetCount = 0;
	}
	
	List<Sheet> nextVolume(final int overhead, ArrayList<AnchorData> ad) {
		i++;
		totalOverheadCount += overhead;
		final int splitterMax = splitterLimit.getSplitterLimit(i);
		final int targetSheetsInVolume = (i==crh.getVolumeCount()?splitterMax:splitter.sheetsInVolume(i));
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
		units = sp.getTail();
		List<Sheet> contents = sp.getHead();
		int pageCount = countPages(contents);
		// TODO: In a volume-by-volume scenario, how can we make this work
		contentPaginator.setVolumeScope(i, pageIndex, pageIndex+pageCount); 
		pageIndex += pageCount;
		//totalPageCount += pageCount;
		for (Sheet sheet : contents) {
			for (PageImpl p : sheet.getPages()) {
				for (String id : p.getIdentifiers()) {
					crh.setVolumeNumber(id, i);
				}
				if (p.getAnchors().size()>0) {
					ad.add(new AnchorData(p.getPageIndex(), p.getAnchors()));
				}
			}
		}
		sheetCount += contents.size();
		return contents;
	}
	
	void update() {
		splitter.updateSheetCount(countTotalSheets());
	}
	
	void adjustVolumeCount() {
		splitter.adjustVolumeCount(countTotalSheets());
	}
	
	int countTotalSheets() {
		return sheetCount + totalOverheadCount + getRemaining().size();
	}
	
	int countRemainingPages() {
		return countPages(getRemaining());
	}
	
	/**
	 * The total number of pages provided so far
	 * @return the number of pages
	 */
	int getTotalPageCount() {
		return pageIndex;
	}
	
	private SplitPoint<Sheet> getSplitPoint(int contentSheets) {
		if (units.size()<=contentSheets) {
			SplitPoint<Sheet> ret = findManualVolumeBreak(contentSheets);
			if (ret!=null) {
				return ret;
			}
		}
		return volSplitter.split(contentSheets, true, units);
	}
	
	/**
	 * Since the cost function isn't used when units.size()<=contentSheets, we need this code in that case. 
	 * @param contentSheets
	 * @return returns the split point, or null if not found
	 */
	private SplitPoint<Sheet> findManualVolumeBreak(int contentSheets) {
		int i = 0;
		for (Sheet s : units) {
			if (s.shouldStartNewVolume() && i>0) {
				return new SplitPoint<Sheet>(units.subList(0, i), null, units.subList(i, units.size()), null, false);
			}
			if (i>=contentSheets) {
				break;
			}
			i++;
		}
		return null;
	}
	
	boolean hasNext() {
		return !units.isEmpty();
	}
	
	List<Sheet> getRemaining() {
		return units;
	}

	static int countPages(List<Sheet> sheets) {
		int ret = 0;
		for (Sheet s : sheets) {
			ret += s.getPages().size();
		}
		return ret;
	}

}
