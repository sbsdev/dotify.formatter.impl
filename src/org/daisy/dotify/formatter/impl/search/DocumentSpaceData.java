package org.daisy.dotify.formatter.impl.search;

import com.github.krukow.clj_ds.Persistents;
import com.github.krukow.clj_lang.IPersistentVector;

import org.daisy.dotify.common.collection.ImmutableMap;

/**
 * Provides the data needed for searching a document space.  
 * @author Joel Håkansson
 */
class DocumentSpaceData implements Cloneable {

		IPersistentVector<PageDetails> pageDetails;
		ImmutableMap<Integer, View<PageDetails>> volumeViews;
		ImmutableMap<Integer, View<PageDetails>> sequenceViews;
		
		DocumentSpaceData() {
			this.pageDetails = (IPersistentVector)Persistents.<PageDetails>vector();
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
