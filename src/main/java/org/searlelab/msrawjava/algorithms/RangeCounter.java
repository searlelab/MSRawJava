package org.searlelab.msrawjava.algorithms;

import java.util.ArrayList;

import org.searlelab.msrawjava.model.Range;

import gnu.trove.list.array.TIntArrayList;

final public class RangeCounter {
	public final Range range;
	private ArrayList<Range> acquiredRanges;
	TIntArrayList acquiredIndicies;

	public RangeCounter(Range r) {
		this.range=r;
		acquiredRanges=new ArrayList<Range>();
		acquiredIndicies=new TIntArrayList();
	}
	
	void addRange(Range acquired, int index) {
		acquiredRanges.add(acquired);
		acquiredIndicies.add(index);
	}
}