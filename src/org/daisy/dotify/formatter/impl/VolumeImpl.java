package org.daisy.dotify.formatter.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import org.daisy.dotify.writer.impl.Section;
import org.daisy.dotify.writer.impl.Volume;

/**
 * Provides a container for a physical volume of braille
 * @author Joel Håkansson
 */
class VolumeImpl implements Volume {
	private List<Section> body;
	private List<Section> preVolData;
	private List<Section> postVolData;
	private int preVolSize;
	private int postVolSize;
	private int bodyVolSize;
	
	VolumeImpl() {
		this.preVolSize = 0;
		this.postVolSize = 0;
		this.bodyVolSize = 0;
	}

	public void setBody(SectionBuilder body) {
		bodyVolSize = body.getSheetCount();
		this.body = body.getSections();
	}
	
	public void setPreVolData(SectionBuilder preVolData) {
		//use the highest value to avoid oscillation
		preVolSize = Math.max(preVolSize, preVolData.getSheetCount());
		this.preVolData = preVolData.getSections();
	}

	public void setPostVolData(SectionBuilder postVolData) {
		//use the highest value to avoid oscillation
		postVolSize = Math.max(postVolSize, postVolData.getSheetCount());
		this.postVolData = postVolData.getSections();
	}
	
	public int getOverhead() {
		return preVolSize + postVolSize;
	}
	
	public int getBodySize() {
		return bodyVolSize;
	}
	
	public int getVolumeSize() {
		return preVolSize + postVolSize + bodyVolSize;
	}

	@Override
	public Iterable<? extends Section> getSections() {
		List<Section> contents = new ArrayList<>();
		contents.addAll(preVolData);
		contents.addAll(body);
		contents.addAll(postVolData);
		return contents;
	}

}