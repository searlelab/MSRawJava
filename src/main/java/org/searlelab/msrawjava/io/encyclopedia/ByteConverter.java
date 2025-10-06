package org.searlelab.msrawjava.io.encyclopedia;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;

public class ByteConverter {
	private static ByteOrder order=ByteOrder.BIG_ENDIAN; // usual for java

	public static byte[] toByteArray(float[] f) {
		return toByteArray(f, order);
	}

	public static byte[] toByteArray(float[] f, ByteOrder order) {
		byte[] b=new byte[f.length*4];
		ByteBuffer bb=ByteBuffer.wrap(b);
		if (order!=null) bb.order(order);
		FloatBuffer fb=bb.asFloatBuffer();
		fb.put(f);
		return b;
	}

	public static float[] toFloatArray(byte[] b) {
		return toFloatArray(b, order);
	}

	public static float[] toFloatArray(byte[] b, ByteOrder order) {
		float[] f=new float[b.length/4];
		ByteBuffer bb=ByteBuffer.wrap(b);
		if (order!=null) bb.order(order);
		FloatBuffer fb=bb.asFloatBuffer();
		fb.get(f);
		return f;
	}

	public static byte[] toByteArray(double[] d) {
		return toByteArray(d, order);
	}

	public static byte[] toByteArray(double[] d, ByteOrder order) {
		byte[] b=new byte[d.length*8];
		ByteBuffer bb=ByteBuffer.wrap(b);
		if (order!=null) bb.order(order);
		DoubleBuffer db=bb.asDoubleBuffer();
		db.put(d);
		return b;
	}

	public static double[] toDoubleArray(byte[] b) {
		return toDoubleArray(b, order);
	}

	public static double[] toDoubleArray(byte[] b, ByteOrder order) {
		double[] d=new double[b.length/8];
		ByteBuffer bb=ByteBuffer.wrap(b);
		if (order!=null) bb.order(order);
		DoubleBuffer db=bb.asDoubleBuffer();
		db.get(d);
		return d;
	}

	public static float[] toFloatArray(Number[] n) {
		float[] f=new float[n.length];
		for (int i=0; i<f.length; i++) {
			f[i]=n[i].floatValue();
		}
		return f;
	}

	public static double[] toDoubleArray(Number[] n) {
		double[] d=new double[n.length];
		for (int i=0; i<d.length; i++) {
			d[i]=n[i].doubleValue();
		}
		return d;
	}

	// wasteful boolean to byte array (assumes short arrays)
	public static byte[] toByteArray(boolean[] a) {
		byte[] b=new byte[a.length];
		for (int i=0; i<a.length; i++) {
			if (a[i]) {
				b[i]=1;
			} else {
				b[i]=0;
			}
		}
		return b;
	}

	// wasteful boolean to byte array (assumes short arrays)
	public static boolean[] toBooleanArray(byte[] b) {
		boolean[] a=new boolean[b.length];
		for (int i=0; i<b.length; i++) {
			a[i]=b[i]>0;
		}
		return a;
	}
}
