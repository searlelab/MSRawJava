package org.searlelab.msrawjava.io.mzml;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;

import org.junit.jupiter.api.Test;

class MzmlBinaryUtilsTest {

	@Test
	void privateConstructorCoveredViaReflection() throws Exception {
		Constructor<MzmlBinaryUtils> ctor=MzmlBinaryUtils.class.getDeclaredConstructor();
		ctor.setAccessible(true);
		MzmlBinaryUtils instance=ctor.newInstance();
		assertEquals(MzmlBinaryUtils.class, instance.getClass());
	}

	@Test
	void base64EncodeDecodeHandlesWhitespace() {
		byte[] input="hello-mzml".getBytes(StandardCharsets.UTF_8);
		String encoded=MzmlBinaryUtils.encodeBase64(input);
		byte[] decoded=MzmlBinaryUtils.decodeBase64(" \n  "+encoded+"\t\n");
		assertArrayEquals(input, decoded);
	}

	@Test
	void convertsDoublesAndFloatsRoundTrip() {
		double[] doubles= {100.25, 200.5, 300.75};
		float[] floats= {1.5f, 2.5f, 3.5f};

		byte[] doubleBytes=MzmlBinaryUtils.doublesToBytes(doubles, doubles.length);
		byte[] floatBytes=MzmlBinaryUtils.floatsToBytes(floats, floats.length);

		assertArrayEquals(doubles, MzmlBinaryUtils.bytesToDoubles(doubleBytes), 1e-10);
		assertArrayEquals(floats, MzmlBinaryUtils.bytesToFloats(floatBytes), 1e-6f);
	}

	@Test
	void convertsFloatBytesToDoublesAndDoubleBytesToFloats() {
		float[] floats= {10.0f, 20.5f, 30.25f};
		double[] doubles= {11.0, 22.0, 33.0};

		byte[] floatBytes=MzmlBinaryUtils.floatsToBytes(floats, floats.length);
		byte[] doubleBytes=MzmlBinaryUtils.doublesToBytes(doubles, doubles.length);

		assertArrayEquals(new double[] {10.0, 20.5, 30.25}, MzmlBinaryUtils.floatBytesToDoubles(floatBytes), 1e-8);
		assertArrayEquals(new float[] {11.0f, 22.0f, 33.0f}, MzmlBinaryUtils.doubleBytesToFloats(doubleBytes), 1e-6f);
	}

	@Test
	void zlibDecompressRoundTrip() throws Exception {
		byte[] raw="compressed-data-for-mzml".getBytes(StandardCharsets.UTF_8);
		byte[] compressed=zlib(raw);
		assertArrayEquals(raw, MzmlBinaryUtils.zlibDecompress(compressed));
	}

	@Test
	void zlibDecompressThrowsOnInvalidData() {
		IOException ex=assertThrows(IOException.class, () -> MzmlBinaryUtils.zlibDecompress(new byte[] {1, 2, 3, 4, 5}));
		assertTrue(ex.getMessage().contains("Error decompressing zlib data"));
	}

	private static byte[] zlib(byte[] input) {
		Deflater deflater=new Deflater();
		deflater.setInput(input);
		deflater.finish();
		byte[] out=new byte[input.length*2+64];
		int len=deflater.deflate(out);
		deflater.end();
		byte[] trimmed=new byte[len];
		System.arraycopy(out, 0, trimmed, 0, len);
		return trimmed;
	}
}
