package org.daisy.dotify.formatter.impl;

import java.util.List;

import org.daisy.dotify.common.split.SplitPointDataSource;
import org.daisy.dotify.common.split.Supplements;

class RowGroupDataSource implements SplitPointDataSource<RowGroup> {
	//for now, use a list internally
	private final List<RowGroup> units;
	private final Supplements<RowGroup> supplements;
	private final int offset;
	private final VerticalSpacing vs;
	
	RowGroupDataSource(LayoutMaster master, BlockContext bc, RowGroupSequence rgs, Supplements<RowGroup> supplements) {
		ScenarioData data = new ScenarioData();
		for (Block g : rgs.getBlocks()) {
			data.processBlock(master, g, g.getBlockContentManager(bc));
		}

		this.units = data.getSingleGroup();
		this.offset = 0;
		this.supplements = supplements;
		this.vs = rgs.getVerticalSpacing();
	}
	
	private RowGroupDataSource(List<RowGroup> units, Supplements<RowGroup> supplements, int offset, VerticalSpacing vs) {
		this.units = units;
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
	}

	@Override
	public Supplements<RowGroup> getSupplements() {
		return supplements;
	}

	@Override
	public boolean hasElementAt(int index) {
		return this.units.size()>index+offset;
	}

	@Override
	public boolean isEmpty() {
		return this.units.size()<=offset;
	}

	@Override
	public RowGroup get(int n) {
		return this.units.get(offset+n);
	}
	
	public List<RowGroup> head(int n) {
		return this.units.subList(offset, offset+n);
	}
	
	public List<RowGroup> getRemaining() {
		return this.units.subList(offset, units.size());
	}

	@Override
	public SplitPointDataSource<RowGroup> tail(int n) {
		return new RowGroupDataSource(units, supplements, offset+n, vs);
	}

	@Override
	public int getSize(int limit) {
		return Math.min(this.units.size()-offset, limit);
	}

	VerticalSpacing getVerticalSpacing() {
		return vs;
	}

}
