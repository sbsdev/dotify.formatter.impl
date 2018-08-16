package org.daisy.dotify.formatter.impl.row;

import java.util.Optional;

interface CurrentResult {
	boolean hasNext(SegmentProcessing spi);
	Optional<RowImpl> process(SegmentProcessing spi, Integer page, float position, boolean wholeWordsOnly);
	CurrentResult copy();
}