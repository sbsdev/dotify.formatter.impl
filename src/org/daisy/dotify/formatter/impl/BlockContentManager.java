package org.daisy.dotify.formatter.impl;

import static java.lang.Math.min;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.daisy.dotify.api.formatter.Context;
import org.daisy.dotify.api.formatter.FormattingTypes;
import org.daisy.dotify.api.formatter.Leader;
import org.daisy.dotify.api.formatter.Marker;
import org.daisy.dotify.api.translator.BrailleTranslatorResult;
import org.daisy.dotify.api.translator.Translatable;
import org.daisy.dotify.api.translator.TranslationException;
import org.daisy.dotify.api.translator.UnsupportedMetricException;
import org.daisy.dotify.common.text.StringTools;
import org.daisy.dotify.formatter.impl.search.CrossReferenceHandler;
import org.daisy.dotify.formatter.impl.segment.AnchorSegment;
import org.daisy.dotify.formatter.impl.segment.Evaluate;
import org.daisy.dotify.formatter.impl.segment.PageNumberReferenceSegment;
import org.daisy.dotify.formatter.impl.segment.Segment;
import org.daisy.dotify.formatter.impl.segment.TextSegment;

/**
 * BlockHandler is responsible for breaking blocks of text into rows. BlockProperties
 * such as list numbers, leaders and margins are resolved in the process. The input
 * text is filtered using the supplied StringFilter before breaking into rows, since
 * the length of the text could change.
 * 
 * @author Joel Håkansson
 */
class BlockContentManager extends AbstractBlockContentManager {
	private static final Pattern softHyphenPattern  = Pattern.compile("\u00ad");
	private static final Pattern trailingWsBraillePattern = Pattern.compile("[\\s\u2800]+\\z");

	private final Stack<RowImpl> rows;
	private final CrossReferenceHandler refs;
	private final int available;
	private final Context context;
	private final Stack<Segment> segments;

	private boolean hasCalculated;
	private RowImpl.Builder currentRow;
	private Leader currentLeader;
	private ListItem item;
	private int forceCount;
	private int minLeft;
	private int minRight;

	// List of BrailleTranslatorResult or Marker or AnchorSegment
	private List<Object> layoutOrApplyAfterLeader = null;
	private String currentLeaderMode = null;
	private boolean seenSegmentAfterLeader = false;
	
	BlockContentManager(int flowWidth, Stack<Segment> segments, RowDataProperties rdp, CrossReferenceHandler refs, Context context, FormatterContext fcontext) {
		super(flowWidth, rdp, fcontext);
		this.refs = refs;
		this.currentLeader = null;
		this.available = flowWidth - rightMargin.getContent().length();

		this.item = rdp.getListItem();

		this.context = context;
		this.hasCalculated = false;
		this.segments = segments;
		this.rows = new Stack<>();
		this.minLeft = flowWidth;
		this.minRight = flowWidth;
	}
	
	private void ensureBuffer() {
		isVolatile = false;
		
		for (Segment s : segments) {
			switch (s.getSegmentType()) {
				case NewLine:
				{
					//flush
					layoutLeader();
					flushCurrentRow();
					MarginProperties ret = new MarginProperties(leftMargin.getContent()+StringTools.fill(fcontext.getSpaceCharacter(), rdp.getTextIndent()), leftMargin.isSpaceOnly());
					currentRow = createAndConfigureEmptyNewRowBuilder(ret);
					break;
				}
				case Text:
				{
					TextSegment ts = (TextSegment)s;
					layoutAfterLeader(
							Translatable.text(
									fcontext.getConfiguration().isMarkingCapitalLetters()?
									ts.getText():ts.getText().toLowerCase()
									).
							locale(ts.getTextProperties().getLocale()).
							hyphenate(ts.getTextProperties().isHyphenating()).
							attributes(ts.getTextAttribute()).build(),
							ts.getTextProperties().getTranslationMode());
					break;
				}
				case Leader:
				{
					if (currentLeader!=null) {
						layoutLeader();
					}
					currentLeader= (Leader)s;
					break;
				}
				case Reference:
				{
					isVolatile = true;
					PageNumberReferenceSegment rs = (PageNumberReferenceSegment)s;
					Integer page = null;
					if (refs!=null) {
						page = refs.getPageNumber(rs.getRefId());
					}
					//TODO: translate references using custom language?
					if (page==null) {
						layoutAfterLeader(Translatable.text("??").locale(null).build(), null);
					} else {
						String txt = "" + rs.getNumeralStyle().format(page);
						layoutAfterLeader(Translatable.text(
								fcontext.getConfiguration().isMarkingCapitalLetters()?txt:txt.toLowerCase()
								).locale(null).attributes(rs.getTextAttribute(txt.length())).build(), null);
					}
					break;
				}
				case Evaluate:
				{
					isVolatile = true;
					Evaluate e = (Evaluate)s;
					String txt = e.getExpression().render(context);
					if (!txt.isEmpty()) // Don't create a new row if the evaluated expression is empty
					                    // Note: this could be handled more generally (also for regular text) in layout().
						layoutAfterLeader(
								Translatable.text(fcontext.getConfiguration().isMarkingCapitalLetters()?txt:txt.toLowerCase()).
								locale(e.getTextProperties().getLocale()).
								hyphenate(e.getTextProperties().isHyphenating()).
								attributes(e.getTextAttribute(txt.length())).
								build(), 
								null);
					break;
				}
				case Marker:
				{
					Marker m = (Marker)s;
					applyAfterLeader(m);
					break;
				}
				case Anchor:
				{
					AnchorSegment as = (AnchorSegment)s;
					applyAfterLeader(as);
					break;
				}
			}
		}
		if (currentLeader!=null || item!=null) {
			layoutLeader();
		}
		flushCurrentRow();
		if (rows.size()>0 && rdp.getUnderlineStyle() != null) {
			if (minLeft < leftMargin.getContent().length() || minRight < rightMargin.getContent().length()) {
				throw new RuntimeException("coding error");
			}
			rows.add(new RowImpl.Builder(StringTools.fill(fcontext.getSpaceCharacter(), minLeft - leftMargin.getContent().length())
			                     + StringTools.fill(rdp.getUnderlineStyle(), flowWidth - minLeft - minRight))
						.leftMargin(leftMargin)
						.rightMargin(rightMargin)
						.adjustedForMargin(true)
						.build());
		}
	}

	private void flushCurrentRow() {
		if (currentRow!=null) {
			if (rows.isEmpty()) {
				// Clear group anchors and markers (since we have content, we don't need them)
				currentRow.addAnchors(0, groupAnchors);
				groupAnchors.clear();
				currentRow.addMarkers(0, groupMarkers);
				groupMarkers.clear();
			}
			RowImpl r = currentRow.build();
			rows.add(r);
			//Make calculations for underlining
			int width = r.getChars().length();
			int left = r.getLeftMargin().getContent().length();
			int right = r.getRightMargin().getContent().length();
			int space = flowWidth - width - left - right;
			left += r.getAlignment().getOffset(space);
			right = flowWidth - width - left;
			minLeft = min(minLeft, left);
			minRight = min(minRight, right);
			currentRow = null;
		}
	}
	
	private void layoutAfterLeader(Translatable spec, String mode) {
		if (currentLeader!=null) {
			if (layoutOrApplyAfterLeader == null) {
				layoutOrApplyAfterLeader = new ArrayList<Object>();
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
			layout(spec, mode);
		}
	}
	
	private void applyAfterLeader(final Marker marker) {
		if (currentLeader!=null) {
			if (layoutOrApplyAfterLeader == null) {
				layoutOrApplyAfterLeader = new ArrayList<Object>();
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
		if (currentLeader!=null) {
			if (layoutOrApplyAfterLeader == null) {
				layoutOrApplyAfterLeader = new ArrayList<Object>();
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
	
	private void layoutLeader() {
		if (currentLeader!=null) {
			// layout() sets currentLeader to null
			if (layoutOrApplyAfterLeader == null) {
				layout("", null, null);
			} else {
				layout(new AggregatedBrailleTranslatorResult(layoutOrApplyAfterLeader), currentLeaderMode);
				layoutOrApplyAfterLeader = null;
				seenSegmentAfterLeader = false;
			}
		}
	}

	@Override
	public int getRowCount() {
		verifyCalculated();
		return rows.size();
	}
	
    private void verifyCalculated() {
        if (!hasCalculated) {
            ensureBuffer();
            hasCalculated = true;
        }
    }
	
	@Override
	RowImpl get(int i) {
		verifyCalculated();
		return rows.get(i);
	}
	
	@Override
	boolean supportsVariableWidth() {
		return true;
	}

	private void layout(String c, String locale, String mode) {
		layout(Translatable.text(fcontext.getConfiguration().isMarkingCapitalLetters()?c:c.toLowerCase()).locale(locale).build(), mode);
	}
	
	private void layout(Translatable spec, String mode) {
		try {
			layout(fcontext.getTranslator(mode).translate(spec), mode);
		} catch (TranslationException e) {
			throw new RuntimeException(e);
		}
	}

	private void layout(BrailleTranslatorResult btr, String mode) {
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
					newRow(btr, listLabel, 0, rdp.getBlockIndentParent(), mode);
				} else {
					newRow(btr, listLabel, rdp.getFirstLineIndent(), rdp.getBlockIndent(), mode);
				}
				item = null;
			} else {
				newRow(btr, "", rdp.getFirstLineIndent(), rdp.getBlockIndent(), mode);
			}
		} else {
			newRow(new RowInfo("", currentRow), btr, rdp.getBlockIndent(), mode);
		}
		while (btr.hasNext()) { //LayoutTools.length(chars.toString())>0
			newRow(btr, "", rdp.getTextIndent(), rdp.getBlockIndent(), mode);
		}
		if (btr.supportsMetric(BrailleTranslatorResult.METRIC_FORCED_BREAK)) {
			forceCount += btr.getMetric(BrailleTranslatorResult.METRIC_FORCED_BREAK);
		}
	}
	
	private void newRow(BrailleTranslatorResult chars, String contentBefore, int indent, int blockIndent, String mode) {
		flushCurrentRow();
		currentRow = createAndConfigureEmptyNewRowBuilder(leftMargin);
		newRow(new RowInfo(getPreText(contentBefore, indent, blockIndent), currentRow), chars, blockIndent, mode);
	}
	
	private String getPreText(String contentBefore, int indent, int blockIndent) {
		int thisIndent = Math.max(
				// There is one known cause for this calculation to become < 0. That is when an ordered list is so long
				// that the number takes up more space than the indent reserved for it.
				// In that case it is probably best to push the content instead of failing altogether.
				indent + blockIndent - StringTools.length(contentBefore),
				0);
		return contentBefore + StringTools.fill(fcontext.getSpaceCharacter(), thisIndent).toString();
	}

	//TODO: check leader functionality
	private void newRow(RowInfo m, BrailleTranslatorResult btr, int blockIndent, String mode) {
		// [margin][preContent][preTabText][tab][postTabText] 
		//      preContentPos ^
		String tabSpace = "";
		if (currentLeader!=null) {
			int leaderPos = currentLeader.getPosition().makeAbsolute(available);
			int offset = leaderPos-m.preTabPos;
			int align = getLeaderAlign(currentLeader, btr.countRemaining());
			
			if (m.preTabPos>leaderPos || offset - align < 0) { // if tab position has been passed or if text does not fit within row, try on a new row
				flushCurrentRow();
				currentRow = createAndConfigureEmptyNewRowBuilder(rows.peek().getLeftMargin());
				m = new RowInfo(StringTools.fill(fcontext.getSpaceCharacter(), rdp.getTextIndent()+blockIndent), currentRow);
				//update offset
				offset = leaderPos-m.preTabPos;
			}
			tabSpace = buildLeader(offset - align, mode);
		}
		breakNextRow(m, btr, tabSpace);
	}

	private String buildLeader(int len, String mode) {
		try {
			if (len > 0) {
				String leaderPattern;
				try {
					leaderPattern = fcontext.getTranslator(mode).translate(Translatable.text(currentLeader.getPattern()).build()).getTranslatedRemainder();
				} catch (TranslationException e) {
					throw new RuntimeException(e);
				}
				return StringTools.fill(leaderPattern, len);
			} else {
				Logger.getLogger(this.getClass().getCanonicalName())
					.fine("Leader position has been passed on an empty row or text does not fit on an empty row, ignoring...");
				return "";
			}
		} finally {
			// always discard leader
			currentLeader = null;
		}
	}

	private void breakNextRow(RowInfo m, BrailleTranslatorResult btr, String tabSpace) {
		int contentLen = StringTools.length(tabSpace) + m.preTabTextLen;
		boolean force = contentLen == 0;
		//don't know if soft hyphens need to be replaced, but we'll keep it for now
		String next = softHyphenPattern.matcher(btr.nextTranslatedRow(m.maxLenText - contentLen, force)).replaceAll("");
		if ("".equals(next) && "".equals(tabSpace)) {
			m.row.text(m.preContent + trailingWsBraillePattern.matcher(m.preTabText).replaceAll(""));
		} else {
			m.row.text(m.preContent + m.preTabText + tabSpace + next);
			m.row.leaderSpace(m.row.getLeaderSpace()+tabSpace.length());
		}
		if (btr instanceof AggregatedBrailleTranslatorResult) {
			AggregatedBrailleTranslatorResult abtr = ((AggregatedBrailleTranslatorResult)btr);
			abtr.addMarkers(m.row);
			abtr.addAnchors(m.row);
		}
	}
	
	private static int getLeaderAlign(Leader leader, int length) {
		switch (leader.getAlignment()) {
			case LEFT:
				return 0;
			case RIGHT:
				return length;
			case CENTER:
				return length/2;
		}
		return 0;
	}
	
	private class RowInfo {
		final String preTabText;
		final int preTabTextLen;
		final String preContent;
		final int preTabPos;
		final int maxLenText;
		final RowImpl.Builder row;
		private RowInfo(String preContent, RowImpl.Builder r) {
			this.preTabText = r.getText();
			this.row = r;
			this.preContent = preContent;
			int preContentPos = r.getLeftMargin().getContent().length()+StringTools.length(preContent);
			this.preTabTextLen = StringTools.length(preTabText);
			this.preTabPos = preContentPos+preTabTextLen;
			this.maxLenText = available-(preContentPos);
			if (this.maxLenText<1) {
				throw new RuntimeException("Cannot continue layout: No space left for characters.");
			}
		}
	}

	@Override
	int getForceBreakCount() {
		verifyCalculated();
		return forceCount;
	}
	
	private static class AggregatedBrailleTranslatorResult implements BrailleTranslatorResult {
		
		private final List<Object> results;
		private int currentIndex = 0;
		
		public AggregatedBrailleTranslatorResult(List<Object> results) {
			this.results = results;
		}

		public String nextTranslatedRow(int limit, boolean force) {
			String row = "";
			BrailleTranslatorResult current = computeNext();
			while (limit > row.length()) {
				row += current.nextTranslatedRow(limit - row.length(), force);
				current = computeNext();
				if (current == null) {
					break;
				}
			}
			return row;
		}

		private BrailleTranslatorResult computeNext() {
			while (currentIndex < results.size()) {
				Object o = results.get(currentIndex);
				if (o instanceof BrailleTranslatorResult) {
					BrailleTranslatorResult current = ((BrailleTranslatorResult)o);
					if (current.hasNext()) {
						return current;
					}
				} else if (o instanceof Marker) {
					pendingMarkers.add((Marker)o);
				} else if (o instanceof AnchorSegment) {
					pendingAnchors.add((AnchorSegment)o);
				} else {
					throw new RuntimeException("coding error");
				}
				currentIndex++;
			}
			return null;
		}

		public String getTranslatedRemainder() {
			String remainder = "";
			for (int i = currentIndex; i < results.size(); i++) {
				Object o = results.get(i);
				if (o instanceof BrailleTranslatorResult) {
					remainder += ((BrailleTranslatorResult)o).getTranslatedRemainder();
				}
			}
			return remainder;
		}

		public int countRemaining() {
			int remaining = 0;
			for (int i = currentIndex; i < results.size(); i++) {
				Object o = results.get(i);
				if (o instanceof BrailleTranslatorResult) {
					remaining += ((BrailleTranslatorResult)o).countRemaining();
				}
			}
			return remaining;
		}

		public boolean hasNext() {
			return computeNext() != null;
		}
		
		List<Marker> pendingMarkers = new ArrayList<Marker>();
		private void addMarkers(RowImpl.Builder row) {
			row.addMarkers(pendingMarkers);
			pendingMarkers.clear();
		}

		List<AnchorSegment> pendingAnchors = new ArrayList<AnchorSegment>();
		private void addAnchors(RowImpl.Builder row) {
			for (AnchorSegment a : pendingAnchors) {
				row.addAnchor(a.getReferenceID());
			}
			pendingAnchors.clear();
		}

		public boolean supportsMetric(String metric) {
			// since we cannot assume that the individual results of any metric can be added, we only support the following known cases
			if (METRIC_FORCED_BREAK.equals(metric) || METRIC_HYPHEN_COUNT.equals(metric)) {
				for (int i = 0; i <= currentIndex && i < results.size(); i++) {
					Object o = results.get(i);
					if (o instanceof BrailleTranslatorResult) {
						if (!((BrailleTranslatorResult)o).supportsMetric(metric)) {
							return false;
						}
					}
				}
				return true;
			} else {
				return false;
			}
		}

		public double getMetric(String metric) {
			// since we cannot assume that the individual results of any metric can be added, we only support the following known cases
			if (METRIC_FORCED_BREAK.equals(metric) || METRIC_HYPHEN_COUNT.equals(metric)) {
				int count = 0;
				for (int i = 0; i <= currentIndex && i < results.size(); i++) {
					Object o = results.get(i);
					if (o instanceof BrailleTranslatorResult) {
						count += ((BrailleTranslatorResult)o).getMetric(metric);
					}
				}
				return count;
			} else {
				throw new UnsupportedMetricException("Metric not supported: " + metric);
			}
		}
	}
}
