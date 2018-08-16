package org.daisy.dotify.formatter.impl.page;

import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.List;
import java.util.NoSuchElementException;

import org.daisy.dotify.api.formatter.FormattingTypes.BreakBefore;
import org.daisy.dotify.common.splitter.SplitPointDataSource;
import org.daisy.dotify.common.splitter.SplitPointHandler;
import org.daisy.dotify.common.splitter.SplitPointSpecification;
import org.daisy.dotify.common.splitter.Supplements;
import org.daisy.dotify.formatter.impl.core.Block;
import org.daisy.dotify.formatter.impl.core.BlockContext;
import org.daisy.dotify.formatter.impl.core.LayoutMaster;

/**
 * <p>Provides a data source for row groups.</p>
 * 
 * <p>Note that the implementation requires that break point searching is performed on a copy,
 * so that the items are created when a split is performed. If this assumption is not met,
 * things will break.</p>
 * @author Joel Håkansson
 */
class RowGroupDataSource extends BlockProcessor implements SplitPointDataSource<RowGroup> {

	private final LayoutMaster master;
	private final Supplements<RowGroup> supplements;
	private final BreakBefore breakBefore;
	private final VerticalSpacing vs;
	private final List<Block> blocks;
	private List<RowGroup> groups;
	private int groupIndex;
	private BlockContext bc;
	private int blockIndex;
	private boolean allowHyphenateLastLine;
	// FIXME: remove need for mergeRefs variable by making the getContext function private?
	// -> instead pass context via RowGroup units?
	private boolean mergeRefs;

	RowGroupDataSource(LayoutMaster master, BlockContext bc, List<Block> blocks, BreakBefore breakBefore, VerticalSpacing vs, Supplements<RowGroup> supplements) {
		super();
		this.master = master;
		this.bc = bc;
		this.groups = null;
		this.groupIndex = 0;
		this.blocks = blocks;
		this.supplements = supplements;
		this.breakBefore = breakBefore;
		this.vs = vs;
		this.blockIndex = 0;
		this.allowHyphenateLastLine = true;
		this.mergeRefs = false;
	}

	/**
	 * Creates a deep copy of template
	 *
	 * @param template the template
	 */
	private RowGroupDataSource(RowGroupDataSource template) {
		super(template);
		this.master = template.master;
		this.bc = template.bc;
		this.groups = template.groups == null ? null : new ArrayList<>(template.groups);
		this.groupIndex = template.groupIndex;
		this.blocks = template.blocks;
		this.supplements = template.supplements;
		this.breakBefore = template.breakBefore;
		this.vs = template.vs;
		this.blockIndex = template.blockIndex;
		this.allowHyphenateLastLine = template.allowHyphenateLastLine;
		this.mergeRefs = template.mergeRefs;
	}
	
	static RowGroupDataSource copyUnlessNull(RowGroupDataSource template) {
		return template==null?null:new RowGroupDataSource(template);
	}

	VerticalSpacing getVerticalSpacing() {
		return vs;
	}
	
	BreakBefore getBreakBefore() {
		return breakBefore;
	}
	
	BlockContext getContext() {
		return getContext(false);
	}
	
	private BlockContext getContext(boolean calledFromLoadBlock) {
		// flush blocks
		if (!calledFromLoadBlock) {
			maybeLoadBlock();
		}
		// merge modifications to refs by rowGroupProvider
		if (mergeRefs && rowGroupProvider != null) {
			bc = bc.builder().refs(rowGroupProvider.getRefs()).build();
		}
		mergeRefs = false;
		return bc;
	}
	
	// FIXME: make immutable
	// -> e.g. by returning a new RowGroupDataSource
	void modifyContext(Consumer<? super BlockContext.Builder> modifier) {
		BlockContext.Builder b = getContext().builder();
		modifier.accept(b);
		bc = b.build();
	}
	
	// FIXME: make immutable
	// -> e.g. by returning a new RowGroupDataSource
	/**
	 * <p>Sets the hyphenate last line property.</p>
	 * 
	 * <p>Note that the implementation assumes that this is only used immediately before
	 * calling split on a {@link SplitPointHandler} with a {@link SplitPointSpecification}.
	 * Calling this method after a call to split is not necessary.</p>
	 * @param value the value
	 */
	void setAllowHyphenateLastLine(boolean value) {
		this.allowHyphenateLastLine = value;
	}

	private int currentRowCount() {
		return groups==null?0:groups.size();
	}

	@Override
	public Supplements<RowGroup> getSupplements() {
		return supplements;
	}

	// load new block if the row buffer and current block are empty
	private void maybeLoadBlock() {
		if (groupIndex < currentRowCount() || hasNextInBlock()) {
			return;
		}
		while (blockIndex < blocks.size()) {
			Block b = blocks.get(blockIndex);
			blockIndex++;
			loadBlock(master, b, getContext(true));
			if (hasNextInBlock()) {
				return;
			} else {
				mergeRefs = true;
			}
		}
	}

	@Override
	public boolean isEmpty() {
		maybeLoadBlock();
		return groupIndex >= currentRowCount() && !hasNextInBlock();
	}

	@Override
	public Iterator<RowGroup> iterator() {
		return new RowGroupDataSource(this).asIterator();
	}

	private Iterator<RowGroup> asIterator() {
		return new RowGroupDataSourceIterator();
	}

	@Override
	protected void newRowGroupSequence(BreakBefore breakBefore, VerticalSpacing vs) {
		if (groups!=null) {
			throw new IllegalStateException();
		} else {
			groups = new ArrayList<>();
		}
	}

	@Override
	protected boolean hasSequence() {
		return groups!=null;
	}

	@Override
	protected boolean hasResult() {
		return hasSequence() && !groups.isEmpty();
	}

	@Override
	protected void addRowGroup(RowGroup rg) {
		groups.add(rg);
	}

	private class RowGroupDataSourceIterator implements Iterator<RowGroup> {

		@Override
		public boolean hasNext() {
			return !isEmpty();
		}

		@Override
		public RowGroup next(float position, boolean last) throws NoSuchElementException {
			// hasNext calls maybeLoadBlock
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			if (currentRowCount() <= groupIndex) {
				processNextRowGroup(getContext(), getContext().getPageShape(), position, !allowHyphenateLastLine && last);
				// refs possibly mutated
				mergeRefs = true;
			}
			return groups.get(groupIndex++);
		}

		@Override
		public RowGroupDataSource iterable() {
			return new RowGroupDataSource(RowGroupDataSource.this);
		}
	}
	
	@Override
	public String toString() {
		return super.toString().substring("org.daisy.dotify.formatter.impl.page.".length());
	}
}
