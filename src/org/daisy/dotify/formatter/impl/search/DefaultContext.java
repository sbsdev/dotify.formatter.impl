package org.daisy.dotify.formatter.impl.search;

import org.daisy.dotify.api.formatter.Context;

public interface DefaultContext extends Context {

	public CrossReferenceHandler getRefs();
	
	public Space getSpace();
	
	public DefaultContext.Builder builder();
	
	public interface Builder extends DefaultContext {
		
		@Override
		public CrossReferenceHandler.Builder getRefs();
		
		public Builder currentVolume(Integer value);
		
		public Builder currentPage(Integer value);
		
		public Builder metaVolume(Integer value);
		
		public Builder metaPage(Integer value);
		
		public Builder refs(CrossReferenceHandler crh);
		
		public Builder space(Space value);
		
		public DefaultContext build();
		
	}
	
	public static DefaultContext.Builder from() {
		return new Impl();
	}
	
	public static DefaultContext.Builder from(Context base) {
		return new Impl(base, true);
	}
	
	public static DefaultContext.Builder from(DefaultContext base) {
		return new Impl(base, true);
	}
	
	// private not allowed
	public static class Impl implements DefaultContext.Builder {
		
		private Integer currentVolume=null,
						currentPage=null,
						metaVolume=null,
						metaPage=null;
		private Space space = null;
		private CrossReferenceHandler crh = null;
		
		protected Impl() {
		}
		
		/** Makes a deep copy */
		protected Impl(Context base, boolean mutable) {
			this.currentVolume = base.getCurrentVolume();
			this.currentPage = base.getCurrentPage();
			this.metaVolume = base.getMetaVolume();
			this.metaPage = base.getMetaPage();
			if (base instanceof DefaultContext) {
				this.space = ((DefaultContext)base).getSpace();
				this.crh = ((DefaultContext)base).getRefs();
				if (this.crh != null) {
					if (mutable) {
						this.crh = this.crh.builder();
					} else if (base instanceof DefaultContext.Builder) {
						this.crh = ((CrossReferenceHandler.Builder)this.crh).build();
					}
				}
			}
		}
		
		@Override
		public DefaultContext build() {
			return new Impl(this, false);
		}

		@Override
		public DefaultContext.Builder builder() {
			return new Impl(this, true);
		}
		
		@Override
		public Builder currentVolume(Integer value) {
			this.currentVolume = value;
			return this;
		}
		
		@Override
		public Builder currentPage(Integer value) {
			this.currentPage = value;
			return this;
		}
		
		@Override
		public Builder metaVolume(Integer value) {
			this.metaVolume = value;
			return this;
		}
		
		@Override
		public Builder metaPage(Integer value) {
			this.metaPage = value;
			return this;
		}
		
		@Override
		public Builder refs(CrossReferenceHandler crh) {
			this.crh = crh.builder();
			return this;
		}
		
		@Override
		public Builder space(Space value) {
			this.space = value;
			return this;
		}
		
		@Override
		public Integer getCurrentVolume() {
			return currentVolume;
		}
		
		@Override
		public Integer getVolumeCount() {
			return (crh==null?null:crh.getVolumeCount());
		}
		
		@Override
		public Integer getCurrentPage() {
			return currentPage;
		}
		
		@Override
		public Integer getMetaVolume() {
			return metaVolume;
		}
		
		@Override
		public Integer getMetaPage() {
			return metaPage;
		}
		
		@Override
		public Integer getPagesInVolume() {
			return (crh==null||currentVolume==null?null:crh.getPagesInVolume(currentVolume));
		}
		
		@Override
		public Integer getPagesInDocument() {
			return (crh==null?null:crh.getPagesInDocument());
		}
		
		@Override
		public Integer getSheetsInVolume() {
			return (crh==null||currentVolume==null?null:crh.getSheetsInVolume(currentVolume));
		}
		
		@Override
		public Integer getSheetsInDocument() {
			return (crh==null?null:crh.getSheetsInDocument());
		}
		
		@Override
		public CrossReferenceHandler.Builder getRefs() {
			return (CrossReferenceHandler.Builder)crh;
		}
		
		public Space getSpace() {
			return space;
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((crh == null) ? 0 : crh.hashCode());
			result = prime * result + ((currentPage == null) ? 0 : currentPage.hashCode());
			result = prime * result + ((currentVolume == null) ? 0 : currentVolume.hashCode());
			result = prime * result + ((metaPage == null) ? 0 : metaPage.hashCode());
			result = prime * result + ((metaVolume == null) ? 0 : metaVolume.hashCode());
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
			Impl other = (Impl) obj;
			if (crh == null) {
				if (other.crh != null) {
					return false;
				}
			} else if (!crh.equals(other.crh)) {
				return false;
			}
			if (currentPage == null) {
				if (other.currentPage != null) {
					return false;
				}
			} else if (!currentPage.equals(other.currentPage)) {
				return false;
			}
			if (currentVolume == null) {
				if (other.currentVolume != null) {
					return false;
				}
			} else if (!currentVolume.equals(other.currentVolume)) {
				return false;
			}
			if (metaPage == null) {
				if (other.metaPage != null) {
					return false;
				}
			} else if (!metaPage.equals(other.metaPage)) {
				return false;
			}
			if (metaVolume == null) {
				if (other.metaVolume != null) {
					return false;
				}
			} else if (!metaVolume.equals(other.metaVolume)) {
				return false;
			}
			return true;
		}
	}
}
