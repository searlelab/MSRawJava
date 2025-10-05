package org.searlelab.msrawjava.io.tims;

import java.nio.file.Path;

import org.searlelab.msrawjava.model.SpectrumRecord;

public final class TimsReader implements AutoCloseable {
    private final long datasetHandle;

    private TimsReader(long datasetHandle) {
        if (datasetHandle == 0) throw new IllegalStateException("dataset handle is 0");
        this.datasetHandle = datasetHandle;
    }

    public static TimsReader open(Path dPath) {
        long h = TimsNative.openDataset(dPath.toString());
        return new TimsReader(h);
    }

    public RustIterator createIterator(int[] frameIdsSortedByRt,
                                       double mzLo, double mzHi,
                                       int scanLoInclusive, int scanHiInclusive) {
        long it = TimsNative.createIterator(datasetHandle, frameIdsSortedByRt, mzLo, mzHi,
                                            scanLoInclusive, scanHiInclusive);
        return new RustIterator(it);
    }
    
	public SpectrumRecord readSpectrum(int frameId, double mzLo, double mzHi, int scanLoInclusive, int scanHiInclusive) {
		Object res = TimsNative.readSpectrum(this.datasetHandle, frameId, mzLo, mzHi, scanLoInclusive, scanHiInclusive);
		if (res == null) return null;
		Object[] arr = (Object[]) res;
		double[] mz = (double[]) arr[0];
		float[] ims = (float[]) arr[1];
		float[] intensity = (float[]) arr[2];
		int msLevel = (Integer) arr[3];
		int frameIndex = (Integer) arr[4];
		double rtSeconds = (Double) arr[5];
		return new SpectrumRecord(mz, ims, intensity, msLevel, frameIndex, rtSeconds);
	}

    @Override
    public void close() {
        TimsNative.closeDataset(datasetHandle);
    }
}
