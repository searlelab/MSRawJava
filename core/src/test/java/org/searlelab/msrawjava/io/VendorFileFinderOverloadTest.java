package org.searlelab.msrawjava.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VendorFileFinderOverloadTest {

	@TempDir
	Path tmp;

	@Test
	void overloadsReturnNewAggregatesWithExpectedEnabledTypes() throws Exception {
		Path raw=Files.writeString(tmp.resolve("sample.RAW"), "");
		Path dia=Files.writeString(tmp.resolve("sample.dia"), "");
		Path mzml=Files.writeString(tmp.resolve("sample.mzML"), "");
		Path bruker=Files.createDirectory(tmp.resolve("sample.d"));
		Files.writeString(bruker.resolve("nested.raw"), "");

		VendorFiles defaultFiles=VendorFileFinder.findAndAddRawAndD(tmp);
		assertEquals(1, defaultFiles.getThermoFiles().size());
		assertEquals(raw.toAbsolutePath().normalize(), defaultFiles.getThermoFiles().get(0));
		assertEquals(1, defaultFiles.getBrukerDirs().size());
		assertEquals(0, defaultFiles.getDiaFiles().size());
		assertEquals(0, defaultFiles.getMzmlFiles().size());

		VendorFiles withDia=VendorFileFinder.findAndAddRawAndD(tmp, true);
		assertEquals(1, withDia.getDiaFiles().size());
		assertEquals(dia.toAbsolutePath().normalize(), withDia.getDiaFiles().get(0));

		VendorFiles withMzml=VendorFileFinder.findAndAddRawAndD(tmp, true, true);
		assertEquals(1, withMzml.getMzmlFiles().size());
		assertEquals(mzml.toAbsolutePath().normalize(), withMzml.getMzmlFiles().get(0));
	}

	@Test
	void singleFileMzmlIsCollectedOnlyWhenEnabled() throws Exception {
		Path mzml=Files.writeString(tmp.resolve("only.mzml"), "");

		assertEquals(0, VendorFileFinder.findAndAddRawAndD(mzml, true).getMzmlFiles().size());
		assertEquals(1, VendorFileFinder.findAndAddRawAndD(mzml, true, true).getMzmlFiles().size());
	}

	@Test
	void nullStartIsRejectedBeforeFileSystemAccess() {
		assertThrows(NullPointerException.class, () -> VendorFileFinder.findAndAddRawAndD(null));
	}
}
