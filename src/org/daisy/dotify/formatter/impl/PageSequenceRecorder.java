package org.daisy.dotify.formatter.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.daisy.dotify.api.formatter.BlockPosition;
import org.daisy.dotify.api.formatter.FormattingTypes.BreakBefore;
import org.daisy.dotify.api.formatter.FormattingTypes.Keep;
import org.daisy.dotify.api.formatter.RenderingScenario;

class PageSequenceRecorder {
	private static final String baseline = "base";
	private static final String scenario = "best";
	
	private PageSequenceRecorderData data;

	private RenderingScenario current = null;
	private RenderingScenario invalid = null;
	private double cost = 0;
	private float height = 0;
	private float minWidth = 0;
	private double forceCount = 0;
	private Map<String, PageSequenceRecorderData> states;

	PageSequenceRecorder() {
		data = new PageSequenceRecorderData();
		states = new HashMap<>();
	}
	
	private void saveState(String id) {
		states.put(id, new PageSequenceRecorderData(data));
	}
	
	private void restoreState(String id) {
		PageSequenceRecorderData state = states.get(id);
		if (state!=null) {
			data = new PageSequenceRecorderData(state);
		}
	}
	
	private void clearState(String id) {
		states.remove(id);
	}
	
	private boolean hasState(String id) {
		return states.containsKey(id);
	}
	
	/**
	 * Process a new block for a scenario
	 * @param g
	 * @param rec
	 */
	void processBlock(LayoutMaster master, Block g, BlockContext context) {
		AbstractBlockContentManager ret = processBlockInner(g, context);
		data.processBlock(master, g, ret);
	}

	private AbstractBlockContentManager processBlockInner(Block g, BlockContext context) {
		AbstractBlockContentManager ret = g.getBlockContentManager(context);
		if (g.getRenderingScenario()!=null) {
			if (invalid!=null && g.getRenderingScenario()==invalid) {
				//we're still in the same scenario
				return ret;
			}
			if (current==null) {
				height = data.calcSize();
				cost = Double.MAX_VALUE;
				minWidth = ret.getMinimumAvailableWidth();
				forceCount = 0;
				clearState(scenario);
				saveState(baseline);
				current = g.getRenderingScenario();
				invalid = null;
			} else {
				if (current!=g.getRenderingScenario()) {
					if (invalid!=null) {
						invalid = null;
						if (hasState(scenario)) {
							restoreState(scenario);
						} else {
							restoreState(baseline);
						}
					} else {
						//TODO: measure, evaluate
						float size = data.calcSize()-height;
						double ncost = current.calculateCost(setParams(size, minWidth, forceCount));
						if (ncost<cost) {
							//if better, store
							cost = ncost;
							saveState(scenario);
						}
						restoreState(baseline);
						minWidth = ret.getMinimumAvailableWidth();
						forceCount = 0;
					}
					current = g.getRenderingScenario();
				} // we're rendering the current scenario
				forceCount += ret.getForceBreakCount();
				minWidth = Math.min(minWidth, ret.getMinimumAvailableWidth());
			}
		} else {
			finishBlockProcessing();
		}
		return ret;
	}
	
	private void finishBlockProcessing() {
		if (current!=null) {
			if (invalid!=null) {
				invalid = null;
				if (hasState(scenario)) {
					restoreState(scenario);
				} else {
					throw new RuntimeException("Failed to render any scenario.");
				}
			} else {
				//if not better
				float size = data.calcSize()-height;
				double ncost = current.calculateCost(setParams(size, minWidth, forceCount));
				if (ncost>cost) {
					restoreState(scenario);
				}
			}
			current = null;
			invalid = null;
		}
	}
	
	/**
	 * Invalidates the current scenario, if any. This causes the remainder of the
	 * scenario to be excluded from further processing.
	 * 
	 * @param e the exception that caused the scenario to be invalidated
	 * @throws RuntimeException if no scenario is active 
	 */
	void invalidateScenario(Exception e) {
		if (current==null) {
			throw new RuntimeException(e);
		} else {
			invalid = current;
		}
	}
	
	void newRowGroupSequence(BlockPosition pos, RowImpl emptyRow) {
		data.newRowGroupSequence(pos, emptyRow);
	}
	
	boolean isDataGroupsEmpty() {
		return data.dataGroups.isEmpty();
	}
	
	boolean isDataEmpty() {
		return data.isDataEmpty();
	}
	
	RowGroupSequence currentSequence() {
		return data.dataGroups.peek();
	}
	
	void addRowGroup(RowGroup rg) {
		data.addRowGroup(rg); 
	}
	
	void addBlock(Block b) {
		data.addBlock(b);
	}
	
	PageSequenceRecorderData getData() {
		return data;
	}
	
	List<RowGroupSequence> processResult() {
		finishBlockProcessing();
		return data.dataGroups;
	}
	
	int getKeepWithNext() {
		return data.keepWithNext;
	}
	
	void setKeepWithNext(int keepWithNext) {
		data.keepWithNext = keepWithNext;
	}

	private static Map<String, Double> setParams(double height, double minBlockWidth, double forceCount) {
		Map<String, Double> params = new HashMap<>();
		params.put("total-height", height);
		params.put("min-block-width", minBlockWidth);
		params.put("forced-break-count", forceCount);
		return params;
	}

	static class PageSequenceRecorderData {
		Stack<RowGroupSequence> dataGroups = new Stack<>();
		private int keepWithNext = 0;

		PageSequenceRecorderData() {
			dataGroups = new Stack<>();
			keepWithNext = 0;
		}

		public int getKeepWithNext() {
			return keepWithNext;
		}

		public void setKeepWithNext(int keepWithNext) {
			this.keepWithNext = keepWithNext;
		}


		/**
		 * Creates a deep copy of the supplied instance
		 * @param template the instance to copy
		 */
		PageSequenceRecorderData(PageSequenceRecorderData template) {
			dataGroups = new Stack<>();
			for (RowGroupSequence rgs : template.dataGroups) {
				dataGroups.add(new RowGroupSequence(rgs));
			}
			keepWithNext = template.keepWithNext;
		}

		float calcSize() {
			float size = 0;
			for (RowGroupSequence rgs : dataGroups) {
				size += rgs.calcSequenceSize();
			}
			return size;
		}
		
		boolean isDataEmpty() {
			return (dataGroups.isEmpty()||dataGroups.peek().getGroup().isEmpty());
		}
		
		void newRowGroupSequence(BlockPosition pos, RowImpl emptyRow) {
			RowGroupSequence rgs = new RowGroupSequence(pos, emptyRow);
			dataGroups.add(rgs);
		}
		
		void addRowGroup(RowGroup rg) {
			dataGroups.peek().getGroup().add(rg);
		}
		
		void addBlock(Block b) {
			dataGroups.peek().getBlocks().add(b);
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
		
		void processBlock(LayoutMaster master, Block g, AbstractBlockContentManager bcm) {
			if (dataGroups.isEmpty() || (g.getBreakBeforeType()==BreakBefore.PAGE && !isDataEmpty()) || g.getVerticalPosition()!=null) {
				newRowGroupSequence(g.getVerticalPosition(), new RowImpl("", bcm.getLeftMarginParent(), bcm.getRightMarginParent()));
				setKeepWithNext(-1);
			}
			addBlock(g);
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
			
			if (bcm.getRowCount()==0) { //TODO: Does this interfere with collapsing margins? 
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
			for (RowImpl r : bcm) {
				i++;
				r.setAdjustedForMargin(true);
				if (i==bcm.getRowCount()) {
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
			if (store.isEmpty() && !dataGroups.isEmpty()) {
				RowGroup gx = dataGroups.peek().currentGroup();
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
	}
}
