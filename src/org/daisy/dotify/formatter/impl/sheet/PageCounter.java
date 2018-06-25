package org.daisy.dotify.formatter.impl.sheet;

/**
 * Provides state needed for a text flow.
 * 
 * @author Joel Håkansson
 */
public class PageCounter {
	private final int pageOffset;
	private final int pageCount;

	public PageCounter() {
		pageOffset = 0;
		pageCount = 0;
	}

	private PageCounter(Builder builder) {
		pageOffset = builder.pageOffset;
		pageCount = builder.pageCount;
	}

	public int getDefaultPageOffset() {
		return pageOffset;
	}

	/**
	 * This is used for searching and MUST be continuous. Do not use for page numbers.
	 * @return returns the page count
	 */
	public int getPageCount() {
		return pageCount;
	}
	
	public Builder builder() {
		return from(this);
	}
	
	public static Builder from(PageCounter template) {
		return new Builder(template);
	}

	public static class Builder {
		
		private int pageOffset;
		private int pageCount;
		
		private Builder(PageCounter template) {
			pageOffset = template.pageOffset;
			pageCount = template.pageCount;
		}
		
		public Builder setDefaultPageOffset(int value) {
			pageOffset = value;
			return this;
		}
		
		/**
		 * Advance to the next page.
		 */
		public Builder increasePageCount() {
			pageCount++;
			return this;
		}
		
		public PageCounter build() {
			return new PageCounter(this);
		}
	}
}
