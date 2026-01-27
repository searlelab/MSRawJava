package org.searlelab.msrawjava.gui.graphing;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.searlelab.msrawjava.algorithms.MatrixMath;
import org.searlelab.msrawjava.io.utils.Pair;
import org.searlelab.msrawjava.model.AcquiredSpectrum;
import org.searlelab.msrawjava.model.Range;

import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TFloatArrayList;
import gnu.trove.map.hash.TDoubleDoubleHashMap;
import gnu.trove.procedure.TDoubleDoubleProcedure;

public class XYTrace implements XYTraceInterface, Comparable<XYTraceInterface> {
	private final String name;
	private final ArrayList<XYPoint> points;
	private final GraphType type;
	private final Optional<Color> color;
	private final Optional<Float> thickness;

	public XYTrace(AcquiredSpectrum spectrum) {
		color=Optional.empty();
		thickness=Optional.empty();
		this.type=GraphType.spectrum;
		this.points=new ArrayList<XYPoint>();
		this.name=spectrum.getSpectrumName();

		double[] mzs=spectrum.getMassArray();
		float[] intensities=spectrum.getIntensityArray();

		for (int i=0; i<intensities.length; i++) {
			points.add(new XYPoint(mzs[i], intensities[i]));
		}

		Collections.sort(points);
	}

	public XYTrace(double[] x, double[] y, GraphType type, String name, Optional<Color> color, Optional<Float> thickness) {
		this(x, y, type, name, color.orElse(null), thickness.orElse(null));
	}

	public XYTrace(double[] x, double[] y, GraphType type, String name, Color color, Float thickness) {
		this.color=Optional.ofNullable(color);
		this.thickness=Optional.ofNullable(thickness);
		this.type=type;
		this.points=new ArrayList<XYPoint>();
		this.name=name;

		assert (x.length==y.length);
		for (int i=0; i<x.length; i++) {
			points.add(new XYPoint(x[i], y[i]));
		}
		Collections.sort(points);
	}

	public XYTrace(double[] x, double[] y, GraphType type, String name) {
		this(x, y, type, name, Optional.ofNullable((Color)null), Optional.ofNullable((Float)null));
	}

	public XYTrace(float[] x, float[] y, GraphType type, String name) {
		this(MatrixMath.toDoubleArray(x), MatrixMath.toDoubleArray(y), type, name, Optional.ofNullable((Color)null), Optional.ofNullable((Float)null));
	}

	public XYTrace(double[] x, float[] y, GraphType type, String name) {
		this(x, MatrixMath.toDoubleArray(y), type, name, Optional.ofNullable((Color)null), Optional.ofNullable((Float)null));
	}

	public XYTrace(float[] x, float[] y, GraphType type, String name, Color color, Float thickness) {
		this(MatrixMath.toDoubleArray(x), MatrixMath.toDoubleArray(y), type, name, color, thickness);
	}

	public XYTrace(TDoubleDoubleHashMap map, GraphType type, String name, Color color, Float thickness) {
		this.color=Optional.ofNullable(color);
		this.thickness=Optional.ofNullable(thickness);
		this.type=type;
		this.points=new ArrayList<XYPoint>();
		this.name=name;

		map.forEachEntry(new TDoubleDoubleProcedure() {
			public boolean execute(double x, double y) {
				points.add(new XYPoint(x, y));
				return true;
			}
		});
		Collections.sort(points);
	}

	@Override
	public Optional<Color> getColor() {
		return color;
	}

	@Override
	public Optional<Float> getThickness() {
		return thickness;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public GraphType getType() {
		return type;
	}

	@Override
	public Pair<double[], double[]> toArrays() {
		return toArrays(points);
	}

	public int size() {
		return points.size();
	}

	public String toString() {
		Pair<double[], double[]> pair=toArrays(points);
		StringBuilder sb=new StringBuilder("// "+getName()+"\n");
		sb.append("float[] x=new float[] {");
		boolean first=true;
		for (double d : pair.x) {
			if (first) {
				first=false;
			} else {
				sb.append(',');
			}
			sb.append(d);
			sb.append('f');
		}
		sb.append("};\n");
		sb.append("float[] y=new float[] {");
		first=true;
		for (double d : pair.y) {
			if (first) {
				first=false;
			} else {
				sb.append(',');
			}
			sb.append(d);
			sb.append('f');
		}
		sb.append("};\n");
		return sb.toString();
	}

	public static ArrayList<XYPoint> toPoints(double[] xs, double[] ys) {
		assert (xs.length==ys.length);

		ArrayList<XYPoint> points=new ArrayList<XYPoint>();
		for (int i=0; i<ys.length; i++) {
			points.add(new XYPoint(xs[i], ys[i]));
		}
		return points;
	}

	public static ArrayList<XYPoint> toPoints(float[] xs, float[] ys) {
		assert (xs.length==ys.length);

		ArrayList<XYPoint> points=new ArrayList<XYPoint>();
		for (int i=0; i<ys.length; i++) {
			points.add(new XYPoint(xs[i], ys[i]));
		}
		return points;
	}

	public static Pair<double[], double[]> toArrays(List<XYPoint> points) {
		TDoubleArrayList xs=new TDoubleArrayList();
		TDoubleArrayList ys=new TDoubleArrayList();
		for (XYPoint point : points) {
			xs.add(point.getX());
			ys.add(point.getY());
		}
		return new Pair<double[], double[]>(xs.toArray(), ys.toArray());
	}

	public static Pair<float[], float[]> toFloatArrays(List<XYPoint> points) {
		TFloatArrayList xs=new TFloatArrayList();
		TFloatArrayList ys=new TFloatArrayList();
		for (XYPoint point : points) {
			xs.add((float)point.getX());
			ys.add((float)point.getY());
		}
		return new Pair<float[], float[]>(xs.toArray(), ys.toArray());
	}

	//@Immutable
	static class XYPoint implements Comparable<XYPoint> {
		public final double x;
		public final double y;

		public XYPoint(double x, double y) {
			this.x=x;
			this.y=y;
		}

		@Override
		public String toString() {
			return x+","+y;
		}

		public double getX() {
			return x;
		}

		public double getY() {
			return y;
		}

		/**
		 * compares on X first then on Y
		 */
		@Override
		public int compareTo(XYPoint o) {
			if (o==null) return 1;
			if (x>o.getX()) return 1;
			if (x<o.getX()) return -1;
			if (y>o.getY()) return 1;
			if (y<o.getY()) return -1;
			return 0;
		}

		@Override
		public int hashCode() {
			return Double.hashCode(x)+Double.hashCode(y);
		}

		@Override
		public boolean equals(Object obj) {
			return compareTo((XYPoint)obj)==0;
		}
	}

	public int compareTo(XYTraceInterface o) {
		if (o==null) return 1;
		return name.compareTo(o.getName());
	}

	public double getMaxY() {
		XYPoint maxXYInRange=getMaxXYInRange(new Range(-Double.MAX_VALUE, Double.MAX_VALUE));
		if (maxXYInRange==null) return 0.0;
		return maxXYInRange.y;
	}

	public XYPoint getMaxXYInRange(Range xrange) {
		XYPoint max=null;
		for (XYPoint xy : points) {
			if (xrange.contains(xy.getX())) {
				if (max==null||xy.y>max.y) {
					max=xy;
				}
			}
		}
		return max;
	}

	public static double getMaxY(XYTraceInterface[] traces) {
		double max=-Double.MAX_VALUE;
		for (XYTraceInterface xyTrace : traces) {
			double newMax=xyTrace.getMaxY();
			if (newMax>max) {
				max=newMax;
			}
		}
		return max;
	}
}
