package org.searlelab.msrawjava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.logging.LogRecorder;
import org.searlelab.msrawjava.logging.Logger;

class VersionParsingTest {

	private PrintStream originalOut;
	private ByteArrayOutputStream outBytes;

	@BeforeEach
	void setUp() {
		resetLogger();
		originalOut=System.out;
		outBytes=new ByteArrayOutputStream();
		System.setOut(new PrintStream(outBytes, true, StandardCharsets.UTF_8));
	}

	@AfterEach
	void tearDown() {
		System.setOut(originalOut);
		resetLogger();
	}

	@Test
	void parsesSnapshotAndVPrefix() {
		Version v1=new Version("v1.2.3");
		assertEquals("v1.2.3", v1.toString());

		Version v2=new Version("v1.2.3-SNAPSHOT");
		assertEquals("1.2.3-SNAPSHOT", v2.toString(), "Snapshot should not keep the v prefix");

		Version v3=new Version("1.2");
		assertEquals("1.2.0", v3.toString());
	}

	@Test
	void nullVersionDefaultsToSnapshotZero() {
		Version v=new Version((String)null);
		assertEquals("0.0.0-SNAPSHOT", v.toString());
	}

	@Test
	void parsesThreePartVersionWithDoubleDigitMinor() {
		Version v=new Version("26.1.30");
		assertEquals("26.1.30", v.toString());
	}

	@Test
	void invalidRevisionLogsAndUsesMinusOne() {
		Version v=new Version("1.2.beta");
		assertEquals("1.2.-1", v.toString());
		String out=outBytes.toString(StandardCharsets.UTF_8);
		assertTrue(out.contains("Unexpected revision beta"));
	}

	@Test
	void comparisonAndEqualityBehaveAsExpected() {
		Version a=new Version("1.2.3");
		Version b=new Version("1.2.4");
		Version c=new Version("1.2.3");

		assertTrue(b.amIAbove(a));
		assertFalse(a.amIAbove(b));
		assertEquals(0, a.compareTo(c));
		assertEquals(a, c);
		assertEquals(a.hashCode(), c.hashCode());
		assertEquals(1, a.compareTo(null));
	}

	@Test
	void versionProviderReturnsNonEmptyString() {
		Main.VersionProvider provider=new Main.VersionProvider();
		String[] version=provider.getVersion();
		assertNotNull(version);
		assertEquals(4, version.length);
		for (String line : version) {
			assertNotNull(line);
			assertTrue(!line.isEmpty());
		}
	}

	private static void resetLogger() {
		Logger.PRINT_TO_SCREEN=true;
		Logger.PRINT_TO_STDOUT=true;
		Logger.PRINT_TO_STDERR=true;
		Logger.setConsoleStatus(null);
		try {
			Field recordersField=Logger.class.getDeclaredField("recorders");
			recordersField.setAccessible(true);
			@SuppressWarnings("unchecked")
			List<LogRecorder> recorders=(List<LogRecorder>)recordersField.get(null);
			recorders.clear();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Failed to reset Logger recorders", e);
		}
	}
}
