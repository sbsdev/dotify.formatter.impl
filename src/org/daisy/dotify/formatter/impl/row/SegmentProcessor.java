package org.daisy.dotify.formatter.impl.row;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.daisy.dotify.api.formatter.Context;
import org.daisy.dotify.api.formatter.FormattingTypes;
import org.daisy.dotify.api.formatter.Marker;
import org.daisy.dotify.api.translator.BrailleTranslatorResult;
import org.daisy.dotify.api.translator.Translatable;
import org.daisy.dotify.api.translator.TranslationException;
import org.daisy.dotify.common.text.StringTools;
import org.daisy.dotify.formatter.impl.common.FormatterCoreContext;
import org.daisy.dotify.formatter.impl.search.CrossReferenceHandler;
import org.daisy.dotify.formatter.impl.search.DefaultContext;
import org.daisy.dotify.formatter.impl.segment.AnchorSegment;
import org.daisy.dotify.formatter.impl.segment.Evaluate;
import org.daisy.dotify.formatter.impl.segment.LeaderSegment;
import org.daisy.dotify.formatter.impl.segment.MarkerSegment;
import org.daisy.dotify.formatter.impl.segment.PageNumberReferenceSegment;
import org.daisy.dotify.formatter.impl.segment.Segment;
import org.daisy.dotify.formatter.impl.segment.TextSegment;

class SegmentProcessor {
	private static final Pattern softHyphenPattern  = Pattern.compile("\u00ad");
	private static final Pattern trailingWsBraillePattern = Pattern.compile("[\\s\u2800]+\\z");
	private final List<Segment> segments;
	private final int flowWidth;
	private final CrossReferenceHandler refs;
	private final int available;
	private final BlockMargin margins;
	private final FormatterCoreContext fcontext;
	private final RowDataProperties rdp;
	private Context context;
	private final boolean significantContent;

	private int segmentIndex;
	private RowImpl.Builder currentRow;
	private final ArrayList<Marker> groupMarkers;
	private final ArrayList<String> groupAnchors;
	private AggregatedBrailleTranslatorResult.Builder layoutOrApplyAfterLeader;
	private String currentLeaderMode;
	private boolean seenSegmentAfterLeader;
	private final LeaderManager leaderManager;
	private ListItem item;
	private int forceCount;
	private int minLeft;
	private int minRight;
	private boolean empty;
	private CurrentResult cr;
	private boolean closed;

	SegmentProcessor(List<Segment> segments, int flowWidth, CrossReferenceHandler refs, Context context, int available, BlockMargin margins, FormatterCoreContext fcontext, RowDataProperties rdp) {
		this.segments = Collections.unmodifiableList(segments);
		this.flowWidth = flowWidth;
		this.refs = refs;
		this.context = context;
		this.available = available;
		this.margins = margins;
		this.fcontext = fcontext;
		this.rdp = rdp;
		this.groupMarkers = new ArrayList<>();
		this.groupAnchors = new ArrayList<>();
		this.leaderManager = new LeaderManager();
		this.significantContent = calculateSignificantContent(segments, context, rdp);
		initFields();
	}
	
	SegmentProcessor(SegmentProcessor template) {
		this.flowWidth = template.flowWidth;
		// Refs is mutable, but for now we assume that the same context should be used.
		this.refs = template.refs;
		this.available = template.available;
		this.margins = template.margins;
		// Mutable
		this.fcontext = template.fcontext;
		// Context is mutable, but for now we assume that the same context should be used.
		this.context = template.context;
		this.rdp = template.rdp;
		this.currentRow = template.currentRow==null?null:new RowImpl.Builder(template.currentRow);
		this.groupAnchors = new ArrayList<>(template.groupAnchors);
		this.groupMarkers = new ArrayList<>(template.groupMarkers);
		this.leaderManager = new LeaderManager(template.leaderManager);
		this.layoutOrApplyAfterLeader = template.layoutOrApplyAfterLeader==null?null:new AggregatedBrailleTranslatorResult.Builder(template.layoutOrApplyAfterLeader);
		this.currentLeaderMode = template.currentLeaderMode;
		this.seenSegmentAfterLeader = template.seenSegmentAfterLeader;
		this.item = template.item;
		this.forceCount = template.forceCount;
		this.minLeft = template.minLeft;
		this.minRight = template.minRight;
		this.empty  = template.empty;
		this.segments = template.segments;
		this.segmentIndex = template.segmentIndex;
		this.cr = template.cr!=null?template.cr.copy():null;
		this.closed = template.closed;
		this.significantContent = template.significantContent;
	}
	
	private static boolean calculateSignificantContent(List<Segment> segments, Context context, RowDataProperties rdp) {
		for (Segment s : segments) {
			switch (s.getSegmentType()) {
				case Marker:
				case Anchor:
					// continue
					break;
				case Evaluate:
					if (!((Evaluate)s).getExpression().render(context).isEmpty()) {
						return true;
					}
					break;
				case Text:
					if (!((TextSegment)s).getText().isEmpty()) {
						return true;
					}
					break;
				case NewLine:
				case Leader:
				case Reference:
				default:
					return true;
			}
		}
		return rdp.getUnderlineStyle()!=null;
	}

	private void initFields() {
		segmentIndex = 0;
		currentRow = null;
		leaderManager.discardAllLeaders();
		layoutOrApplyAfterLeader = null;
		currentLeaderMode = null;
		seenSegmentAfterLeader = false;
		item = rdp.getListItem();
		minLeft = flowWidth;
		minRight = flowWidth;
		empty = true;
		cr = null;
		closed = false;
		// produce group markers and anchors
		getNext(false);
	}

	boolean couldTriggerNewRow() {
		if (!hasSegments()) {
			//There's a lot of conditions to keep track of here, but hopefully we can simplify later on
			return !closed && (currentRow!=null || !empty && rdp.getUnderlineStyle()!=null || leaderManager.hasLeader());
		}
		Segment s = segments.get(segmentIndex);
		switch (s.getSegmentType()) {
			case Marker:
			case Anchor:
				return false;
			case Evaluate:
				return !((Evaluate)s).getExpression().render(context).isEmpty();
			case Text:
				return !((TextSegment)s).getText().isEmpty();
			default:
				return true;
		}
	}

	boolean hasMoreData() {
		return hasSegments() || !closed || cr!=null && cr.hasNext();
	}
	
	private boolean hasSegments() {
		return segmentIndex<segments.size();
	}

	void prepareNext() {
		if (!hasMoreData()) {
			throw new IllegalStateException();
		}
		if (cr == null) {
			if (!hasSegments() && !closed) {
				closed = true;
				cr = new CloseResult(layoutLeader());
			} else {
				cr = loadNextSegment().orElse(null);
			}
		}
	}
	
	boolean hasNext() {
		return cr!=null && cr.hasNext();
	}
	
	public boolean hasSignificantContent() {
		return significantContent;
	}
	
	Optional<RowImpl> getNext() {
		return getNext(true);
	}

	private Optional<RowImpl> getNext(boolean produceRow) {
		while (true) {
			if (cr!=null && cr.hasNext()) {
				try {
					Optional<RowImpl> ret = cr.process();
					if (ret.isPresent()) {
						if (!produceRow) {
							// there is a test below that verifies that the current segment cannot produce a row result
							// and the segment was processed under this assumption. If a row has been produced anyway, that's an error
							// in the code.
							throw new RuntimeException("Error in code");
						}
						return ret;
					} // else try the next segment.
				} finally {
					if (!cr.hasNext()) {
						cr = null;
					}
				}
			} else if (hasMoreData()) {
				if (!produceRow && couldTriggerNewRow()) {
					return Optional.empty();
				}
				prepareNext();
			} else {
				return Optional.empty();
			}
		}
	}
	
	private Optional<CurrentResult> loadNextSegment() {
		Segment s = segments.get(segmentIndex);
		segmentIndex++;
		switch (s.getSegmentType()) {
			case NewLine:
				//flush
				return Optional.of(new NewLineResult(layoutLeader()));
			case Text:
				return layoutTextSegment((TextSegment)s);
			case Leader:
				return layoutLeaderSegment((LeaderSegment)s);
			case Reference:
				return layoutPageSegment((PageNumberReferenceSegment)s);
			case Evaluate:
				return layoutEvaluate((Evaluate)s);
			case Marker:
				applyAfterLeader((MarkerSegment)s);
				return Optional.empty();
			case Anchor:
				applyAfterLeader((AnchorSegment)s);
				return Optional.empty();
			default:
				return Optional.empty();
		}
	}
	
	private class CloseResult implements CurrentResult {
		private Optional<CurrentResult> cr;
		private boolean doFlush;
		private boolean doUnderline;
		
		private CloseResult(Optional<CurrentResult> cr) {
			this.cr = cr;
			this.doFlush = true;
			this.doUnderline = rdp.getUnderlineStyle()!=null;
		}
		
		private CloseResult(CloseResult template) {
			if (template.cr.isPresent()) {
				this.cr = Optional.of(template.cr.get().copy());
			}
			this.doFlush = template.doFlush;
			this.doUnderline = template.doUnderline;
		}

		@Override
		public boolean hasNext() {
			return cr.isPresent() && cr.get().hasNext() || doFlush || (!empty && doUnderline);
		}

		@Override
		public Optional<RowImpl> process() {
			if (cr.isPresent() && cr.get().hasNext()) {
				return cr.get().process();
			} else if (doFlush) {
				doFlush = false;
				if (currentRow!=null) {
					return Optional.of(flushCurrentRow());
				}
			} else if (!empty && doUnderline) {
				doUnderline = false;
				if (minLeft < margins.getLeftMargin().getContent().length() || minRight < margins.getRightMargin().getContent().length()) {
					throw new RuntimeException("coding error");
				}
				return Optional.of(new RowImpl.Builder(StringTools.fill(fcontext.getSpaceCharacter(), minLeft - margins.getLeftMargin().getContent().length())
							+ StringTools.fill(rdp.getUnderlineStyle(), flowWidth - minLeft - minRight))
							.leftMargin(margins.getLeftMargin())
							.rightMargin(margins.getRightMargin())
							.adjustedForMargin(true)
							.build());
			}
			return Optional.empty();
		}

		@Override
		public CurrentResult copy() {
			return new CloseResult(this);
		}
	}

	private RowImpl flushCurrentRow() {
		if (empty) {
			// Clear group anchors and markers (since we have content, we don't need them)
			currentRow.addAnchors(0, groupAnchors);
			groupAnchors.clear();
			currentRow.addMarkers(0, groupMarkers);
			groupMarkers.clear();
		}
		RowImpl r = currentRow.build();
		empty = false;
		//Make calculations for underlining
		int width = r.getChars().length();
		int left = r.getLeftMargin().getContent().length();
		int right = r.getRightMargin().getContent().length();
		int space = flowWidth - width - left - right;
		left += r.getAlignment().getOffset(space);
		right = flowWidth - width - left;
		minLeft = Math.min(minLeft, left);
		minRight = Math.min(minRight, right);
		currentRow = null;
		return r;
	}
	
	private class NewLineResult implements CurrentResult {
		private boolean newLine;
		private Optional<CurrentResult> cr;

		private NewLineResult(Optional<CurrentResult> cr) {
			this.cr = cr;
			this.newLine = true;
		}
		
		private NewLineResult(NewLineResult template) {
			if (template.cr.isPresent()) {
				this.cr = Optional.of(template.cr.get().copy());
			}
			this.newLine = template.newLine;
		}

		@Override
		public boolean hasNext() {
			return cr.isPresent() && cr.get().hasNext() || newLine;
		}

		@Override
		public Optional<RowImpl> process() {
			if (cr.isPresent() && cr.get().hasNext()) {
				return cr.get().process();
			} else if (newLine) {
				newLine = false;
				try {
					if (currentRow!=null) {
						return Optional.of(flushCurrentRow());
					}
				} finally {
					MarginProperties ret = new MarginProperties(margins.getLeftMargin().getContent()+StringTools.fill(fcontext.getSpaceCharacter(), rdp.getTextIndent()), margins.getLeftMargin().isSpaceOnly());
					currentRow = rdp.configureNewEmptyRowBuilder(ret, margins.getRightMargin());
				}
			}
			return Optional.empty();
		}

		@Override
		public CurrentResult copy() {
			return new NewLineResult(this);
		}		
	}
	
	private Optional<CurrentResult> layoutTextSegment(TextSegment ts) {
		Translatable spec = Translatable.text(
						fcontext.getConfiguration().isMarkingCapitalLetters()?
						ts.getText():ts.getText().toLowerCase()
				)
				.locale(ts.getTextProperties().getLocale())
				.hyphenate(ts.getTextProperties().isHyphenating())
				.attributes(ts.getTextAttribute()).build();
		String mode = ts.getTextProperties().getTranslationMode();
		if (leaderManager.hasLeader()) {
			layoutAfterLeader(spec, mode);
		} else {
			BrailleTranslatorResult btr = toResult(spec, mode);
			CurrentResult cr = new CurrentResultImpl(btr, mode);
			return Optional.of(cr);
		}
		return Optional.empty();
	}
	
	private Optional<CurrentResult> layoutLeaderSegment(LeaderSegment ls) {
		try {
			if (leaderManager.hasLeader()) {
				return layoutLeader();
			}
			return Optional.empty();
		} finally {
			leaderManager.addLeader(ls);
		}
	}

	private Optional<CurrentResult> layoutPageSegment(PageNumberReferenceSegment rs) {
		Integer page = null;
		if (refs!=null) {
			page = refs.getPageNumber(rs.getRefId());
		}
		//TODO: translate references using custom language?
		Translatable spec;
		if (page==null) {
			spec = Translatable.text("??").locale(null).build();
		} else {
			String txt = "" + rs.getNumeralStyle().format(page);
			spec = Translatable.text(
					fcontext.getConfiguration().isMarkingCapitalLetters()?txt:txt.toLowerCase()
					).locale(null).attributes(rs.getTextAttribute(txt.length())).build();
		}
		if (leaderManager.hasLeader()) {
			layoutAfterLeader(spec, null);
		} else {
			String mode = null;
			BrailleTranslatorResult btr = toResult(spec, null);
			CurrentResult cr = new CurrentResultImpl(btr, mode);
			return Optional.of(cr);
		}
		return Optional.empty();
	}
	
	private Optional<CurrentResult> layoutEvaluate(Evaluate e) {
		String txt = e.getExpression().render(context);
		if (!txt.isEmpty()) { // Don't create a new row if the evaluated expression is empty
		                    // Note: this could be handled more generally (also for regular text) in layout().
			Translatable spec = Translatable.text(fcontext.getConfiguration().isMarkingCapitalLetters()?txt:txt.toLowerCase()).
					locale(e.getTextProperties().getLocale()).
					hyphenate(e.getTextProperties().isHyphenating()).
					attributes(e.getTextAttribute(txt.length())).
					build();
			if (leaderManager.hasLeader()) {
				layoutAfterLeader(spec, null);
			} else {
				String mode = null;
				BrailleTranslatorResult btr = toResult(spec, mode);
				CurrentResult cr = new CurrentResultImpl(btr, mode);
				return Optional.of(cr);
			}
		}
		return Optional.empty(); 
	}

	private void layoutAfterLeader(Translatable spec, String mode) {
		if (leaderManager.hasLeader()) {
			if (layoutOrApplyAfterLeader == null) {
				layoutOrApplyAfterLeader = new AggregatedBrailleTranslatorResult.Builder();
				// use the mode of the first following segment to translate the leader pattern (or
				// the mode of the first preceding segment)
				if (!seenSegmentAfterLeader) {
					currentLeaderMode = mode;
					seenSegmentAfterLeader = true;
				}
			}
			try {
				layoutOrApplyAfterLeader.add(fcontext.getTranslator(mode).translate(spec));
			} catch (TranslationException e) {
				throw new RuntimeException(e);
			}
		} else {
			throw new RuntimeException("Error in code.");
		}
	}
	
	private void applyAfterLeader(MarkerSegment marker) {
		if (leaderManager.hasLeader()) {
			if (layoutOrApplyAfterLeader == null) {
				layoutOrApplyAfterLeader = new AggregatedBrailleTranslatorResult.Builder();
			}
			layoutOrApplyAfterLeader.add(marker);
		} else {
			if (currentRow==null) {
				groupMarkers.add(marker);
			} else {
				currentRow.addMarker(marker);
			}
		}
	}
	
	private void applyAfterLeader(final AnchorSegment anchor) {
		if (leaderManager.hasLeader()) {
			if (layoutOrApplyAfterLeader == null) {
				layoutOrApplyAfterLeader = new AggregatedBrailleTranslatorResult.Builder();
			}
			layoutOrApplyAfterLeader.add(anchor);
		} else {
			if (currentRow==null) {
				groupAnchors.add(anchor.getReferenceID());
			} else {
				currentRow.addAnchor(anchor.getReferenceID());
			}
		}
	}
	
	private Optional<CurrentResult> layoutLeader() {
		if (leaderManager.hasLeader()) {
			// layout() sets currentLeader to null
			BrailleTranslatorResult btr;
			String mode;
			if (layoutOrApplyAfterLeader == null) {
				btr = toResult("");
				mode = null;
			} else {
				btr = layoutOrApplyAfterLeader.build();
				mode = currentLeaderMode;
				
				layoutOrApplyAfterLeader = null;
				seenSegmentAfterLeader = false;
			}
			CurrentResult cr = new CurrentResultImpl(btr, mode);
			return Optional.of(cr);
		}
		return Optional.empty();
	}

	private BrailleTranslatorResult toResult(String c) {
		return toResult(Translatable.text(fcontext.getConfiguration().isMarkingCapitalLetters()?c:c.toLowerCase()).build(), null);
	}
	
	private BrailleTranslatorResult toResult(Translatable spec, String mode) {
		try {
			return fcontext.getTranslator(mode).translate(spec);
		} catch (TranslationException e) {
			throw new RuntimeException(e);
		}		
	}
	
	private interface CurrentResult {
		boolean hasNext();
		Optional<RowImpl> process();
		CurrentResult copy();
	}
	
	private class CurrentResultImpl implements CurrentResult {
		private final BrailleTranslatorResult btr;
		private final String mode;
		private boolean first;
		
		CurrentResultImpl(BrailleTranslatorResult btr, String mode) {
			this.btr = btr;
			this.mode = mode;
			this.first = true;
		}
		
		CurrentResultImpl(CurrentResultImpl template) {
			this.btr = template.btr.copy();
			this.mode = template.mode;
			this.first = template.first;
		}
		
		public CurrentResult copy() {
			return new CurrentResultImpl(this);
		}

		public boolean hasNext() {
			return first || btr.hasNext();
		}

		public Optional<RowImpl> process() {
			if (first) {
				first = false;
				return processFirst();
			}
			try {
				if (btr.hasNext()) { //LayoutTools.length(chars.toString())>0
					if (currentRow!=null) {
						return Optional.of(flushCurrentRow());
					}
					return startNewRow(btr, "", rdp.getTextIndent(), rdp.getBlockIndent(), mode);
				}
			} finally {
				if (!btr.hasNext() && btr.supportsMetric(BrailleTranslatorResult.METRIC_FORCED_BREAK)) {
					forceCount += btr.getMetric(BrailleTranslatorResult.METRIC_FORCED_BREAK);
				}
			}
			return Optional.empty();
		}
	
		private Optional<RowImpl> processFirst() {
			// process first row, is it a new block or should we continue the current row?
			if (currentRow==null) {
				// add to left margin
				if (item!=null) { //currentListType!=BlockProperties.ListType.NONE) {
					String listLabel;
					try {
						listLabel = fcontext.getTranslator(mode).translate(Translatable.text(fcontext.getConfiguration().isMarkingCapitalLetters()?item.getLabel():item.getLabel().toLowerCase()).build()).getTranslatedRemainder();
					} catch (TranslationException e) {
						throw new RuntimeException(e);
					}
					try {
						if (item.getType()==FormattingTypes.ListStyle.PL) {
							return startNewRow(btr, listLabel, 0, rdp.getBlockIndentParent(), mode);
						} else {
							return startNewRow(btr, listLabel, rdp.getFirstLineIndent(), rdp.getBlockIndent(), mode);
						}
					} finally {
						item = null;
					}
				} else {
					return startNewRow(btr, "", rdp.getFirstLineIndent(), rdp.getBlockIndent(), mode);
				}
			} else {
				return continueRow(new RowInfo("", available), btr, rdp.getBlockIndent(), mode);
			}
		}
		
		private Optional<RowImpl> startNewRow(BrailleTranslatorResult chars, String contentBefore, int indent, int blockIndent, String mode) {
			if (currentRow!=null) {
				throw new RuntimeException("Error in code.");
			}
			currentRow = rdp.configureNewEmptyRowBuilder(margins.getLeftMargin(), margins.getRightMargin());
			return continueRow(new RowInfo(getPreText(contentBefore, indent+blockIndent), available), chars, blockIndent, mode);
		}
		
		private String getPreText(String contentBefore, int totalIndent) {
			int thisIndent = Math.max(
					// There is one known cause for this calculation to become < 0. That is when an ordered list is so long
					// that the number takes up more space than the indent reserved for it.
					// In that case it is probably best to push the content instead of failing altogether.
					totalIndent - StringTools.length(contentBefore),
					0);
			return contentBefore + StringTools.fill(fcontext.getSpaceCharacter(), thisIndent);
		}
	
		//TODO: check leader functionality
		private Optional<RowImpl> continueRow(RowInfo m1, BrailleTranslatorResult btr, int blockIndent, String mode) {
			RowImpl ret = null;
			// [margin][preContent][preTabText][tab][postTabText] 
			//      preContentPos ^
			String tabSpace = "";
			if (leaderManager.hasLeader()) {
				int preTabPos = m1.getPreTabPosition(currentRow);
				int leaderPos = leaderManager.getLeaderPosition(available);
				int offset = leaderPos-preTabPos;
				int align = leaderManager.getLeaderAlign(btr.countRemaining());
				
				if (preTabPos>leaderPos || offset - align < 0) { // if tab position has been passed or if text does not fit within row, try on a new row
					MarginProperties _leftMargin = currentRow.getLeftMargin();
					if (currentRow!=null) {
						ret = flushCurrentRow();
					}
					currentRow = rdp.configureNewEmptyRowBuilder(_leftMargin, margins.getRightMargin());
					m1 = new RowInfo(getPreText("", rdp.getTextIndent()+blockIndent), available);
					//update offset
					offset = leaderPos-m1.getPreTabPosition(currentRow);
				}
				try {
					tabSpace = leaderManager.getLeaderPattern(fcontext.getTranslator(mode), offset - align);
				} finally {
					// always discard leader
					leaderManager.removeLeader();
				}
			}
			breakNextRow(m1, currentRow, btr, tabSpace);
			return Optional.ofNullable(ret);
		}
	
		private void breakNextRow(RowInfo m1, RowImpl.Builder row, BrailleTranslatorResult btr, String tabSpace) {
			int contentLen = StringTools.length(tabSpace) + StringTools.length(row.getText());
			boolean force = contentLen == 0;
			//don't know if soft hyphens need to be replaced, but we'll keep it for now
			String next = softHyphenPattern.matcher(btr.nextTranslatedRow(m1.getMaxLength(row) - contentLen, force)).replaceAll("");
			if ("".equals(next) && "".equals(tabSpace)) {
				row.text(m1.getPreContent() + trailingWsBraillePattern.matcher(row.getText()).replaceAll(""));
			} else {
				row.text(m1.getPreContent() + row.getText() + tabSpace + next);
				row.leaderSpace(row.getLeaderSpace()+tabSpace.length());
			}
			if (btr instanceof AggregatedBrailleTranslatorResult) {
				AggregatedBrailleTranslatorResult abtr = ((AggregatedBrailleTranslatorResult)btr);
				row.addMarkers(abtr.getMarkers());
				row.addAnchors(abtr.getAnchors());
				abtr.clearPending();
			}
		}
	}
	
	void reset() {
		groupAnchors.clear();
		groupMarkers.clear();
		initFields();
	}
	
	List<Marker> getGroupMarkers() {
		return groupMarkers;
	}
	
	List<String> getGroupAnchors() {
		return groupAnchors;
	}
	
	void setContext(DefaultContext context) {
		this.context = context;
	}
	
	int getForceCount() {
		return forceCount;
	}
}