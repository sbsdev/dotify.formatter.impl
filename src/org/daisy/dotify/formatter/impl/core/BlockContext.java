package org.daisy.dotify.formatter.impl.core;

import org.daisy.dotify.formatter.impl.search.CrossReferenceHandler;
import org.daisy.dotify.formatter.impl.search.DefaultContext;
import org.daisy.dotify.formatter.impl.search.Space;

public interface BlockContext extends DefaultContext {

	public int getFlowWidth();

	public FormatterContext getFcontext();

	@Override
	public BlockContext.Builder builder();

	public interface Builder extends BlockContext, DefaultContext.Builder {
		
		public BlockContext.Builder flowWidth(int value);
		
		public BlockContext.Builder formatterContext(FormatterContext value);

		@Override
		public BlockContext.Builder currentVolume(Integer value);

		@Override
		public BlockContext.Builder currentPage(Integer value);

		@Override
		public BlockContext.Builder metaVolume(Integer value);

		@Override
		public BlockContext.Builder metaPage(Integer value);

		@Override
		public BlockContext.Builder refs(CrossReferenceHandler crh);
		
		@Override
		public BlockContext.Builder space(Space value);

		@Override
		public BlockContext build();
		
	}
	
	public Builder formatterContext(FormatterContext value);
	
	public static BlockContext.Builder from(DefaultContext base) {
		return new Impl(base, true);
	}

	public static BlockContext.Builder from(BlockContext base) {
		return new Impl(base, true);
	}

	public static class Impl extends DefaultContext.Impl implements BlockContext.Builder {
		
		private int flowWidth = 0;
		private FormatterContext fcontext = null;
		
		private Impl(DefaultContext base, boolean mutable) {
			super(base, mutable);
		}
		
		private Impl(BlockContext base, boolean mutable) {
			super(base, mutable);
			this.flowWidth = base.getFlowWidth();
			this.fcontext = base.getFcontext();
		}
		
		@Override
		public BlockContext build() {
			return new BlockContext.Impl(this, false);
		}
		
		@Override
		public BlockContext.Builder builder() {
			return new BlockContext.Impl(this, true);
		}
		
		@Override
		public BlockContext.Builder flowWidth(int value) {
			this.flowWidth = value;
			return this;
		}
		
		@Override
		public BlockContext.Builder formatterContext(FormatterContext value) {
			this.fcontext = value;
			return this;
		}

		@Override
		public BlockContext.Builder currentVolume(Integer value) {
			super.currentVolume(value);
			return this;
		}

		@Override
		public BlockContext.Builder currentPage(Integer value) {
			super.currentPage(value);
			return this;
		}

		@Override
		public BlockContext.Builder metaVolume(Integer value) {
			super.metaVolume(value);
			return this;
		}

		@Override
		public BlockContext.Builder metaPage(Integer value) {
			super.metaPage(value);
			return this;
		}

		@Override
		public BlockContext.Builder refs(CrossReferenceHandler value) {
			super.refs(value);
			return this;
		}

		@Override
		public BlockContext.Builder space(Space value) {
			super.space(value);
			return this;
		}

		@Override
		public int getFlowWidth() {
			return flowWidth;
		}

		@Override
		public FormatterContext getFcontext() {
			return fcontext;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = super.hashCode();
			result = prime * result + ((fcontext == null) ? 0 : fcontext.hashCode());
			result = prime * result + flowWidth;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (!super.equals(obj))
				return false;
			if (getClass() != obj.getClass())
				return false;
			BlockContext.Impl other = (BlockContext.Impl) obj;
			if (fcontext == null) {
				if (other.fcontext != null)
					return false;
			} else if (!fcontext.equals(other.fcontext))
				return false;
			if (flowWidth != other.flowWidth)
				return false;
			return true;
		}
	}
}
