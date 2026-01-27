package org.searlelab.msrawjava.io.tims;

import java.nio.file.Path;
import java.util.Optional;

import org.searlelab.msrawjava.exceptions.TdfFormatException;
import org.searlelab.msrawjava.io.utils.Triplet;

/**
 * TimsReader provides lower-level helpers for Bruker timsTOF ingestion: it mediates access to per-frame/per-scan
 * content, applies calibration and intensity corrections, and transforms native buffers into the project’s model
 * objects. Positioned beneath BrukerTIMSFile, it concentrates error translation and data shaping so the higher-level
 * reader can focus on run-level coordination and public API consistency.
 */
public final class TimsReader implements AutoCloseable {
	private final long datasetHandle;
	private final Optional<MzCalibrator> calibrator;

	private TimsReader(long datasetHandle, Optional<MzCalibrator> calibrator) {
		if (datasetHandle==0) throw new IllegalStateException("dataset handle is 0");
		this.datasetHandle=datasetHandle;
		this.calibrator=calibrator;
	}

	public long getDatasetHandle() {
		return datasetHandle;
	}

	public static TimsReader open(Path dPath, Optional<MzCalibrator> calibrator) {
		long h=TimsNative.openDataset(dPath.toString());
		return new TimsReader(h, calibrator);
	}

	public Triplet<double[], float[], int[]> readFrame(int frameId) {
		Object res=TimsNative.readRawFrame(this.datasetHandle, frameId);
		if (res==null) return null;
		Object[] arr=(Object[])res;

		double[] intensityArrayDouble=(double[])arr[1];
		//double[] imsArrayDouble=(double[])arr[2];
		float[] intensityArrayFloat=new float[intensityArrayDouble.length];
		//float[] imsArrayFloat=new float[imsArrayDouble.length];
		for (int i=0; i<intensityArrayDouble.length; i++) {
			intensityArrayFloat[i]=(float)intensityArrayDouble[i];
			//imsArrayFloat[i]=(float)imsArrayDouble[i];
		}

		return new Triplet<double[], float[], int[]>((double[])arr[0], intensityArrayFloat, (int[])arr[2]);
	}

	public Triplet<double[], float[], int[]> readFrameWithRange(int frameId, int scanLoInclusive, int scanHiInclusive) {
		try {
			Object res=TimsNative.readRawFrameRange(this.datasetHandle, frameId, scanLoInclusive, scanHiInclusive);

			if (res==null) return null;
			Object[] arr=(Object[])res;

			double[] intensityArrayDouble=(double[])arr[1];
			//double[] imsArrayDouble=(double[])arr[2];
			float[] intensityArrayFloat=new float[intensityArrayDouble.length];
			//float[] imsArrayFloat=new float[imsArrayDouble.length];
			for (int i=0; i<intensityArrayDouble.length; i++) {
				intensityArrayFloat[i]=(float)intensityArrayDouble[i];
				//imsArrayFloat[i]=(float)imsArrayDouble[i];
			}

			return new Triplet<double[], float[], int[]>((double[])arr[0], intensityArrayFloat, (int[])arr[2]);
		} catch (TdfFormatException tdf) {
			return null;
		}
	}

	public Triplet<double[], float[], int[]> readRawFrameAndCalibrate(int frameId, int scanLoInclusive, int scanHiInclusive, double realT1) {
		if (calibrator.isEmpty()) {
			return readFrameWithRange(frameId, scanLoInclusive, scanHiInclusive);
		}

		try {
			Object res=TimsNative.readRawFrameTofIntRange(this.datasetHandle, frameId, scanLoInclusive, scanHiInclusive);

			if (res==null) return null;
			Object[] raw=(Object[])res;

			int[] tofRaw=(int[])raw[0];
			int[] intensRaw=(int[])raw[1];
			int[] scan=(int[])raw[2];

			double[] mz=calibrator.get().tofToMz(tofRaw, realT1);

			float[] intensityArrayFloat=new float[intensRaw.length];
			for (int i=0; i<intensRaw.length; i++) {
				intensityArrayFloat[i]=(float)intensRaw[i];
			}

			return new Triplet<double[], float[], int[]>(mz, intensityArrayFloat, scan);
		} catch (TdfFormatException tdf) {
			return null;
		}
	}

	/**
	 * useful for converting precursor m/zs stored in the TDF into their corrected m/zs
	 * 
	 * @param uncorrectedMz
	 * @param realT1
	 * @return
	 */
	public double calibrateMz(double uncorrectedMz, double realT1) {
		if (calibrator.isEmpty()) return uncorrectedMz;

		double[] a=new double[] {uncorrectedMz};
		double[] r=calibrator.get().uncorrectedMzToMz(a, realT1);
		return r[0];
	}

	/**
	 * useful for converting precursor m/zs stored in the TDF into their corrected m/zs, assuming the global T1
	 * (necessary for keeping values consistent across time, e.g., precursor isolation windows).
	 * 
	 * @param uncorrectedMz
	 * @return
	 */
	public double calibrateMz(double uncorrectedMz) {
		if (calibrator.isEmpty()) return uncorrectedMz;

		double[] a=new double[] {uncorrectedMz};
		MzCalibrator mzCalibrator=calibrator.get();
		double[] r=mzCalibrator.uncorrectedMzToMz(a, mzCalibrator.getGlobalT1());
		return r[0];
	}

	/**
	 * useful for converting real m/zs (calculated from peptides) into what the instrument would have measured, assuming
	 * the global T1 (necessary for keeping values consistent across time, e.g., precursor isolation windows).
	 * 
	 * @param correctedMz
	 * @return
	 */
	public double uncalibrateMz(double correctedMz) {
		if (calibrator.isEmpty()) return correctedMz;

		double[] a=new double[] {correctedMz};
		MzCalibrator mzCalibrator=calibrator.get();
		int[] tofIndexes=mzCalibrator.mzToTof(a, mzCalibrator.getGlobalT1());
		double[] r=mzCalibrator.getLinear().tofToMz(tofIndexes, mzCalibrator.getGlobalT1());

		return r[0];
	}

	@Override
	public void close() {
		TimsNative.closeDataset(datasetHandle);
	}
}
