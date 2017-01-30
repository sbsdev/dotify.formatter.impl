package org.daisy.dotify.formatter.impl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.daisy.dotify.api.formatter.SequenceProperties.SequenceBreakBefore;
import org.daisy.dotify.common.split.SplitPointDataSource;

class PageStructBuilder {

	private final FormatterContext context;
	private final Iterable<BlockSequence> fs;
	private final CrossReferenceHandler crh;
	private PageStruct struct;
	

	public PageStructBuilder(FormatterContext context, Iterable<BlockSequence> fs, CrossReferenceHandler crh) {
		this.context = context;
		this.fs = fs;
		this.crh = crh;
	}
	
	SplitPointDataSource<Sheet> paginate(DefaultContext rcontext) throws PaginatorException {
		restart:while (true) {
			struct = new PageStruct();
			try {
				return paginate(rcontext, fs);
			} catch (RestartPaginationException e) {
				continue restart;
			}
		}
	}
	
	Iterable<SplitPointDataSource<Sheet>> paginateGrouped(DefaultContext rcontext) {
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
					return paginateGrouped(rcontext, volGroups).iterator();
				} catch (PaginatorException e) {
					throw new RuntimeException(e);
				}
			}};
	}

	private List<SplitPointDataSource<Sheet>> paginateGrouped(DefaultContext rcontext, Iterable<Iterable<BlockSequence>> volGroups) throws PaginatorException {
		restart:while (true) {
			struct = new PageStruct();

			List<SplitPointDataSource<Sheet>> ret = new ArrayList<>();
			for (Iterable<BlockSequence> glist : volGroups) {
				try {
					ret.add(paginate(rcontext, glist));
				} catch (RestartPaginationException e) {
					continue restart;
				}
			}
			return ret;
		}
	}
	
	private SplitPointDataSource<Sheet> paginate(DefaultContext rcontext, Iterable<BlockSequence> seqs) throws PaginatorException, RestartPaginationException {
		return new SheetDataSource(struct, crh, context, rcontext, seqs.iterator());
	}

	void setVolumeScope(int volumeNumber, int fromIndex, int toIndex) {
		struct.setVolumeScope(volumeNumber, fromIndex, toIndex);
	}



}
