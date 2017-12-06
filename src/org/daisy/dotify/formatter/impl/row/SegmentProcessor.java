package org.daisy.dotify.formatter.impl.row;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
	}

	private void initFields() {
		segmentIndex = 0;
		currentRow = null;
		leaderManager.discardLeader();
		layoutOrApplyAfterLeader = null;
		currentLeaderMode = null;
		seenSegmentAfterLeader = false;
		item = rdp.getListItem();
		minLeft = flowWidth;
		minRight = flowWidth;
		empty = true;
	}

	boolean couldTriggerNewRow() {
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
		return segmentIndex<segments.size();
	}

	List<RowImpl> getNextRows() {
		List<RowImpl> rows = new ArrayList<>();
		while (rows.isEmpty() && hasMoreData()) {
			Segment s = segments.get(segmentIndex);
			segmentIndex++;
			switch (s.getSegmentType()) {
				case NewLine:
					//flush
					rows.addAll(layoutNewLine());
					break;
				case Text:
					rows.addAll(layoutTextSegment((TextSegment)s));
					break;
				case Leader:
					rows.addAll(
						layoutLeaderSegment((LeaderSegment)s)
					);
					break;
				case Reference:
					rows.addAll(
							layoutPageSegment((PageNumberReferenceSegment)s)
					);
					break;
				case Evaluate:
					rows.addAll(
							layoutEvaluate((Evaluate)s)
					);
					break;
				case Marker:
					applyAfterLeader((MarkerSegment)s);
					break;
				case Anchor:
					applyAfterLeader((AnchorSegment)s);
					break;
			}
		}
		return rows;
	}
	
	List<RowImpl> close() {
		List<RowImpl> rows = new ArrayList<>();
		rows.addAll(layoutLeader());
		rows.addAll(flushCurrentRow());
		if (!empty && rdp.getUnderlineStyle() != null) {
			if (minLeft < margins.getLeftMargin().getContent().length() || minRight < margins.getRightMargin().getContent().length()) {
				throw new RuntimeException("coding error");
			}
			rows.add(new RowImpl.Builder(StringTools.fill(fcontext.getSpaceCharacter(), minLeft - margins.getLeftMargin().getContent().length())
			                     + StringTools.fill(rdp.getUnderlineStyle(), flowWidth - minLeft - minRight))
						.leftMargin(margins.getLeftMargin())
						.rightMargin(margins.getRightMargin())
						.adjustedForMargin(true)
						.build());
		}
		return rows;
	}

	private List<RowImpl> flushCurrentRow() {
		List<RowImpl> rows = new ArrayList<>();
		if (currentRow!=null) {
			if (empty) {
				// Clear group anchors and markers (since we have content, we don't need them)
				currentRow.addAnchors(0, groupAnchors);
				groupAnchors.clear();
				currentRow.addMarkers(0, groupMarkers);
				groupMarkers.clear();
			}
			RowImpl r = currentRow.build();
			empty = false;
			rows.add(r);
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
		}
		return rows;
	}

	private List<RowImpl> layoutNewLine() {
		List<RowImpl> rows = new ArrayList<>();
		rows.addAll(layoutLeader());
		rows.addAll(flushCurrentRow());
		MarginProperties ret = new MarginProperties(margins.getLeftMargin().getContent()+StringTools.fill(fcontext.getSpaceCharacter(), rdp.getTextIndent()), margins.getLeftMargin().isSpaceOnly());
		currentRow = rdp.configureNewEmptyRowBuilder(ret, margins.getRightMargin());
		return rows;
	}
	
	private List<RowImpl> layoutTextSegment(TextSegment ts) {
		List<RowImpl> rows = new ArrayList<>();
		rows.addAll(
		layoutAfterLeader(
				Translatable.text(
						fcontext.getConfiguration().isMarkingCapitalLetters()?
						ts.getText():ts.getText().toLowerCase()
						).
				locale(ts.getTextProperties().getLocale()).
				hyphenate(ts.getTextProperties().isHyphenating()).
				attributes(ts.getTextAttribute()).build(),
				ts.getTextProperties().getTranslationMode())
		);
		return rows;
	}
	
	private List<RowImpl> layoutLeaderSegment(LeaderSegment ls) {
		List<RowImpl> rows = new ArrayList<>();
		if (leaderManager.hasLeader()) {
			rows.addAll(
					layoutLeader()
			);
		}
		leaderManager.setLeader(ls);
		return rows;
	}

	private List<RowImpl> layoutPageSegment(PageNumberReferenceSegment rs) {
		List<RowImpl> rows = new ArrayList<>();
		Integer page = null;
		if (refs!=null) {
			page = refs.getPageNumber(rs.getRefId());
		}
		//TODO: translate references using custom language?
		if (page==null) {
			rows.addAll(
					layoutAfterLeader(Translatable.text("??").locale(null).build(), null)
			);
		} else {
			String txt = "" + rs.getNumeralStyle().format(page);
			rows.addAll(
					layoutAfterLeader(Translatable.text(
					fcontext.getConfiguration().isMarkingCapitalLetters()?txt:txt.toLowerCase()
					).locale(null).attributes(rs.getTextAttribute(txt.length())).build(), null)
			);
		}
		return rows;
	}
	
	private List<RowImpl> layoutEvaluate(Evaluate e) {
		List<RowImpl> rows = new ArrayList<>();
		String txt = e.getExpression().render(context);
		if (!txt.isEmpty()) { // Don't create a new row if the evaluated expression is empty
		                    // Note: this could be handled more generally (also for regular text) in layout().
			rows.addAll(
			layoutAfterLeader(
					Translatable.text(fcontext.getConfiguration().isMarkingCapitalLetters()?txt:txt.toLowerCase()).
					locale(e.getTextProperties().getLocale()).
					hyphenate(e.getTextProperties().isHyphenating()).
					attributes(e.getTextAttribute(txt.length())).
					build(), 
					null)
			);
		}
		return rows; 
	}

	private List<RowImpl> layoutAfterLeader(Translatable spec, String mode) {
		List<RowImpl> rows = new ArrayList<>();
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
			rows.addAll(layout(spec, mode));
		}
		return rows;
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
	
	private List<RowImpl> layoutLeader() {
		List<RowImpl> rows = new ArrayList<>();
		if (leaderManager.hasLeader()) {
			// layout() sets currentLeader to null
			if (layoutOrApplyAfterLeader == null) {
				rows.addAll(layout("", null, null));
			} else {
				rows.addAll(
						layout(layoutOrApplyAfterLeader.build(), currentLeaderMode)
				);
				layoutOrApplyAfterLeader = null;
				seenSegmentAfterLeader = false;
			}
		}
		return rows;
	}

	private List<RowImpl> layout(String c, String locale, String mode) {
		return layout(Translatable.text(fcontext.getConfiguration().isMarkingCapitalLetters()?c:c.toLowerCase()).locale(locale).build(), mode);
	}
	
	private List<RowImpl> layout(Translatable spec, String mode) {
		try {
			return layout(fcontext.getTranslator(mode).translate(spec), mode);
		} catch (TranslationException e) {
			throw new RuntimeException(e);
		}
	}

	private List<RowImpl> layout(BrailleTranslatorResult btr, String mode) {
		List<RowImpl> rows = new ArrayList<>();
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
				if (item.getType()==FormattingTypes.ListStyle.PL) {
					rows.addAll(
							newRow(btr, listLabel, 0, rdp.getBlockIndentParent(), mode)
					);
				} else {
					rows.addAll(
					newRow(btr, listLabel, rdp.getFirstLineIndent(), rdp.getBlockIndent(), mode));
				}
				item = null;
			} else {
				rows.addAll(
				newRow(btr, "", rdp.getFirstLineIndent(), rdp.getBlockIndent(), mode));
			}
		} else {
			rows.addAll(
			continueRow(new RowInfo("", available), btr, rdp.getBlockIndent(), mode));
		}
		while (btr.hasNext()) { //LayoutTools.length(chars.toString())>0
			rows.addAll(
			newRow(btr, "", rdp.getTextIndent(), rdp.getBlockIndent(), mode));
		}
		if (btr.supportsMetric(BrailleTranslatorResult.METRIC_FORCED_BREAK)) {
			forceCount += btr.getMetric(BrailleTranslatorResult.METRIC_FORCED_BREAK);
		}
		return rows;
	}
	
	private List<RowImpl> newRow(BrailleTranslatorResult chars, String contentBefore, int indent, int blockIndent, String mode) {
		List<RowImpl> rows = new ArrayList<>();
		rows.addAll(flushCurrentRow());
		currentRow = rdp.configureNewEmptyRowBuilder(margins.getLeftMargin(), margins.getRightMargin());
		rows.addAll(
		continueRow(new RowInfo(getPreText(contentBefore, indent, blockIndent), available), chars, blockIndent, mode));
		return rows;
	}
	
	private String getPreText(String contentBefore, int indent, int blockIndent) {
		int thisIndent = Math.max(
				// There is one known cause for this calculation to become < 0. That is when an ordered list is so long
				// that the number takes up more space than the indent reserved for it.
				// In that case it is probably best to push the content instead of failing altogether.
				indent + blockIndent - StringTools.length(contentBefore),
				0);
		return contentBefore + StringTools.fill(fcontext.getSpaceCharacter(), thisIndent);
	}

	//TODO: check leader functionality
	private List<RowImpl> continueRow(RowInfo m1, BrailleTranslatorResult btr, int blockIndent, String mode) {
		List<RowImpl> rows = new ArrayList<>();
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
				rows.addAll(flushCurrentRow());
				currentRow = rdp.configureNewEmptyRowBuilder(_leftMargin, margins.getRightMargin());
				m1 = new RowInfo(StringTools.fill(fcontext.getSpaceCharacter(), rdp.getTextIndent()+blockIndent), available);
				//update offset
				offset = leaderPos-m1.getPreTabPosition(currentRow);
			}
			try {
				tabSpace = leaderManager.getLeaderPattern(fcontext.getTranslator(mode), offset - align);
			} finally {
				// always discard leader
				leaderManager.discardLeader();
			}
		}
		breakNextRow(m1, currentRow, btr, tabSpace);
		return rows;
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