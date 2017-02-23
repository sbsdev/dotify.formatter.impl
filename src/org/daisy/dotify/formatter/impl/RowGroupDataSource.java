package org.daisy.dotify.formatter.impl;

import java.util.List;

import org.daisy.dotify.common.split.SplitPointDataSource;
import org.daisy.dotify.common.split.Supplements;

class RowGroupDataSource implements SplitPointDataSource<RowGroup> {
	private final ScenarioData data;
	private final Supplements<RowGroup> supplements;
	private final int offset;
	private final VerticalSpacing vs;
	private int blockIndex;
	
	RowGroupDataSource(LayoutMaster master, BlockContext bc, RowGroupSequence rgs, Supplements<RowGroup> supplements) {
		this.data = new ScenarioData();
		for (Block g : rgs.getBlocks()) {
			data.processBlock(master, g, g.getBlockContentManager(bc));
		}
		this.offset = 0;
		this.supplements = supplements;
		this.vs = rgs.getVerticalSpacing();
		this.blockIndex = 0;
	}
	
	private RowGroupDataSource(ScenarioData data, Supplements<RowGroup> supplements, int offset, VerticalSpacing vs, int blockIndex) {
		this.data = data;
		this.offset = offset;
		if (supplements==null) {
			this.supplements = new Supplements<RowGroup>() {
				@Override
				public RowGroup get(String id) {
					return null;
				}
			};
		} else {
			this.supplements = supplements;
		}
		this.vs = vs;
		this.blockIndex = blockIndex;
	}

	@Override
	public Supplements<RowGroup> getSupplements() {
		return supplements;
	}

	@Override
	public boolean hasElementAt(int index) {
		return this.data.getSingleGroup().size()>index+offset;
	}

	@Override
	public boolean isEmpty() {
		return this.data.getSingleGroup().size()<=offset;
	}

	@Override
	public RowGroup get(int n) {
		return this.data.getSingleGroup().get(offset+n);
	}
	
	public List<RowGroup> head(int n) {
		return this.data.getSingleGroup().subList(offset, offset+n);
	}
	
	public List<RowGroup> getRemaining() {
		return this.data.getSingleGroup().subList(offset, data.getSingleGroup().size());
	}

	@Override
	public SplitPointDataSource<RowGroup> tail(int n) {
		return new RowGroupDataSource(new ScenarioData(data), supplements, offset+n, vs, 0);
	}

	@Override
	public int getSize(int limit) {
		return Math.min(this.data.getSingleGroup().size()-offset, limit);
	}

	VerticalSpacing getVerticalSpacing() {
		return vs;
	}
	
	/**
	 * Ensures that there are at least index elements in the buffer.
	 * When index is -1 this method always returns false.
	 * @param index the index (or -1 to get all remaining elements)
	 * @return returns true if the index element was available, false otherwise
	 */
	private boolean ensureBuffer(int index) {
		/*
		while (index<0 || sheetBuffer.size()<=index) {
			if (!seqsIterator.hasNext()) {
				return false;
			}
		}*/
		return true;
	}

}
