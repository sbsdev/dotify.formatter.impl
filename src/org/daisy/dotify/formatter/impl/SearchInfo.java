package org.daisy.dotify.formatter.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.daisy.dotify.formatter.impl.DefaultContext.Space;

class SearchInfo {
	private List<PageDetails> pageDetails;
	private final Map<Integer, View<PageDetails>> volumeViews;
	private final Map<Space, Map<Integer, View<PageDetails>>> sequenceViews;
	
	SearchInfo() {
		this.pageDetails = new ArrayList<>();
		this.volumeViews = new HashMap<>();
		this.sequenceViews = new HashMap<>();
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
	
	View<PageDetails> getContentsInSequence(Space space, int sequenceNumber) {
		return getViewForSpace(space).get(sequenceNumber);
	}
	
	void setSequenceScope(Space space, int sequenceNumber, int fromIndex, int toIndex) {
		View<PageDetails> pw = new View<PageDetails>(pageDetails, fromIndex, toIndex);
		getViewForSpace(space).put(sequenceNumber, pw);
	}

	void setVolumeScope(int volumeNumber, int fromIndex, int toIndex) {
		View<PageDetails> pw = new View<PageDetails>(pageDetails, fromIndex, toIndex);
		for (PageDetails p : pw.getItems()) {
			p.setVolumeNumber(volumeNumber);
		}
		volumeViews.put(volumeNumber, pw);
	}
	
	Map<Integer, View<PageDetails>> getViewForSpace(Space space) {
		Map<Integer, View<PageDetails>> ret = sequenceViews.get(space);
		if (ret==null) {
			ret = new HashMap<>();
			sequenceViews.put(space, ret);
		}
		return ret;
	}
}
