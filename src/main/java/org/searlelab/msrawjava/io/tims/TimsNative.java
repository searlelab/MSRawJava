package org.searlelab.msrawjava.io.tims;

/**
 * JNI façade for the Rust iterator.
 * Returned value from next(): Object[] of length 6
 * [0]=double[] mz, [1]=float[] ims, [2]=float[] intensity,
 * [3]=Integer msLevel, [4]=Integer frameIndex, [5]=Double rtSeconds
 */
public final class TimsNative {
    static {
        NativeLibraryLoader.load();
    }

    private TimsNative() {}

    // dataset lifecycle
    public static native long openDataset(String pathToD);
    public static native void closeDataset(long handle);

	// iterator
	public static native long createIterator(long handle, int[] frameIndices, double mzLo, double mzHi, int scanLo,
			int scanHi);
    public static native Object next(long iteratorHandle);   // returns null when exhausted
    public static native void destroyIterator(long iteratorHandle);
    
	public static native Object readSpectrum(long handle, int frameId, double mzLo, double mzHi, int scanLo,
			int scanHi);
}
