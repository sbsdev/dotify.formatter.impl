package org.daisy.dotify.formatter.impl.sheet;

import java.util.function.Consumer;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.daisy.dotify.api.formatter.TransitionBuilderProperties.ApplicationRange;
import org.daisy.dotify.api.writer.SectionProperties;
import org.daisy.dotify.common.collection.ImmutableList;
import org.daisy.dotify.common.splitter.SplitPointDataSource;
import org.daisy.dotify.common.splitter.Supplements;
import org.daisy.dotify.formatter.impl.core.FormatterContext;
import org.daisy.dotify.formatter.impl.core.TransitionContent;
import org.daisy.dotify.formatter.impl.datatype.VolumeKeepPriority;
import org.daisy.dotify.formatter.impl.page.BlockSequence;
import org.daisy.dotify.formatter.impl.page.PageImpl;
import org.daisy.dotify.formatter.impl.page.PageSequenceBuilder2;
import org.daisy.dotify.formatter.impl.page.RestartPaginationException;
import org.daisy.dotify.formatter.impl.search.CrossReferenceHandler;
import org.daisy.dotify.formatter.impl.search.DefaultContext;
import org.daisy.dotify.formatter.impl.search.DocumentSpace;
import org.daisy.dotify.formatter.impl.search.PageDetails;
import org.daisy.dotify.formatter.impl.search.PageId;
import org.daisy.dotify.formatter.impl.search.SheetIdentity;
import org.daisy.dotify.formatter.impl.search.TransitionProperties;

/**
 * Provides a data source for sheets. Given a list of 
 * BlockSequences, sheets are produced one by one.
 * 
 * @author Joel Håkansson
 */
public class SheetDataSource implements SplitPointDataSource<Sheet,SheetDataSource> {
	//Global state
	private final FormatterContext context;
	//Input data
	private PageCounter pageCounter;
	private DefaultContext initialContext;
	private final Integer volumeGroup;
	private final List<BlockSequence> seqsIterator;
	//Local state
	private int seqsIndex;
	private PageSequenceBuilder2 psb;
	private SectionProperties sectionProperties;
	private int sheetIndex;
	private int pageIndex;
	private String counter;
	private int initialPageOffset;
	private boolean volBreakAllowed;
	private boolean updateCounter;
	private boolean allowsSplit;
	private boolean wasSplitInsideSequence;
	private boolean volumeEnded;
	//Output buffer
	private ImmutableList.Builder<Sheet> sheetBuffer;
	private int bufferIndex;

	public SheetDataSource(FormatterContext context, Integer volumeGroup, List<BlockSequence> seqsIterator) {
		this.context = context;
		this.volumeGroup = volumeGroup;
		this.seqsIterator = seqsIterator;
		this.sheetBuffer = ImmutableList.<Sheet>empty().builder();
		this.bufferIndex = 0;
		this.volBreakAllowed = true;
		this.seqsIndex = 0;
		this.psb = null;
		this.sectionProperties = null;
		this.sheetIndex = 0;
		this.pageIndex = 0;
		this.counter = null;
		this.initialPageOffset = 0;
		this.updateCounter = false;
		this.allowsSplit = true;
		this.wasSplitInsideSequence = false;
		this.volumeEnded = false;
		// initialized later
		this.pageCounter = null;
		this.initialContext = null;
	}

	/**
	 * Creates a deep copy of template
	 *
	 * @param template the template
	 */
	private SheetDataSource(SheetDataSource template) {
		this.pageCounter = template.pageCounter;
		this.context = template.context;
		this.initialContext = template.initialContext;
		this.volumeGroup = template.volumeGroup;
		this.seqsIterator = template.seqsIterator;
		this.seqsIndex = template.seqsIndex;
		this.psb = PageSequenceBuilder2.copyUnlessNull(template.psb);
		this.sectionProperties = template.sectionProperties;
		this.sheetIndex = template.sheetIndex;
		this.pageIndex = template.pageIndex;
		this.sheetBuffer = template.sheetBuffer.clone();
		this.bufferIndex = template.bufferIndex;
		this.volBreakAllowed = template.volBreakAllowed;
		this.counter = template.counter;
		this.initialPageOffset = template.initialPageOffset;
		this.updateCounter = template.updateCounter;
		this.allowsSplit = template.allowsSplit;
		this.wasSplitInsideSequence = template.wasSplitInsideSequence;
		this.volumeEnded = template.volumeEnded;
	}

	// FIXME: make immutable
	public void initialize(PageCounter counter, DefaultContext rcontext) {
		this.pageCounter = counter;
		this.initialContext = rcontext;
	}

	private void checkInitialized() {
		if (pageCounter == null || initialContext == null)
			throw new IllegalStateException("Not initialized yet");
	}

	PageCounter getPageCounter() {
		checkInitialized();
		return pageCounter;
	}

	public DefaultContext getContext() {
		checkInitialized();
		if (psb == null)
			return initialContext;
		else
			return psb.getContext();
	}

	private void modifyPageCounter(Consumer<PageCounter.Builder> modifier) {
		PageCounter.Builder b = pageCounter.builder();
		modifier.accept(b);
		pageCounter = b.build();
	}

	// FIXME: make immutable
	// -> e.g. by returning a new SheetDataSource
	public void modifyContext(Consumer<DefaultContext.Builder> modifier) {
		if (psb == null) {
			DefaultContext.Builder b = initialContext.builder();
			modifier.accept(b);
			initialContext = b.build();
		} else
			psb.modifyContext(modifier);
	}
	
	private void modifyRefs(Consumer<CrossReferenceHandler.Builder> modifier) {
		modifyContext(c -> modifier.accept(c.getRefs()));
	}

	@Override
	public Supplements<Sheet> getSupplements() {
		return null;
	}
	
	@Override
	public boolean isEmpty() {
		checkInitialized();
		return seqsIndex>=seqsIterator.size() && bufferIndex >= sheetBuffer.size() && (psb==null || !psb.hasNext());
	}
	
	@Override
	public Iterator<Sheet,SheetDataSource> iterator() {
		checkInitialized();
		return new SheetDataSource(this).asIterator();
	}
	
	private Iterator<Sheet,SheetDataSource> asIterator() {
		return new SheetDataSourceIterator();
	}
	
	public int countRemainingSheets() {
		int count = 0;
		for (Iterator<Sheet,SheetDataSource> it = iterator(); it.hasNext();) {
			// position is irrelevant
			it.next(-1, false);
			count++;
		}
		return count;
	}

	public int countRemainingPages() {
		int pages = 0;
		for (Iterator<Sheet,SheetDataSource> it = iterator(); it.hasNext();) {
			// position is irrelevant
			pages += it.next(-1, false).getPages().size();
		}
		return pages;
	}

	private class SheetDataSourceIterator implements Iterator<Sheet,SheetDataSource> {

		@Override
		public boolean hasNext() {
			return seqsIndex < seqsIterator.size() || bufferIndex < sheetBuffer.size() || (psb != null && psb.hasNext());
		}

		/** @param position is ignored */
		@Override
		public Sheet next(float position, boolean last) throws RestartPaginationException {
			if (last) {
				if (!allowsSplit) {
					throw new IllegalStateException();
				}
				allowsSplit = false;
			}
			
			Sheet.Builder s = null;
			SheetIdentity si = null;
			while (sheetBuffer.size() <= bufferIndex) {
				if (updateCounter) {
					if(counter!=null) {
						initialPageOffset = getContext().getRefs().getPageNumberOffset(counter) - psb.size();
					} else {
						initialPageOffset = pageCounter.getDefaultPageOffset() - psb.size();
					}
					updateCounter = false;
				}
				if (psb==null || !psb.hasNext()) {
					if (s!=null) {
						//Last page in the sequence doesn't need volume keep priority
						sheetBuffer.add(s.build());
						s=null;
						continue;
					}
					if (seqsIndex>=seqsIterator.size()) {
						throw new NoSuchElementException();
					}
					// init new sequence
					BlockSequence bs = seqsIterator.get(seqsIndex);
					seqsIndex++;
					counter = bs.getSequenceProperties().getPageCounterName().orElse(null);
					if (bs.getInitialPageNumber()!=null) {
						 initialPageOffset = bs.getInitialPageNumber() - 1;
					} else if (counter!=null) {
						initialPageOffset = Optional.ofNullable(getContext().getRefs().getPageNumberOffset(counter)).orElse(0);
					} else {
						 initialPageOffset = pageCounter.getDefaultPageOffset();
					}
					psb = new PageSequenceBuilder2(pageCounter.getPageCount(), bs.getLayoutMaster(), initialPageOffset, bs, context, getContext(), seqsIndex);
					sectionProperties = bs.getLayoutMaster().newSectionProperties();
					s = null;
					si = null;
					sheetIndex = 0;
					pageIndex = 0;
				}
				int currentSize = sheetBuffer.size();
				while (psb.hasNext() && currentSize == sheetBuffer.size()) {
					if (!sectionProperties.duplex() || pageIndex % 2 == 0 || volumeEnded || s==null) {
						if (s!=null) {
							Sheet r = s.build();
							sheetBuffer.add(r);
							s = null;
							if (volumeEnded) {
								pageIndex += pageIndex%2==1?1:0;
							}
							continue;
						} else if (volumeEnded) {
							throw new AssertionError("Error in code.");
						}
						volBreakAllowed = true;
						s = new Sheet.Builder(sectionProperties);
						si = new SheetIdentity(getContext().getSpace(), getContext().getCurrentVolume(), volumeGroup, sheetBuffer.size());
						sheetIndex++;
					}
	
					TransitionContent transition = null;
					if (context.getTransitionBuilder().getProperties().getApplicationRange()!=ApplicationRange.NONE) {
						if (!allowsSplit && last) {
							if ((!sectionProperties.duplex() || pageIndex % 2 == 1)) {
								transition = context.getTransitionBuilder().getInterruptTransition();
							} else if (context.getTransitionBuilder().getProperties().getApplicationRange()==ApplicationRange.SHEET) {
								// This id is the same id as the one created below in the call to nextPage
								PageId thisPageId = psb.nextPageId(0);
								// This gets the page details for the next page in this sequence (if any)
								Optional<PageDetails> next = getContext().getRefs().findNextPageInSequence(thisPageId);
								// If there is a page details in this sequence and volume break is preferred on this page
								if (next.isPresent()) {
									TransitionProperties st = getContext().getRefs().getTransitionProperties(thisPageId);
									double v1 = st.getVolumeKeepPriority().orElse(10) + (st.hasBlockBoundary()?0.5:0);
									st = getContext().getRefs().getTransitionProperties(next.get().getPageId());
									double v2 = st.getVolumeKeepPriority().orElse(10) + (st.hasBlockBoundary()?0.5:0);
									if (v1>v2) {
										//break here
										transition = context.getTransitionBuilder().getInterruptTransition();
									}
								}
							}
							volumeEnded = transition!=null;
						} else if (wasSplitInsideSequence && (!sectionProperties.duplex() || pageIndex % 2 == 0)) {
							transition = context.getTransitionBuilder().getResumeTransition();
						}
					}
					boolean hyphenateLastLine =
							!(	!context.getConfiguration().allowsEndingVolumeOnHyphen()
									&& last
									&& (!sectionProperties.duplex() || pageIndex % 2 == 1));
					
					PageImpl p = psb.nextPage(initialPageOffset, hyphenateLastLine, Optional.ofNullable(transition));
					modifyPageCounter(str -> str.increasePageCount());
					VolumeKeepPriority vpx = p.getAvoidVolumeBreakAfter();
					if (context.getTransitionBuilder().getProperties().getApplicationRange()==ApplicationRange.SHEET) {
						Sheet sx = s.build();
						if (!sx.getPages().isEmpty()) {
							VolumeKeepPriority vp = sx.getAvoidVolumeBreakAfterPriority();
							if (vp.orElse(10)>vpx.orElse(10)) {
								vpx = vp;
							}
						}
					}
					s.avoidVolumeBreakAfterPriority(vpx);
					if (!psb.hasNext()) {
						s.avoidVolumeBreakAfterPriority(VolumeKeepPriority.empty());
						//Don't get or store this value in crh as it is transient and not a property of the sheet context
						s.breakable(true);
					} else {
						boolean br = getContext().getRefs().getBreakable(si);
						//TODO: the following is a low effort way of giving existing uses of non-breakable units a high priority, but it probably shouldn't be done this way
						if (!br) {
							s.avoidVolumeBreakAfterPriority(VolumeKeepPriority.of(1));
						}
						s.breakable(br);
					}
	
					setPreviousSheet(si.getSheetIndex()-1, Math.min(p.keepPreviousSheets(), sheetIndex-1));
					volBreakAllowed &= p.allowsVolumeBreak();
					if (!sectionProperties.duplex() || pageIndex % 2 == 1) {
						final SheetIdentity _si = si;
						modifyRefs(refs -> refs.setBreakable(_si, volBreakAllowed));
					}
					s.add(p);
					pageIndex++;
				}
				if (!psb.hasNext()||volumeEnded) {
					if (!psb.hasNext()) {
						modifyRefs(
							refs -> refs.setSequenceScope(new DocumentSpace(getContext().getSpace(), getContext().getCurrentVolume()),
							                              seqsIndex, psb.getGlobalStartIndex(), psb.getToIndex()));
					}
					if (counter!=null) {
						modifyRefs(refs -> refs.setPageNumberOffset(counter, initialPageOffset + psb.getSizeLast()));
					} else {
						modifyPageCounter(str -> str.setDefaultPageOffset(initialPageOffset + psb.getSizeLast()));
					}
				}
			}
			
			if (last) {
				if (counter!=null) {
					modifyRefs(refs -> refs.setPageNumberOffset(counter, initialPageOffset + psb.getSizeLast()));
				} else {
					modifyPageCounter(str -> str.setDefaultPageOffset(initialPageOffset + psb.getSizeLast()));
				}
				wasSplitInsideSequence = psb.hasNext();
				allowsSplit = true;
				updateCounter = true;
				volumeEnded = false;
			} else {
				wasSplitInsideSequence = false;
			}
			return sheetBuffer.get(bufferIndex++);
		}

		@Override
		public SheetDataSource iterable() {
			return new SheetDataSource(SheetDataSource.this);
		}
		
		private void setPreviousSheet(int start, int p) {
			int i = 0;
			//TODO: simplify this?
			for (int x = start; i < p && x > 0; x--) {
				SheetIdentity si = new SheetIdentity(getContext().getSpace(), getContext().getCurrentVolume(), volumeGroup, x);
				modifyRefs(refs -> refs.setBreakable(si, false));
				i++;
			}
		}
	}
}
