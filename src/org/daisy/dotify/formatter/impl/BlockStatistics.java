package org.daisy.dotify.formatter.impl;

interface BlockStatistics {
	
	int getForceBreakCount();
	
	/**
	 * Gets the minimum width available for content (excluding margins)
	 * @return returns the available width, in characters
	 */
	int getMinimumAvailableWidth();

}
