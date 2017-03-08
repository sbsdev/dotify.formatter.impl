package org.daisy.dotify.formatter.impl;

import java.util.ArrayList;
import java.util.Iterator;

import org.daisy.dotify.api.formatter.FormattingTypes.BreakBefore;
import org.daisy.dotify.api.formatter.FormattingTypes.Keep;

/**
 * Provides data about a single rendering scenario.
 * 
 * @author Joel Håkansson
 */
abstract class BlockProcessor {
	private int keepWithNext = 0;
	private InnerBlockProcessor rowGroupIterator;
	
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

	void processBlock(LayoutMaster master, Block g, AbstractBlockContentManager bcm) {
		loadBlock(master, g, bcm);
		while (rowGroupIterator.hasNext()) {
			addRowGroup(rowGroupIterator.next());
		}
	}
	
	void loadBlock(LayoutMaster master, Block g, AbstractBlockContentManager bcm) {
		if (!hasSequence() || ((g.getBreakBeforeType()==BreakBefore.PAGE  || g.getVerticalPosition()!=null) && hasResult())) {
            newRowGroupSequence(
                    g.getVerticalPosition()!=null?
                            new VerticalSpacing(g.getVerticalPosition(), new RowImpl("", bcm.getLeftMarginParent(), bcm.getRightMarginParent()))
                                    :null
            );
			keepWithNext = -1;
		}
		rowGroupIterator = new InnerBlockProcessor(master, g, bcm);
		if (!rowGroupIterator.hasNext() && hasSequence()) {
			RowGroup gx = peekResult();
			if (gx!=null && gx.getAvoidVolumeBreakAfterPriority()==g.getAvoidVolumeBreakInsidePriority()
					&&gx.getAvoidVolumeBreakAfterPriority()!=g.getAvoidVolumeBreakAfterPriority()) {
				gx.setAvoidVolumeBreakAfterPriority(g.getAvoidVolumeBreakAfterPriority());
			}
		}
	}
	
	private class InnerBlockProcessor implements Iterator<RowGroup> {
		private final LayoutMaster master;
		private final Block g;
		private final AbstractBlockContentManager bcm;
		private final Iterator<RowImpl> ri;
		private final OrphanWidowControl owc;
		private final boolean otherData;
		private int i;
		private int phase;

		private InnerBlockProcessor(LayoutMaster master, Block g, AbstractBlockContentManager bcm) {
			this.master = master;
			this.g = g;
			this.bcm = bcm;
			this.ri = bcm.iterator();
			this.phase = 0;
			this.i = 0;
			this.owc = new OrphanWidowControl(g.getRowDataProperties().getOrphans(),
					g.getRowDataProperties().getWidows(), 
					bcm.getRowCount());
			this.otherData = (!bcm.getGroupAnchors().isEmpty() || !bcm.getGroupMarkers().isEmpty() || !"".equals(g.getIdentifier())
					|| g.getKeepWithNextSheets()>0 || g.getKeepWithPreviousSheets()>0);

		}
		
		@Override
		public boolean hasNext() {
			// these conditions must match the ones in next()
			return 
				phase < 1 && bcm.hasCollapsiblePreContentRows()
				||
				phase < 2 && bcm.hasInnerPreContentRows()
				||
				phase < 3 && shouldAddGroupForEmptyContent()
				||
				phase < 4 && ri.hasNext()
				||
				phase < 5 && bcm.hasPostContentRows()
				||
				phase < 6 && bcm.hasSkippablePostContentRows();
		}
		
		@Override
		public RowGroup next() {
			RowGroup b = nextInner();
			if (!rowGroupIterator.hasNext()) {
				b.setAvoidVolumeBreakAfterPriority(g.getAvoidVolumeBreakAfterPriority());
			} else {
				b.setAvoidVolumeBreakAfterPriority(g.getAvoidVolumeBreakInsidePriority());
			}
			return b;
		}

		private RowGroup nextInner() {
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
				if (ri.hasNext()) {
					i++;
					RowImpl r = ri.next();
					r.setAdjustedForMargin(true);
					if (!ri.hasNext()) {
						//we're at the last line, this should be kept with the next block's first line
						keepWithNext = g.getKeepWithNext();
					}
					RowGroup.Builder rgb = new RowGroup.Builder(master.getRowSpacing()).add(r).
							collapsible(false).skippable(false).breakable(
									r.allowsBreakAfter()&&
									owc.allowsBreakAfter(i-1)&&
									keepWithNext<=0 &&
									(Keep.AUTO==g.getKeepType() || i==bcm.getRowCount()) &&
									(i<bcm.getRowCount() || !bcm.hasPostContentRows())
									);
					if (i==1) { //First item
						setProperties(rgb, bcm, g);
					}
					keepWithNext = keepWithNext-1;
					return rgb.build();
				} else {
					phase++;
				}
			}
			if (phase==4) {
				phase++;
				if (bcm.hasPostContentRows()) {
					return new RowGroup.Builder(master.getRowSpacing(), bcm.getPostContentRows()).
						collapsible(false).skippable(false).breakable(keepWithNext<0).build();
				}
			}
			if (phase==5) {
				phase++;
				if (bcm.hasSkippablePostContentRows()) {
					return new RowGroup.Builder(master.getRowSpacing(), bcm.getSkippablePostContentRows()).
						collapsible(true).skippable(true).breakable(keepWithNext<0).build();
				}
			}
			return null;
		}
		
		private boolean shouldAddGroupForEmptyContent() {
			return !ri.hasNext() && otherData;
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