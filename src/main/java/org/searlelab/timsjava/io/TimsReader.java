package org.searlelab.timsjava.io;

import java.nio.file.Path;

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

    @Override
    public void close() {
        TimsNative.closeDataset(datasetHandle);
    }
}
