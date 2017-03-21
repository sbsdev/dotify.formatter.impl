package org.daisy.dotify.formatter.impl;

import org.daisy.dotify.api.formatter.BlockPosition;
import org.daisy.dotify.api.formatter.FormattingTypes;
import org.daisy.dotify.api.formatter.RenderingScenario;
import org.daisy.dotify.formatter.impl.segment.Segment;
import org.daisy.dotify.formatter.impl.segment.TextSegment;

/**
 * <p>Provides a block of rows and the properties associated with it.</p>
 * <p><b>Note that this class does not map directly to OBFL blocks.</b> 
 * OBFL has hierarchical blocks, which is represented by multiple
 * Block objects in sequence, a new one is created on each block boundary
 * transition.</p>
 * 
 * @author Joel Håkansson
 */

public abstract class Block implements Cloneable {
	private BlockContext context;
	private AbstractBlockContentManager rdm;
	private final String blockId;
	private FormattingTypes.BreakBefore breakBefore;
	private FormattingTypes.Keep keep;
	private int keepWithNext;
	private int keepWithPreviousSheets;
	private int keepWithNextSheets;
	private Integer avoidVolumeBreakInsidePriority;
	private Integer avoidVolumeBreakAfterPriority;
	private String id;
	protected RowDataProperties rdp;
	private BlockPosition verticalPosition;
	protected Integer metaVolume = null, metaPage = null;
	private final RenderingScenario rs;

	Block(String blockId, RowDataProperties rdp) {
		this(blockId, rdp, null);
	}
	
	Block(String blockId, RowDataProperties rdp, RenderingScenario rs) {
		this.breakBefore = FormattingTypes.BreakBefore.AUTO;
		this.keep = FormattingTypes.Keep.AUTO;
		this.keepWithNext = 0;
		this.keepWithPreviousSheets = 0;
		this.keepWithNextSheets = 0;
		this.avoidVolumeBreakInsidePriority = null;
		this.avoidVolumeBreakAfterPriority = null;
		this.id = "";
		this.blockId = blockId;
		this.rdp = rdp;
		this.verticalPosition = null;
		this.rdm = null;
		this.rs = rs;
	}
	
	abstract void addSegment(Segment s);

	abstract void addSegment(TextSegment s);
	
	abstract boolean isEmpty();
	
	protected abstract AbstractBlockContentManager newBlockContentManager(BlockContext context);

	FormattingTypes.BreakBefore getBreakBeforeType() {
		return breakBefore;
	}
	
	FormattingTypes.Keep getKeepType() {
		return keep;
	}
	
	int getKeepWithNext() {
		return keepWithNext;
	}
	
	int getKeepWithPreviousSheets() {
		return keepWithPreviousSheets;
	}
	
	int getKeepWithNextSheets() {
		return keepWithNextSheets;
	}
	
	Integer getVolumeKeepInsidePriority() {
		return avoidVolumeBreakInsidePriority;
	}
	
	Integer getVolumeKeepAfterPriority() {
		return avoidVolumeBreakAfterPriority;
	}
	
	String getIdentifier() {
		return id;
	}

	BlockPosition getVerticalPosition() {
		return verticalPosition;
	}

	void setBreakBeforeType(FormattingTypes.BreakBefore breakBefore) {
		this.breakBefore = breakBefore;
	}
	
	void setKeepType(FormattingTypes.Keep keep) {
		this.keep = keep;
	}
	
	void setKeepWithNext(int keepWithNext) {
		this.keepWithNext = keepWithNext;
	}
	
	void setKeepWithPreviousSheets(int keepWithPreviousSheets) {
		this.keepWithPreviousSheets = keepWithPreviousSheets;
	}
	
	void setKeepWithNextSheets(int keepWithNextSheets) {
		this.keepWithNextSheets = keepWithNextSheets;
	}
	
	void setVolumeKeepInsidePriority(Integer priority) {
		this.avoidVolumeBreakInsidePriority = priority;
	}
	
	void setVolumeKeepAfterPriority(Integer priority) {
		this.avoidVolumeBreakAfterPriority = priority;
	}
	
	void setIdentifier(String id) {
		this.id = id;
	}

	/**
	 * Sets the vertical position of the block on page.
	 * @param vertical the position
	 */
	void setVerticalPosition(BlockPosition vertical) {
		this.verticalPosition = vertical;
	}

	public String getBlockIdentifier() {
		return blockId;
	}
	
	AbstractBlockContentManager getBlockContentManager(BlockContext context) {
		if (!context.equals(this.context)) {
			//invalidate, if existing
			rdm = null;
		}
		this.context = context;
		if (rdm==null || rdm.isVolatile()) {
			rdm = newBlockContentManager(context);
		}
		return rdm;
	}
	
	public void setMetaVolume(Integer metaVolume) {
		this.metaVolume = metaVolume;
	}

	public void setMetaPage(Integer metaPage) {
		this.metaPage = metaPage;
	}
	
	RowDataProperties getRowDataProperties() {
		return rdp;
	}
	
	void setRowDataProperties(RowDataProperties value) {
		rdp = value;
	}

	RenderingScenario getRenderingScenario() {
		return rs;
	}
	
	Integer getAvoidVolumeBreakAfterPriority() {
		return avoidVolumeBreakAfterPriority;
	}
	
	void setAvoidVolumeBreakAfterPriority(Integer value) {
		this.avoidVolumeBreakAfterPriority = value;
	}
	
	Integer getAvoidVolumeBreakInsidePriority() {
		return avoidVolumeBreakInsidePriority;
	}
	

	@Override
	public Object clone() {
    	try {
	    	Block newObject = (Block)super.clone();
	    	/* Probably no need to deep copy clone segments
	    	if (this.segments!=null) {
	    		newObject.segments = (Stack<Segment>)this.segments.clone();
	    	}*/
	    	return newObject;
    	} catch (CloneNotSupportedException e) { 
    	    // this shouldn't happen, since we are Cloneable
    	    throw new InternalError();
    	}
    }

}
