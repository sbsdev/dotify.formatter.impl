package org.daisy.dotify.obfl;

import java.util.Map;

import org.daisy.dotify.api.formatter.SequenceProperties;

public class StaticSequenceEventImpl extends FormatterCoreEventImpl implements StaticSequenceEvent {
	private final SequenceProperties props;
	
	/**
	 * Creates a new sequence event
	 * @param props
	 */
	public StaticSequenceEventImpl(SequenceProperties props) {
		this.props = props;
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 4646831324973203983L;

	public SequenceProperties getSequenceProperties() {
		return props;
	}

	public VolumeSequenceType getVolumeSequenceType() {
		return VolumeSequenceType.STATIC;
	}

	public void setEvaluateContext(Map<String, String> vars) {
		for (BlockEvent e : this) {
			e.setEvaluateContext(vars);
		}
	}
}
