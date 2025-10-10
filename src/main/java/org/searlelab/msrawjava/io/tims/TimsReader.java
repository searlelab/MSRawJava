package org.searlelab.msrawjava.io.tims;

import java.nio.file.Path;
import java.util.Optional;

import org.searlelab.msrawjava.exceptions.TdfFormatException;
import org.searlelab.msrawjava.io.utils.Triplet;

public final class TimsReader implements AutoCloseable {
	private final long datasetHandle;
	private final Optional<MzCalibrationParams> params;

	private TimsReader(long datasetHandle, Optional<MzCalibrationParams> params) {
		if (datasetHandle==0) throw new IllegalStateException("dataset handle is 0");
		this.datasetHandle=datasetHandle;
		this.params=params;
	}

	public static TimsReader open(Path dPath, Optional<MzCalibrationParams> params) {
		long h=TimsNative.openDataset(dPath.toString());
		return new TimsReader(h, params);
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
	
	public Triplet<double[], float[], int[]> readRawFrameAndCalibrate(int frameId, int scanLoInclusive, int scanHiInclusive, double realT1) {
		if (params.isEmpty()) {
			return readFrameWithRange(frameId, scanLoInclusive, scanHiInclusive);
		}
		
		try {
			Object res=TimsNative.readRawFrameTofIntRange(this.datasetHandle, frameId, scanLoInclusive, scanHiInclusive);
			
			if (res==null) return null;
			Object[] raw=(Object[])res;

			int[] tofRaw = (int[]) raw[0];
			int[] intensRaw = (int[]) raw[1];
			int[] scan = (int[]) raw[2];
			
			double[] mz = MzCalibrationPoly.tofToMz(tofRaw, params.get(), realT1);
	
			float[] intensityArrayFloat=new float[intensRaw.length];
			for (int i=0; i<intensRaw.length; i++) {
				intensityArrayFloat[i]=(float)intensRaw[i];
			}
		
			return new Triplet<double[], float[], int[]>(mz, intensityArrayFloat, scan);
		} catch (TdfFormatException tdf) {
			return null;
		}
	}

	@Override
	public void close() {
		TimsNative.closeDataset(datasetHandle);
	}
}
