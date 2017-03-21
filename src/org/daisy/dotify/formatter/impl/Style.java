package org.daisy.dotify.formatter.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.daisy.dotify.api.translator.DefaultTextAttribute;
import org.daisy.dotify.api.translator.MarkerProcessor;
import org.daisy.dotify.api.translator.MarkerProcessorConfigurationException;
import org.daisy.dotify.api.translator.TextAttribute;
import org.daisy.dotify.formatter.impl.segment.TextSegment;

/**
 * Associates a text style with a group of segments.
 */
class Style extends SegmentGroup {
	final FormatterCoreContext fc;
	final Style parentStyle;
	final int idx;
	final String name;
	private MarkerProcessor mp;
	
	Style(String name, FormatterCoreContext fc) {
		this(name, null, fc);
	}
	
	Style(String name, Style parentStyle, FormatterCoreContext fc) {
		super();
		this.fc = fc;
		this.parentStyle = parentStyle;
		if (parentStyle != null)
			idx = parentStyle.add(this);
		else
			idx = -1;
		this.name = name;
	}
	
	SegmentGroup processAttributes;
	SegmentGroup processAttributes() {
		if (parentStyle != null) {
			return parentStyle.processAttributes().getGroupAt(idx);
		} else {
			
			// FIXME: either make group incl. children immutable, or recompute whenever group is mutated
			if (processAttributes == null) {
				List<String> text = _text(segments);
				TextAttribute attributes = _attributes(name, segments);
				if (mp == null) {
					try {
						String locale = fc.getConfiguration().getLocale();
						String mode = fc.getTranslatorMode();
						mp = fc.getMarkerProcessorFactoryMakerService().newMarkerProcessor(locale, mode);
					} catch (MarkerProcessorConfigurationException e) {
						throw new IllegalArgumentException(e);
					}
				}
				String[] processedText = mp.processAttributesRetain(attributes, text.toArray(new String[text.size()]));
				processAttributes = _processAttributes(segments, Arrays.asList(processedText).iterator());
			}
			return processAttributes;
		}
	}
	
	private static List<String> _text(List<Object> segments) {
		List<String> l = new ArrayList<String>();
		for (Object o : segments) {
			if (o instanceof TextSegment) {
				TextSegment s = (TextSegment)o;
				l.add(s.getText());
			} else {
				SegmentGroup g = (SegmentGroup)o;
				l.addAll(_text(g.segments));
			}
		}
		return l;
	}
	
	private static TextAttribute _attributes(String name, List<Object> segments) {
		DefaultTextAttribute.Builder b = new DefaultTextAttribute.Builder(name);
		int w = 0;
		for (Object o : segments) {
			if (o instanceof TextSegment) {
				TextSegment s = (TextSegment)o;
				TextAttribute a = new DefaultTextAttribute.Builder().build(s.getText().length());
				b.add(a);
				w += a.getWidth();
			} else if (o instanceof Style) {
				Style s = (Style)o;
				TextAttribute a = _attributes(s.name, s.segments);
				b.add(a);
				w += a.getWidth();
			} else {
				SegmentGroup g = (SegmentGroup)o;
				TextAttribute a = _attributes(null, g.segments);
				b.add(a);
				w += a.getWidth();
			}
		}
		return b.build(w);
	}
	
	private static SegmentGroup _processAttributes(List<Object> segments, Iterator<String> processedText) {
		SegmentGroup processedGroup = new SegmentGroup();
		for (Object o : segments) {
			if (o instanceof TextSegment) {
				processedGroup.add(new TextSegment(processedText.next(), ((TextSegment)o).getTextProperties()));
			} else {
				processedGroup.add(_processAttributes(((Style)o).segments, processedText));
			}
		}
		return processedGroup;
	}
}