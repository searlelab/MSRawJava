package org.searlelab.msrawjava.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TIntArrayList;

/**
 * MassTolerance abstracts a mass-error policy for comparing or windowing m/z values.
 */
// @Immutable
public abstract class MassTolerance {

	public abstract double getToleranceInMz(double m1, double m2);

	/**
	 * if first is less, -1, if second is less 1, otherwise 0
	 * 
	 * @param m1
	 * @param m2
	 * @return
	 */
	public int compareTo(double m1, double m2) {
		double tolerance=getToleranceInMz(m1, m2);
		if (m1+tolerance<m2) return -1;
		if (m1-tolerance>m2) return 1;
		return 0;
	}

	/**
	 * @param peaks
	 *            -- assumes sorted array of peaks
	 * @param target
	 * @return all matching masses in range
	 */
	public int[] getIndices(double[] peaks, double target) {
		int value=Arrays.binarySearch(peaks, target);
		// exact match (not likely)
		if (value<0) {
			// insertion point
			value=-(value+1);
		}

		TIntArrayList matches=new TIntArrayList();
		// look below
		int index=value;
		while (index>0&&compareTo(peaks[index-1], target)==0) {
			matches.add(index-1);
			index--;
		}

		// look up
		index=value;
		while (index<peaks.length&&compareTo(peaks[index], target)==0) {
			matches.add(index);
			index++;
		}

		return matches.toArray();
	}

	/**
	 * @param peaks
	 *            -- assumes sorted array of peaks
	 * @param target
	 * @return all matching masses in range
	 */
	public int[] getIndices(TDoubleArrayList peaks, double target) {
		int value=peaks.binarySearch(target);
		// exact match (not likely)
		if (value<0) {
			// insertion point
			value=-(value+1);
		}

		TIntArrayList matches=new TIntArrayList();
		// look below
		int index=value;
		while (index>0&&compareTo(peaks.get(index-1), target)==0) {
			matches.add(index-1);
			index--;
		}

		// look up
		index=value;
		while (index<peaks.size()&&compareTo(peaks.get(index), target)==0) {
			matches.add(index);
			index++;
		}

		return matches.toArray();
	}

	/**
	 * @param peaks
	 *            -- assumes sorted array of peaks
	 * @param target
	 * @return all matching masses in range
	 */
	public int[] getIndices(PeakInterface[] peaks, PeakInterface target) {
		int value=Arrays.binarySearch(peaks, target);
		// exact match (not likely)
		if (value<0) {
			// insertion point
			value=-(value+1);
		}

		TIntArrayList matches=new TIntArrayList();
		// look below
		int index=value;
		while (index>0&&compareTo(peaks[index-1].getMz(), target.getMz())==0) {
			matches.add(index-1);
			index--;
		}

		// look up
		index=value;
		while (index<peaks.length&&compareTo(peaks[index].getMz(), target.getMz())==0) {
			matches.add(index);
			index++;
		}

		return matches.toArray();
	}

	/**
	 * @param peaks
	 *            -- assumes sorted array of peaks
	 * @param target
	 * @return all matching masses in range
	 */
	public int[] getIndices(List<? extends PeakInterface> peaks, PeakInterface target) {
		int value=Collections.binarySearch(peaks, target);
		// exact match (not likely)
		if (value<0) {
			// insertion point
			value=-(value+1);
		}

		TIntArrayList matches=new TIntArrayList();
		// look below
		int index=value;
		while (index>0&&compareTo(peaks.get(index-1).getMz(), target.getMz())==0) {
			matches.add(index-1);
			index--;
		}

		// look up
		index=value;
		while (index<peaks.size()&&compareTo(peaks.get(index).getMz(), target.getMz())==0) {
			matches.add(index);
			index++;
		}

		return matches.toArray();
	}
}
