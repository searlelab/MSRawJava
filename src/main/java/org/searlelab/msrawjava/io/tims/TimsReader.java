package org.searlelab.msrawjava.io.tims;

import java.nio.file.Path;

import org.searlelab.msrawjava.exceptions.TdfFormatException;
import org.searlelab.msrawjava.io.utils.Triplet;

public final class TimsReader implements AutoCloseable {
	private final long datasetHandle;

	private TimsReader(long datasetHandle) {
		if (datasetHandle==0) throw new IllegalStateException("dataset handle is 0");
		this.datasetHandle=datasetHandle;
	}

	public static TimsReader open(Path dPath) {
		long h=TimsNative.openDataset(dPath.toString());
		return new TimsReader(h);
	}

	public Triplet<double[], double[], int[]> readFrame(int frameId) {
		Object res=TimsNative.readRawFrame(this.datasetHandle, frameId);
		if (res==null) return null;
		Object[] arr=(Object[])res;
		return new Triplet<double[], double[], int[]>((double[])arr[0], (double[])arr[1], (int[])arr[2]);
	}

	public Triplet<double[], float[], int[]> readFrameWithRange(int frameId, int scanLoInclusive, int scanHiInclusive) {
		try {
			Object res=TimsNative.readRawFrameRange(this.datasetHandle, frameId, scanLoInclusive, scanHiInclusive);
			
			if (res==null) return null;
			Object[] arr=(Object[])res;
			
			double[] intensityArrayDouble=(double[])arr[1];
			float[] intensityArrayFloat=new float[intensityArrayDouble.length];
			for (int i=0; i<intensityArrayDouble.length; i++) {
				intensityArrayFloat[i]=(float)intensityArrayDouble[i];
			}
	
			return new Triplet<double[], float[], int[]>((double[])arr[0], intensityArrayFloat, (int[])arr[2]);
		} catch (TdfFormatException tdf) {
			return null;
		}
	}
	
	@Override
	public void close() {
		TimsNative.closeDataset(datasetHandle);
	}
}
