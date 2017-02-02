package org.daisy.dotify.formatter.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class SearchInfo {
	private List<PageDetails> pageDetails;
	private final Map<Integer, View<PageDetails>> volumeViews;
	
	SearchInfo() {
		this.pageDetails = new ArrayList<>();
		this.volumeViews = new HashMap<>();
	}

	void addPageDetails(PageDetails value) {
		if (value.getPageId()<0) {
			throw new IllegalArgumentException("Negative page id not allowed.");
		}
		while (value.getPageId()>=pageDetails.size()) {
			pageDetails.add(null);
		}
		pageDetails.set(value.getPageId(), value);
	}

	View<PageDetails> getPageView() {
		return new View<PageDetails>(pageDetails, 0, pageDetails.size());
	}

	View<PageDetails> getContentsInVolume(int volumeNumber) {
		return volumeViews.get(volumeNumber);
	}

	void setVolumeScope(int volumeNumber, int fromIndex, int toIndex) {
		View<PageDetails> pw = new View<PageDetails>(pageDetails, fromIndex, toIndex);
		for (PageDetails p : pw.getItems()) {
			p.setVolumeNumber(volumeNumber);
		}
		volumeViews.put(volumeNumber, pw);
	}
	
}
