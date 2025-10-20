package org.searlelab.msrawjava.io.encyclopedia;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.zip.DataFormatException;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;

class CompressionUtilsTest {

	@Test
	void compressDecompress_roundTrip_small() throws IOException {
		// might as well test non-ascii as well
		byte[] original="hello compression 👋".getBytes(StandardCharsets.UTF_8);
		byte[] zipped=CompressionUtils.compress(original);
		assertNotNull(zipped);
		assertTrue(zipped.length>0);

		byte[] roundTrip=CompressionUtils.decompress(zipped);
		assertArrayEquals(original, roundTrip);
	}

	@Test
	void compressDecompress_roundTrip_empty() throws IOException {
		byte[] original=new byte[0];
		byte[] zipped=CompressionUtils.compress(original);
		assertNotNull(zipped);

		byte[] roundTrip=CompressionUtils.decompress(zipped);
		assertArrayEquals(original, roundTrip);
	}

	@Test
	void compressDecompress_roundTrip_largeMultiChunk() throws IOException {
		// 100k deterministic random bytes to ensure multi-chunk deflate/inflate behavior
		byte[] original=new byte[100000];
		Random r=new Random(42);
		r.nextBytes(original);

		// zero out 10k to 30k of 100k
		for (int i=10000; i<30000; i++) {
			original[i]=0; // make sure some part of it is meaningfully compressible
		}

		byte[] zipped=CompressionUtils.compress(original);
		assertTrue(zipped.length<original.length);
		assertEquals(0.80f, (zipped.length/(float)original.length), 0.1f); // we zeroed 20% of the data

		byte[] roundTrip=CompressionUtils.decompress(zipped);
		assertArrayEquals(original, roundTrip);
	}

	@Test
	void decompress_withExplicitLength_matches() throws IOException, DataFormatException {
		byte[] original="length-bound inflate".getBytes(StandardCharsets.UTF_8);
		byte[] zipped=CompressionUtils.compress(original);

		byte[] out=CompressionUtils.decompress(zipped, original.length);
		assertArrayEquals(original, out);
	}

	@Test
	void decompress_withExplicitLength_badData_throwsDataFormat() {
		byte[] garbage=new byte[] {1, 2, 3, 4, 5, 6, 7, 8};
		assertThrows(DataFormatException.class, () -> CompressionUtils.decompress(garbage, 32));
	}

	@Test
	void decompress_badData_throwsIllegalState() {
		byte[] garbage=new byte[] {9, 8, 7, 6, 5};
		IllegalStateException ex=assertThrows(IllegalStateException.class, () -> CompressionUtils.decompress(garbage));
		assertTrue(ex.getMessage().toLowerCase().contains("format"), "message should mention formatting error");
	}

	@Test
	void decompressGzip_roundTrip() throws IOException {
		byte[] original="Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum."
				.getBytes(StandardCharsets.UTF_8);

		ByteArrayOutputStream bout=new ByteArrayOutputStream();
		try (GZIPOutputStream gout=new GZIPOutputStream(bout)) {
			gout.write(original);
		}
		byte[] gz=bout.toByteArray();
		assertTrue(gz.length<original.length);

		byte[] out=CompressionUtils.decompressGzip(gz, original.length);
		assertArrayEquals(original, out);
	}
}
