package org.daisy.dotify.formatter.impl;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.util.Stack;

import org.daisy.dotify.api.formatter.Context;
import org.daisy.dotify.api.formatter.FormatterConfiguration;
import org.daisy.dotify.api.formatter.Leader;
import org.daisy.dotify.api.formatter.Position;
import org.daisy.dotify.api.formatter.TextProperties;
import org.daisy.dotify.api.translator.BrailleTranslatorFactory;
import org.daisy.dotify.api.translator.TranslatorConfigurationException;
import org.daisy.dotify.consumer.translator.BrailleTranslatorFactoryMaker;
import org.daisy.dotify.consumer.translator.MarkerProcessorFactoryMaker;
import org.daisy.dotify.consumer.translator.TextBorderFactoryMaker;
import org.daisy.dotify.formatter.impl.search.CrossReferenceHandler;
import org.daisy.dotify.formatter.impl.search.DefaultContext;
import org.daisy.dotify.formatter.impl.segment.LeaderSegment;
import org.daisy.dotify.formatter.impl.segment.NewLineSegment;
import org.daisy.dotify.formatter.impl.segment.Segment;
import org.daisy.dotify.formatter.impl.segment.TextSegment;
import org.junit.Test;

@SuppressWarnings("javadoc")
public class BlockContentManagerTest {
	
	@Test
	public void testHangingIndent() throws TranslatorConfigurationException {
		//setup
		FormatterContext c = new FormatterContext(BrailleTranslatorFactoryMaker.newInstance(), TextBorderFactoryMaker.newInstance(), MarkerProcessorFactoryMaker.newInstance(), FormatterConfiguration.with("sv-SE", BrailleTranslatorFactory.MODE_UNCONTRACTED).build());
		Stack<Segment> segments = new Stack<>();
		for (int i=0; i<6; i++) {
			segments.push(new TextSegment("... ", new TextProperties.Builder("sv-SE").build()));
		}
		RowDataProperties rdp = new RowDataProperties.Builder().firstLineIndent(1).textIndent(3).build();
		CrossReferenceHandler refs = mock(CrossReferenceHandler.class);
		Context context = createContext();
		AbstractBlockContentManager m = new BlockContentManager(10, segments, rdp, refs, context, c);

		//test
		assertEquals(3, m.getRowCount());
		assertEquals("⠀⠄⠄⠄⠀⠄⠄⠄", m.get(0).getChars());
		assertEquals("⠀⠀⠀⠄⠄⠄⠀⠄⠄⠄", m.get(1).getChars());
		assertEquals("⠀⠀⠀⠄⠄⠄⠀⠄⠄⠄", m.get(2).getChars());
	}
	
	@Test
	public void testLeader() throws TranslatorConfigurationException {
		//setup
		FormatterContext c = new FormatterContext(BrailleTranslatorFactoryMaker.newInstance(), TextBorderFactoryMaker.newInstance(), MarkerProcessorFactoryMaker.newInstance(), FormatterConfiguration.with("sv-SE", BrailleTranslatorFactory.MODE_UNCONTRACTED).build());
		Stack<Segment> segments = new Stack<>();
		segments.push(new LeaderSegment(
				new Leader.Builder().align(org.daisy.dotify.api.formatter.Leader.Alignment.RIGHT).pattern(" ").position(new Position(1.0, true)).build())
		);
		segments.push(new TextSegment("...", new TextProperties.Builder("sv-SE").build()));

		RowDataProperties rdp = new RowDataProperties.Builder().firstLineIndent(1).textIndent(3).build();
		CrossReferenceHandler refs = mock(CrossReferenceHandler.class);
		Context context = createContext();
		AbstractBlockContentManager m = new BlockContentManager(10, segments, rdp, refs, context, c);

		//test
		assertEquals(1, m.getRowCount());
		assertEquals("⠀⠀⠀⠀⠀⠀⠀⠄⠄⠄", m.get(0).getChars());
	}
	
	@Test
	public void testNewLine() throws TranslatorConfigurationException {
		//setup
		FormatterContext c = new FormatterContext(BrailleTranslatorFactoryMaker.newInstance(), TextBorderFactoryMaker.newInstance(), MarkerProcessorFactoryMaker.newInstance(), FormatterConfiguration.with("sv-SE", BrailleTranslatorFactory.MODE_UNCONTRACTED).build());
		Stack<Segment> segments = new Stack<>();
		segments.push(new TextSegment("... ... ...", new TextProperties.Builder("sv-SE").build()));
		segments.push(new NewLineSegment());
		segments.push(new TextSegment("...", new TextProperties.Builder("sv-SE").build()));
		segments.push(new NewLineSegment());
		segments.push(new TextSegment("...", new TextProperties.Builder("sv-SE").build()));

		RowDataProperties rdp = new RowDataProperties.Builder().firstLineIndent(1).textIndent(3).build();
		CrossReferenceHandler refs = mock(CrossReferenceHandler.class);
		Context context = createContext();
		AbstractBlockContentManager m = new BlockContentManager(10, segments, rdp, refs, context, c);

		//test
		assertEquals(4, m.getRowCount());
		assertEquals("⠀⠄⠄⠄⠀⠄⠄⠄", m.get(0).getChars());
		RowImpl r = m.get(1);
		assertEquals("⠀⠀⠀⠄⠄⠄", r.getLeftMargin().getContent()+r.getChars());
		r = m.get(2);
		assertEquals("⠀⠀⠀⠄⠄⠄", r.getLeftMargin().getContent()+r.getChars());
		r = m.get(3);
		assertEquals("⠀⠀⠀⠄⠄⠄", r.getLeftMargin().getContent()+r.getChars());
	}

	
	private static Context createContext() {
		CrossReferenceHandler crh = new CrossReferenceHandler();
		crh.setVolumeCount(1);
		return new DefaultContext.Builder().currentVolume(1).referenceHandler(crh).build();
	}

}
