package org.daisy.dotify.formatter.impl.row;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.daisy.dotify.api.formatter.Marker;
import org.daisy.dotify.formatter.impl.common.FormatterCoreContext;
import org.daisy.dotify.formatter.impl.page.PageShape;
import org.daisy.dotify.formatter.impl.search.DefaultContext;
import org.daisy.dotify.formatter.impl.segment.Segment;

/**
 * BlockHandler is responsible for breaking blocks of text into rows. BlockProperties
 * such as list numbers, leaders and margins are resolved in the process. The input
 * text is filtered using the supplied StringFilter before breaking into rows, since
 * the length of the text could change.
 * 
 * @author Joel Håkansson
 */
public class BlockContentManager extends AbstractBlockContentManager {
	private int rowCount;
	private final SegmentProcessor sp;

	public BlockContentManager(String blockId, int flowWidth, PageShape pageShape, List<Segment> segments, RowDataProperties rdp, DefaultContext context, FormatterCoreContext fcontext) {
		super(flowWidth, rdp, fcontext);
		this.sp = new SegmentProcessor(blockId, segments, flowWidth, pageShape, context, margins, fcontext, rdp);
		this.rowCount = 0;
	}
	
	private BlockContentManager(BlockContentManager template) {
		super(template);
		this.sp = new SegmentProcessor(template.sp);
		this.rowCount = template.rowCount;
	}
	
	// FIXME: make immutable
    @Override
	public void setContext(DefaultContext context, PageShape pageShape) {
		this.sp.setContext(context, pageShape);
	}

	@Override
	public AbstractBlockContentManager copy() {
		return new BlockContentManager(this);
	}
	
	@Override
	public int getRowCount() {
		if (hasNext()) {
			throw new IllegalStateException();
		}
		return rowCount;
	}
	
	@Override
	public boolean supportsVariableWidth() {
		return true;
	}
	
	@Override
	public void reset() {
		sp.reset();
		rowCount = 0;
	}

	@Override
	public boolean hasNext() {
		if (!sp.hasMoreData()) {
			return false;
		} else {
			return new SegmentProcessor(sp).getNext(0, false).isPresent();
		}
	}

	@Override
	public Optional<RowImpl> getNext(float position, boolean wholeWordsOnly) {
		if (!sp.hasMoreData()) {
			return Optional.empty();
		}
		Optional<RowImpl> v = sp.getNext(position, wholeWordsOnly);
		if (v.isPresent()) {
			rowCount++;
		}
		return v;
	}

	@Override
	public int getForceBreakCount() {
		if (hasNext()) {
			throw new IllegalStateException();
		}
		return sp.getForceCount();
	}

	@Override
	public List<Marker> getGroupMarkers() {
		return sp.getGroupMarkers();
	}
	
	@Override
	public List<String> getGroupAnchors() {
		return sp.getGroupAnchors();
	}

	@Override
	public List<String> getGroupIdentifiers() {
		return sp.getGroupIdentifiers();
	}

	@Override
	public boolean hasSignificantContent() {
		return sp.hasSignificantContent();
	}

}
