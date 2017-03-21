package org.daisy.dotify.formatter.impl;

import java.util.Stack;

import org.daisy.dotify.api.formatter.RenderingScenario;
import org.daisy.dotify.formatter.impl.FormatterCoreImpl.ConnectedTextSegment;
import org.daisy.dotify.formatter.impl.search.DefaultContext;
import org.daisy.dotify.formatter.impl.segment.Segment;
import org.daisy.dotify.formatter.impl.segment.TextSegment;
import org.daisy.dotify.formatter.impl.segment.Segment.SegmentType;

class RegularBlock extends Block {
	private boolean isVolatile;
	private boolean hasProcessedAttributes;

	RegularBlock(String blockId, RowDataProperties rdp, RenderingScenario scenario) {
		super(blockId, rdp, scenario);
		this.isVolatile = false;
		this.hasProcessedAttributes = false;
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
		if (segments.size() > 0 && segments.peek().getSegmentType() == SegmentType.Text) {
			TextSegment ts = ((TextSegment) segments.peek());
			if (ts.getTextProperties().equals(s.getTextProperties())
			    && ts.getTextAttribute() == null && s.getTextAttribute() == null) {
				// Logger.getLogger(this.getClass().getCanonicalName()).finer("Appending chars to existing text segment.");
				segments.pop();
				segments.push(new TextSegment(ts.getText() + "" + s.getText(), ts.getTextProperties()));
				return;
			}
		}
		segments.push(s);
	}

	@Override
	protected AbstractBlockContentManager newBlockContentManager(BlockContext context) {
		if (!hasProcessedAttributes) {
			Stack<Segment> processedSegments = processAttributes(segments);
			segments.clear();
			for (Segment s : processedSegments) {
				if (s instanceof TextSegment) {
					// cast to TextSegment in order to enable merging
					addSegment((TextSegment)s);
				} else {
					addSegment(s);
				}
			}
			hasProcessedAttributes = true;
		}
		return new BlockContentManager(context.getFlowWidth(), segments, rdp, isVolatile, context.getRefs(),
				DefaultContext.from(context.getContext()).metaVolume(metaVolume).metaPage(metaPage).build(),
				context.getFcontext());
	}
	
	/*
	 * Process non-null text attributes of text segments. "Connected" segments are processed
	 * together.
	 */
	static Stack<Segment> processAttributes(Stack<Segment> segments) {
		Stack<Segment> processedSegments = new Stack<Segment>();
		for (Segment s : segments) {
			if (s instanceof ConnectedTextSegment) {
				s = ((ConnectedTextSegment)s).processAttributes();
			}
			processedSegments.push(s);
		}
		return processedSegments;
	}

}
