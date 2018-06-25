package org.daisy.dotify.formatter.impl.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides the data needed for searching a document space.  
 * @author Joel Håkansson
 */
class DocumentSpaceData implements Cloneable {

		List<PageDetails> pageDetails;
		Map<Integer, View<PageDetails>> volumeViews;
		Map<Integer, View<PageDetails>> sequenceViews;
		
		DocumentSpaceData() {
			this.pageDetails = new ArrayList<>();
			this.volumeViews = new HashMap<>();
			this.sequenceViews = new HashMap<>();		
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public DocumentSpaceData clone() {
			DocumentSpaceData clone;
			try {
				clone = (DocumentSpaceData)super.clone();
			} catch (CloneNotSupportedException e) {
				throw new InternalError("coding error");
			}
			clone.pageDetails = (ArrayList<PageDetails>)((ArrayList<PageDetails>)pageDetails).clone();
			clone.volumeViews = (HashMap<Integer, View<PageDetails>>)((HashMap<Integer, View<PageDetails>>)volumeViews).clone();
			clone.sequenceViews = (HashMap<Integer, View<PageDetails>>)((HashMap<Integer, View<PageDetails>>)sequenceViews).clone();
			return clone;
		}
}
