package org.searlelab.msrawjava.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VendorFileTest {

	@Test
	void matchesNameAndDisplayName_areConsistent() {
		assertTrue(VendorFile.THERMO.matchesName("sample.RAW"));
		assertTrue(VendorFile.BRUKER.matchesName("bundle.d"));
		assertTrue(VendorFile.ENCYCLOPEDIA.matchesName("file.dia"));
		assertFalse(VendorFile.THERMO.matchesName(null));
		assertFalse(VendorFile.ENCYCLOPEDIA.matchesName("file.raw"));

		assertEquals("Thermo .raw", VendorFile.THERMO.getDisplayName());
		assertEquals("Bruker .d", VendorFile.BRUKER.getDisplayName());
		assertEquals("EncyclopeDIA .dia", VendorFile.ENCYCLOPEDIA.getDisplayName());
	}

	@Test
	void matchesPath_andFromPath_respectBundleTypes(@TempDir Path tempDir) throws Exception {
		Path raw=tempDir.resolve("sample.raw");
		Files.writeString(raw, "x");
		Path dia=tempDir.resolve("sample.dia");
		Files.writeString(dia, "x");
		Path dDir=tempDir.resolve("bundle.d");
		Files.createDirectories(dDir);

		assertTrue(VendorFile.THERMO.matchesPath(raw));
		assertTrue(VendorFile.ENCYCLOPEDIA.matchesPath(dia));
		assertTrue(VendorFile.BRUKER.matchesPath(dDir));
		assertFalse(VendorFile.BRUKER.matchesPath(raw));
		assertFalse(VendorFile.THERMO.matchesPath(dDir));

		assertEquals(VendorFile.THERMO, VendorFile.fromPath(raw).orElseThrow());
		assertEquals(VendorFile.ENCYCLOPEDIA, VendorFile.fromPath(dia).orElseThrow());
		assertEquals(VendorFile.BRUKER, VendorFile.fromPath(dDir).orElseThrow());
		assertTrue(VendorFile.fromPath(tempDir.resolve("missing.raw")).isEmpty());
	}

	@Test
	void fromName_andList_coverEnumHelpers() {
		assertEquals(VendorFile.THERMO, VendorFile.fromName("test.raw").orElseThrow());
		assertEquals(VendorFile.BRUKER, VendorFile.fromName("test.d").orElseThrow());
		assertEquals(VendorFile.ENCYCLOPEDIA, VendorFile.fromName("test.dia").orElseThrow());
		assertEquals(VendorFile.MZML, VendorFile.fromName("test.mzml").orElseThrow());
		assertTrue(VendorFile.fromName(null).isEmpty());

		assertEquals(4, VendorFile.list().size());
	}
}
