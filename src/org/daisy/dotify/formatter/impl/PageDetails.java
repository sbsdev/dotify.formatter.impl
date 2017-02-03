package org.daisy.dotify.formatter.impl;

import java.util.ArrayList;
import java.util.List;

import org.daisy.dotify.api.formatter.Marker;

class PageDetails {
	private final boolean duplex;
	private final int ordinal;
	private final int globalStartIndex;
	private final SequenceId sequenceId;
	private int volumeNumber;
	int contentMarkersBegin;
	
	ArrayList<Marker> markers;
	
	PageDetails(boolean duplex, int ordinal, int globalStartIndex, SequenceId sequenceId) {
		this.duplex = duplex;
		this.ordinal = ordinal;
		this.globalStartIndex = globalStartIndex;
		this.sequenceId = sequenceId;
		//FIXME: for this to work as intended, the markers have to have some way of remaining while being updated
		this.markers = new ArrayList<>();
		this.contentMarkersBegin = 0;
		this.volumeNumber = 0;
	}

	boolean duplex() {
		return duplex;
	}
	
	int getGlobalStartIndex() {
		return globalStartIndex;
	}
	
	int getOrdinal() {
		return ordinal;
	}
	
	int getPageId() {
		return globalStartIndex + ordinal;
	}
	
	SequenceId getSequenceId() {
		return sequenceId;
	}
	
	int getVolumeNumber() {
		return volumeNumber;
	}
	
	void setVolumeNumber(int volNumber) {
		this.volumeNumber = volNumber;
	}
	
	/*
	 * This method is unused at the moment, but could be activated once additional scopes are added to the API,
	 * namely SPREAD_WITHIN_SEQUENCE
	 */
	@SuppressWarnings("unused") 
	private boolean isWithinSequenceSpreadScope(int offset) {
		return 	offset==0 ||
				(
					duplex() && 
					(
						(offset == 1 && getOrdinal() % 2 == 1) ||
						(offset == -1 && getOrdinal() % 2 == 0)
					)
				);
	}
	
	boolean isWithinSpreadScope(int offset, PageDetails other) {
		if (other==null) { 
			return ((offset == 1 && getOrdinal() % 2 == 1) ||
					(offset == -1 && getOrdinal() % 2 == 0));
		} else {
			return (
					(offset == 1 && getOrdinal() % 2 == 1 && duplex()==true) ||
					(offset == -1 && getOrdinal() % 2 == 0 && other.duplex()==true && other.getOrdinal() % 2 == 1)
				);
		}
	}
	
	/**
	 * Get all markers for this page
	 * @return returns a list of all markers on a page
	 */
	List<Marker> getMarkers() {
		return markers;
	}
	
	/**
	 * Get markers for this page excluding markers before text content
	 * @return returns a list of markers on a page
	 */
	List<Marker> getContentMarkers() {
		return markers.subList(contentMarkersBegin, markers.size());
	}

	PageDetails getPageInScope(View<PageDetails> pageView, int offset, boolean adjustOutOfBounds) {
		if (offset==0) {
			return this;
		} else {
			if (pageView!=null) {
				int next = pageView.toLocalIndex(getPageId())+offset;
				int size = pageView.size();
				if (adjustOutOfBounds) {
					next = Math.min(size-1, Math.max(0, next));
				}
				if (next < size && next >= 0) {
					return pageView.get(next);
				}
			}
			return null;
		}
	}
	
	boolean isWithinSheetScope(int offset) {
		return 	offset==0 || 
				(
					duplex() &&
					(
						(offset == 1 && getOrdinal() % 2 == 0) ||
						(offset == -1 && getOrdinal() % 2 == 1)
					)
				);
	}
	
}
