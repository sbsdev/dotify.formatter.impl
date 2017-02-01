package org.daisy.dotify.formatter.impl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.daisy.dotify.api.formatter.SequenceProperties.SequenceBreakBefore;
import org.daisy.dotify.common.split.SplitPointDataSource;

class PageStructBuilder {

	private final FormatterContext context;
	private final CrossReferenceHandler crh;

	public PageStructBuilder(FormatterContext context,  CrossReferenceHandler crh) {
		this.context = context;
		this.crh = crh;
	}
	
	SplitPointDataSource<Sheet> prepareToPaginate(PageStruct struct, Iterable<BlockSequence> fs, DefaultContext rcontext) throws PaginatorException {
		return prepareToPaginate(struct, rcontext, fs);
	}
	
	Iterable<SplitPointDataSource<Sheet>> prepareToPaginateWithVolumeGroups(PageStruct struct, Iterable<BlockSequence> fs, DefaultContext rcontext) {
		List<Iterable<BlockSequence>> volGroups = new ArrayList<>();
		List<BlockSequence> currentGroup = new ArrayList<>();
		volGroups.add(currentGroup);
		for (BlockSequence bs : fs) {
			if (bs.getSequenceProperties().getBreakBeforeType()==SequenceBreakBefore.VOLUME) {
				currentGroup = new ArrayList<>();
				volGroups.add(currentGroup);
			}
			currentGroup.add(bs);
		}
		return new Iterable<SplitPointDataSource<Sheet>>(){
			@Override
			public Iterator<SplitPointDataSource<Sheet>> iterator() {
				try {
					return prepareToPaginateWithVolumeGroups(struct, rcontext, volGroups).iterator();
				} catch (PaginatorException e) {
					throw new RuntimeException(e);
				}
			}};
	}

	private List<SplitPointDataSource<Sheet>> prepareToPaginateWithVolumeGroups(PageStruct struct, DefaultContext rcontext, Iterable<Iterable<BlockSequence>> volGroups) throws PaginatorException {

		List<SplitPointDataSource<Sheet>> ret = new ArrayList<>();
		for (Iterable<BlockSequence> glist : volGroups) {
			ret.add(prepareToPaginate(struct, rcontext, glist));
		}
		return ret;
	}
	
	private SplitPointDataSource<Sheet> prepareToPaginate(PageStruct struct, DefaultContext rcontext, Iterable<BlockSequence> seqs) throws PaginatorException {
		return new SheetDataSource(struct, crh, context, rcontext, seqs.iterator());
	}

}
