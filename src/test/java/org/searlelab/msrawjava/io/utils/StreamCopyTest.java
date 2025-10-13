package org.searlelab.msrawjava.io.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

class StreamCopyTest {

	@Test
	public void testStreamReplace_handlesSimpleAndBoundarySplit() throws Exception {
		final String needle="SPECTRUM_LIST_COUNT_PLACEHOLDER";
		final String replacement="42";

		final int splitPrefix=StreamCopy.BUFFER_SIZE-(needle.length()/2); // ensure the needle straddles boundary

		StringBuilder sb=new StringBuilder(StreamCopy.BUFFER_SIZE*2);
		sb.append("header-");
		sb.append(needle);
		sb.append("-middle-");

		char[] fill=new char[splitPrefix];
		Arrays.fill(fill, 'X');
		sb.append(fill);

		// this occurrence should be split across the buffer boundary inside the method
		sb.append(needle);
		sb.append("-tail");

		String input=sb.toString();

		// Expected output: all occurrences replaced
		String expected=input.replace(needle, replacement);

		Path tempDir=Files.createTempDirectory("streamReplaceTest");
		Path src=tempDir.resolve("input.txt");
		Path dst=tempDir.resolve("output.txt");

		Files.writeString(src, input, StandardCharsets.UTF_8);

		StreamCopy.streamReplace(src, dst, needle, replacement);

		assertTrue(Files.exists(dst), "Destination file should exist");
		String actual=Files.readString(dst, StandardCharsets.UTF_8);

		assertEquals(expected, actual, "All occurrences (including split across buffer) must be replaced");

		// Cleanup
		Files.deleteIfExists(src);
		Files.deleteIfExists(dst);
		Files.deleteIfExists(tempDir);
	}
}
