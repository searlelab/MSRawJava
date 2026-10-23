package org.searlelab.msrawjava.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.msrawjava.io.VendorFile;
import org.searlelab.msrawjava.io.VendorFileFinder;
import org.searlelab.msrawjava.io.VendorFiles;

class BrowserDatasetsTest {
	@Test
	void onlyAnalysisTdfMakesABrowserBundle(@TempDir Path temp) throws Exception {
		Path directory=Files.createDirectory(temp.resolve("ssh_config.D"));
		assertFalse(BrowserDatasets.isDataset(directory));
		Files.createFile(directory.resolve("analysis.tsf"));
		Files.createFile(directory.resolve("unrelated.tdf"));
		assertFalse(BrowserDatasets.isDataset(directory));
		Path tdf=Files.createDirectory(directory.resolve("analysis.tdf"));
		assertFalse(BrowserDatasets.isDataset(directory));
		Files.delete(tdf);
		Files.createFile(tdf);
		assertTrue(BrowserDatasets.isDataset(directory));
	}

	@Test
	void browserTraversesOrdinaryDotDirectoriesWhileCoreRetainsTsfDetection(@TempDir Path temp) throws Exception {
		Path ordinary=Files.createDirectory(temp.resolve("ssh_config.d"));
		Path tsfOnly=Files.createDirectory(temp.resolve("pasef-off.d"));
		Files.createFile(tsfOnly.resolve("analysis.tsf"));
		Path nestedRaw=Files.createFile(ordinary.resolve("nested.raw"));
		Path nestedDataset=Files.createDirectory(ordinary.resolve("actual.D"));
		Files.createFile(nestedDataset.resolve("analysis.tdf"));
		Files.createFile(nestedDataset.resolve("inside.raw"));
		Path mzml=Files.createFile(ordinary.resolve("nested.mzML"));
		Path dia=Files.createFile(ordinary.resolve("nested.DIA"));

		VendorFiles files=BrowserDatasets.find(temp);
		assertEquals(List.of(nestedDataset), files.getBrukerDirs());
		assertEquals(List.of(nestedRaw), files.getThermoFiles());
		assertEquals(List.of(mzml), files.getMzmlFiles());
		assertEquals(List.of(dia), files.getDiaFiles());
		VendorFiles fromOrdinary=BrowserDatasets.find(ordinary);
		assertEquals(files.getBrukerDirs(), fromOrdinary.getBrukerDirs());
		assertEquals(files.getThermoFiles(), fromOrdinary.getThermoFiles());
		assertTrue(BrowserDatasets.find(tsfOnly).getBrukerDirs().isEmpty());
		assertEquals(VendorFile.BRUKER, VendorFile.fromPath(tsfOnly).orElseThrow());
		assertEquals(List.of(tsfOnly), VendorFileFinder.findAndAddRawAndD(tsfOnly).getBrukerDirs());
	}
}
