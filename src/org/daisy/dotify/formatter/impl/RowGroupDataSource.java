package org.daisy.dotify.formatter.impl;

import java.util.ArrayList;
import java.util.List;

import org.daisy.dotify.common.split.SplitPointDataList;
import org.daisy.dotify.common.split.SplitPointDataSource;
import org.daisy.dotify.common.split.Supplements;

class RowGroupDataSource implements SplitPointDataSource<RowGroup> {
	//for now, use a list internally
	private final SplitPointDataSource<RowGroup> source;
	private final VerticalSpacing vs;
	
	RowGroupDataSource(LayoutMaster master, BlockContext bc, RowGroupSequence rgs, Supplements<RowGroup> supplements) {
		ScenarioData data = new ScenarioData();
		for (Block g : rgs.getBlocks()) {
			data.processBlock(master, g, g.getBlockContentManager(bc));
		}
		if (data.dataGroups.size()!=1) {
			throw new RuntimeException("Coding error.");
		}
		this.source = new SplitPointDataList<RowGroup>(data.dataGroups.peek().getGroup(), supplements);
		this.vs = rgs.getVerticalSpacing();
	}
	
	RowGroupDataSource(SplitPointDataSource<RowGroup> source, VerticalSpacing vs) {
		this.source = source;
		this.vs = vs;
	}

	@Override
	public RowGroup get(int index) {
		return source.get(index);
	}

	@Override
	public List<RowGroup> head(int toIndex) {
		return source.head(toIndex);
	}

	@Override
	public List<RowGroup> getRemaining() {
		return source.getRemaining();
	}

	@Override
	public SplitPointDataSource<RowGroup> tail(int fromIndex) {
		return new RowGroupDataSource(source.tail(fromIndex), vs);
	}

	@Override
	public boolean hasElementAt(int index) {
		return source.hasElementAt(index);
	}

	@Override
	public int getSize(int limit) {
		return source.getSize(limit);
	}

	@Override
	public boolean isEmpty() {
		return source.isEmpty();
	}

	@Override
	public Supplements<RowGroup> getSupplements() {
		return source.getSupplements();
	}
	
	VerticalSpacing getVerticalSpacing() {
		return vs;
	}

}
