package org.daisy.dotify.formatter.impl;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Map;
import java.util.Stack;

import org.daisy.dotify.api.formatter.BlockPosition;
import org.daisy.dotify.api.formatter.RenderingScenario;

class PageSequenceRecorder {
	private static final String base = "base";
	private static final String best = "best";
	
	private PageSequenceRecorderData data;

	private RenderingScenario current = null;
	private boolean invalid = false;
	private double cost = 0;
	private float height = 0;
	private float minWidth = 0;
	private double forceCount = 0;
	private Map<String, PageSequenceRecorderData> states;

	PageSequenceRecorder() {
		data = new PageSequenceRecorderData();
		states = new HashMap<>();
	}
	
	private void saveState(String id) {
		states.put(id, new PageSequenceRecorderData(data));
	}
	
	private void restoreState(String id) {
		PageSequenceRecorderData state = states.get(id);
		if (state!=null) {
			data = new PageSequenceRecorderData(state);
		}
	}
	
	private void clearState(String id) {
		states.remove(id);
	}
	
	private boolean hasState(String id) {
		return states.containsKey(id);
	}
	
	void startScenario(RenderingScenario scenario) {
		if (current == scenario) {
			throw new IllegalStateException();
		} else if (current == null) {
			height = data.calcSize();
			cost = Double.MAX_VALUE;
			clearState(best);
			saveState(base);
		} else {
			if (!invalid) {
				//TODO: measure, evaluate
				float size = data.calcSize()-height;
				double ncost = current.calculateCost(setParams(size, minWidth, forceCount));
				if (ncost<cost) {
					//if better, store
					cost = ncost;
					saveState(best);
				}
			}
			restoreState(base);
		}
		forceCount = 0;
		minWidth = Float.MAX_VALUE;
		current = scenario;
		invalid = false;
	}

	void endScenarios() {
		if (current == null) {
			throw new IllegalStateException();
		}
		if (invalid) {
			if (hasState(best)) {
				restoreState(best);
			} else {
				throw new RuntimeException("Failed to render any scenario.");
			}
		} else {
			//if not better
			float size = data.calcSize()-height;
			double ncost = current.calculateCost(setParams(size, minWidth, forceCount));
			if (ncost>cost) {
				restoreState(best);
			}
		}
		current = null;
	}
	
	/**
	 * Invalidates the current scenario, if any. This causes the remainder of the
	 * scenario to be excluded from further processing.
	 * 
	 * @param e the exception that caused the scenario to be invalidated
	 * @throws RuntimeException if no scenario is active 
	 */
	void invalidateScenario(Exception e) {
		if (current == null) {
			throw new IllegalStateException();
		}
		invalid = true;
	}
	
	/**
	 * Process a new block for a scenario
	 * @param g
	 * @param rec
	 */
	void processBlock(AbstractBlockContentManager bcm) {
		if (current != null) {
			if (invalid) {
				return;
			}
			forceCount += bcm.getForceBreakCount();
			minWidth = Math.min(minWidth, bcm.getMinimumAvailableWidth());
		}
	}
	
	void newRowGroupSequence(BlockPosition pos, RowImpl emptyRow) {
		data.newRowGroupSequence(pos, emptyRow);
	}
	
	boolean isDataGroupsEmpty() {
		return data.dataGroups.isEmpty();
	}
	
	boolean isDataEmpty() {
		return data.isDataEmpty();
	}
	
	RowGroupSequence currentSequence() {
		return data.dataGroups.peek();
	}
	
	void addRowGroup(RowGroup rg) {
		data.addRowGroup(rg); 
	}
	
	Iterator<RowGroupSequence> getResult() {
		return getResult(0);
	}
	
	Iterator<RowGroupSequence> getResult(int index) {
		if (current != null) {
			throw new IllegalStateException();
		}
		return data.dataGroups.listIterator(index);
	}
	
	int getKeepWithNext() {
		return data.keepWithNext;
	}
	
	void setKeepWithNext(int keepWithNext) {
		data.keepWithNext = keepWithNext;
	}

	private static Map<String, Double> setParams(double height, double minBlockWidth, double forceCount) {
		Map<String, Double> params = new HashMap<>();
		params.put("total-height", height);
		params.put("min-block-width", minBlockWidth);
		params.put("forced-break-count", forceCount);
		return params;
	}

	private static class PageSequenceRecorderData {
		private final PushOnlyStack<RowGroupSequence> dataGroups;
		private int keepWithNext = 0;

		PageSequenceRecorderData() {
			dataGroups = new PushOnlyStack<>();
			keepWithNext = 0;
		}

		/**
		 * Creates a deep copy of the supplied instance
		 * @param template the instance to copy
		 */
		PageSequenceRecorderData(PageSequenceRecorderData template) {
			dataGroups = new PushOnlyStack<>(template.dataGroups);
			keepWithNext = template.keepWithNext;
		}

		float calcSize() {
			float size = 0;
			for (RowGroupSequence rgs : dataGroups) {
				size += rgs.calcSequenceSize();
			}
			return size;
		}
		
		boolean isDataEmpty() {
			return (dataGroups.isEmpty()||dataGroups.peek().getGroup().isEmpty());
		}
		
		void newRowGroupSequence(BlockPosition pos, RowImpl emptyRow) {
			RowGroupSequence rgs = new RowGroupSequence(pos, emptyRow);
			dataGroups.push(rgs);
		}
		
		void addRowGroup(RowGroup rg) {
			dataGroups.peek().getGroup().add(rg);
		}

	}
	
	private static class PushOnlyStack<E extends Cloneable> implements Iterable<E> {
		
		Stack<E> stack;
		int size;
		
		/** Size of template */
		final int initialSize;
		
		/** Deep copy of template.peek() */
		final E peekCopy;
		
		/** The PushOnlyStack that created stack */
		PushOnlyStack<E> stackCreator;
		
		/** stackCreator.stackOwner determines which PushOnlyStack has claimed the rights to push to stack */
		PushOnlyStack<E> stackOwner;
		
		PushOnlyStack() {
			stack = new Stack<E>();
			size = initialSize = 0;
			peekCopy = null;
			stackCreator = this;
		}
		
		/**
		 * The assumption is made that all items except template.peek() are not mutated.
		 */
		PushOnlyStack(PushOnlyStack<E> template) {
			stack = template.stack;
			size = initialSize = template.size;
			peekCopy = size > 0 ? deepCopy(template.peek()) : null;
			stackCreator = template.stackCreator;
		}
		
		E peek() {
			if (peekCopy != null && size == initialSize) {
				return peekCopy;
			}
			return stack.elementAt(size-1);
		}
		
		void push(E item) {
			lazyClaimOrCopyStack();
			stack.push(item);
			size++;
		}
		
		boolean isEmpty() {
			return size == 0;
		}
		
		public Iterator<E> iterator() {
			return listIterator(0);
		}
		
		ListIterator<E> listIterator(int index) {
			lazyClaimOrCopyStack();
			return unmodifiableIterator(stack.listIterator(index));
		}
		
		private void lazyClaimOrCopyStack() {
			if (stackCreator.stackOwner == null) {
				stackCreator.stackOwner = this;
			} else if (stackCreator.stackOwner != this) {
				Stack<E> copy = new Stack<E>();
				for (int i = 0; i < initialSize; i++) {
					if (i == initialSize - 1) {
						copy.add(peekCopy);
					} else {
						copy.add(stack.elementAt(i));
					}
				}
				stack = copy;
				stackCreator = stackOwner = this;
			}
		}
		
		/** Assumes that clone() does a deep copy of item */
		@SuppressWarnings("unchecked")
		private static <E> E deepCopy(E item) {
			try {
				return (E)item.getClass().getMethod("clone").invoke(item);
			} catch (IllegalAccessException e) {
				throw new RuntimeException("coding error");
			} catch (SecurityException e) {
				throw new RuntimeException("coding error");
			} catch (NoSuchMethodException e) {
				throw new RuntimeException("coding error");
			} catch (IllegalArgumentException e) {
				throw new RuntimeException("coding error");
			} catch (InvocationTargetException e) {
				throw new RuntimeException(e.getTargetException());
			}
		}
		
		private static <E> UnmodifiableIterator<E> unmodifiableIterator(final ListIterator<E> iterator) {
			return new UnmodifiableIterator<E>() {
				
				@Override
				public int nextIndex() {
					return iterator.nextIndex();
				}
				
				@Override
				public int previousIndex() {
					return iterator.previousIndex();
				}
				
				@Override
				public boolean hasNext() {
					return iterator.hasNext();
				}
				
				@Override
				public boolean hasPrevious() {
					return iterator.hasPrevious();
				}
				
				@Override
				public E next() {
					return iterator.next();
				}
				
				@Override
				public E previous() {
					return iterator.previous();
				}
			};
		}
		
		private static abstract class UnmodifiableIterator<E> implements ListIterator<E> {
			
			@Override
			public void remove() {
				throw new UnsupportedOperationException("unmodifiable");
			}
			
			@Override
			public void set(E e) {
				throw new UnsupportedOperationException("unmodifiable");
			}
			
			@Override
			public void add(E e) {
				throw new UnsupportedOperationException("unmodifiable");
			}
		}
	}
}
