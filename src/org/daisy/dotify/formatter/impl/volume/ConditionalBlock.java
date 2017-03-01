package org.daisy.dotify.formatter.impl.volume;

import org.daisy.dotify.api.formatter.Condition;
import org.daisy.dotify.api.formatter.Context;
import org.daisy.dotify.formatter.impl.FormatterCoreImpl;


class ConditionalBlock {
	private final Condition condition;
	private final FormatterCoreImpl sequence;
	
	public ConditionalBlock(FormatterCoreImpl sequence, Condition condition) {
		this.sequence = sequence;
		this.condition = condition;
	}
	
	public FormatterCoreImpl getSequence() {
		return sequence;
	}
	
	public boolean appliesTo(Context context) {
		if (condition==null) {
			return true;
		}
		return condition.evaluate(context);
	}

}