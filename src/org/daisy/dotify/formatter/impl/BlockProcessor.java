package org.daisy.dotify.formatter.impl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.daisy.dotify.api.formatter.FormattingTypes.BreakBefore;
import org.daisy.dotify.api.formatter.FormattingTypes.Keep;

/**
 * Provides data about a single rendering scenario.
 * 
 * @author Joel Håkansson
 */
abstract class BlockProcessor {
	private int keepWithNext = 0;
	
	abstract void newRowGroupSequence(VerticalSpacing vs);
	
	abstract boolean hasSequence();
	abstract boolean hasResult();
	abstract void addRowGroup(RowGroup rg);
	abstract RowGroup peekResult();
	
	BlockProcessor() {
		this.keepWithNext = 0;
	}

	BlockProcessor(BlockProcessor template) {
		this.keepWithNext = template.keepWithNext;
	}

	private int getKeepWithNext() {
		return keepWithNext;
	}

	private void setKeepWithNext(int keepWithNext) {
		this.keepWithNext = keepWithNext;
	}
		
	void processBlock(LayoutMaster master, Block g, AbstractBlockContentManager bcm) {
		if (!hasSequence() || ((g.getBreakBeforeType()==BreakBefore.PAGE  || g.getVerticalPosition()!=null) && hasResult())) {
            newRowGroupSequence(
                    g.getVerticalPosition()!=null?
                            new VerticalSpacing(g.getVerticalPosition(), new RowImpl("", bcm.getLeftMarginParent(), bcm.getRightMarginParent()))
                                    :null
            );
			setKeepWithNext(-1);
		}
		List<RowGroup> store = new ArrayList<>();

		InnerBlockProcessor ibp = new InnerBlockProcessor(master, g, bcm);
		while (ibp.hasNext()) {
			store.add(ibp.next());
		}
		
		Iterator<RowImpl> ri = bcm.iterator();

		int i = 0;
		List<RowImpl> rl3 = bcm.getPostContentRows();
		OrphanWidowControl owc = new OrphanWidowControl(g.getRowDataProperties().getOrphans(),
														g.getRowDataProperties().getWidows(), 
														bcm.getRowCount());
		while (ri.hasNext()) {
			i++;
			RowImpl r = ri.next();
			r.setAdjustedForMargin(true);
			if (!ri.hasNext()) {
				//we're at the last line, this should be kept with the next block's first line
				setKeepWithNext(g.getKeepWithNext());
			}
			RowGroup.Builder rgb = new RowGroup.Builder(master.getRowSpacing()).add(r).
					collapsible(false).skippable(false).breakable(
							r.allowsBreakAfter()&&
							owc.allowsBreakAfter(i-1)&&
							getKeepWithNext()<=0 &&
							(Keep.AUTO==g.getKeepType() || i==bcm.getRowCount()) &&
							(i<bcm.getRowCount() || rl3.isEmpty())
							);
			if (i==1) { //First item
				setProperties(rgb, bcm, g);
			}
			store.add(rgb.build());
			setKeepWithNext(getKeepWithNext()-1);
		}
		if (!rl3.isEmpty()) {
			store.add(new RowGroup.Builder(master.getRowSpacing(), rl3).
				collapsible(false).skippable(false).breakable(getKeepWithNext()<0).build());
		}
		List<RowImpl> rl4 = bcm.getSkippablePostContentRows();
		if (!rl4.isEmpty()) {
			store.add(new RowGroup.Builder(master.getRowSpacing(), rl4).
				collapsible(true).skippable(true).breakable(getKeepWithNext()<0).build());
		}
		if (store.isEmpty() && hasSequence()) {
			RowGroup gx = peekResult();
			if (gx!=null && gx.getAvoidVolumeBreakAfterPriority()==g.getAvoidVolumeBreakInsidePriority()
					&&gx.getAvoidVolumeBreakAfterPriority()!=g.getAvoidVolumeBreakAfterPriority()) {
				gx.setAvoidVolumeBreakAfterPriority(g.getAvoidVolumeBreakAfterPriority());
			}
		} else {
			for (int j=0; j<store.size(); j++) {
				RowGroup b = store.get(j);
				if (j==store.size()-1) { //!hasNext()
					b.setAvoidVolumeBreakAfterPriority(g.getAvoidVolumeBreakAfterPriority());
				} else {
					b.setAvoidVolumeBreakAfterPriority(g.getAvoidVolumeBreakInsidePriority());
				}
				addRowGroup(b);
			}
		}
	}
	
	private static class InnerBlockProcessor implements Iterator<RowGroup> {
		private final LayoutMaster master;
		private final Block g;
		private final AbstractBlockContentManager bcm;
		private final Iterator<RowImpl> ri;
		private int phase;

		private InnerBlockProcessor(LayoutMaster master, Block g, AbstractBlockContentManager bcm) {
			this.master = master;
			this.g = g;
			this.bcm = bcm;
			this.ri = bcm.iterator();
			this.phase = 0;
		}
		
		@Override
		public boolean hasNext() {
			// these conditions must match the ones in next()
			return 
				phase < 1 && bcm.hasCollapsiblePreContentRows()
				||
				phase < 2 && bcm.hasInnerPreContentRows()
				||
				phase < 3 && shouldAddGroupForEmptyContent();
		}
		
		@Override
		public RowGroup next() {
			if (phase==0) {
				phase++;
				//if there is a row group, return it (otherwise, try next phase)
				if (bcm.hasCollapsiblePreContentRows()) {
					return new RowGroup.Builder(master.getRowSpacing(), bcm.getCollapsiblePreContentRows()).
											collapsible(true).skippable(false).breakable(false).build();
				}
			}
			if (phase==1) {
				phase++;
				//if there is a row group, return it (otherwise, try next phase)
				if (bcm.hasInnerPreContentRows()) {
					return new RowGroup.Builder(master.getRowSpacing(), bcm.getInnerPreContentRows()).
											collapsible(false).skippable(false).breakable(false).build();
				}
			}
			if (phase==2) {
				phase++;
				//TODO: Does this interfere with collapsing margins?
				if (shouldAddGroupForEmptyContent()) {
					RowGroup.Builder rgb = new RowGroup.Builder(master.getRowSpacing(), new ArrayList<RowImpl>());
					setProperties(rgb, bcm, g);
					return rgb.build();
				}
			}
			if (phase==3) {
				phase++;
			}
			return null;
		}
		
		private boolean shouldAddGroupForEmptyContent() {
			return !ri.hasNext() && (!bcm.getGroupAnchors().isEmpty() || !bcm.getGroupMarkers().isEmpty() || !"".equals(g.getIdentifier())
					|| g.getKeepWithNextSheets()>0 || g.getKeepWithPreviousSheets()>0);
		}
	}
	
	private static void setProperties(RowGroup.Builder rgb, AbstractBlockContentManager bcm, Block g) {
		if (!"".equals(g.getIdentifier())) { 
			rgb.identifier(g.getIdentifier());
		}
		rgb.markers(bcm.getGroupMarkers());
		rgb.anchors(bcm.getGroupAnchors());
		rgb.keepWithNextSheets(g.getKeepWithNextSheets());
		rgb.keepWithPreviousSheets(g.getKeepWithPreviousSheets());
	}
}