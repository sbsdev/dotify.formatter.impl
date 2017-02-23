package org.daisy.dotify.formatter.impl;

import java.util.List;

import org.daisy.dotify.common.split.SplitPointDataSource;
import org.daisy.dotify.common.split.Supplements;

class RowGroupDataSource implements SplitPointDataSource<RowGroup> {
	private final LayoutMaster master;
	private final BlockContext bc;
	private final ScenarioData data;
	private final Supplements<RowGroup> supplements;
	private final int offset;
	private final VerticalSpacing vs;
	private final List<Block> blocks;
	private int blockIndex;
	
	RowGroupDataSource(LayoutMaster master, BlockContext bc, RowGroupSequence rgs, Supplements<RowGroup> supplements) {
		this.master = master;
		this.bc = bc;
		this.data = new ScenarioData();
		this.blocks = rgs.getBlocks();
		this.offset = 0;
		this.supplements = supplements;
		this.vs = rgs.getVerticalSpacing();
		this.blockIndex = 0;
	}
	
	private RowGroupDataSource(LayoutMaster master, BlockContext bc, ScenarioData data, List<Block> blocks, Supplements<RowGroup> supplements, int offset, VerticalSpacing vs, int blockIndex) {
		this.master = master;
		this.bc = bc;
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
		this.blocks = blocks;
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
		return this.data.getSingleGroup().size()<=offset && blockIndex>=blocks.size();
	}

	@Override
	public RowGroup get(int n) {
		if (!ensureBuffer(n)) {
			throw new IndexOutOfBoundsException("" + n);
		}
		return this.data.getSingleGroup().get(offset+n);
	}
	
	public List<RowGroup> head(int n) {
		if (!ensureBuffer(n-1)) {
			//throw new IndexOutOfBoundsException();
		}
		return this.data.getSingleGroup().subList(offset, offset+n);
	}
	
	public List<RowGroup> getRemaining() {
		ensureBuffer(-1);
		return this.data.getSingleGroup().subList(offset, data.getSingleGroup().size());
	}

	@Override
	public SplitPointDataSource<RowGroup> tail(int n) {
		if (!ensureBuffer(n)) {
			throw new IndexOutOfBoundsException("" + n);
		}
		return new RowGroupDataSource(master, bc, new ScenarioData(data), blocks, supplements, offset+n, vs, blockIndex);
	}

	@Override
	public int getSize(int limit) {
		if (!ensureBuffer(limit-1))  {
			//we have buffered all elements
			return this.data.getSingleGroup().size()-offset;
		} else {
			return limit;
		}
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
		while (index<0 || this.data.getSingleGroup().size()-offset<=index) {
			if (blockIndex>=blocks.size()) {
				return false;
			}
			while (blockIndex<blocks.size()) {
				//get next block
				Block b = blocks.get(blockIndex);
				blockIndex++;			
				data.processBlock(master, b, b.getBlockContentManager(bc));
			}
		}
		return true;
	}

}
