package org.daisy.dotify.formatter.impl.row;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.daisy.dotify.api.formatter.Context;
import org.daisy.dotify.api.formatter.Marker;
import org.daisy.dotify.formatter.impl.common.FormatterCoreContext;
import org.daisy.dotify.formatter.impl.search.CrossReferenceHandler;
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
	private final List<RowImpl> rows;
	private final SegmentProcessor sp;
	private int rowIndex;
	
	public BlockContentManager(int flowWidth, List<Segment> segments, RowDataProperties rdp, CrossReferenceHandler refs, Context context, FormatterCoreContext fcontext) {
		super(flowWidth, rdp, fcontext);
		this.rows = new ArrayList<>();
		this.sp = new SegmentProcessor(segments, flowWidth, refs, context, flowWidth - margins.getRightMargin().getContent().length(), margins, fcontext, rdp);
		initFields();
	}
	
	private BlockContentManager(BlockContentManager template) {
		super(template);
		this.rows = new ArrayList<>(template.rows);
		this.sp = new SegmentProcessor(template.sp);
		this.rowIndex = template.rowIndex;
	}
	
    private void initFields() {
		rowIndex = 0;
    }
	
    @Override
	public void setContext(DefaultContext context) {
		this.sp.setContext(context);
	}

	@Override
	public AbstractBlockContentManager copy() {
		return new BlockContentManager(this);
	}
	
	private boolean ensureBuffer(int index) {
		return ensureBuffer(index, false);
	}

	/**
	 * Ensures that the specified result index is available in the result list.
	 * Note that this function is modeled after {@link RowGroupDataSource}, but that it
	 * isn't used in the same way (yet).
	 * @param index the index to ensure
	 * @param testOnly if a row should actually be produced
	 * @return returns true if the specified index is available in the result list, false
	 * if the specified index cannot be made available (because the input doesn't contain
	 * the required amount of data).
	 */
	private boolean ensureBuffer(int index, boolean testOnly) {
		while (sp.hasNext() || index<0 || rows.size()<index) {
			if (!sp.hasNext()) {
				if (!sp.hasMoreData()) {
					return false;
				}
				if (testOnly && sp.couldTriggerNewRow()) {
					return true;
				}
				sp.prepareNext();
			}
			if (sp.hasNext()) {
				sp.getNext().ifPresent(v->rows.add(v));
			}
		}
		return true;
	}
	
	@Override
	public int getRowCount() {
		ensureBuffer(-1);
		return rows.size();
	}
	
	@Override
	public boolean supportsVariableWidth() {
		return true;
	}
	
    @Override
	public void reset() {
    	sp.reset();
    	rows.clear();
    	initFields();
    }

    @Override
    public boolean hasNext() {
        return ensureBuffer(rowIndex+1, true);
    }

	@Override
	public RowImpl getNext() {
		ensureBuffer(rowIndex+1);
		RowImpl ret = rows.get(rowIndex);
		rowIndex++;
		return ret;
	}

	@Override
	public int getForceBreakCount() {
		ensureBuffer(-1);
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

}
