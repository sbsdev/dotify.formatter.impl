package org.daisy.dotify.formatter.impl;

import java.util.HashMap;
import java.util.Map;

class SearchInfo {
	private Map<Integer, PageDetails> pageDetails;
	
	SearchInfo() {
		this.pageDetails = new HashMap<>();
	}

	void addPageDetails(PageDetails value) {
		pageDetails.put(value.getPageId(), value);
	}
	
	
}
