package org.searlelab.msrawjava.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VendorFilesTest {

	private VendorFiles vendorFiles;

	@BeforeEach
	void setUp() {
		vendorFiles=new VendorFiles();
	}

	@Test
	void constructorCreatesEmptyLists() {
		assertNotNull(vendorFiles.getThermoFiles());
		assertNotNull(vendorFiles.getBrukerDirs());
		assertTrue(vendorFiles.getThermoFiles().isEmpty());
		assertTrue(vendorFiles.getBrukerDirs().isEmpty());
	}

	@Test
	void addRawSinglePath() {
		Path rawFile=Paths.get("/data/sample.raw");
		vendorFiles.addRaw(rawFile);

		assertEquals(1, vendorFiles.getThermoFiles().size());
		assertEquals(rawFile, vendorFiles.getThermoFiles().get(0));
		assertTrue(vendorFiles.getBrukerDirs().isEmpty());
	}

	@Test
	void addDSinglePath() {
		Path dDir=Paths.get("/data/sample.d");
		vendorFiles.addD(dDir);

		assertEquals(1, vendorFiles.getBrukerDirs().size());
		assertEquals(dDir, vendorFiles.getBrukerDirs().get(0));
		assertTrue(vendorFiles.getThermoFiles().isEmpty());
	}

	@Test
	void addRawMultiplePaths() {
		vendorFiles.addRaw(Paths.get("/data/sample1.raw"));
		vendorFiles.addRaw(Paths.get("/data/sample2.raw"));
		vendorFiles.addRaw(Paths.get("/data/sample3.raw"));

		assertEquals(3, vendorFiles.getThermoFiles().size());
	}

	@Test
	void addDMultiplePaths() {
		vendorFiles.addD(Paths.get("/data/sample1.d"));
		vendorFiles.addD(Paths.get("/data/sample2.d"));

		assertEquals(2, vendorFiles.getBrukerDirs().size());
	}

	@Test
	void addRawArrayList() {
		ArrayList<Path> rawFiles=new ArrayList<>();
		rawFiles.add(Paths.get("/data/sample1.raw"));
		rawFiles.add(Paths.get("/data/sample2.raw"));

		vendorFiles.addRaw(rawFiles);

		assertEquals(2, vendorFiles.getThermoFiles().size());
	}

	@Test
	void addDArrayList() {
		ArrayList<Path> dDirs=new ArrayList<>();
		dDirs.add(Paths.get("/data/sample1.d"));
		dDirs.add(Paths.get("/data/sample2.d"));
		dDirs.add(Paths.get("/data/sample3.d"));

		vendorFiles.addD(dDirs);

		assertEquals(3, vendorFiles.getBrukerDirs().size());
	}

	@Test
	void addBothTypesAtOnce() {
		ArrayList<Path> rawFiles=new ArrayList<>();
		rawFiles.add(Paths.get("/data/sample1.raw"));
		rawFiles.add(Paths.get("/data/sample2.raw"));

		ArrayList<Path> dDirs=new ArrayList<>();
		dDirs.add(Paths.get("/data/sample1.d"));

		vendorFiles.add(rawFiles, dDirs);

		assertEquals(2, vendorFiles.getThermoFiles().size());
		assertEquals(1, vendorFiles.getBrukerDirs().size());
	}

	@Test
	void addAccumulates() {
		// First batch
		vendorFiles.addRaw(Paths.get("/data/batch1/sample1.raw"));
		vendorFiles.addD(Paths.get("/data/batch1/sample1.d"));

		// Second batch
		ArrayList<Path> moreRaw=new ArrayList<>();
		moreRaw.add(Paths.get("/data/batch2/sample2.raw"));
		moreRaw.add(Paths.get("/data/batch2/sample3.raw"));
		vendorFiles.addRaw(moreRaw);

		ArrayList<Path> moreD=new ArrayList<>();
		moreD.add(Paths.get("/data/batch2/sample2.d"));
		vendorFiles.addD(moreD);

		assertEquals(3, vendorFiles.getThermoFiles().size());
		assertEquals(2, vendorFiles.getBrukerDirs().size());
	}

	@Test
	void gettersReturnSameListInstance() {
		// Verify that getters return the actual internal list (not a copy)
		ArrayList<Path> thermoList=vendorFiles.getThermoFiles();
		ArrayList<Path> brukerList=vendorFiles.getBrukerDirs();

		vendorFiles.addRaw(Paths.get("/data/test.raw"));
		vendorFiles.addD(Paths.get("/data/test.d"));

		// Changes should be visible through original references
		assertEquals(1, thermoList.size());
		assertEquals(1, brukerList.size());

		// Confirm same instance
		assertSame(thermoList, vendorFiles.getThermoFiles());
		assertSame(brukerList, vendorFiles.getBrukerDirs());
	}

	@Test
	void addEmptyArrayLists() {
		vendorFiles.addRaw(new ArrayList<>());
		vendorFiles.addD(new ArrayList<>());

		assertTrue(vendorFiles.getThermoFiles().isEmpty());
		assertTrue(vendorFiles.getBrukerDirs().isEmpty());
	}

	@Test
	void addWithBothEmptyArrayLists() {
		vendorFiles.add(new ArrayList<>(), new ArrayList<>());

		assertTrue(vendorFiles.getThermoFiles().isEmpty());
		assertTrue(vendorFiles.getBrukerDirs().isEmpty());
	}

	@Test
	void pathsPreserveOrder() {
		Path first=Paths.get("/data/1.raw");
		Path second=Paths.get("/data/2.raw");
		Path third=Paths.get("/data/3.raw");

		vendorFiles.addRaw(first);
		vendorFiles.addRaw(second);
		vendorFiles.addRaw(third);

		ArrayList<Path> files=vendorFiles.getThermoFiles();
		assertEquals(first, files.get(0));
		assertEquals(second, files.get(1));
		assertEquals(third, files.get(2));
	}

	@Test
	void mixedOperationsWork() {
		// Single adds
		vendorFiles.addRaw(Paths.get("/single/a.raw"));
		vendorFiles.addD(Paths.get("/single/a.d"));

		// Batch add raw
		ArrayList<Path> batchRaw=new ArrayList<>();
		batchRaw.add(Paths.get("/batch/b.raw"));
		batchRaw.add(Paths.get("/batch/c.raw"));
		vendorFiles.addRaw(batchRaw);

		// Combined add
		ArrayList<Path> moreRaw=new ArrayList<>();
		moreRaw.add(Paths.get("/combined/d.raw"));
		ArrayList<Path> moreD=new ArrayList<>();
		moreD.add(Paths.get("/combined/b.d"));
		moreD.add(Paths.get("/combined/c.d"));
		vendorFiles.add(moreRaw, moreD);

		assertEquals(4, vendorFiles.getThermoFiles().size());
		assertEquals(3, vendorFiles.getBrukerDirs().size());
	}

	@Test
	void duplicatePathsAllowed() {
		Path duplicate=Paths.get("/data/sample.raw");
		vendorFiles.addRaw(duplicate);
		vendorFiles.addRaw(duplicate);

		// Duplicates are allowed (no deduplication)
		assertEquals(2, vendorFiles.getThermoFiles().size());
	}
}
