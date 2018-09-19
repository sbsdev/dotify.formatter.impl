package org.daisy.dotify.formatter.impl;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.daisy.dotify.api.formatter.Context;
import org.daisy.dotify.api.formatter.SequenceProperties.SequenceBreakBefore;
import org.daisy.dotify.common.splitter.SplitPoint;
import org.daisy.dotify.common.splitter.SplitPointCost;
import org.daisy.dotify.common.splitter.SplitPointDataSource;
import org.daisy.dotify.common.splitter.SplitPointHandler;
import org.daisy.dotify.common.splitter.SplitPointSpecification;
import org.daisy.dotify.common.splitter.StandardSplitOption;
import org.daisy.dotify.formatter.impl.core.PaginatorException;
import org.daisy.dotify.formatter.impl.datatype.VolumeKeepPriority;
import org.daisy.dotify.formatter.impl.page.BlockSequence;
import org.daisy.dotify.formatter.impl.page.PageImpl;
import org.daisy.dotify.formatter.impl.page.RestartPaginationException;
import org.daisy.dotify.formatter.impl.search.AnchorData;
import org.daisy.dotify.formatter.impl.search.CrossReferenceHandler;
import org.daisy.dotify.formatter.impl.search.DefaultContext;
import org.daisy.dotify.formatter.impl.search.Space;
import org.daisy.dotify.formatter.impl.sheet.PageCounter;
import org.daisy.dotify.formatter.impl.sheet.SectionBuilder;
import org.daisy.dotify.formatter.impl.sheet.Sheet;
import org.daisy.dotify.formatter.impl.sheet.SheetDataSource;
import org.daisy.dotify.formatter.impl.sheet.SheetGroup;
import org.daisy.dotify.formatter.impl.sheet.SheetGroupManager;
import org.daisy.dotify.formatter.impl.sheet.SplitterLimit;
import org.daisy.dotify.formatter.impl.sheet.VolumeImpl;
import org.daisy.dotify.formatter.impl.volume.VolumeSequence;
import org.daisy.dotify.formatter.impl.volume.VolumeTemplate;

/**
 * Provides contents in volumes.
 *  
 * @author Joel Håkansson
 *
 */
public class VolumeProvider {
	private static final Logger logger = Logger.getLogger(VolumeProvider.class.getCanonicalName());
	private static final int DEFAULT_SPLITTER_MAX = 50;
	private final List<BlockSequence> blocks;
	private final AtomicBoolean dirty;
	// FIXME: only use in init (and done?)
	private CrossReferenceHandler crh;
	private DefaultContext initialContext;
	private SheetGroupManager groups;
	private final SplitPointHandler<Sheet,SheetDataSource> volSplitter;

	private int pageIndex = 0;
	private int currentVolumeNumber=0;
	private boolean init = false;
	private int j = 1;
	
	private final SplitterLimit splitterLimit;
    private final Stack<VolumeTemplate> volumeTemplates;
    private final LazyFormatterContext context;

	/**
	 * Creates a new volume provider with the specifed parameters
	 * @param blocks the block sequences
	 * @param volumeTemplates volume templates
	 * @param context the formatter context
	 */
	VolumeProvider(List<BlockSequence> blocks, Stack<VolumeTemplate> volumeTemplates, LazyFormatterContext context) {
		this.blocks = blocks;
		this.splitterLimit = volumeNumber -> {
            final Context c = DefaultContext.from()
                    .currentVolume(volumeNumber)
                    .build();
            Optional<VolumeTemplate> ot = volumeTemplates.stream().filter(t -> t.appliesTo(c)).findFirst();
            if (ot.isPresent()) {
                return ot.get().getVolumeMaxSize();
            } else {
                logger.fine("Found no applicable volume template.");
                return DEFAULT_SPLITTER_MAX;                
            }
        };
		this.volumeTemplates = volumeTemplates;
		this.context = context;
		this.dirty = new AtomicBoolean(false);
		this.crh = new CrossReferenceHandler(() -> dirty.set(true));
		this.volSplitter = new SplitPointHandler<>();
	}
		
	/**
	 * Resets the volume provider to its initial state (with some information preserved). 
	 * @return the estimated number of volumes
	 * @throws RestartPaginationException
	 */
	int prepare() {
		PageCounter initialCounter = new PageCounter();
		initialContext = DefaultContext.from().refs(crh).space(Space.BODY).build();
		// FIXME: move to constructor
		if (!init) {
			groups = new SheetGroupManager(splitterLimit);
			// make a preliminary calculation based on a contents only
			Iterable<SheetDataSource> allUnits = prepareToPaginateWithVolumeGroups(blocks);
			int volCount = 0;
			for (SheetDataSource data : allUnits) {
				data.initialize(initialCounter, initialContext);
				SheetGroup g = groups.add();
				g.setUnits(data);
				int sheetCount = data.countRemainingSheets();
				g.getSplitter().updateSheetCount(sheetCount, sheetCount);
				volCount += g.getSplitter().getVolumeCount();
			}
			DefaultContext.Builder b = initialContext.builder();
			b.getRefs().setVolumeCount(volCount);
			initialContext = b.build();
			//if there is an error, we won't have a proper initialization and have to retry from the beginning
			init = true;
		}
		Iterable<SheetDataSource> allUnits = prepareToPaginateWithVolumeGroups(blocks);
		int i=0;
		for (SheetDataSource unit : allUnits) {
			if (i == 0)
				unit.initialize(initialCounter, initialContext);
			groups.atIndex(i).setUnits(unit);
			i++;
		}
		pageIndex = 0;
		currentVolumeNumber=0;
		groups.resetAll();
		initialContext = null;
		return getContext().getRefs().getVolumeCount();
	}
	
	private DefaultContext getContext() {
		if (initialContext != null) {
			return initialContext;
		} else {
			return groups.currentGroup().getUnits().getContext();
		}
	}
	
	private void modifyContext(Consumer<DefaultContext.Builder> modifier) {
		if (initialContext != null) {
			DefaultContext.Builder b = initialContext.builder();
			modifier.accept(b);
			initialContext = b.build();
		} else {
			groups.currentGroup().getUnits().modifyContext(modifier);
		}
	}
	
	private void modifyRefs(Consumer<CrossReferenceHandler.Builder> modifier) {
		modifyContext(c -> modifier.accept(c.getRefs()));
	}
	
	/**
	 * @return returns the next volume
	 * @throws RestartPaginationException if pagination should be restarted
	 */
	VolumeImpl nextVolume() {
		currentVolumeNumber++;
		VolumeImpl volume = new VolumeImpl(getContext().getRefs().getOverhead(currentVolumeNumber));
		ArrayList<AnchorData> ad = new ArrayList<>();
		volume.setPreVolData(updateVolumeContents(currentVolumeNumber, ad, true));
		volume.setBody(nextBodyContents(volume.getOverhead().total(), ad));
		
		if (logger.isLoggable(Level.FINE)) {
			logger.fine("Sheets  in volume " + currentVolumeNumber + ": " + (volume.getVolumeSize()) + 
					", content:" + volume.getBodySize() +
					", overhead:" + volume.getOverhead());
		}
		volume.setPostVolData(updateVolumeContents(currentVolumeNumber, ad, false));
		modifyRefs(refs -> {
				refs.setSheetsInVolume(currentVolumeNumber, volume.getBodySize() + volume.getOverhead().total())
				    //.setPagesInVolume(i, value)
				    .setAnchorData(currentVolumeNumber, ad)
				    .setOverhead(currentVolumeNumber, volume.getOverhead());
			});
		groups.nextVolume();
		return volume;

	}
	
	/**
	 * Gets the contents of the next volume
	 * @param overhead the number of sheets in this volume that's not part of the main body of text
	 * @param ad the anchor data
	 * @return returns the contents of the next volume
	 */
	private SectionBuilder nextBodyContents(final int overhead, ArrayList<AnchorData> ad) {
		groups.currentGroup().setOverheadCount(groups.currentGroup().getOverheadCount() + overhead);
		final int splitterMax = splitterLimit.getSplitterLimit(currentVolumeNumber);
		final int targetSheetsInVolume = (groups.lastInGroup()?splitterMax:groups.sheetsInCurrentVolume());
		//Not using lambda for now, because it's noticeably slower.
		SplitPointCost<Sheet> cost = new SplitPointCost<Sheet>(){
			@Override
			public double getCost(Sheet lastSheet, int index, int breakpoint) {
				int contentSheetTarget = targetSheetsInVolume - overhead;
				double priorityPenalty = 0;
				int sheetCount = index + 1;
				// Calculates a maximum offset based on the maximum possible number of sheets
				double range = splitterMax * 0.4;
				VolumeKeepPriority avoid = lastSheet.getAvoidVolumeBreakAfterPriority();
				if (avoid.hasValue()) {
					// Reverses 1-9 to 9-1 with bounds control and normalizes that to [1/9, 1]
					double normalized = ((10 - avoid.getValue())/9d);
					// Calculates a number of sheets that a high priority can beat
					priorityPenalty = range * normalized;
				}
				// sets the preferred value to targetSheetsInVolume, where cost will be 0
				// including a small preference for bigger volumes
				double distancePenalty = Math.abs(contentSheetTarget - sheetCount) + (contentSheetTarget-sheetCount)*0.001;
				int unbreakablePenalty = lastSheet.isBreakable()?0:100;
				return distancePenalty + priorityPenalty + unbreakablePenalty;
			}};
		SplitPoint<Sheet,SheetDataSource> sp;

		SheetDataSource data = groups.currentGroup().getUnits();
		SplitPointSpecification spec = volSplitter.find(splitterMax,
				overhead, data,
				cost, StandardSplitOption.ALLOW_FORCE);
		sp = volSplitter.split(spec, overhead, groups.currentGroup().getUnits());
		/*
			sp = volSplitter.split(splitterMax-overhead, 
					groups.currentGroup().getUnits(),
					cost, StandardSplitOption.ALLOW_FORCE);
		*/
		groups.currentGroup().setUnits(sp.getTail());
		List<Sheet> contents = sp.getHead();
		int pageCount = Sheet.countPages(contents);
		modifyRefs(refs -> refs.setVolumeScope(currentVolumeNumber, pageIndex, pageIndex+pageCount));

		pageIndex += pageCount;
		SectionBuilder sb = new SectionBuilder();
		for (Sheet sheet : contents) {
			for (PageImpl p : sheet.getPages()) {
				for (String id : p.getIdentifiers()) {
					modifyRefs(refs -> refs.setVolumeNumber(id, currentVolumeNumber));
				}
				if (p.getAnchors().size()>0) {
					ad.add(new AnchorData(p.getAnchors(), p.getPageNumber()));
				}
			}
			sb.addSheet(sheet);
		}
		groups.currentGroup().setSheetCount(groups.currentGroup().getSheetCount() + contents.size());
		return sb;
	}
	
	private SectionBuilder updateVolumeContents(int volumeNumber, ArrayList<AnchorData> ad, boolean pre) {
		Context c = getContext().builder()
						.currentVolume(volumeNumber)
						.build();
		try {
			ArrayList<BlockSequence> ib = new ArrayList<>();
			for (VolumeTemplate t : volumeTemplates) {
				if (t.appliesTo(c)) { // CrossReferenceHandler used for getting volume count
					for (VolumeSequence seq : (pre?t.getPreVolumeContent():t.getPostVolumeContent())) {
						// - CrossReferenceHandler used for getting volume of references in table-of-contents and list-of-references
						// - Context used for getting current volume
						BlockSequence s = seq.getBlockSequence(context.getFormatterContext(), c, getContext().getRefs());
						if (s!=null) {
							ib.add(s);
						}
					}
					break;
				}
			}
			SectionBuilder sb = new SectionBuilder();
			int count = 0;
			SheetDataSource sheets = prepareToPaginate(ib, null);
			sheets.initialize(new PageCounter(),
			                  DefaultContext.from(c)
			                                .space(pre?Space.PRE_CONTENT:Space.POST_CONTENT)
			                                .refs(getContext().getRefs())
			                                .build());
			SplitPointDataSource.Iterator<Sheet,SheetDataSource> it = sheets.iterator();
			while (it.hasNext()) {
				Sheet ps = it.next(count++, false);
				for (PageImpl p : ps.getPages()) {
					if (p.getAnchors().size()>0) {
						ad.add(new AnchorData(p.getAnchors(), p.getPageNumber()));
					}
				}
				sb.addSheet(ps);
			}
			modifyContext(
				ctxt -> ctxt.refs(it.iterable().getContext().getRefs())
			);
			return sb;
		} catch (PaginatorException e) {
			return null;
		}
	}
	
	private SheetDataSource prepareToPaginate(List<BlockSequence> fs, Integer volumeGroup) throws PaginatorException {
		return prepareToPaginate(volumeGroup, fs);
	}
	
	private Iterable<SheetDataSource> prepareToPaginateWithVolumeGroups(List<BlockSequence> fs) {
		List<List<BlockSequence>> volGroups = new ArrayList<>();
		List<BlockSequence> currentGroup = new ArrayList<>();
		volGroups.add(currentGroup);
		for (BlockSequence bs : fs) {
			if (bs.getSequenceProperties().getBreakBeforeType()==SequenceBreakBefore.VOLUME) {
				currentGroup = new ArrayList<>();
				volGroups.add(currentGroup);
			}
			currentGroup.add(bs);
		}
		modifyRefs(refs -> refs.resetUniqueChecks());
		return new Iterable<SheetDataSource>(){
			@Override
			public Iterator<SheetDataSource> iterator() {
				try {
					return prepareToPaginateWithVolumeGroups(volGroups).iterator();
				} catch (PaginatorException e) {
					throw new RuntimeException(e);
				}
			}};
	}

	private List<SheetDataSource> prepareToPaginateWithVolumeGroups(Iterable<List<BlockSequence>> volGroups) throws PaginatorException {
		List<SheetDataSource> ret = new ArrayList<>();
		int i = 0;
		for (List<BlockSequence> glist : volGroups) {
			ret.add(prepareToPaginate(i++, glist));
		}
		return ret;
	}
	
	private SheetDataSource prepareToPaginate(Integer volumeGroup, List<BlockSequence> seqs) throws PaginatorException {
		return new SheetDataSource(context.getFormatterContext(), volumeGroup, seqs);
	}
	
	/**
	 * Informs the volume provider that the caller has finished requesting volumes.
	 * <b>Note: only use after all volumes have been calculated.</b>
	 * @return returns true if the volumes can be accepted, false otherwise  
	 */
	boolean done() {
		if (groups.hasNext()) {
			if (logger.isLoggable(Level.FINE)) {
				logger.fine("There is more content (sheets: " + groups.countRemainingSheets() + ", pages: " + groups.countRemainingPages() + ")");
			}
		}
		// this changes the value of groups.getVolumeCount() to the newly computed
		// required number of volume based on groups.countTotalSheets()
		groups.updateAll();
		if (crh.getDirty()) {
			dirty.set(true);
		}
		// used next time in prepare
		crh = groups.atIndex(groups.size() - 1)
		            .getUnits()
		            .getContext()
		            .getRefs()
		            .builder()
		            .trimPageDetails()
		            .setVolumeCount(groups.getVolumeCount())
		            .setSheetsInDocument(groups.countTotalSheets())
		            .resetCounters()
		            .resetDirty()
		            .build();
		
		// System.err.println("crh = " + crh.breakable);
		
		//crh.setPagesInDocument(value);
		
		if (!dirty.get() && !groups.hasNext()) {
			return true;
		} else {
			dirty.set(false);
			//crh.resetLookupHandlers();
			logger.info("Things didn't add up, running another iteration (" + j + ")");
		}
		j++;
		return false;
	}

}
