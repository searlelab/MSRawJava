package org.searlelab.msrawjava.algorithms;

import java.util.ArrayList;
import java.util.Arrays;

import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.Range;

import gnu.trove.list.array.TFloatArrayList;
import gnu.trove.list.array.TIntArrayList;

public class StaggeredDemultiplexer {
	private static final float windowBoundaryTolerance=0.01f;

	private final double[][] invertedStructureMatrix;

	public StaggeredDemultiplexer(ArrayList<Range> acquiredWindows) {
		acquiredWindows.sort(null);
		ArrayList<RangeCounter> subRanges=getSubRanges(acquiredWindows);

		double[][] matrix=new double[subRanges.size()][];
		for (int i=0; i<matrix.length; i++) {
			// note, this will pad the matrix by a final column, which will be set to 0
			matrix[i]=new double[subRanges.size()];

			RangeCounter subRange=subRanges.get(i);
			for (int j=0; j<subRange.acquiredIndicies.size(); j++) {
				matrix[i][subRange.acquiredIndicies.get(j)]=1.0;
			}
		}
		// set the final row/column to 1 to complete the matrix with the hanging edge
		matrix[matrix.length-1][matrix.length-1]=1.0;

		System.out.println(matrix.length+" rows by "+matrix[0].length+" columns");

		// inverse requires square matrix
		this.invertedStructureMatrix=MatrixMath.invert(MatrixMath.transpose(matrix));
		System.out.println(invertedStructureMatrix.length+" rows by "+invertedStructureMatrix[0].length+" columns");
	}
	
	public ArrayList<FragmentScan> demultiplex(ArrayList<FragmentScan> cycleM2, ArrayList<FragmentScan> cycleM1, ArrayList<FragmentScan> cycleP1, ArrayList<FragmentScan> cycleP2) {
		System.out.print(cycleM2.get(0).getScanStartTime());
		System.out.print("\t"+cycleM1.get(0).getScanStartTime());
		System.out.print("\t"+cycleP1.get(0).getScanStartTime());
		System.out.print("\t"+cycleP2.get(0).getScanStartTime()+", [");
		for (FragmentScan msms : cycleP1) {
			System.out.print("\t"+(int)msms.getPrecursorRange().getMiddle());
		}		
		System.out.println("]");
		// FIXME
		return new ArrayList<FragmentScan>();
	}

	public static ArrayList<RangeCounter> getSubRanges(ArrayList<Range> acquiredWindows) {
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
