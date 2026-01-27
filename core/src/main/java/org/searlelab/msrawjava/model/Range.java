package org.searlelab.msrawjava.model;

import java.util.ArrayList;
import java.util.Comparator;

/**
 * Range represents a interval with explicit lower and upper bounds, used throughout the model to express m/z windows,
 * retention-time slices, or other bounded domains.
 */
// @Immutable
public class Range implements Comparable<Range> {
	private final float start, stop;

	public Range(double start, double stop) {
		this((float)start, (float)stop);
	}

	public Range(float start, float stop) {
		// ensure that start comes before stop
		if (start<=stop) {
			this.start=start;
			this.stop=stop;
		} else {
			this.start=stop;
			this.stop=start;
		}
	}

	public Range(float[] data) {
		this(min(data), max(data));
	}

	private static float min(float[] v) {
		float min=Float.MAX_VALUE;
		for (int i=0; i<v.length; i++) {
			if (v[i]<min) {
				min=v[i];
			}
		}
		return min;
	}

	private static float max(float[] v) {
		float max=-Float.MAX_VALUE;
		for (int i=0; i<v.length; i++) {
			if (v[i]>max) {
				max=v[i];
			}
		}
		return max;
	}

	public Range addBuffer(float buffer) {
		return new Range(start-buffer, stop+buffer);
	}

	@Override
	public String toString() {
		return Math.round(start*10.0f)/10.0f+" to "+Math.round(stop*10.0f)/10.0f;
	}

	@Override
	public int hashCode() {
		// round is close enough for this work, theoretically it's possible for 1.4999999999999!=1.500000000000, but this is safer than truncate since int to float conversions are common
		return Math.round(start)+16807*Math.round(stop);
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Range)) return false;
		Range r=(Range)obj;
		if (Math.abs(start-r.start)>1e-3f) return false;
		if (Math.abs(stop-r.stop)>1e-3f) return false;
		return true;
	}

	public float getStart() {
		return start;
	}

	public float getStop() {
		return stop;
	}

	public float getMiddle() {
		return (start+stop)/2.0f;
	}

	public float getRange() {
		return stop-start;
	}

	public boolean contains(double value) {
		if (value>=start&&value<=stop) {
			return true;
		}
		return false;
	}

	public boolean contains(float value) {
		if (value>=start&&value<=stop) {
			return true;
		}
		return false;
	}

	public boolean contains(Range value) {
		if (value.getStop()>=start&&value.getStart()<=stop) {
			return true;
		}
		return false;
	}

	public ArrayList<Range> chunkIntoBins(int binCount) {
		float delta=getRange()/binCount;

		ArrayList<Range> ranges=new ArrayList<Range>();
		float currentMin=start;
		for (int i=0; i<binCount; i++) {
			float currentMax=currentMin+delta;
			ranges.add(new Range(currentMin, currentMax));
			currentMin=currentMax;
		}
		return ranges;
	}

	/**
	 * sorts on start location, then on stop location
	 */
	@Override
	public int compareTo(Range o) {
		if (o==null) return 1;
		if (start>o.start) return 1;
		if (start<o.start) return -1;
		if (stop>o.stop) return 1;
		if (stop<o.stop) return -1;
		return 0;
	}

	/**
	 * sorts on normal compareTo, but equals is inclusive of any range boundary intersection
	 */
	public static final Comparator<Range> RANGE_CONTAINS_COMPARATOR=new Comparator<Range>() {
		@Override
		public int compare(Range o1, Range o2) {
			if (o1==null&&o2==null) return 0;
			if (o1==null) return 1;
			if (o2==null) return -1;

			Range smaller, larger;
			if (o1.getRange()>o2.getRange()) {
				larger=o1;
				smaller=o2;
			} else {
				smaller=o1;
				larger=o2;
			}
			if (larger.contains(smaller.getStart())||larger.contains(smaller.getStop())) {
				return 0;
			}
			return o1.compareTo(o2);
		}
	};

	public float linearInterp(float X, float minY, float maxY) {
		float deltaX=getRange();
		if (deltaX==0) {
			float half=(maxY+minY)/2f;
			if (half<minY) return minY;
			if (half>maxY) return maxY;
			return half;
		}
		float deltaY=maxY-minY;
		if (deltaY==0) {
			return maxY;
		}
		float interp=((deltaY/deltaX)*(X-getStart())+minY);
		if (interp<minY) return minY;
		if (interp>maxY) return maxY;
		return interp;
	}

	public int linearInterp(float X, int minY, int maxY) {
		return Math.round(linearInterp(X, (float)minY, (float)maxY));
	}

	public float mapBackToRange(float Y, float minY, float maxY) {
		float deltaX=getRange();
		if (deltaX==0) {
			return getStop();
		}

		float deltaY=maxY-minY;
		if (deltaY==0) {
			float half=(getStart()+getStop())/2f;
			if (half<getStart()) return getStart();
			if (half>getStop()) return getStop();
			return half;
		}
		float interp=((deltaX/deltaY)*(Y-minY)+getStart());
		if (interp<getStart()) return getStart();
		if (interp>getStop()) return getStop();
		return interp;
	}

	public static Range getWidestRange(ArrayList<Range> ranges) {
		float min=Float.MAX_VALUE;
		float max=-Float.MAX_VALUE;
		for (Range range : ranges) {
			if (range.getStart()<min) {
				min=range.getStart();
			}
			if (range.getStop()>max) {
				max=range.getStop();
			}
		}
		return new Range(min, max);
	}
}
