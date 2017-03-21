package org.daisy.dotify.formatter.impl;

import org.daisy.dotify.api.formatter.TextProperties;
import org.daisy.dotify.api.translator.DefaultTextAttribute;
import org.daisy.dotify.api.translator.TextAttribute;
import org.daisy.dotify.formatter.impl.segment.TextSegment;

/**
 * Text segment that is "connected" with other segments through Style elements.
 */
class ConnectedTextSegment extends TextSegment {		
	final StyledSegmentGroup parentStyle;
	final int idx;
	final int width;
	
	ConnectedTextSegment(String chars, TextProperties tp, StyledSegmentGroup parentStyle) {
		super(chars, tp);
		this.parentStyle = parentStyle;
		idx = parentStyle.add(this);
		width = chars.length();
	}
	
	@Override
	public TextAttribute getTextAttribute() {
		DefaultTextAttribute.Builder b = new DefaultTextAttribute.Builder();
		StyledSegmentGroup s = parentStyle;
		while (s != null) {
			b = new DefaultTextAttribute.Builder(s.name).add(b.build(width));
			s = s.parentStyle;
		}
		return b.build(width);
	}
	
	TextSegment processAttributes() {
		return parentStyle.processAttributes().getSegmentAt(idx);
	}
}
