
package org.searlelab.msrawjava.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VendorFileFinderTest {

	private static final Path RAWDATA=Path.of("src", "test", "resources", "rawdata");

	@Test
	void findFromDirectory_collectsExpectedVendorSets() throws Exception {
		Assumptions.assumeTrue(Files.isDirectory(RAWDATA), "Missing test fixture dir: "+RAWDATA);

		VendorFiles files=new VendorFiles();
		VendorFileFinder.findAndAddRawAndD(RAWDATA, files);

		assertEquals(4, files.getThermoFiles().size(), "Expected four .raw files");
		assertEquals(2, files.getBrukerDirs().size(), "Expected two .d directories");
		assertEquals(0, files.getDiaFiles().size(), "Expected no .dia files without discover flag");

		Set<String> rawNames=files.getThermoFiles().stream().map(p -> p.getFileName().toString()).collect(Collectors.toCollection(TreeSet::new));
		Set<String> dNames=files.getBrukerDirs().stream().map(p -> p.getFileName().toString()).collect(Collectors.toCollection(TreeSet::new));

		assertFalse(rawNames.contains("README.md"));
		assertFalse(dNames.contains("README.md"));

		assertTrue(rawNames.contains("Astral_GPFDIA_2mz.raw"));
		assertTrue(rawNames.contains("Exploris_DIA_16mzst.raw"));
		assertTrue(rawNames.contains("Stellar_DIA_4mz.raw"));
		assertTrue(rawNames.contains("Stellar_DDA.raw"));

		assertTrue(dNames.contains("230711_idleflow_400-1000mz_25mz_diaPasef_10sec.d"));
		assertTrue(dNames.contains("dda_test.d"));
	}

	@Test
	void findFromDirectory_withDiscoverDia_collectsDiaFiles() throws Exception {
		Assumptions.assumeTrue(Files.isDirectory(RAWDATA), "Missing test fixture dir: "+RAWDATA);

		VendorFiles files=new VendorFiles();
		VendorFileFinder.findAndAddRawAndD(RAWDATA, files, true);

		assertEquals(3, files.getDiaFiles().size(), "Expected three .dia files");
		Set<String> diaNames=files.getDiaFiles().stream().map(p -> p.getFileName().toString()).collect(Collectors.toCollection(TreeSet::new));
		assertTrue(diaNames.contains("HeLa_16mzst_29to31min.dia"));
		assertTrue(diaNames.contains("HeLa_16mzst_demux.dia"));
		assertTrue(diaNames.contains("HeLa_16mzst_orig.dia"));
	}

	@Test
	void singleRawFile_shortCircuitsToRawOnly() throws Exception {
		Path raw=RAWDATA.resolve("Stellar_DDA.raw");
		Assumptions.assumeTrue(Files.exists(raw), "Fixture missing: "+raw);

		VendorFiles files=new VendorFiles();
		VendorFileFinder.findAndAddRawAndD(raw, files);

		assertEquals(1, files.getThermoFiles().size());
		assertEquals(raw.toAbsolutePath().normalize(), files.getThermoFiles().get(0));
		assertTrue(files.getBrukerDirs().isEmpty());
		assertTrue(files.getDiaFiles().isEmpty());
	}

	@Test
	void singleDDirectory_shortCircuitsToDOnly() throws Exception {
		Path ddir=RAWDATA.resolve("dda_test.d");
		Assumptions.assumeTrue(Files.isDirectory(ddir), "Fixture missing: "+ddir);

		VendorFiles files=new VendorFiles();
		VendorFileFinder.findAndAddRawAndD(ddir, files);

		assertEquals(1, files.getBrukerDirs().size());
		assertEquals(ddir.toAbsolutePath().normalize(), files.getBrukerDirs().get(0));
		assertTrue(files.getThermoFiles().isEmpty());
		assertTrue(files.getDiaFiles().isEmpty());
	}

	@Test
	void singleDiaFile_shortCircuitsToDiaOnly() throws Exception {
		Path dia=RAWDATA.resolve("HeLa_16mzst_29to31min.dia");
		Assumptions.assumeTrue(Files.exists(dia), "Fixture missing: "+dia);

		VendorFiles files=new VendorFiles();
		VendorFileFinder.findAndAddRawAndD(dia, files);

		assertEquals(1, files.getDiaFiles().size());
		assertEquals(dia.toAbsolutePath().normalize(), files.getDiaFiles().get(0));
		assertTrue(files.getThermoFiles().isEmpty());
		assertTrue(files.getBrukerDirs().isEmpty());
	}

	@Test
	void nonexistentPath_throwsNoSuchFile() {
		Path missing=RAWDATA.resolve("definitely_not_here_12345");
		VendorFiles files=new VendorFiles();
		assertThrows(NoSuchFileException.class, () -> VendorFileFinder.findAndAddRawAndD(missing, files));
	}

	@Test
	void walkSkipsDSubtrees_andDoesNotCollectInnerRaw(@TempDir Path tmp) throws Exception {
		Path outer=tmp.resolve("outer");
		Files.createDirectories(outer);
		Path dsub=outer.resolve("inner.d");
		Files.createDirectories(dsub);
		Files.writeString(dsub.resolve("ignored.raw"), "dummy");
		Path keep=outer.resolve("keep.raw");
		Files.writeString(keep, "dummy");

		VendorFiles files=new VendorFiles();
		VendorFileFinder.findAndAddRawAndD(tmp, files);

		assertEquals(1, files.getThermoFiles().size(), "Only the top-level raw should be collected");
		assertEquals(keep.toAbsolutePath().normalize(), files.getThermoFiles().get(0));
		assertEquals(1, files.getBrukerDirs().size(), "The .d directory should be collected once");
		assertEquals(dsub.toAbsolutePath().normalize(), files.getBrukerDirs().get(0));
		assertTrue(files.getDiaFiles().isEmpty());
	}

	@Test
	void extensionCheck_isCaseInsensitive(@TempDir Path tmp) throws Exception {
		Path fRAW=tmp.resolve("SAMPLE.RAW");
		Files.writeString(fRAW, "x");
		Path dDIR=tmp.resolve("BUNDLE.D");
		Files.createDirectories(dDIR);

		VendorFiles files1=new VendorFiles();
		VendorFileFinder.findAndAddRawAndD(fRAW, files1);
		assertEquals(1, files1.getThermoFiles().size());
		assertTrue(files1.getBrukerDirs().isEmpty());
		assertTrue(files1.getDiaFiles().isEmpty());

		VendorFiles files2=new VendorFiles();
		VendorFileFinder.findAndAddRawAndD(dDIR, files2);
		assertEquals(1, files2.getBrukerDirs().size());
		assertTrue(files2.getThermoFiles().isEmpty());
		assertTrue(files2.getDiaFiles().isEmpty());
	}
}
