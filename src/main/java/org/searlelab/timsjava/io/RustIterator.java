package org.searlelab.timsjava.io;

import org.searlelab.timsjava.model.SpectrumRecord;

public final class RustIterator implements AutoCloseable {
    private final long iteratorHandle;

    RustIterator(long iteratorHandle) {
        if (iteratorHandle == 0) throw new IllegalStateException("native iterator handle is 0");
        this.iteratorHandle = iteratorHandle;
    }

    public SpectrumRecord next() {
        Object res = TimsNative.next(iteratorHandle);
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
        TimsNative.destroyIterator(iteratorHandle);
    }
}
