package org.searlelab.msrawjava.io.mzml;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Shared binary encoding/decoding utilities for mzML reading and writing.
 * Handles Base64, little-endian IEEE-754 byte conversion, and zlib (de)compression.
 */
public final class MzmlBinaryUtils {

	private MzmlBinaryUtils() {}

	// ---- Encoding (write path) ----

	/** Encode a byte array to Base64. */
	public static String encodeBase64(byte[] bytes) {
		return Base64.getEncoder().encodeToString(bytes);
	}

	/** Decode a Base64 string to bytes. */
	public static byte[] decodeBase64(String base64) {
		return Base64.getDecoder().decode(base64.strip());
	}

	/** Convert doubles to little-endian 64-bit IEEE-754 bytes. */
	public static byte[] doublesToBytes(double[] a, int n) {
		byte[] out=new byte[n*8];
		for (int i=0; i<n; i++) {
			long bits=Double.doubleToRawLongBits(a[i]);
			int o=i*8;
			out[o]=(byte)(bits);
			out[o+1]=(byte)(bits>>>8);
			out[o+2]=(byte)(bits>>>16);
			out[o+3]=(byte)(bits>>>24);
			out[o+4]=(byte)(bits>>>32);
			out[o+5]=(byte)(bits>>>40);
			out[o+6]=(byte)(bits>>>48);
			out[o+7]=(byte)(bits>>>56);
		}
		return out;
	}

	/** Convert floats to little-endian 32-bit IEEE-754 bytes. */
	public static byte[] floatsToBytes(float[] a, int n) {
		byte[] out=new byte[n*4];
		for (int i=0; i<n; i++) {
			int bits=Float.floatToRawIntBits(a[i]);
			int o=i*4;
			out[o]=(byte)(bits);
			out[o+1]=(byte)(bits>>>8);
			out[o+2]=(byte)(bits>>>16);
			out[o+3]=(byte)(bits>>>24);
		}
		return out;
	}

	// ---- Decoding (read path) ----

	/** Decode little-endian 64-bit bytes to double[]. */
	public static double[] bytesToDoubles(byte[] bytes) {
		ByteBuffer buf=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		double[] result=new double[bytes.length/8];
		for (int i=0; i<result.length; i++) {
			result[i]=buf.getDouble();
		}
		return result;
	}

	/** Decode little-endian 32-bit float bytes to double[] (widening). */
	public static double[] floatBytesToDoubles(byte[] bytes) {
		ByteBuffer buf=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		double[] result=new double[bytes.length/4];
		for (int i=0; i<result.length; i++) {
			result[i]=buf.getFloat();
		}
		return result;
	}

	/** Decode little-endian 32-bit bytes to float[]. */
	public static float[] bytesToFloats(byte[] bytes) {
		ByteBuffer buf=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		float[] result=new float[bytes.length/4];
		for (int i=0; i<result.length; i++) {
			result[i]=buf.getFloat();
		}
		return result;
	}

	/** Decode little-endian 64-bit double bytes to float[] (narrowing). */
	public static float[] doubleBytesToFloats(byte[] bytes) {
		ByteBuffer buf=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		float[] result=new float[bytes.length/8];
		for (int i=0; i<result.length; i++) {
			result[i]=(float)buf.getDouble();
		}
		return result;
	}

	// ---- Compression ----

	/** Decompress zlib-compressed data. */
	public static byte[] zlibDecompress(byte[] compressed) throws IOException {
		Inflater inflater=new Inflater();
		try {
			inflater.setInput(compressed);
			byte[] buffer=new byte[1024];
			ByteArrayOutputStream bos=new ByteArrayOutputStream(compressed.length*2);
			while (!inflater.finished()) {
				try {
					int count=inflater.inflate(buffer);
					if (count==0&&inflater.needsInput()) break;
					bos.write(buffer, 0, count);
				} catch (DataFormatException e) {
					throw new IOException("Error decompressing zlib data: "+e.getMessage(), e);
				}
			}
			return bos.toByteArray();
		} finally {
			inflater.end();
		}
	}
}
