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
}
