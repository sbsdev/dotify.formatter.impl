package org.daisy.dotify.formatter.impl.search;

import com.github.krukow.clj_ds.Persistents;
import com.github.krukow.clj_lang.IPersistentMap;
import com.github.krukow.clj_lang.IPersistentVector;

/**
 * Provides the data needed for searching a document space.  
 * @author Joel Håkansson
 */
class DocumentSpaceData implements Cloneable {

		IPersistentVector<PageDetails> pageDetails;
		IPersistentMap<Integer, View<PageDetails>> volumeViews;
		IPersistentMap<Integer, View<PageDetails>> sequenceViews;
		
		DocumentSpaceData() {
			this.pageDetails = (IPersistentVector)Persistents.<PageDetails>vector();
			this.volumeViews = (IPersistentMap)Persistents.hashMap();
			this.sequenceViews = (IPersistentMap)Persistents.hashMap();
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
