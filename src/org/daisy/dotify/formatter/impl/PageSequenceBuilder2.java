package org.daisy.dotify.formatter.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.daisy.dotify.api.formatter.BlockPosition;
import org.daisy.dotify.api.formatter.FallbackRule;
import org.daisy.dotify.api.formatter.MarginRegion;
import org.daisy.dotify.api.formatter.MarkerIndicatorRegion;
import org.daisy.dotify.api.formatter.PageAreaProperties;
import org.daisy.dotify.api.formatter.RenameFallbackRule;
import org.daisy.dotify.api.translator.Translatable;
import org.daisy.dotify.api.translator.TranslationException;
import org.daisy.dotify.api.writer.SectionProperties;
import org.daisy.dotify.common.split.SplitPoint;
import org.daisy.dotify.common.split.SplitPointDataList;
import org.daisy.dotify.common.split.SplitPointDataSource;
import org.daisy.dotify.common.split.SplitPointHandler;
import org.daisy.dotify.common.split.StandardSplitOption;
import org.daisy.dotify.common.split.Supplements;
import org.daisy.dotify.formatter.impl.search.CrossReferenceHandler;
import org.daisy.dotify.formatter.impl.search.DocumentSpace;
import org.daisy.dotify.formatter.impl.search.PageDetails;
import org.daisy.dotify.formatter.impl.search.SequenceId;
import org.daisy.dotify.formatter.impl.search.View;
import org.daisy.dotify.writer.impl.Page;
import org.daisy.dotify.writer.impl.Section;

class PageSequenceBuilder2 extends View<PageImpl> implements Section {
	private final FormatterContext context;
	private final CrossReferenceHandler crh;
	private final UnwriteableAreaInfo uai;
	private final PageAreaContent staticAreaContent;
	private final PageAreaProperties areaProps;

	private ContentCollectionImpl collection;
	private final BlockContext blockContext;
	private final LayoutMaster master;
	private final int pageNumberOffset;
	private final ListIterator<RowGroupSequence> dataGroups;
	private final int sequenceId;
	
	private SplitPointHandler<RowGroup> sph = new SplitPointHandler<>();
	private boolean force;
	
	private State state;
	
	private static class State implements Cloneable {
		PageImpl current;
		int keepNextSheets;
		int pageCount = 0;
		public Object clone() {
			State clone; {
				try {
					clone = (State)super.clone();
				} catch (CloneNotSupportedException e) {
					throw new InternalError("coding error");
				}
			}
			if (this.current != null) {
				clone.current = (PageImpl)this.current.clone();
			}
			return clone;
		}
	}

	PageSequenceBuilder2(PageStruct parent, LayoutMaster master, int pageOffset, CrossReferenceHandler crh, UnwriteableAreaInfo uai,
	                     BlockSequence seq, FormatterContext context, DefaultContext rcontext, int sequenceId) { 
		super(parent.getPages(), parent.getPages().size());
		this.master = master;
		this.pageNumberOffset = pageOffset;
		this.context = context;
		this.crh = crh;
		this.uai = uai;
		this.sequenceId = sequenceId;

		this.collection = null;
		this.areaProps = seq.getLayoutMaster().getPageArea();
		if (this.areaProps!=null) {
			this.collection = context.getCollections().get(areaProps.getCollectionId());
		}
		this.state = new State() {{
			current = null;
			keepNextSheets = 0;
		}};
		
		this.blockContext = new BlockContext(seq.getLayoutMaster().getFlowWidth(), crh, rcontext, context);
		this.staticAreaContent = new PageAreaContent(seq.getLayoutMaster().getPageAreaBuilder(), blockContext, uai);
		this.dataGroups = new RowGroupBuilder(master, seq, blockContext, uai).getResult();
	}

	private PageImpl newPage() {
		PageImpl buffer = state.current;
		SequenceId seqId = new SequenceId(sequenceId, new DocumentSpace(blockContext.getContext().getSpace(), blockContext.getContext().getCurrentVolume()));
		PageDetails details = new PageDetails(master.duplex(), state.pageCount, getGlobalStartIndex(), seqId);
		crh.getSearchInfo().addPageDetails(details);
		state.current = new PageImpl(crh, details, master, context, state.pageCount+pageNumberOffset, staticAreaContent.getBefore(), staticAreaContent.getAfter(), uai);
		state.pageCount ++;
		if (state.keepNextSheets>0) {
			currentPage().setAllowsVolumeBreak(false);
		}
		if (!master.duplex() || state.pageCount%2==0) {
			if (state.keepNextSheets>0) {
				state.keepNextSheets--;
			}
		}
		return buffer;
	}

	private void setKeepWithPreviousSheets(int value) {
		currentPage().setKeepWithPreviousSheets(value);
	}

	private void setKeepWithNextSheets(int value) {
		state.keepNextSheets = Math.max(value, state.keepNextSheets);
		if (state.keepNextSheets>0) {
			currentPage().setAllowsVolumeBreak(false);
		}
	}
	
	private PageImpl currentPage() {
		return state.current;
	}

	/**
	 * Space used, in rows
	 * 
	 * @return
	 */
	private int spaceUsedOnPage(int offs) {
		return currentPage().spaceUsedOnPage(offs);
	}

	private void newRow(RowImpl row) throws PageFullException {
		if (spaceUsedOnPage(1) > currentPage().getFlowHeight()) {
			throw new RuntimeException("Error in code.");
			//newPage();
		}
		currentPage().newRow(row);
	}

	private void insertIdentifier(String id) {
		crh.setPageNumber(id, currentPage().getPageIndex() + 1);
		currentPage().addIdentifier(id);
	}
	
	boolean hasNext() {
		return (nextPages != null && nextPages.hasNext()) || dataGroups.hasNext() || state.current!=null;
	}
	
	private Iterator<PageImpl> nextPages;
	
	PageImpl nextPage() throws PaginatorException,
    RestartPaginationException, // pagination must be restarted in PageStructBuilder.paginateInner
    RestartPaginationOfSequenceException // pagination must be restarted in PageStructBuilder.newSequence
	{
		PageImpl ret = nextPageInner();
		//This is for pre/post volume contents, where the volume number is known
		if (blockContext.getContext().getCurrentVolume()!=null) {
			for (String id : ret.getIdentifiers()) {
				crh.setVolumeNumber(id, blockContext.getContext().getCurrentVolume());
			}
		}
		addPage(ret);
		return ret;
	}

	PageImpl nextPageInner() throws PaginatorException,
		                       RestartPaginationException, // pagination must be restarted in PageStructBuilder.paginateInner
		                       RestartPaginationOfSequenceException // pagination must be restarted in PageStructBuilder.newSequence
	{
		if (nextPages != null && nextPages.hasNext()) {
			return nextPages.next();
		}
		List<PageImpl> pages = new ArrayList<PageImpl>();
        // uai.mark();
	  restartRowGroupSequence: while (dataGroups.hasNext()) {
			State stateBeforeDataGroup = (State)state.clone();
			int pageCountBeforeDataGroup = pages.size();
            // FIXME: mark() is used here to empty the map field of uai because setUnwriteableArea is only called when a
            // text line is too long to be flowed into a header/footer, so isDirty would give false positives which
            // could result in an endless loop. However with this solution isDirty gives false negatives which could
            // result in some lines being too short.
			uai.mark();
			//pick up next group
			RowGroupSequence rgs = dataGroups.next();
			CollectionData cd = new CollectionData(blockContext);
			SplitPointDataSource<RowGroup> data = new SplitPointDataList<>(rgs.getGroup(), cd);
			if (rgs.getBlockPosition()!=null) {
				if (state.pageCount==0) {
					// we know newPage returns null
					newPage();
				}
				float size = 0;
				for (RowGroup g : data.getRemaining()) {
					size += g.getUnitSize();
				}
				int pos = calculateVerticalSpace(rgs.getBlockPosition(), (int)Math.ceil(size));
				for (int i = 0; i < pos; i++) {
					RowImpl ri = rgs.getEmptyRow();
					try {
						RowImpl r = new RowImpl(ri.getChars(), ri.getLeftMargin(), ri.getRightMargin());
						r.block = ri.block;
						r.positionInBlock = ri.positionInBlock;
						newRow(r);
					} catch (PageFullException e) {
						throw new RuntimeException("A layout unit was too big for the page.", e);
					}
				}
			} else {
				PageImpl p = newPage();
				if (p!=null) {
					pages.add(p);
				}
			}
			force = false;
			while (!data.isEmpty()) {
				cd.reset();
				//Discards leading skippable row groups, but retains their properties
				SplitPoint<RowGroup> sl = SplitPointHandler.trimLeading(data);
				for (RowGroup rg : sl.getDiscarded()) {
					addProperties(rg);
				}
				data = sl.getTail();
				SplitPointDataSource<RowGroup> spd = data;
				int flowHeight = currentPage().getFlowHeight();
				SplitPoint<RowGroup> res;
				List<RowGroup> head;
				State stateBeforeSplit = (State)state.clone();
				uai.markUncommitted();
			  restartSplit: while (true) {
					res = sph.split(flowHeight, spd, force?StandardSplitOption.ALLOW_FORCE:null);
					if (res.getHead().size()==0 && force) {
						if (firstUnitHasSupplements(spd) && hasPageAreaCollection()) {
							reassignCollection();
							throw new RestartPaginationException();
						} else {
							throw new RuntimeException("A layout unit was too big for the page.");
						}
					}
					force = res.getHead().size()==0;
					data = res.getTail();
					head = res.getHead();
					for (RowGroup rg : head) {
						addProperties(rg);
						for (RowImpl r : rg.getRows()) { 
							if (r.shouldAdjustForMargin()) {
								// clone the row as not to append the margins twice
								r = RowImpl.withRow(r);
								for (MarginRegion mr : currentPage().getPageTemplate().getLeftMarginRegion()) {
									r.setLeftMargin(getMarginRegionValue(mr, r, false).append(r.getLeftMargin()));
								}
								for (MarginRegion mr : currentPage().getPageTemplate().getRightMarginRegion()) {
									r.setRightMargin(r.getRightMargin().append(getMarginRegionValue(mr, r, true)));
								}
							}
							try {
								currentPage().newRow(r);
							} catch (PageFullException e) {
								if (e.isHeaderRowTooShort()) {
									// Text that didn't fit in the header could come from a RowGroupSequence that also
									// has rows in an already return page. Because we can't easily predict this
									// situation we simply propagate the exception to PageStructBuilder.newSequence and
									// start all over.
                                    uai.commit();
									throw new RestartPaginationOfSequenceException();
								}
								int effectiveFlowHeight = e.getEffectiveFlowHeight();
								if (effectiveFlowHeight > flowHeight) {
									throw new RuntimeException("coding error");
								} else if (effectiveFlowHeight == flowHeight) {
									if (!uai.isDirty()) {
										throw new RuntimeException("coding error");
									}
                                    uai.commit();
									try {
										dataGroups.previous();
									} catch (IllegalStateException ee) {
                                        // This will happen when recomputing the previous RowGroupSequence results in a
                                        // different scenario being selected than before, and one or more
                                        // RowGroupSequences already consumed contain rows that belong to the old
                                        // scenario.
										throw new RestartPaginationOfSequenceException();
									}
                                    state = (State)stateBeforeDataGroup.clone();
                                    // Note: in case of an endless loop this will eventually cause a
                                    // StackOverFlowError on the next call to pages.add()
                                    pages = pages.subList(0, pageCountBeforeDataGroup);
                                    continue restartRowGroupSequence;
								} else {
									flowHeight = effectiveFlowHeight;
									state = (State)stateBeforeSplit.clone();
									uai.resetUncommitted();
									continue restartSplit;
								}
							}
						}
					}
					break;
				}
				Integer lastPriority = getLastPriority(head);
				if (!res.getDiscarded().isEmpty()) {
					//override if not empty
					lastPriority = getLastPriority(res.getDiscarded());
				}
				currentPage().setAvoidVolumeBreakAfter(lastPriority);
				for (RowGroup rg : res.getDiscarded()) {
					addProperties(rg);
				}
				for (RowGroup rg : res.getSupplements()) {
					currentPage().addToPageArea(rg.getRows());
				}
				if (hasPageAreaCollection() && currentPage().pageAreaSpaceNeeded() > master.getPageArea().getMaxHeight()) {
					reassignCollection();
					throw new RestartPaginationException();
				}
				if (!data.isEmpty()) {
					pages.add(newPage());
				}
			}
			if (uai.isDirty()) {
                uai.commit();
				state = (State)stateBeforeDataGroup.clone();
				dataGroups.previous();
				pages = pages.subList(0, pageCountBeforeDataGroup);
				continue restartRowGroupSequence;
            } else {
                uai.reset();
                // uai.mark();
            }
			if (!pages.isEmpty()) {
				nextPages = pages.iterator();
				return nextPages.next();
			}
		}
		//flush current page
		PageImpl ret = state.current;
		state.current = null;
		return ret;
	}
	
	private static Integer getLastPriority(List<RowGroup> list) {
		if (!list.isEmpty()) {
			return list.get(list.size()-1).getAvoidVolumeBreakAfterPriority();
		} else {
			return null;
		}
	}
	
	private boolean firstUnitHasSupplements(SplitPointDataSource<RowGroup> spd) {
		return !spd.isEmpty() && !spd.get(0).getSupplementaryIDs().isEmpty();
	}
	
	private boolean hasPageAreaCollection() {
		return master.getPageArea()!=null && collection!=null;
	}
	
	private MarginProperties getMarginRegionValue(MarginRegion mr, RowImpl r, boolean rightSide) throws PaginatorException {
		String ret = "";
		int w = mr.getWidth();
		if (mr instanceof MarkerIndicatorRegion) {
			ret = firstMarkerForRow(r, (MarkerIndicatorRegion)mr);
			if (ret.length()>0) {
				try {
					ret = context.getDefaultTranslator().translate(Translatable.text(context.getConfiguration().isMarkingCapitalLetters()?ret:ret.toLowerCase()).build()).getTranslatedRemainder();
				} catch (TranslationException e) {
					throw new PaginatorException("Failed to translate: " + ret, e);
				}
			}
			boolean spaceOnly = ret.length()==0;
			if (ret.length()<w) {
				StringBuilder sb = new StringBuilder();
				if (rightSide) {
					while (sb.length()<w-ret.length()) { sb.append(context.getSpaceCharacter()); }
					sb.append(ret);
				} else {
					sb.append(ret);				
					while (sb.length()<w) { sb.append(context.getSpaceCharacter()); }
				}
				ret = sb.toString();
			} else if (ret.length()>w) {
				throw new PaginatorException("Cannot fit " + ret + " into a margin-region of size "+ mr.getWidth());
			}
			return new MarginProperties(ret, spaceOnly);
		} else {
			throw new PaginatorException("Unsupported margin-region type: " + mr.getClass().getName());
		}
	}
	
	private String firstMarkerForRow(RowImpl r, MarkerIndicatorRegion mrr) {
		return mrr.getIndicators().stream()
				.filter(mi -> r.hasMarkerWithName(mi.getName()))
				.map(mi -> mi.getIndicator())
				.findFirst().orElse("");
	}
	
	private void addProperties(RowGroup rg) {
		if (rg.getIdentifier()!=null) {
			insertIdentifier(rg.getIdentifier());
		}
		currentPage().addMarkers(rg.getMarkers());
		//TODO: addGroupAnchors
		setKeepWithNextSheets(rg.getKeepWithNextSheets());
		setKeepWithPreviousSheets(rg.getKeepWithPreviousSheets());
	}
	
	private void reassignCollection() throws PaginatorException {
		//reassign collection
		if (areaProps!=null) {
			int i = 0;
			for (FallbackRule r : areaProps.getFallbackRules()) {
				i++;
				if (r instanceof RenameFallbackRule) {
					collection = context.getCollections().remove(r.applyToCollection());
					if (context.getCollections().put(((RenameFallbackRule)r).getToCollection(), collection)!=null) {
						throw new PaginatorException("Fallback id already in use:" + ((RenameFallbackRule)r).getToCollection());
					}							
				} else {
					throw new PaginatorException("Unknown fallback rule: " + r);
				}
			}
			if (i==0) {
				throw new PaginatorException("Failed to fit collection '" + areaProps.getCollectionId() + "' within the page-area boundaries, and no fallback was defined.");
			}
		}
	}
	
	private class CollectionData implements Supplements<RowGroup> {
		private boolean first;
		private final BlockContext c;
		private final Map<String, RowGroup> map;
		
		private CollectionData(BlockContext c) {
			this.c = c;
			this.first = true;
			this.map = new HashMap<>();
		}
		
		void reset() {
			first = true;
			map.clear();
		}

		@Override
		public RowGroup get(String id) {
			if (collection!=null) {
				RowGroup ret = map.get(id);
				if (ret==null) {
					RowGroup.Builder b = new RowGroup.Builder(master.getRowSpacing());
					for (Block g : collection.getBlocks(id)) {
						AbstractBlockContentManager bcm = g.getBlockContentManager(c, uai);
						b.addAll(bcm.getCollapsiblePreContentRows());
						b.addAll(bcm.getInnerPreContentRows());
						for (RowImpl r : bcm) {
							b.add(r);
						}
						b.addAll(bcm.getPostContentRows());
						b.addAll(bcm.getSkippablePostContentRows());
					}
					if (first) {
						b.overhead(currentPage().staticAreaSpaceNeeded());
						first = false;
					}
					ret = b.build();
					map.put(id, ret);
				} 
				return ret;
			} else {
				return null;
			}
		}
		
	}
	
	private int calculateVerticalSpace(BlockPosition p, int blockSpace) {
		if (p != null) {
			int pos = p.getPosition().makeAbsolute(currentPage().getFlowHeight());
			int t = pos - spaceUsedOnPage(0);
			if (t > 0) {
				int advance = 0;
				switch (p.getAlignment()) {
				case BEFORE:
					advance = t - blockSpace;
					break;
				case CENTER:
					advance = t - blockSpace / 2;
					break;
				case AFTER:
					advance = t;
					break;
				}
				return (int)Math.floor(advance / master.getRowSpacing());
			}
		}
		return 0;
	}
	
	private void addPage(PageImpl p) {
		items.add(p);
		setToIndex(getToIndex() + 1);
	}

	/**
	 * Gets the layout master for this sequence
	 * @return returns the layout master for this sequence
	 */
	LayoutMaster getLayoutMaster() {
		return master;
	}

	public int getPageNumberOffset() {
		return pageNumberOffset;
	}

	@Override
	public SectionProperties getSectionProperties() {
		return master;
	}

	@Override
	public List<? extends Page> getPages() {
		return getItems();
	}


}
