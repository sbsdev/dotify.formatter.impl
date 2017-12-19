package org.daisy.dotify.formatter.impl.obfl;

import static org.junit.Assert.assertEquals;

import org.daisy.dotify.api.formatter.NumeralStyle;
import org.daisy.dotify.formatter.impl.obfl.ObflParser;
import org.junit.Test;

@SuppressWarnings("javadoc")
public class OBFLParserTest {

	@Test
	public void testParseNumeralStyle_01() {
		NumeralStyle st = ObflParser.parseNumeralStyle("upper-alpha");
		assertEquals(NumeralStyle.UPPER_ALPHA, st);
	}
	
	@Test
	public void testParseNumeralStyle_02() {
		NumeralStyle st = ObflParser.parseNumeralStyle("upper_alpha");
		assertEquals(NumeralStyle.UPPER_ALPHA, st);
	}
	
	@Test
	public void testParseNumeralStyle_03() {
		NumeralStyle st = ObflParser.parseNumeralStyle("A");
		assertEquals(NumeralStyle.UPPER_ALPHA, st);
	}

}
