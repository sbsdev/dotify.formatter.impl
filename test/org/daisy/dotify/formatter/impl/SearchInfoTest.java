package org.daisy.dotify.formatter.impl;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import org.daisy.dotify.api.formatter.Marker;
import org.daisy.dotify.formatter.impl.DefaultContext.Space;
import org.junit.Test;

public class SearchInfoTest {
	
	@Test
	public void testSearchInfo_01() {
		SearchInfo si = new SearchInfo();
		addPages(si, 6, 0, true, 0, 0, Space.PRE_CONTENT);
		addPages(si, 3, 0, true, 0, 0, Space.BODY);
		addPages(si, 3, 3, true, 0, 0, Space.BODY);
		addPages(si, 6, 0, true, 0, 0, Space.POST_CONTENT);
		si.setVolumeScope(1, 0, 3, Space.BODY);
		si.setVolumeScope(2, 3, 6, Space.BODY);
		si.setSequenceScope(Space.BODY, 0, 0, 6);
		View<PageDetails> vol1 = si.getContentsInVolume(1, Space.BODY);
		assertEquals(3, vol1.size());
		
		View<PageDetails> vol2 = si.getContentsInVolume(2, Space.BODY);
		assertEquals(3, vol2.size());
		
		View<PageDetails> seq = si.getContentsInSequence(new SequenceId(0, Space.BODY));
		assertEquals(6, seq.size());
	}
	
	private static void addPages(SearchInfo si, int count, int offset, boolean duplex, int globalStartIndex, int sequenceId, Space space) {
		addPages(si, count, offset, duplex, globalStartIndex, sequenceId, space, Collections.emptyMap());
	}
	
	private static void addPages(SearchInfo si, int count, int offset, boolean duplex, int globalStartIndex, int sequenceId, Space space, Map<Integer, ArrayList<Marker>> marker) {
		for (int i=0; i<count; i++) {
			PageDetails pd = new PageDetails(true, i+offset, globalStartIndex, new SequenceId(sequenceId, space));
			ArrayList<Marker> m = marker.get(i+offset);
			if (m!=null) {
				pd.markers = m;
			}
			si.addPageDetails(pd);
		}
	}

}
