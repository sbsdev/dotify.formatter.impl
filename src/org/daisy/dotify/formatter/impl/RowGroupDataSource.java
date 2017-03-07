package org.daisy.dotify.formatter.impl;

import java.util.ArrayList;
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
	private final BlockContext bc;
	private final RowGroupData data;
	private final Supplements<RowGroup> supplements;
	private final VerticalSpacing vs;
	private final List<Block> blocks;
	private int blockIndex;
	
	private class RowGroupData extends BlockProcessor {
		private List<RowGroup> data;
		
		RowGroupData() {
			this.data = null;
		}
		
		RowGroupData(RowGroupData template) {
			this.data = template.data==null?null:new ArrayList<>(template.data);
		}

		RowGroupData(RowGroupData template, int offset) {
			this.data = new ArrayList<>(template.data.subList(offset, template.data.size()));
		}

		@Override
		void newRowGroupSequence(VerticalSpacing vs) {
			if (data!=null) {
				throw new IllegalStateException();
			} else {
				data = new ArrayList<>();
			}
		}

		@Override
		boolean hasSequence() {
			return data!=null;
		}

		@Override
		boolean hasResult() {
			return hasSequence() && !data.isEmpty();
		}

		@Override
		void addRowGroup(RowGroup rg) {
			data.add(rg);
		}

		@Override
		RowGroup peekResult() {
			return data.isEmpty()?null:data.get(data.size()-1);
		}
		
		List<RowGroup> getList() {
			return data;
		}
		
		int size() {
			return data==null?0:data.size();
		}
		
	}
	
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
		return ensureBuffer(index);
	}

	@Override
	public boolean isEmpty() {
		return this.data.size()==0 && blockIndex>=blocks.size();
	}

	@Override
	public RowGroup get(int n) {
		if (!ensureBuffer(n)) {
			throw new IndexOutOfBoundsException("" + n);
		}
		return this.data.getList().get(n);
	}
	
	public List<RowGroup> head(int n) {
		if (n==0) {
			return Collections.emptyList();
		} else if (!ensureBuffer(n-1)) {
			//throw new IndexOutOfBoundsException();
		}
		return this.data.getList().subList(0, n);
	}
	
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
		if (!ensureBuffer(limit-1))  {
			//we have buffered all elements
			return this.data.size();
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
		while (index<0 || this.data.size()<=index) {
			if (blockIndex>=blocks.size()) {
				return false;
			}
			//get next block
			Block b = blocks.get(blockIndex);
			blockIndex++;			
			data.processBlock(master, b, b.getBlockContentManager(bc));
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
