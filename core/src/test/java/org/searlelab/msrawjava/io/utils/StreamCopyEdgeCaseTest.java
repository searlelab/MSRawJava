package org.searlelab.msrawjava.io.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StreamCopyEdgeCaseTest {

	@TempDir
	Path tmp;

	@Test
	void streamCopyCopiesExactContent() throws Exception {
		Path src=tmp.resolve("src.txt");
		Path dst=tmp.resolve("dst.txt");
		String content="Line1\nLine2\nLine3";
		Files.writeString(src, content, StandardCharsets.UTF_8);

		StreamCopy.streamCopy(src, dst);

		assertTrue(Files.exists(dst));
		assertEquals(content, Files.readString(dst, StandardCharsets.UTF_8));
	}

	@Test
	void streamReplaceHandlesNullNeedleAndShortInputs() throws Exception {
		Path src=tmp.resolve("src2.txt");
		Path dst=tmp.resolve("dst2.txt");
		String content="short";
		Files.writeString(src, content, StandardCharsets.UTF_8);

		StreamCopy.streamReplace(src, dst, null, "X");
		assertEquals(content, Files.readString(dst, StandardCharsets.UTF_8));

		StreamCopy.streamReplace(src, dst, "longer-needle", "X");
		assertEquals(content, Files.readString(dst, StandardCharsets.UTF_8));
	}

	@Test
	void streamReplaceHandlesOverlappingMatches() throws Exception {
		Path src=tmp.resolve("src3.txt");
		Path dst=tmp.resolve("dst3.txt");
		Files.writeString(src, "aaaa", StandardCharsets.UTF_8);

		StreamCopy.streamReplace(src, dst, "aa", "b");

		assertEquals("bb", Files.readString(dst, StandardCharsets.UTF_8));
	}
}
