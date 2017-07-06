package org.daisy.dotify.formatter.impl;

import java.util.ArrayList;
import java.util.Iterator;

import org.daisy.dotify.api.formatter.FormattingTypes.BreakBefore;
import org.daisy.dotify.api.formatter.FormattingTypes.Keep;
import org.daisy.dotify.formatter.impl.row.AbstractBlockContentManager;
import org.daisy.dotify.formatter.impl.row.BlockStatistics;
import org.daisy.dotify.formatter.impl.row.RowImpl;
import org.daisy.dotify.formatter.impl.search.CrossReferenceHandler;
import org.daisy.dotify.formatter.impl.search.DefaultContext;

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
		this.rowGroupIterator = copyUnlessNull(template.rowGroupIterator);
	}
	
	void loadBlock(LayoutMaster master, Block g, BlockContext bc) {
		AbstractBlockContentManager bcm = g.getBlockContentManager(bc);
		if (!hasSequence() || ((g.getBreakBeforeType()==BreakBefore.PAGE  || g.getVerticalPosition()!=null) && hasResult())) {
            newRowGroupSequence(
                    g.getVerticalPosition()!=null?
                            new VerticalSpacing(g.getVerticalPosition(), new RowImpl("", bcm.getLeftMarginParent(), bcm.getRightMarginParent()))
                                    :null
            );
			keepWithNext = -1;
		}
		rowGroupIterator = new InnerBlockProcessor(master, g, bcm, bc);
	}
	
	void processNextRowGroup(DefaultContext context) {
		rowGroupIterator.setContext(context);
		if (hasNextInBlock()) {
			addRowGroup(rowGroupIterator.next());
		}
	}
	
	boolean hasNextInBlock() {
		if (rowGroupIterator != null) {
			if (rowGroupIterator.hasNext()) {
				return true;
			} else {
				rowGroupIterator.bc.getRefs().setGroupAnchors(rowGroupIterator.g.getBlockAddress(),
						rowGroupIterator.bcm.getGroupAnchors());
				rowGroupIterator.bc.getRefs().setGroupMarkers(rowGroupIterator.g.getBlockAddress(),
						rowGroupIterator.bcm.getGroupMarkers());
			}
		}
		return false;
	}
	
	private InnerBlockProcessor copyUnlessNull(InnerBlockProcessor template) {
		return template==null?null:new InnerBlockProcessor(template);
	}

	/**
	 * Gets the current block's statistics, or null if no block has been loaded.
	 * @return returns the block statistics, or null
	 */
	BlockStatistics getBlockStatistics() {
		if (rowGroupIterator!=null) {
			return rowGroupIterator.bcm;
		} else {
			return null;
		}
	}
	
	private class InnerBlockProcessor implements Iterator<RowGroup> {
		private final LayoutMaster master;
		private final Block g;
		private final AbstractBlockContentManager bcm;
		private final BlockContext bc;

		private final OrphanWidowControl owc;
		private final boolean otherData;
		private DefaultContext context;
		private int rowIndex;
		private int phase;
		
		private InnerBlockProcessor(InnerBlockProcessor template) {
			this.master = template.master;
			this.g= template.g;
			this.bcm = template.bcm==null?null:template.bcm.copy();
			this.bc = template.bc;
			this.owc = template.owc;
			this.otherData = template.otherData;
			this.rowIndex = template.rowIndex;
			this.phase = template.phase;
		}

		private InnerBlockProcessor(LayoutMaster master, Block g, AbstractBlockContentManager bcm, BlockContext bc) {
			this.master = master;
			this.g = g;
			this.bcm = bcm;
			this.bc = bc;
			this.phase = 0;
			this.rowIndex = 0;
			this.owc = new OrphanWidowControl(g.getRowDataProperties().getOrphans(),
					g.getRowDataProperties().getWidows(), 
					bc.getRefs().getRowCount(g.getBlockAddress()));
			this.otherData = !bc.getRefs().getGroupAnchors(g.getBlockAddress()).isEmpty()
					|| !bc.getRefs().getGroupMarkers(g.getBlockAddress()).isEmpty() || !"".equals(g.getIdentifier())
					|| g.getKeepWithNextSheets() > 0 || g.getKeepWithPreviousSheets() > 0;
		}
		
		private void setContext(DefaultContext context) {
			if (this.context==null || !this.context.equals(context)) {
				this.context = g.contextWithMeta(context);
				bcm.setContext(this.context);
			}
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
				phase < 4 && bcm.hasNext()
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
					setProperties(rgb, bc.getRefs(), g);
					return rgb.build();
				}
			}
			if (phase==3) {
				if (bcm.hasNext()) {
					RowImpl r = bcm.getNext();
					rowIndex++;
					if (!bcm.hasNext()) {
						//we're at the last line, this should be kept with the next block's first line
						keepWithNext = g.getKeepWithNext();
						bc.getRefs().setRowCount(g.getBlockAddress(), bcm.getRowCount());
					}
					RowGroup.Builder rgb = new RowGroup.Builder(master.getRowSpacing()).add(r).
							collapsible(false).skippable(false).breakable(
									r.allowsBreakAfter()&&
									owc.allowsBreakAfter(rowIndex-1)&&
									keepWithNext<=0 &&
									(Keep.AUTO==g.getKeepType() || !bcm.hasNext()) &&
									(bcm.hasNext() || !bcm.hasPostContentRows())
									);
					if (rowIndex==1) { //First item
						setProperties(rgb, bc.getRefs(), g);
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
			return !bcm.hasNext() && otherData;
		}
	}
	
	private static void setProperties(RowGroup.Builder rgb, CrossReferenceHandler crh, Block g) {
		if (!"".equals(g.getIdentifier())) { 
			rgb.identifier(g.getIdentifier());
		}
		rgb.markers(crh.getGroupMarkers(g.getBlockAddress()));
		rgb.anchors(crh.getGroupAnchors(g.getBlockAddress()));
		rgb.keepWithNextSheets(g.getKeepWithNextSheets());
		rgb.keepWithPreviousSheets(g.getKeepWithPreviousSheets());
	}
}