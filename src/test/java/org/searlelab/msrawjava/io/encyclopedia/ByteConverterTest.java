package org.searlelab.msrawjava.io.encyclopedia;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

class ByteConverterTest {

	@Test
	void toByteArray_float_roundTrip() {
		float[] src=new float[] {0.0f, 1.0f, -2.5f, 123456.75f};
		byte[] bytes=ByteConverter.toByteArray(src);

		assertNotNull(bytes, "byte[] should not be null");
		assertEquals(src.length*Float.BYTES, bytes.length, "byte[] length must be 4x float count");

		// Decode without using ByteConverter to keep test independent
		float[] decoded=new float[src.length];
		ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).asFloatBuffer().get(decoded);
		assertArrayEquals(src, decoded, 0.0f, "round-trip floats should match exactly for these values");
	}

	@Test
	void toByteArray_float_empty() {
		float[] src=new float[0];
		byte[] bytes=ByteConverter.toByteArray(src);
		assertNotNull(bytes);
		assertEquals(0, bytes.length);
	}

	@Test
	void toByteArray_float_specials() {
		float[] src=new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -0.0f};
		byte[] bytes=ByteConverter.toByteArray(src);

		float[] decoded=new float[src.length];
		ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).asFloatBuffer().get(decoded);

		assertTrue(Float.isNaN(decoded[0]), "NaN should round-trip as NaN");
		assertEquals(Float.POSITIVE_INFINITY, decoded[1]);
		assertEquals(Float.NEGATIVE_INFINITY, decoded[2]);
		// -0.0f equals 0.0f by ==, but preserves sign bit in IEEE 754; verify sign bit via raw int
		int raw=ByteBuffer.wrap(bytes, 3*Float.BYTES, Float.BYTES).order(ByteOrder.BIG_ENDIAN).getInt();
		assertEquals(0x80000000, raw, "Negative zero should preserve sign bit (0x80000000).");
	}

	@Test
	void toByteArray_float_endianness_bigEndian() {
		float[] src=new float[] {1.0f};
		byte[] bytes=ByteConverter.toByteArray(src);
		// 1.0f in IEEE-754 big-endian: 0x3F 0x80 0x00 0x00
		byte[] expected=new byte[] {0x3F, (byte)0x80, 0x00, 0x00};
		assertArrayEquals(expected, Arrays.copyOfRange(bytes, 0, 4), "Encoding should be big-endian IEEE-754");
	}

	@Test
	void toBooleanArray_basicMapping() {
		byte[] bytes=new byte[] {0, 1, -1, 2, 0};
		boolean[] flags=ByteConverter.toBooleanArray(bytes);
		assertArrayEquals(new boolean[] {false, true, false, true, false}, flags);
	}

	@Test
	void toBooleanArray_empty() {
		byte[] bytes=new byte[0];
		boolean[] flags=ByteConverter.toBooleanArray(bytes);
		assertNotNull(flags);
		assertEquals(0, flags.length);
	}

	@Test
	void toFloatArray_fromBytes_defaultOrder_bigEndianRoundTrip() {
		float[] src=new float[] {0.0f, 1.0f, -2.5f, 123456.75f};
		byte[] bytes=new byte[src.length*Float.BYTES];
		ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).asFloatBuffer().put(src);

		float[] decoded=ByteConverter.toFloatArray(bytes);
		assertArrayEquals(src, decoded, 0.0f);
	}

	@Test
	void toFloatArray_fromBytes_littleEndian_override() {
		float[] src=new float[] {-1.5f, 2.25f, 3.5f};
		byte[] bytes=new byte[src.length*Float.BYTES];
		ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().put(src);

		float[] decoded=ByteConverter.toFloatArray(bytes, ByteOrder.LITTLE_ENDIAN);
		assertArrayEquals(src, decoded, 0.0f);
	}

	@Test
	void toDoubleArray_fromBytes_defaultOrder_bigEndianRoundTrip() {
		double[] src=new double[] {0.0, 1.0, -2.5, 123456.75};
		byte[] bytes=new byte[src.length*Double.BYTES];
		ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).asDoubleBuffer().put(src);

		double[] decoded=ByteConverter.toDoubleArray(bytes);
		assertArrayEquals(src, decoded, 0.0);
	}

	@Test
	void toDoubleArray_fromBytes_littleEndian_override() {
		double[] src=new double[] {-1.5, 2.25, 3.5};
		byte[] bytes=new byte[src.length*Double.BYTES];
		ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asDoubleBuffer().put(src);

		double[] decoded=ByteConverter.toDoubleArray(bytes, ByteOrder.LITTLE_ENDIAN);
		assertArrayEquals(src, decoded, 0.0);
	}

	@Test
	void toFloatArray_fromBytes_empty_and_toDoubleArray_empty() {
		float[] f=ByteConverter.toFloatArray(new byte[0]);
		double[] d=ByteConverter.toDoubleArray(new byte[0]);
		assertEquals(0, f.length);
		assertEquals(0, d.length);
	}

	@Test
	void toFloatArray_fromNumberArray_mixedTypes() {
		Number[] src=new Number[] {1, 2.5f, 3.25d, -4L};
		float[] out=ByteConverter.toFloatArray(src);
		assertArrayEquals(new float[] {1f, 2.5f, 3.25f, -4f}, out, 0.0f);
	}

	@Test
	void toDoubleArray_fromNumberArray_mixedTypes() {
		Number[] src=new Number[] {1, 2.5f, 3.25d, -4L};
		double[] out=ByteConverter.toDoubleArray(src);
		assertArrayEquals(new double[] {1.0, 2.5, 3.25, -4.0}, out, 0.0);
	}

	@Test
	void toByteArray_boolean_basicMapping_andRoundTripThroughToBooleanArray() {
		boolean[] flags=new boolean[] {true, false, true, true, false};
		byte[] b=ByteConverter.toByteArray(flags);
		assertArrayEquals(new byte[] {1, 0, 1, 1, 0}, b);
		assertArrayEquals(flags, ByteConverter.toBooleanArray(b));
	}

	@Test
	void toByteArray_boolean_emptyProducesEmptyBytes() {
		byte[] out=ByteConverter.toByteArray(new boolean[0]);
		assertNotNull(out);
		assertEquals(0, out.length);
	}

}
