package org.daisy.dotify.formatter.impl;

import java.util.Collections;
import java.util.List;

import org.daisy.dotify.common.split.SplitPointDataSource;
import org.daisy.dotify.common.split.SplitResult;
import org.daisy.dotify.common.split.Supplements;

class RowGroupDataSource implements SplitPointDataSource<RowGroup> {
	private static final Supplements<RowGroup> EMPTY_SUPPLEMENTS = new Supplements<RowGroup>() {
		@Override
		public RowGroup get(String id) {
			return null;
		}
	};
	private final LayoutMaster master;
	private final RowGroupData data;
	private final Supplements<RowGroup> supplements;
	private final VerticalSpacing vs;
	private final List<Block> blocks;
	private BlockContext bc;
	private int blockIndex;

	RowGroupDataSource(LayoutMaster master, BlockContext bc, List<Block> blocks, VerticalSpacing vs, Supplements<RowGroup> supplements) {
		this.master = master;
		this.bc = bc;
		this.data = new RowGroupData();
		this.blocks = blocks;
		this.supplements = supplements;
		this.vs = vs;
		this.blockIndex = 0;
	}
	
	RowGroupDataSource(RowGroupDataSource template) {
		this.master = template.master;
		this.bc = template.bc;
		this.data = new RowGroupData(template.data);
		this.blocks = template.blocks;
		this.supplements = template.supplements;
		this.vs = template.vs;
		this.blockIndex = template.blockIndex;
	}
	
	static RowGroupDataSource copyUnlessNull(RowGroupDataSource template) {
		return template==null?null:new RowGroupDataSource(template);
	}
	
	private RowGroupDataSource(LayoutMaster master, BlockContext bc, RowGroupData data, List<Block> blocks, Supplements<RowGroup> supplements, VerticalSpacing vs, int blockIndex) {
		this.master = master;
		this.bc = bc;
		this.data = data;
		if (supplements==null) {
			this.supplements = EMPTY_SUPPLEMENTS;
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
		return ensureBuffer(index+1);
	}

	@Override
	public boolean isEmpty() {
		return this.data.size()==0 && blockIndex>=blocks.size() && !data.hasNextInBlock();
	}

	@Override
	public RowGroup get(int n) {
		if (!ensureBuffer(n+1)) {
			throw new IndexOutOfBoundsException("" + n);
		}
		return this.data.getList().get(n);
	}
	
	@Override
	public List<RowGroup> head(int n) {
		if (n==0) {
			return Collections.emptyList();
		} else if (!ensureBuffer(n)) {
			throw new IndexOutOfBoundsException();
		}
		return this.data.getList().subList(0, n);
	}
	
	@Override
	public List<RowGroup> getRemaining() {
		ensureBuffer(-1);
		return this.data.getList().subList(0, data.size());
	}

	@Override
	public SplitPointDataSource<RowGroup> tail(int n) {
		if (!ensureBuffer(n)) {
			throw new IndexOutOfBoundsException("" + n);
		}
		return new RowGroupDataSource(master, bc, new RowGroupData(data, n), blocks, supplements, vs, blockIndex);
	}

	@Override
	public int getSize(int limit) {
		if (!ensureBuffer(limit))  {
			//we have buffered all elements
			return this.data.size();
		} else {
			return limit;
		}
	}

	VerticalSpacing getVerticalSpacing() {
		return vs;
	}
	
	BlockContext getContext() {
		return bc;
	}
	
	void setContext(BlockContext c) {
		this.bc = c;
	}
	
	/**
	 * Ensures that there are at least index elements in the buffer.
	 * When index is -1 this method always returns false.
	 * @param index the index (or -1 to get all remaining elements)
	 * @return returns true if the index element was available, false otherwise
	 */
	private boolean ensureBuffer(int index) {
		while (index<0 || this.data.size()<index) {
			if (blockIndex>=blocks.size() && !data.hasNextInBlock()) {
				return false;
			}
			if (!data.hasNextInBlock()) {
				//get next block
				Block b = blocks.get(blockIndex);
				blockIndex++;
				data.loadBlock(master, b, b.getBlockContentManager(bc));
			}
			data.processNextRowGroup();
		}
		return true;
	}

	@Override
	public SplitResult<RowGroup> split(int atIndex) {
		SplitPointDataSource<RowGroup> tail = tail(atIndex);
		List<RowGroup> head = head(atIndex);
		return new SplitResult<RowGroup>(head, tail);
	}

}
