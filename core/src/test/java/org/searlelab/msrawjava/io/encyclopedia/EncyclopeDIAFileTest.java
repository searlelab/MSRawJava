package org.searlelab.msrawjava.io.encyclopedia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.msrawjava.io.utils.Pair;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.WindowData;

class EncyclopeDIAFileTest {

	@TempDir
	Path tmp;

	private static PrecursorScan ms1(String name, int index, float rtSeconds, double scanLo, double scanHi, double[] mz, float[] inten, float[] ims) {
		return new PrecursorScan(name, index, rtSeconds, 0, scanLo, scanHi, null, mz, inten, ims);
	}

	private static FragmentScan ms2(String name, int index, float rtSeconds, double isoLo, double isoHi, byte z, double[] mz, float[] inten, float[] ims) {
		return new FragmentScan(name, "prec", index, (isoLo+isoHi)/2.0, rtSeconds, 0, null, isoLo, isoHi, mz, inten, ims, z, 0.0, 3000.0);
	}

	private static HashMap<Range, WindowData> singleRange() {
		HashMap<Range, WindowData> m=new HashMap<>();
		m.put(new Range(495.0, 505.0), new WindowData(0.5f, 1));
		return m;
	}

	@Test
	void writesMinimalDiaDatabase_withTablesAndRowCounts() throws Exception {
		// Build tiny inputs
		ArrayList<PrecursorScan> ms1s=new ArrayList<>();
		ms1s.add(ms1("ms1-1", 1, 1.0f, 100.0, 1000.0, new double[] {100.0, 200.0}, new float[] {10.0f, 0.0f}, null));

		ArrayList<FragmentScan> ms2s=new ArrayList<>();
		ms2s.add(ms2("ms2-2", 2, 2.0f, 499.9, 500.1, (byte)2, new double[] {150.0, 250.0}, new float[] {5.0f, 0.0f}, null));

		HashMap<Range, WindowData> ranges=singleRange();

		// Write .DIA
		EncyclopeDIAFile dia=new EncyclopeDIAFile();
		dia.openFile();
		assertEquals(".dia", dia.getFileExtension().toLowerCase());
		dia.setFileName("tiny_run", "/data/tiny_run");
		dia.setRanges(ranges);
		dia.addMetadata("Instrument", "TestRig");
		dia.addSpectra(ms1s, ms2s);

		java.nio.file.Path out=tmp.resolve("tiny.dia");
		dia.saveAsFile(out.toFile());
		dia.close();

		assertTrue(Files.exists(out), "DIA file should be created");

		// Open SQLite and verify schema + counts
		try (Connection c=DriverManager.getConnection("jdbc:sqlite:"+out.toString())) {
			DatabaseMetaData md=c.getMetaData();

			// Tables present
			assertTableExists(md, "metadata");
			assertTableExists(md, "ranges");
			assertTableExists(md, "spectra");
			assertTableExists(md, "precursor");
			assertTableExists(md, "fractions");

			// Row counts
			assertEquals(1, count(c, "select count(*) from ranges"));
			assertEquals(1, count(c, "select count(*) from spectra"));
			assertEquals(1, count(c, "select count(*) from precursor"));

			// Metadata row exists (we inserted Instrument)
			assertTrue(count(c, "select count(*) from metadata")>=1);
			assertEquals("TestRig", string(c, "select Value from metadata where Key='Instrument'"));

			// Sanity check a spectra row has isolation window and spectrum index
			assertTrue(count(c, "select count(*) from spectra where SpectrumIndex=2")==1);
		}

		dia=new EncyclopeDIAFile();
		dia.openFile();
		assertTrue(dia.getMetadata().size()>0);
		dia.close();
	}

	private static void assertTableExists(DatabaseMetaData md, String tableName) throws SQLException {
		try (ResultSet rs=md.getTables(null, null, tableName, null)) {
			assertTrue(rs.next(), "Expected table not found: "+tableName);
		}
	}

	private static int count(Connection c, String sql) throws SQLException {
		try (Statement s=c.createStatement(); ResultSet rs=s.executeQuery(sql)) {
			return rs.next()?rs.getInt(1):0;
		}
	}

	private static String string(Connection c, String sql) throws SQLException {
		try (Statement s=c.createStatement(); ResultSet rs=s.executeQuery(sql)) {
			return rs.next()?rs.getString(1):null;
		}
	}

	/**
	 * Helper to create a test .dia file with multiple spectra for read testing
	 */
	private File createTestDiaFile() throws Exception {
		// Create precursor scans with different RTs and TIC values
		ArrayList<PrecursorScan> precursors=new ArrayList<>();
		precursors.add(ms1("ms1-1", 1, 1.0f, 100.0, 1500.0, new double[] {100.0, 200.0, 300.0}, new float[] {10.0f, 20.0f, 30.0f}, null));
		precursors.add(ms1("ms1-2", 3, 2.5f, 100.0, 1500.0, new double[] {150.0, 250.0, 350.0}, new float[] {15.0f, 25.0f, 35.0f}, null));
		precursors.add(ms1("ms1-3", 5, 5.0f, 100.0, 1500.0, new double[] {120.0, 220.0, 320.0}, new float[] {12.0f, 22.0f, 32.0f}, null));

		// Create fragment scans with different isolation windows and RTs
		ArrayList<FragmentScan> fragments=new ArrayList<>();
		// Window 400-500, RT=1.5
		fragments.add(ms2("ms2-1", 2, 1.5f, 400.0, 500.0, (byte)2, new double[] {450.0, 460.0}, new float[] {100.0f, 144.0f}, null));
		// Window 400-500, RT=3.0
		fragments.add(ms2("ms2-2", 4, 3.0f, 400.0, 500.0, (byte)2, new double[] {455.0, 465.0}, new float[] {225.0f, 256.0f}, null));
		// Window 600-700, RT=2.0
		fragments.add(ms2("ms2-3", 6, 2.0f, 600.0, 700.0, (byte)3, new double[] {650.0, 660.0}, new float[] {81.0f, 121.0f}, null));
		// Window 600-700, RT=4.5
		fragments.add(ms2("ms2-4", 7, 4.5f, 600.0, 700.0, (byte)3, new double[] {655.0, 665.0}, new float[] {169.0f, 196.0f}, null));
		// Window 400-500, RT=6.0 (outside typical RT range for some tests)
		fragments.add(ms2("ms2-5", 8, 6.0f, 400.0, 500.0, (byte)2, new double[] {452.0, 462.0}, new float[] {64.0f, 100.0f}, null));

		HashMap<Range, WindowData> ranges=new HashMap<>();
		ranges.put(new Range(400.0f, 500.0f), new WindowData(0.5f, 3));
		ranges.put(new Range(600.0f, 700.0f), new WindowData(0.5f, 2));

		EncyclopeDIAFile dia=new EncyclopeDIAFile();
		dia.openFile();
		dia.setFileName("test_run", "/data/test");
		dia.setRanges(ranges);
		dia.addSpectra(precursors, fragments);

		Path out=tmp.resolve("test_reads.dia");
		dia.saveAsFile(out.toFile());
		dia.close();

		return out.toFile();
	}

	@Test
	void getStripes_withTargetMz_returnsMatchingSpectra() throws Exception {
		File diaFile=createTestDiaFile();

		EncyclopeDIAFile dia=new EncyclopeDIAFile();
		dia.openFile(diaFile);

		// Query for targetMz=450.0 (should match window 400-500)
		ArrayList<FragmentScan> stripes=dia.getStripes(450.0, 0.0f, 10.0f, false);

		assertNotNull(stripes);
		assertEquals(3, stripes.size(), "Should find 3 spectra in window 400-500");

		// Verify they are sorted by RT
		assertEquals(1.5f, stripes.get(0).getScanStartTime(), 0.01f);
		assertEquals(3.0f, stripes.get(1).getScanStartTime(), 0.01f);
		assertEquals(6.0f, stripes.get(2).getScanStartTime(), 0.01f);

		// Verify isolation windows are correct
		for (FragmentScan scan : stripes) {
			assertEquals(400.0, scan.getIsolationWindowLower(), 0.01);
			assertEquals(500.0, scan.getIsolationWindowUpper(), 0.01);
		}

		dia.close();
	}

	@Test
	void getStripes_withTargetMz_filtersRTRange() throws Exception {
		File diaFile=createTestDiaFile();

		EncyclopeDIAFile dia=new EncyclopeDIAFile();
		dia.openFile(diaFile);

		// Query with RT filter: minRT=1.0, maxRT=4.0
		ArrayList<FragmentScan> stripes=dia.getStripes(450.0, 1.0f, 4.0f, false);

		assertNotNull(stripes);
		assertEquals(2, stripes.size(), "Should find 2 spectra in RT range 1.0-4.0");

		// Verify RT filtering worked
		assertEquals(1.5f, stripes.get(0).getScanStartTime(), 0.01f);
		assertEquals(3.0f, stripes.get(1).getScanStartTime(), 0.01f);

		dia.close();
	}

	@Test
	void getStripes_withTargetMz_appliesSqrtTransform() throws Exception {
		File diaFile=createTestDiaFile();

		EncyclopeDIAFile dia=new EncyclopeDIAFile();
		dia.openFile(diaFile);

		// Query without sqrt
		ArrayList<FragmentScan> stripesNoSqrt=dia.getStripes(450.0, 1.0f, 2.0f, false);
		assertEquals(1, stripesNoSqrt.size());
		FragmentScan noSqrt=stripesNoSqrt.get(0);

		// Query with sqrt
		ArrayList<FragmentScan> stripesSqrt=dia.getStripes(450.0, 1.0f, 2.0f, true);
		assertEquals(1, stripesSqrt.size());
		FragmentScan withSqrt=stripesSqrt.get(0);

		// Original intensities: 100.0f, 144.0f
		// Expected after sqrt: 10.0f, 12.0f
		assertEquals(10.0f, withSqrt.getIntensityArray()[0], 0.01f, "sqrt(100) = 10");
		assertEquals(12.0f, withSqrt.getIntensityArray()[1], 0.01f, "sqrt(144) = 12");

		// Without sqrt should have original values
		assertEquals(100.0f, noSqrt.getIntensityArray()[0], 0.01f);
		assertEquals(144.0f, noSqrt.getIntensityArray()[1], 0.01f);

		dia.close();
	}

	@Test
	void getStripes_withTargetMz_returnsEmptyForNoMatch() throws Exception {
		File diaFile=createTestDiaFile();

		EncyclopeDIAFile dia=new EncyclopeDIAFile();
		dia.openFile(diaFile);

		// Query for targetMz that doesn't match any window
		ArrayList<FragmentScan> stripes=dia.getStripes(800.0, 0.0f, 10.0f, false);

		assertNotNull(stripes);
		assertTrue(stripes.isEmpty(), "Should find no spectra for targetMz=800");

		dia.close();
	}

	@Test
	void getStripes_withRange_returnsMatchingSpectra() throws Exception {
		File diaFile=createTestDiaFile();

		EncyclopeDIAFile dia=new EncyclopeDIAFile();
		dia.openFile(diaFile);

		// Query for range that overlaps window 600-700
		Range targetRange=new Range(620.0f, 680.0f);
		ArrayList<FragmentScan> stripes=dia.getStripes(targetRange, 0.0f, 10.0f, false);

		assertNotNull(stripes);
		assertEquals(2, stripes.size(), "Should find 2 spectra in window 600-700");

		// Verify isolation windows
		for (FragmentScan scan : stripes) {
			assertEquals(600.0, scan.getIsolationWindowLower(), 0.01);
			assertEquals(700.0, scan.getIsolationWindowUpper(), 0.01);
		}

		dia.close();
	}

	@Test
	void getStripes_withRange_overlapMultipleWindows() throws Exception {
		File diaFile=createTestDiaFile();

		EncyclopeDIAFile dia=new EncyclopeDIAFile();
		dia.openFile(diaFile);

		// Query for range that overlaps both windows (400-500 and 600-700)
		Range targetRange=new Range(450.0f, 650.0f);
		ArrayList<FragmentScan> stripes=dia.getStripes(targetRange, 0.0f, 10.0f, false);

		assertNotNull(stripes);
		assertEquals(5, stripes.size(), "Should find all 5 spectra across both windows");

		dia.close();
	}

	@Test
	void getStripes_withRange_filtersRTRange() throws Exception {
		File diaFile=createTestDiaFile();

		EncyclopeDIAFile dia=new EncyclopeDIAFile();
		dia.openFile(diaFile);

		// Query with RT filter
		Range targetRange=new Range(450.0f, 650.0f);
		ArrayList<FragmentScan> stripes=dia.getStripes(targetRange, 1.5f, 3.5f, false);

		assertNotNull(stripes);
		// Should find: RT=1.5 (window 400-500), RT=2.0 (window 600-700), RT=3.0 (window 400-500)
		assertEquals(3, stripes.size(), "Should find 3 spectra in RT range 1.5-3.5");

		// Verify sorted by RT
		assertEquals(1.5f, stripes.get(0).getScanStartTime(), 0.01f);
		assertEquals(2.0f, stripes.get(1).getScanStartTime(), 0.01f);
		assertEquals(3.0f, stripes.get(2).getScanStartTime(), 0.01f);

		dia.close();
	}

	@Test
	void getStripes_withRange_appliesSqrtTransform() throws Exception {
		File diaFile=createTestDiaFile();

		EncyclopeDIAFile dia=new EncyclopeDIAFile();
		dia.openFile(diaFile);

		Range targetRange=new Range(600.0f, 700.0f);

		// Query with sqrt
		ArrayList<FragmentScan> stripes=dia.getStripes(targetRange, 1.5f, 2.5f, true);
		assertEquals(1, stripes.size());
		FragmentScan scan=stripes.get(0);

		// Original intensities: 81.0f, 121.0f
		// Expected after sqrt: 9.0f, 11.0f
		assertEquals(9.0f, scan.getIntensityArray()[0], 0.01f, "sqrt(81) = 9");
		assertEquals(11.0f, scan.getIntensityArray()[1], 0.01f, "sqrt(121) = 11");

		dia.close();
	}

	@Test
	void getStripes_withRange_returnsEmptyForNoMatch() throws Exception {
		File diaFile=createTestDiaFile();

		EncyclopeDIAFile dia=new EncyclopeDIAFile();
		dia.openFile(diaFile);

		// Query for range that doesn't overlap any window
		Range targetRange=new Range(800.0f, 900.0f);
		ArrayList<FragmentScan> stripes=dia.getStripes(targetRange, 0.0f, 10.0f, false);

		assertNotNull(stripes);
		assertTrue(stripes.isEmpty(), "Should find no spectra for non-overlapping range");

		dia.close();
	}

	@Test
	void getTICTrace_returnsCorrectArrays() throws Exception {
		File diaFile=createTestDiaFile();

		EncyclopeDIAFile dia=new EncyclopeDIAFile();
		dia.openFile(diaFile);

		Pair<float[], float[]> trace=dia.getTICTrace();

		assertNotNull(trace);
		assertNotNull(trace.getX(), "RT array should not be null");
		assertNotNull(trace.getY(), "TIC array should not be null");

		float[] rts=trace.getX();
		float[] tics=trace.getY();

		assertEquals(3, rts.length, "Should have 3 precursor scans");
		assertEquals(3, tics.length, "Should have 3 TIC values");

		// Verify RT values (from precursors created in createTestDiaFile)
		assertEquals(1.0f, rts[0], 0.01f);
		assertEquals(2.5f, rts[1], 0.01f);
		assertEquals(5.0f, rts[2], 0.01f);

		// Verify TIC values are present (should be sum of intensities from each precursor)
		assertTrue(tics[0]>0, "TIC should be positive");
		assertTrue(tics[1]>0, "TIC should be positive");
		assertTrue(tics[2]>0, "TIC should be positive");

		dia.close();
	}

	@Test
	void getTICTrace_withEmptyPrecursors_returnsEmptyArrays() throws Exception {
		// Create DIA file with only fragments, no precursors
		ArrayList<PrecursorScan> precursors=new ArrayList<>();
		ArrayList<FragmentScan> fragments=new ArrayList<>();
		fragments.add(ms2("ms2-1", 1, 1.0f, 400.0, 500.0, (byte)2, new double[] {450.0}, new float[] {100.0f}, null));

		HashMap<Range, WindowData> ranges=new HashMap<>();
		ranges.put(new Range(400.0f, 500.0f), new WindowData(0.5f, 1));

		EncyclopeDIAFile dia=new EncyclopeDIAFile();
		dia.openFile();
		dia.setFileName("empty_precursors", "/data/test");
		dia.setRanges(ranges);
		dia.addSpectra(precursors, fragments);

		Path out=tmp.resolve("empty_precursors.dia");
		dia.saveAsFile(out.toFile());
		dia.close();

		// Now read it back
		dia=new EncyclopeDIAFile();
		dia.openFile(out.toFile());

		Pair<float[], float[]> trace=dia.getTICTrace();

		assertNotNull(trace);
		assertEquals(0, trace.getX().length, "RT array should be empty");
		assertEquals(0, trace.getY().length, "TIC array should be empty");

		dia.close();
	}

	@Test
	void getTIC_usesPrecursorTableEvenWhenMetadataContainsStaleValue() throws Exception {
		File diaFile=createTestDiaFile();
		try (Connection c=DriverManager.getConnection("jdbc:sqlite:"+diaFile.getAbsolutePath()); Statement s=c.createStatement()) {
			s.execute("insert or replace into metadata (Key, Value) values ('totalPrecursorTIC', '1852649200000')");
		}

		EncyclopeDIAFile dia=new EncyclopeDIAFile();
		dia.openFile(diaFile);
		assertEquals("1852649200000", dia.getMetadata().get(EncyclopeDIAFile.TOTAL_PRECURSOR_TIC_ATTRIBUTE),
				"Test setup should contain stale metadata TIC");
		assertEquals(201.0f, dia.getTIC(), 0.0001f, "DIA getTIC should be derived from precursor rows (MS1), not metadata");
		dia.close();
	}

	@Test
	void getStripes_preservesMassAndIonMobility() throws Exception {
		// Create fragment with specific mass values and ion mobility
		ArrayList<PrecursorScan> precursors=new ArrayList<>();
		precursors.add(ms1("ms1-1", 1, 1.0f, 100.0, 1500.0, new double[] {100.0}, new float[] {10.0f}, null));

		ArrayList<FragmentScan> fragments=new ArrayList<>();
		double[] masses=new double[] {400.123, 450.456, 500.789};
		float[] intensities=new float[] {100.0f, 200.0f, 300.0f};
		float[] ims=new float[] {0.5f, 0.6f, 0.7f};
		fragments.add(ms2("ms2-1", 2, 1.5f, 400.0, 500.0, (byte)2, masses, intensities, ims));

		HashMap<Range, WindowData> ranges=new HashMap<>();
		ranges.put(new Range(400.0f, 500.0f), new WindowData(0.5f, 1));

		EncyclopeDIAFile dia=new EncyclopeDIAFile();
		dia.openFile();
		dia.setRanges(ranges);
		dia.addSpectra(precursors, fragments);

		Path out=tmp.resolve("mass_ims_test.dia");
		dia.saveAsFile(out.toFile());
		dia.close();

		// Read it back
		dia=new EncyclopeDIAFile();
		dia.openFile(out.toFile());

		ArrayList<FragmentScan> stripes=dia.getStripes(450.0, 0.0f, 10.0f, false);
		assertEquals(1, stripes.size());

		FragmentScan scan=stripes.get(0);

		// Verify mass array is preserved
		assertEquals(3, scan.getMassArray().length);
		assertEquals(400.123, scan.getMassArray()[0], 0.001);
		assertEquals(450.456, scan.getMassArray()[1], 0.001);
		assertEquals(500.789, scan.getMassArray()[2], 0.001);

		// Verify intensities
		assertEquals(100.0f, scan.getIntensityArray()[0], 0.01f);
		assertEquals(200.0f, scan.getIntensityArray()[1], 0.01f);
		assertEquals(300.0f, scan.getIntensityArray()[2], 0.01f);

		// Verify ion mobility
		assertTrue(scan.getIonMobilityArray().isPresent());
		float[] readIms=scan.getIonMobilityArray().get();
		assertEquals(0.5f, readIms[0], 0.01f);
		assertEquals(0.6f, readIms[1], 0.01f);
		assertEquals(0.7f, readIms[2], 0.01f);

		dia.close();
	}
}
