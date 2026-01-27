package org.searlelab.msrawjava.io.tims;

/**
 * TimsNative declares the JNI surface used by the Java layer to call into the native (Rust) implementation for Bruker
 * timsTOF extraction.
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
