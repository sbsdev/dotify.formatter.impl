package org.daisy.dotify.formatter.impl.page;

import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.Optional;

import org.daisy.dotify.api.formatter.FormattingTypes.Keep;
import org.daisy.dotify.formatter.impl.core.Block;
import org.daisy.dotify.formatter.impl.core.LayoutMaster;
import org.daisy.dotify.formatter.impl.datatype.VolumeKeepPriority;
import org.daisy.dotify.formatter.impl.row.AbstractBlockContentManager;
import org.daisy.dotify.formatter.impl.row.BlockStatistics;
import org.daisy.dotify.formatter.impl.row.RowImpl;
import org.daisy.dotify.formatter.impl.search.CrossReferenceHandler;
import org.daisy.dotify.formatter.impl.search.DefaultContext;

class RowGroupProvider {
	private final LayoutMaster master;
	private final Block g;
	private final AbstractBlockContentManager bcm;
	private DefaultContext context;

	private final OrphanWidowControl owc;
	private final boolean otherData;
	private int rowIndex;
	private int phase;
	private int keepWithNext;
	
	RowGroupProvider(RowGroupProvider template) {
		this.master = template.master;
		this.g= template.g;
		this.bcm = template.bcm==null?null:template.bcm.copy();
		this.context = template.context;
		this.owc = template.owc;
		this.otherData = template.otherData;
		this.rowIndex = template.rowIndex;
		this.phase = template.phase;
		this.keepWithNext = template.keepWithNext;
	}

	RowGroupProvider(LayoutMaster master, Block g, AbstractBlockContentManager bcm, DefaultContext context, int keepWithNext) {
		this.master = master;
		this.g = g;
		this.bcm = bcm;
		this.context = context;
		this.phase = 0;
		this.rowIndex = 0;
		CrossReferenceHandler refs = context.getRefs();
		this.owc = new OrphanWidowControl(g.getRowDataProperties().getOrphans(),
				g.getRowDataProperties().getWidows(), 
				refs.getRowCount(g.getBlockAddress()));
		this.otherData = !refs.getGroupAnchors(g.getBlockAddress()).isEmpty()
				|| !refs.getGroupMarkers(g.getBlockAddress()).isEmpty()
				|| !refs.getGroupIdentifiers(g.getBlockAddress()).isEmpty()
				|| g.getKeepWithNextSheets() > 0 || g.getKeepWithPreviousSheets() > 0;
		this.keepWithNext = keepWithNext;
		if (!hasNext()) {
			close();
		}
	}

	CrossReferenceHandler getRefs() {
		return context.getRefs();
	}

	private void modifyRefs(Consumer<CrossReferenceHandler.Builder> modifier) {
		DefaultContext.Builder b = context.builder();
		modifier.accept(b.getRefs());
		context = b.build();
	}
	
	int getKeepWithNext() {
		return keepWithNext;
	}

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
	
	private void close() {
		modifyRefs(
			refs -> refs.setGroupAnchors(g.getBlockAddress(), bcm.getGroupAnchors())
			            .setGroupMarkers(g.getBlockAddress(), bcm.getGroupMarkers())
			            .setGroupIdentifiers(g.getBlockAddress(), bcm.getGroupIdentifiers()));
	}
	
	BlockStatistics getBlockStatistics() {
		return bcm;
	}
	
	// FIXME: move context argument to separate setContext() method? (that is how it is done everywhere else)
	// -> or make it so that the argument does not mutate the RowGroupProvider object
	public RowGroup next(DefaultContext context, boolean wholeWordsOnly) {
		if (this.context==null || !this.context.equals(context)) {
			this.context = g.contextWithMeta(context);
			bcm.setContext(this.context);
		}
		RowGroup b = nextInner(wholeWordsOnly);
		if (!hasNext()) {
			close();
		}
		return b;
	}

	private RowGroup nextInner(boolean wholeWordsOnly) {
		if (phase==0) {
			phase++;
			//if there is a row group, return it (otherwise, try next phase)
			if (bcm.hasCollapsiblePreContentRows()) {
				return setPropertiesThatDependOnHasNext(new RowGroup.Builder(master.getRowSpacing(), bcm.getCollapsiblePreContentRows()).
										collapsible(true).skippable(false).breakable(false), hasNext(), g).build();
			}
		}
		if (phase==1) {
			phase++;
			//if there is a row group, return it (otherwise, try next phase)
			if (bcm.hasInnerPreContentRows()) {
				return setPropertiesThatDependOnHasNext(new RowGroup.Builder(master.getRowSpacing(), bcm.getInnerPreContentRows()).
										collapsible(false).skippable(false).breakable(false), hasNext(), g).build();
			}
		}
		if (phase==2) {
			phase++;
			//TODO: Does this interfere with collapsing margins?
			if (shouldAddGroupForEmptyContent()) {
				RowGroup.Builder rgb = setPropertiesForFirstContentRowGroup(
					new RowGroup.Builder(master.getRowSpacing(), new ArrayList<RowImpl>()), 
					context.getRefs(),
					g
				);
				return setPropertiesThatDependOnHasNext(rgb, hasNext(), g).build();
			}
		}
		if (phase==3) {
			Optional<RowImpl> rt;
			if ((rt=bcm.getNext(wholeWordsOnly)).isPresent()) {
				RowImpl r = rt.get();
				rowIndex++;
				boolean hasNext = bcm.hasNext(); 
				if (!hasNext) {
					//we're at the last line, this should be kept with the next block's first line
					keepWithNext = g.getKeepWithNext();
					modifyRefs(refs -> refs.setRowCount(g.getBlockAddress(), bcm.getRowCount()));
				}
				RowGroup.Builder rgb = new RowGroup.Builder(master.getRowSpacing()).add(r).
						collapsible(false).skippable(false).breakable(
								r.allowsBreakAfter()&&
								owc.allowsBreakAfter(rowIndex-1)&&
								keepWithNext<=0 &&
								(Keep.AUTO==g.getKeepType() || !hasNext) &&
								(hasNext || !bcm.hasPostContentRows())
								);
				if (rowIndex==1) { //First item
					setPropertiesForFirstContentRowGroup(rgb, context.getRefs(), g);
				}
				keepWithNext = keepWithNext-1;
				return setPropertiesThatDependOnHasNext(rgb, hasNext(), g).build();
			} else {
				phase++;
			}
		}
		if (phase==4) {
			phase++;
			if (bcm.hasPostContentRows()) {
				return setPropertiesThatDependOnHasNext(new RowGroup.Builder(master.getRowSpacing(), bcm.getPostContentRows()).
					collapsible(false).skippable(false).breakable(keepWithNext<0), hasNext(), g).build();
			}
		}
		if (phase==5) {
			phase++;
			if (bcm.hasSkippablePostContentRows()) {
				return setPropertiesThatDependOnHasNext(new RowGroup.Builder(master.getRowSpacing(), bcm.getSkippablePostContentRows()).
					collapsible(true).skippable(true).breakable(keepWithNext<0), hasNext(), g).build();
			}
		}
		return null;
	}
	
	private boolean shouldAddGroupForEmptyContent() {
		return !bcm.hasSignificantContent() && otherData;
	}
	
	private static RowGroup.Builder setPropertiesForFirstContentRowGroup(RowGroup.Builder rgb, CrossReferenceHandler crh, Block g) {
		return rgb.markers(crh.getGroupMarkers(g.getBlockAddress()))
			.anchors(crh.getGroupAnchors(g.getBlockAddress()))
			.identifiers(crh.getGroupIdentifiers(g.getBlockAddress()))
			.keepWithNextSheets(g.getKeepWithNextSheets())
			.keepWithPreviousSheets(g.getKeepWithPreviousSheets());
	}
	
	private static RowGroup.Builder setPropertiesThatDependOnHasNext(RowGroup.Builder rgb, boolean hasNext, Block g) {
		if (hasNext) {
			return rgb.avoidVolumeBreakAfterPriority(VolumeKeepPriority.ofNullable(g.getAvoidVolumeBreakInsidePriority()))
					.lastRowGroupInBlock(false);
		} else {
			return rgb.avoidVolumeBreakAfterPriority(VolumeKeepPriority.ofNullable(g.getAvoidVolumeBreakAfterPriority()))
					.lastRowGroupInBlock(true);
		}
	}
}
