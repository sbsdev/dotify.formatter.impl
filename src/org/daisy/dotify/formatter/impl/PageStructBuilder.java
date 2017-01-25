package org.daisy.dotify.formatter.impl;

import java.util.ArrayList;
import java.util.List;

import org.daisy.dotify.api.formatter.SequenceProperties.SequenceBreakBefore;
import org.daisy.dotify.common.split.SplitPointDataList;
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
	
	List<SplitPointDataSource<Sheet>> paginateGrouped(DefaultContext rcontext) throws PaginatorException {
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
		return paginateGrouped(rcontext, volGroups);
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
		List<Sheet> currentGroup = new ArrayList<>();
		boolean volBreakAllowed = true;
		for (BlockSequence bs : seqs) {
			PageSequence seq = newSequence(bs, rcontext);
			LayoutMaster lm = seq.getLayoutMaster();
			Sheet.Builder s = null;
			SheetIdentity si = null;
			List<PageImpl> pages = seq.getPages();
			int sheetIndex = 0;
			for (int pageIndex = 0; pageIndex<pages.size(); pageIndex++) {
				PageImpl p = pages.get(pageIndex);
				if (!lm.duplex() || pageIndex % 2 == 0) {
					volBreakAllowed = true;
					if (s!=null) {
						Sheet r = s.build();
						currentGroup.add(r);
					}
					s = new Sheet.Builder();
					si = new SheetIdentity(rcontext.getSpace(), rcontext.getCurrentVolume()==null?0:rcontext.getCurrentVolume(), currentGroup.size());
					sheetIndex++;
				}
				s.avoidVolumeBreakAfterPriority(p.getAvoidVolumeBreakAfter());
				if (pageIndex==pages.size()-1) {
					s.avoidVolumeBreakAfterPriority(null);
					//Don't get or store this value in crh as it is transient and not a property of the sheet context
					s.breakable(true);
				} else {
					boolean br = crh.getBreakable(si);
					//TODO: the following is a low effort way of giving existing uses of non-breakable units a high priority, but it probably shouldn't be done this way
					if (!br) {
						s.avoidVolumeBreakAfterPriority(1);
					}
					s.breakable(br);
				}

				setPreviousSheet(si.getSheetIndex()-1, Math.min(p.keepPreviousSheets(), sheetIndex-1), rcontext);
				volBreakAllowed &= p.allowsVolumeBreak();
				if (!lm.duplex() || pageIndex % 2 == 1) {
					crh.keepBreakable(si, volBreakAllowed);
				}
				s.add(p);
			}
			if (s!=null) {
				//Last page in the sequence doesn't need volume keep priority
				currentGroup.add(s.build());
			}
		}
		crh.commitBreakable();
		return new SplitPointDataList<>(currentGroup);
	}

	private PageSequence newSequence(BlockSequence seq, DefaultContext rcontext) throws PaginatorException, RestartPaginationException {
		int offset = getCurrentPageOffset();
		UnwriteableAreaInfo uai = new UnwriteableAreaInfo();
	  restart: while (true) {
			PageSequenceBuilder2 psb = new PageSequenceBuilder2(struct, seq.getLayoutMaster(), seq.getInitialPageNumber()!=null?seq.getInitialPageNumber() - 1:offset, crh, uai, seq, context, rcontext);
			while (psb.hasNext()) {
				try {
					psb.nextPage();
				} catch (RestartPaginationOfSequenceException e) {
					if (!uai.isDirty()) {
						throw new RuntimeException("coding error");
					} else {
						uai.commit();
						uai.rewind();
						continue restart;
					}
				}
			}
			if (uai.isDirty()) {
				throw new RuntimeException("coding error");
			}
			struct.add(psb.getSequence());
			return psb.getSequence();
		}
	}
	
	private int getCurrentPageOffset() {
		if (struct.size()>0) {
			PageSequence prv = (PageSequence)struct.peek();
			if (prv.getLayoutMaster().duplex() && (prv.getPageCount() % 2)==1) {
				return prv.getPageNumberOffset() + prv.getPageCount() + 1;
			} else {
				return prv.getPageNumberOffset() + prv.getPageCount();
			}
		} else {
			return 0;
		}
	}
	
	void setVolumeScope(int volumeNumber, int fromIndex, int toIndex) {
		struct.setVolumeScope(volumeNumber, fromIndex, toIndex);
	}

	private void setPreviousSheet(int start, int p, DefaultContext rcontext) {
		int i = 0;
		//TODO: simplify this?
		for (int x = start; i < p && x > 0; x--) {
			SheetIdentity si = new SheetIdentity(rcontext.getSpace(), rcontext.getCurrentVolume()==null?0:rcontext.getCurrentVolume(), x);
			crh.keepBreakable(si, false);
			i++;
		}
	}

}
