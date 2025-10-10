package org.searlelab.msrawjava.algorithms;

import java.util.ArrayList;
import java.util.Arrays;

import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.StripeFileInterface;

import gnu.trove.list.array.TFloatArrayList;
import gnu.trove.list.array.TIntArrayList;

public class StaggeredDemultiplexer {
	private static final float windowBoundaryTolerance=0.01f;

	public static void getSubRanges(StripeFileInterface file) {
		ArrayList<Range> acquiredWindows=new ArrayList<>(file.getRanges().keySet());
		acquiredWindows.sort(null);
		ArrayList<RangeCounter> subRanges=getSubRanges(acquiredWindows);
		
		double[][] matrix=new double[subRanges.size()][];
		for (int i=0; i<matrix.length; i++) {
			matrix[i]=new double[acquiredWindows.size()];
			
			RangeCounter subRange=subRanges.get(i);
			for (int j=0; j<subRange.acquiredIndicies.size(); j++) {
				matrix[i][subRange.acquiredIndicies.get(j)]=1;
			}
		}
		
		// inverse requires square matrix, so this will truncate the last fractional window
		// FIXME should we pad this?
		matrix=MatrixMath.invert(MatrixMath.transpose(matrix));
		
		System.out.println(matrix.length+" rows by "+matrix[0].length+" columns");
		MatrixMath.print(matrix);
	}
	
	private static ArrayList<RangeCounter> getSubRanges(ArrayList<Range> acquiredWindows) {
		TFloatArrayList boundaries=new TFloatArrayList();
		for (Range range : acquiredWindows) {
			boundaries.add(range.getStart());
			boundaries.add(range.getStop());
		}
		boundaries.sort();
		float anchor=boundaries.getQuick(0);

		// subRanges are implicitly sorted
		ArrayList<RangeCounter> subRanges=new ArrayList<RangeCounter>();
		for (int i=1; i<boundaries.size(); i++) {
			float v=boundaries.getQuick(i);
			if (v-anchor>windowBoundaryTolerance) {
				subRanges.add(new RangeCounter(new Range(anchor, v)));
				anchor=v;
			}
		}

		float[] centers=new float[subRanges.size()];
		for (int i=0; i<centers.length; i++) {
			centers[i]=subRanges.get(i).range.getMiddle();
		}

		for (int i=0; i<acquiredWindows.size(); i++) {
			Range range=acquiredWindows.get(i);
			int[] indicies=getIndicies(centers, range);
			for (int j=0; j<indicies.length; j++) {
				subRanges.get(indicies[j]).addRange(range, i);
			}
		}
		return subRanges;
	}

	private static final class RangeCounter {
		public final Range range;
		private ArrayList<Range> acquiredRanges;
		private TIntArrayList acquiredIndicies;

		public RangeCounter(Range r) {
			this.range=r;
			acquiredRanges=new ArrayList<Range>();
			acquiredIndicies=new TIntArrayList();
		}
		
		private void addRange(Range acquired, int index) {
			acquiredRanges.add(acquired);
			acquiredIndicies.add(index);
		}
	}

	public static int[] getIndicies(float[] centers, Range target) {
		int value=Arrays.binarySearch(centers, target.getMiddle());
		// exact match (not likely)
		if (value<0) {
			// insertion point
			value=-(value+1);
		}

		TIntArrayList matches=new TIntArrayList();
		// look below
		int index=value;
		while (index>0&&target.contains(centers[index-1])) {
			matches.add(index-1);
			index--;
		}

		// look up
		index=value;
		while (index<centers.length&&target.contains(centers[index])) {
			matches.add(index);
			index++;
		}

		return matches.toArray();
	}
}
