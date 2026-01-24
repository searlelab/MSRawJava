package org.searlelab.msrawjava.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MZMLOutputFileEdgeTest {

	@TempDir
	Path tmp;

	@Test
	void helperMethods_coverEscapeCvNameGuessAndSha1() throws Exception {
		Method escape=method("escapeXml", String.class);
		assertEquals("a&amp;b&lt;c&gt;&quot;d", escape.invoke(null, "a&b<c>\"d"));

		Method cvName=method("cvName", String.class);
		assertEquals("Thermo RAW format", cvName.invoke(null, "MS:1000563"));
		assertEquals("mzML format", cvName.invoke(null, "MS:1000584"));
		assertEquals("Bruker/Agilent YEP format", cvName.invoke(null, "MS:1000567"));
		assertEquals("time-of-flight mass analyzer", cvName.invoke(null, "MS:1000442"));
		assertEquals("orbitrap", cvName.invoke(null, "MS:1000484"));
		assertEquals("Bruker TDF format", cvName.invoke(null, "MS:1002817"));
		assertEquals("Bruker TDF nativeID format", cvName.invoke(null, "MS:1002818"));
		assertEquals("beam-type collision-induced dissociation", cvName.invoke(null, "MS:1000422"));
		assertEquals("MS:999999", cvName.invoke(null, "MS:999999"));

		Method guess=method("guessFileFormatAccession", String.class, Map.class);
		assertEquals("MS:1000563", guess.invoke(null, "sample.RAW", Map.of()));
		assertEquals("MS:1000584", guess.invoke(null, "sample.mzML", Map.of()));
		assertEquals("MS:1000567", guess.invoke(null, "sample.yep", Map.of()));
		assertEquals("MS:1002817", guess.invoke(null, "sample.d", Map.of()));
		assertEquals("MS:1002817", guess.invoke(null, "sample.tdf", Map.of()));
		assertEquals("MS:1000584", guess.invoke(null, "sample.unknown", Map.of()));

		Method sha1=method("computeSHA1Safe", String.class);
		assertNull(sha1.invoke(null, (Object)null));

		Path file=tmp.resolve("tiny.txt");
		Files.writeString(file, "abc", StandardCharsets.UTF_8);
		String expected=sha1Hex("abc");
		assertEquals(expected, sha1.invoke(null, file.toString()));
	}

	private static Method method(String name, Class<?>... args) throws Exception {
		Method m=MZMLOutputFile.class.getDeclaredMethod(name, args);
		m.setAccessible(true);
		return m;
	}

	private static String sha1Hex(String content) throws Exception {
		MessageDigest md=MessageDigest.getInstance("SHA-1");
		byte[] d=md.digest(content.getBytes(StandardCharsets.UTF_8));
		StringBuilder sb=new StringBuilder(40);
		for (byte b : d) {
			sb.append(String.format("%02x", b));
		}
		return sb.toString();
	}
}
