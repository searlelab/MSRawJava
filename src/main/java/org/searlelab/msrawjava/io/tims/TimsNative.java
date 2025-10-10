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

	private TimsNative() {
	}

	// dataset lifecycle
	public static native long openDataset(String pathToD);

	public static native void closeDataset(long handle);
	
	public static native Object readRawFrame(long handle, int frameIndex);

	public static native Object[] readRawFrameRange(long handle, int frameIndex, int scanLoInclusive, int scanHiInclusive);
	
    public static native Object[] readRawFrameTofIntRange(long handle, int frameIndex, int scanLoInclusive, int scanHiInclusive);

}
