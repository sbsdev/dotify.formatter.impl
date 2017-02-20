package org.daisy.dotify.formatter.impl;

import java.util.Iterator;

import org.daisy.dotify.api.formatter.MarginRegion;

class RowGroupBuilder {
	
	private static int getTotalMarginRegionWidth(LayoutMaster master) {
		int mw = 0;
		for (MarginRegion mr : master.getTemplate(1).getLeftMarginRegion()) {
			mw += mr.getWidth();
		}
		for (MarginRegion mr : master.getTemplate(1).getRightMarginRegion()) {
			mw += mr.getWidth();
		}
		return mw;
	}

	static Iterator<RowGroupSequence> getResult(LayoutMaster master, BlockSequence in, BlockContext blockContext) {
		//TODO: This assumes that all page templates have margin regions that are of the same width  
		final BlockContext bc = new BlockContext(in.getLayoutMaster().getFlowWidth() - getTotalMarginRegionWidth(master), blockContext.getRefs(), blockContext.getContext(), blockContext.getFcontext());
		PageSequenceRecorderData data = new PageSequenceRecorderData();
		for (RowGroupSequence s : getResultInner(master, in, bc)) {
			for (Block g : s.getBlocks()) {
				data.processBlock(master, g, g.getBlockContentManager(bc));
			}
		}
		return data.dataGroups.iterator();
	}
	
	private static Iterable<RowGroupSequence> getResultInner(LayoutMaster master, Iterable<Block> seq, BlockContext bc) {
		final PageSequenceRecorder rec = new PageSequenceRecorder();
		for (Block g : seq)  {
			try {
				rec.processBlock(master, g, bc);
			} catch (Exception e) {
				rec.invalidateScenario(e);
			}
		}
		return rec.processResult();
	}
	

}