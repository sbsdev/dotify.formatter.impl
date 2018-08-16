package org.daisy.dotify.formatter.impl.row;

import org.daisy.dotify.formatter.impl.common.FormatterCoreContext;
import org.daisy.dotify.formatter.impl.page.PageShape;

/**
 * Provides immutable information about the segment processor's context.
 * 
 * @author Joel Håkansson
 */
class SegmentProcessorContext {
	private final FormatterCoreContext fcontext;
	private final RowDataProperties rdp;
	private final BlockMargin margins;
	private final int flowWidth;
	private PageShape pageShape;
	private final char spaceChar;
	
	SegmentProcessorContext(FormatterCoreContext fcontext, RowDataProperties rdp, BlockMargin margins, int flowWidth, PageShape pageShape) {
		this.fcontext = fcontext;
		this.rdp = rdp;
		this.margins = margins;
		this.flowWidth = flowWidth;
		this.pageShape = pageShape;
		this.spaceChar = fcontext.getSpaceCharacter();
	}

	SegmentProcessorContext(SegmentProcessorContext template) {
		this.fcontext = template.fcontext;
		this.rdp = template.rdp;
		this.margins = template.margins;
		this.flowWidth = template.flowWidth;
		this.pageShape = template.pageShape;
		this.spaceChar = template.spaceChar;
	}
	
	RowDataProperties getRdp() {
		return rdp;
	}

	BlockMargin getMargins() {
		return margins;
	}

	int getFlowWidth() {
		return flowWidth;
	}

	PageShape getPageShape() {
		return pageShape;
	}

	char getSpaceCharacter() {
		return spaceChar;
	}

	FormatterCoreContext getFormatterContext() {
		return fcontext;
	}

	// FIXME: make immutable
	void setContext(PageShape pageShape) {
		this.pageShape = pageShape;
	}
}
