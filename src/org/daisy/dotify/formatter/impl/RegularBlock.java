package org.daisy.dotify.formatter.impl;

import java.util.Stack;

import org.daisy.dotify.api.formatter.RenderingScenario;
import org.daisy.dotify.formatter.impl.search.DefaultContext;
import org.daisy.dotify.formatter.impl.segment.Segment;
import org.daisy.dotify.formatter.impl.segment.Segment.SegmentType;
import org.daisy.dotify.formatter.impl.segment.TextSegment;

class RegularBlock extends Block {
	private boolean isVolatile;
	private final Stack<Segment> segments;

	RegularBlock(String blockId, RowDataProperties rdp, RenderingScenario scenario) {
		super(blockId, rdp, scenario);
		this.segments = new Stack<>();
		this.isVolatile = false;
	}
	
	private void markIfVolatile(Segment s) {
		if (s.getSegmentType()==SegmentType.Reference || s.getSegmentType()==SegmentType.Evaluate) {
			isVolatile = true;
		}
	}
	
	public void addSegment(Segment s) {
		markIfVolatile(s);
		segments.add(s);
	}
	
	public void addSegment(TextSegment s) {
		markIfVolatile(s);
		addSegment(s, segments);
	}
	
	private static void addSegment(TextSegment s, Stack<Segment> segments) {
		if (segments.size() > 0 && segments.peek().getSegmentType() == SegmentType.Text) {
			TextSegment ts = ((TextSegment) segments.peek());
			if (ts.getTextProperties().equals(s.getTextProperties())
			    && ts.getTextAttribute() == null && s.getTextAttribute() == null) {
				// Appending chars to existing text segment
				segments.pop();
				segments.push(new TextSegment(ts.getText() + "" + s.getText(), ts.getTextProperties()));
				return;
			}
		}
		segments.push(s);
	}
	
	@Override
	boolean isEmpty() {
		return segments.isEmpty();
	}

	@Override
	protected AbstractBlockContentManager newBlockContentManager(BlockContext context) {
		return new BlockContentManager(context.getFlowWidth(), processAttributes(segments), rdp, isVolatile, context.getRefs(),
				DefaultContext.from(context.getContext()).metaVolume(metaVolume).metaPage(metaPage).build(),
				context.getFcontext());
	}
	
	/**
	 * Process non-null text attributes of text segments. "Connected" segments are processed
	 * together.
	 */
	private static Stack<Segment> processAttributes(Stack<Segment> segments) {
		Stack<Segment> processedSegments = new Stack<Segment>();
		for (Segment s : segments) {
			if (s instanceof ConnectedTextSegment) {
				s = ((ConnectedTextSegment)s).processAttributes();
			}
			if (s instanceof TextSegment) {
				// cast to TextSegment in order to enable merging
				addSegment((TextSegment)s, processedSegments);
			} else {
				processedSegments.push(s);
			}
		}
		return processedSegments;
	}

}
