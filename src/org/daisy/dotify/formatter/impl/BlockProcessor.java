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
		List<RowImpl> rl1 = bcm.getCollapsiblePreContentRows();
		if (!rl1.isEmpty()) {
			store.add(new RowGroup.Builder(master.getRowSpacing(), rl1).
									collapsible(true).skippable(false).breakable(false).build());
		}
		List<RowImpl> rl2 = bcm.getInnerPreContentRows();
		if (!rl2.isEmpty()) {
			store.add(new RowGroup.Builder(master.getRowSpacing(), rl2).
									collapsible(false).skippable(false).breakable(false).build());
		}
		
		Iterator<RowImpl> ri = bcm.iterator();
		if (!ri.hasNext()) { //TODO: Does this interfere with collapsing margins? 
			if (!bcm.getGroupAnchors().isEmpty() || !bcm.getGroupMarkers().isEmpty() || !"".equals(g.getIdentifier())
					|| g.getKeepWithNextSheets()>0 || g.getKeepWithPreviousSheets()>0 ) {
				RowGroup.Builder rgb = new RowGroup.Builder(master.getRowSpacing(), new ArrayList<RowImpl>());
				setProperties(rgb, bcm, g);
				store.add(rgb.build());
			}
		}

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
				if (j==store.size()-1) {
					b.setAvoidVolumeBreakAfterPriority(g.getAvoidVolumeBreakAfterPriority());
				} else {
					b.setAvoidVolumeBreakAfterPriority(g.getAvoidVolumeBreakInsidePriority());
				}
				addRowGroup(b);
			}
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