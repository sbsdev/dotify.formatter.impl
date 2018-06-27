package org.daisy.dotify.formatter.impl.search;

import org.daisy.dotify.common.collection.ImmutableList;
import org.daisy.dotify.common.collection.ImmutableMap;

/**
 * Provides the data needed for searching a document space.  
 * @author Joel Håkansson
 */
class DocumentSpaceData implements Cloneable {

		ImmutableList<PageDetails> pageDetails;
		ImmutableMap<Integer, View<PageDetails>> volumeViews;
		ImmutableMap<Integer, View<PageDetails>> sequenceViews;
		
		DocumentSpaceData() {
			this.pageDetails = ImmutableList.empty();
			this.volumeViews = ImmutableMap.empty();
			this.sequenceViews = ImmutableMap.empty();
		}
		
		@Override
		public DocumentSpaceData clone() {
			DocumentSpaceData clone;
			try {
				clone = (DocumentSpaceData)super.clone();
			} catch (CloneNotSupportedException e) {
				throw new InternalError("coding error");
			}
			return clone;
		}
}
