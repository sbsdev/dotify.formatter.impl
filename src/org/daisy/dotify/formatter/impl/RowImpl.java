package org.daisy.dotify.formatter.impl;

import java.util.ArrayList;
import java.util.List;

import org.daisy.dotify.api.formatter.FormattingTypes.Alignment;
import org.daisy.dotify.api.formatter.Marker;
import org.daisy.dotify.api.writer.Row;



/**
 * Row represents a single row of text
 * @author Joel Håkansson
 */
class RowImpl implements Row {
	private String chars;
	private List<Marker> markers;
	private List<String> anchors;
	private final MarginProperties leftMargin;
	private final MarginProperties rightMargin;
	private final Alignment alignment;
	private final Float rowSpacing;
	private final boolean adjustedForMargin;
	private final boolean allowsBreakAfter;
	private int leaderSpace;
	
	static class Builder {
		private final String chars;
		private List<Marker> markers = new ArrayList<>();
		private List<String> anchors = new ArrayList<>();
		private MarginProperties leftMargin = MarginProperties.EMPTY_MARGIN;
		private MarginProperties rightMargin = MarginProperties.EMPTY_MARGIN;
		private Alignment alignment = Alignment.LEFT;
		private Float rowSpacing = null;
		private boolean adjustedForMargin = false;
		private boolean allowsBreakAfter = true;
		private int leaderSpace = 0;

		Builder(String chars) {
			this.chars = chars;
		}

		Builder(RowImpl template) {
			this.chars = template.chars;
			this.markers = new ArrayList<>(template.markers);
			this.anchors = new ArrayList<>(template.anchors);
			this.leftMargin = template.leftMargin;
			this.rightMargin = template.rightMargin;
			this.alignment = template.alignment;
			this.rowSpacing = template.rowSpacing;
			this.adjustedForMargin = template.adjustedForMargin;
			this.allowsBreakAfter = template.allowsBreakAfter;
			this.leaderSpace = template.leaderSpace;
		}

		Builder leftMargin(MarginProperties value) {
			this.leftMargin = value;
			return this;
		}
		Builder rightMargin(MarginProperties value) {
			this.rightMargin = value;
			return this;
		}
		Builder alignment(Alignment value) {
			this.alignment = value;
			return this;
		}
		Builder rowSpacing(Float value) {
			this.rowSpacing = value;
			return this;
		}
		Builder adjustedForMargin(boolean value) {
			this.adjustedForMargin = value;
			return this;
		}
		Builder allowsBreakAfter(boolean value) {
			this.allowsBreakAfter = value;
			return this;
		}

		Builder addAnchors(List<String> refs) {
			anchors.addAll(refs);
			return this;
		}

		/**
		 * Add a collection of markers to the Row
		 * @param list the list of markers
		 */
		Builder addMarkers(List<Marker> list) {
			markers.addAll(list);
			return this;
		}

		RowImpl build() {
			return new RowImpl(this);
		}
	}
	
	private RowImpl(Builder builder) {
		this.chars = builder.chars;
		this.markers = builder.markers;
		this.anchors = builder.anchors;
		this.leftMargin = builder.leftMargin;
		this.rightMargin = builder.rightMargin;
		this.alignment = builder.alignment;
		this.rowSpacing = builder.rowSpacing;
		this.adjustedForMargin = builder.adjustedForMargin;
		this.allowsBreakAfter = builder.allowsBreakAfter;
		this.leaderSpace = builder.leaderSpace;
	}
	
	/**
	 * Create a new Row
	 * @param chars the characters on this row
	 */
	public RowImpl(String chars) {
		this(chars, new MarginProperties(), new MarginProperties());
	}
	public RowImpl(String chars, MarginProperties leftMargin, MarginProperties rightMargin) {
		this.chars = chars;
		this.markers = new ArrayList<>();
		this.anchors = new ArrayList<>();
		this.leftMargin = leftMargin;
		this.rightMargin = rightMargin;
		this.alignment = Alignment.LEFT;
		this.rowSpacing = null;
		this.adjustedForMargin = false;
		this.allowsBreakAfter = true;
		this.leaderSpace = 0;
	}
	
	/**
	 * Creates a deep copy of the supplied instance
	 * @param template the instance to copy
	 */
	RowImpl(RowImpl template) {
		this.chars = template.chars;
		this.markers = new ArrayList<>(template.markers);
		this.anchors = new ArrayList<>(template.anchors);
		this.leftMargin = template.leftMargin;
		this.rightMargin = template.rightMargin;
		this.alignment = template.alignment;
		this.rowSpacing = template.rowSpacing;
		this.adjustedForMargin = template.adjustedForMargin;
		this.allowsBreakAfter = template.allowsBreakAfter;
		this.leaderSpace = template.leaderSpace;
	}

	/**
	 * Create a new empty Row
	 */
	public RowImpl() {
		this("");
	}

	/**
	 * Get the characters on this row
	 * @return returns the characters on the row
	 */
	@Override
	public String getChars() {
		return chars;
	}

	public void setChars(String chars) {
		this.chars = chars;
	}
	
	public void setLeaderSpace(int value) {
		this.leaderSpace = value;
	}
	
	public int getLeaderSpace() {
		return leaderSpace;
	}

	public int getWidth() {
		return chars.length()+leftMargin.getContent().length()+rightMargin.getContent().length();
	}
	
	/**
	 * Add a marker to the Row
	 * @param marker
	 */
	public void addMarker(Marker marker) {
		markers.add(marker);
	}

	/**
	 * Add an anchor to the Row
	 * @param ref
	 */
	public void addAnchor(String ref) {
		anchors.add(ref);
	}
	public void addAnchors(int index, List<String> refs) {
		anchors.addAll(index, refs);
	}
	
	/**
	 * Add a collection of markers to the Row
	 * @param index the position in the marker list to insert the markers
	 * @param list the list of markers
     * @throws IndexOutOfBoundsException if the index is out of range
     *         (<tt>index &lt; 0 || index &gt; getMarkers().size()</tt>)
	 */
	public void addMarkers(int index, List<Marker> list) {
		markers.addAll(index, list);
	}

	/**
	 * Get all markers on this Row
	 * @return returns the markers
	 */
	public List<Marker> getMarkers() {
		return markers;
	}
	
	public boolean hasMarkerWithName(String name) {
		for (Marker m : markers) {
			if (m.getName().equals(name)) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Get all anchors on this Row
	 * @return returns an ArrayList of anchors
	 */
	public List<String> getAnchors() {
		return anchors;
	}

	/**
	 * Get the left margin value for the Row, in characters
	 * @return returns the left margin
	 */
	public MarginProperties getLeftMargin() {
		return leftMargin;
	}

	public MarginProperties getRightMargin() {
		return rightMargin;
	}

	/**
	 * Gets the alignment value for the row
	 * @return returns the alignment
	 */
	public Alignment getAlignment() {
		return alignment;
	}

	@Override
	public Float getRowSpacing() {
		return rowSpacing;
	}

	boolean shouldAdjustForMargin() {
		return adjustedForMargin;
	}

	boolean allowsBreakAfter() {
		return allowsBreakAfter;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (adjustedForMargin ? 1231 : 1237);
		result = prime * result + ((alignment == null) ? 0 : alignment.hashCode());
		result = prime * result + (allowsBreakAfter ? 1231 : 1237);
		result = prime * result + ((anchors == null) ? 0 : anchors.hashCode());
		result = prime * result + ((chars == null) ? 0 : chars.hashCode());
		result = prime * result + leaderSpace;
		result = prime * result + ((leftMargin == null) ? 0 : leftMargin.hashCode());
		result = prime * result + ((markers == null) ? 0 : markers.hashCode());
		result = prime * result + ((rightMargin == null) ? 0 : rightMargin.hashCode());
		result = prime * result + ((rowSpacing == null) ? 0 : rowSpacing.hashCode());
		return result;
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		RowImpl other = (RowImpl) obj;
		if (adjustedForMargin != other.adjustedForMargin) {
			return false;
		}
		if (alignment != other.alignment) {
			return false;
		}
		if (allowsBreakAfter != other.allowsBreakAfter) {
			return false;
		}
		if (anchors == null) {
			if (other.anchors != null) {
				return false;
			}
		} else if (!anchors.equals(other.anchors)) {
			return false;
		}
		if (chars == null) {
			if (other.chars != null) {
				return false;
			}
		} else if (!chars.equals(other.chars)) {
			return false;
		}
		if (leaderSpace != other.leaderSpace) {
			return false;
		}
		if (leftMargin == null) {
			if (other.leftMargin != null) {
				return false;
			}
		} else if (!leftMargin.equals(other.leftMargin)) {
			return false;
		}
		if (markers == null) {
			if (other.markers != null) {
				return false;
			}
		} else if (!markers.equals(other.markers)) {
			return false;
		}
		if (rightMargin == null) {
			if (other.rightMargin != null) {
				return false;
			}
		} else if (!rightMargin.equals(other.rightMargin)) {
			return false;
		}
		if (rowSpacing == null) {
			if (other.rowSpacing != null) {
				return false;
			}
		} else if (!rowSpacing.equals(other.rowSpacing)) {
			return false;
		}
		return true;
	}

}