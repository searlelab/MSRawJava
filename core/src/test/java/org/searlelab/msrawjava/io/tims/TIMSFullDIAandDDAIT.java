package org.searlelab.msrawjava.io.tims;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.searlelab.msrawjava.model.AcquiredSpectrum;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;

public class TIMSFullDIAandDDAIT {
	@Test
	void endToEndDIATest() throws Exception {

		Path dataDir=Path.of("src", "test", "resources", "rawdata", "230711_idleflow_400-1000mz_25mz_diaPasef_10sec.d");

		Path tdfPath=dataDir.resolve("analysis.tdf");

		assertTrue(Files.isDirectory(dataDir), "Test data directory missing: "+dataDir.toAbsolutePath());
		assertTrue(Files.isRegularFile(tdfPath), "analysis.tdf missing: "+tdfPath.toAbsolutePath());

		try (Connection conn=DriverManager.getConnection("jdbc:sqlite:"+tdfPath.toString()); BrukerTIMSFile stripe=new BrukerTIMSFile()) {
			stripe.openFile(dataDir);

			// Ground truth from SQLite
			final int sqlMs1=scalarInt(conn, "SELECT COUNT(*) FROM Frames WHERE MsMsType = 0");
			final int sqlDIAFrames=scalarInt(conn, "SELECT COUNT(*) FROM Frames WHERE MsMsType = 9");
			final int sqlDIAGroups=scalarInt(conn, "SELECT MAX(WindowGroup) FROM DiaFrameMsMsWindows");
			final int sqlDIAWindows=scalarInt(conn, "SELECT COUNT(*) FROM DiaFrameMsMsWindows");
			final int sqlDIAMs2=sqlDIAFrames*sqlDIAWindows/sqlDIAGroups;

			System.out.println(
					"MS2 Frames: "+sqlDIAFrames+", WindowGroups: "+sqlDIAGroups+", Windows: "+sqlDIAWindows+" --> expecting a total of: "+sqlDIAMs2+" MS2s");

			// Read via our API across full RT
			final double[] rtRange=rtRange(conn);
			final float minRT=(float)rtRange[0];
			final float maxRT=(float)rtRange[1];

			ArrayList<PrecursorScan> ms1=stripe.getPrecursors(0, Float.MAX_VALUE);
			ArrayList<FragmentScan> ms2=stripe.getStripes(new Range(0, Float.MAX_VALUE), 0.0f, Float.MAX_VALUE, false);

			// Sizes must match SQL
			assertEquals(sqlMs1, ms1.size(), "MS1 count mismatch vs SQLite Frames.MsMsType=0");
			assertEquals(sqlDIAMs2, ms2.size(), "MS2 count mismatch vs SQLite Frames.MsMsType=9 and DiaFrameMsMsWindows");

			// MS1 ordering and quick array sanity
			assertIsNondecreasingRT(ms1);
			sanityCheckPrecursorScansDIA(ms1);

			// MS2 ordering and quick array sanity
			assertIsNondecreasingRT(ms2);
			sanityCheckFragmentScans(ms2);

			// Print a concise summary
			long nonEmptyMs1=ms1.stream().filter(p -> p.getMassArray().length>0).count();
			long nonEmptyMs2=ms2.stream().filter(p -> p.getMassArray().length>0).count();
			System.out.printf(Locale.ROOT, "DIA summary: MS1=%d total (%d non-empty), MS2 windows=%d total (%d non-empty), RT span=%.3f..%.3f s%n", ms1.size(),
					nonEmptyMs1, ms2.size(), nonEmptyMs2, minRT, maxRT);
		}
	}

	@Test
	void endToEndDDATest() throws Exception {

		Path dataDir=Path.of("src", "test", "resources", "rawdata", "dda_test.d");
		;
		Path tdfPath=dataDir.resolve("analysis.tdf");

		assertTrue(Files.isDirectory(dataDir), "Test data directory missing: "+dataDir.toAbsolutePath());
		assertTrue(Files.isRegularFile(tdfPath), "analysis.tdf missing: "+tdfPath.toAbsolutePath());

		try (Connection conn=DriverManager.getConnection("jdbc:sqlite:"+tdfPath.toString()); BrukerTIMSFile stripe=new BrukerTIMSFile()) {
			stripe.openFile(dataDir);

			// Ground truth from SQLite
			final int sqlMs1=scalarInt(conn, "SELECT COUNT(*) FROM Frames WHERE MsMsType = 0");
			final int sqlDdaWindows=scalarInt(conn, "SELECT COUNT(*) FROM PasefFrameMsMsInfo");

			// Read via our API across full RT
			final double[] rtRange=rtRange(conn);
			final float minRT=(float)rtRange[0];
			final float maxRT=(float)rtRange[1];

			ArrayList<PrecursorScan> ms1=stripe.getPrecursors(0, Float.MAX_VALUE);
			ArrayList<FragmentScan> ms2=stripe.getStripes(new Range(0, Float.MAX_VALUE), 0.0f, Float.MAX_VALUE, false);

			// Sizes must match SQL
			assertEquals(sqlMs1, ms1.size(), "MS1 count mismatch vs SQLite Frames.MsMsType=0");
			assertEquals(sqlDdaWindows, ms2.size(), "DDA window count mismatch vs SQLite PasefFrameMsMsInfo");

			// MS1 ordering and quick array sanity
			assertIsNondecreasingRT(ms1);
			sanityCheckPrecursorScansDDA(ms1);

			// MS2 ordering and quick array sanity
			assertIsNondecreasingRT(ms2);
			sanityCheckFragmentScans(ms2);

			// Print a concise summary
			long nonEmptyMs1=ms1.stream().filter(p -> p.getMassArray().length>0).count();
			long nonEmptyMs2=ms2.stream().filter(p -> p.getMassArray().length>0).count();
			System.out.printf(Locale.ROOT, "DDA summary: MS1=%d total (%d non-empty), MS2 windows=%d total (%d non-empty), RT span=%.3f..%.3f s%n", ms1.size(),
					nonEmptyMs1, ms2.size(), nonEmptyMs2, minRT, maxRT);
		}
	}

	@Test
	void endToEndPrmTest() throws Exception {
		String fixtureRoot=System.getProperty("msrawjava.prm.fixture.root");
		Assumptions.assumeTrue(fixtureRoot!=null&&!fixtureRoot.isBlank(), "Set -Dmsrawjava.prm.fixture.root to run the external PRM integration test");
		for (String name : List.of("20260630_blank_PRM_CB_WO03_Slot1-2_1_9513.d", "20260630_blank_PRM_NP_WO01_Slot1-2_1_9510.d")) {
			Path dataDir=Path.of(fixtureRoot, name);
			Path tdfPath=dataDir.resolve("analysis.tdf");
			Assumptions.assumeTrue(Files.isRegularFile(tdfPath), "PRM fixture missing: "+tdfPath.toAbsolutePath());

			try (Connection conn=DriverManager.getConnection("jdbc:sqlite:"+tdfPath); BrukerTIMSFile stripe=new BrukerTIMSFile()) {
				stripe.openFile(dataDir);
				assertTrue(stripe.isPASEFPRM());
				assertFalse(stripe.isPASEFDDA());
				assertFalse(stripe.isPASEFDIA());
				assertEquals("PRM", stripe.getMetadata().get("rawFileStructure.dataAcquisitionType"));
				assertEquals(0.0, stripe.getPrecursorMarginSize());
				assertFalse(stripe.getRanges().isEmpty());

				int expected=scalarInt(conn, "SELECT COUNT(*) FROM PrmFrameMsMsInfo I JOIN Frames F ON F.Id = I.Frame JOIN PrmTargets T ON T.Id = I.Target WHERE F.MsMsType = 10");
				ArrayList<FragmentScan> fragments=stripe.getStripes(new Range(0, Float.MAX_VALUE), 0f, Float.MAX_VALUE, false);
				ArrayList<org.searlelab.msrawjava.model.ScanSummary> summaries=stripe.getScanSummaries(0f, Float.MAX_VALUE);
				assertEquals(expected, fragments.size(), "PRM row count mismatch vs SQLite");
				assertEquals(expected, summaries.stream().filter(summary -> !summary.isPrecursor()).count(), "PRM summary count mismatch vs SQLite");
				assertIsNondecreasingRT(fragments);
				sanityCheckFragmentScans(fragments);

				FragmentScan first=fragments.get(0);
				assertNotNull(first.getPrecursorName());
				assertTrue(first.getPrecursorCharge()>0);
				org.searlelab.msrawjava.model.ScanSummary firstSummary=summaries.stream().filter(summary -> !summary.isPrecursor()).findFirst().orElseThrow();
				AcquiredSpectrum resolved=stripe.getSpectrum(firstSummary);
				assertEquals(firstSummary.getSpectrumIndex(), resolved.getSpectrumIndex(), "PRM summary must resolve its exact frame and IM slice");
				org.searlelab.msrawjava.model.ScanSummary missing=new org.searlelab.msrawjava.model.ScanSummary(firstSummary.getSpectrumName(), Integer.MIN_VALUE,
						firstSummary.getScanStartTime(), firstSummary.getFraction(), firstSummary.getTic(), firstSummary.getPrecursorMz(), false,
						firstSummary.getIonInjectionTime(), firstSummary.getIsolationWindowLower(), firstSummary.getIsolationWindowUpper(),
						firstSummary.getScanWindowLower(), firstSummary.getScanWindowUpper(), firstSummary.getCharge());
				assertNull(stripe.getSpectrum(missing), "An unmatched PRM summary must not resolve to a neighboring spectrum");
			}
		}
	}

	// ---------- Helpers ----------

	private static int scalarInt(Connection c, String sql) throws SQLException {
		try (Statement st=c.createStatement(); ResultSet rs=st.executeQuery(sql)) {
			assertTrue(rs.next(), "No row for query: "+sql);
			return rs.getInt(1);
		}
	}

	private static double[] rtRange(Connection c) throws SQLException {
		try (Statement st=c.createStatement(); ResultSet rs=st.executeQuery("SELECT MIN(Time), MAX(Time) FROM Frames")) {
			assertTrue(rs.next(), "No RT range");
			return new double[] {rs.getDouble(1), rs.getDouble(2)};
		}
	}

	private static void assertIsNondecreasingRT(List<? extends AcquiredSpectrum> scans) {
		double prev=-Double.MAX_VALUE;
		for (AcquiredSpectrum o : scans) {
			double rt=o.getScanStartTime();
			assertTrue(rt>=prev-1e-6, "RT not nondecreasing: "+prev+" then "+rt);
			prev=rt;
		}
	}

	private static void sanityCheckPrecursorScansDIA(List<PrecursorScan> scans) {
		for (AcquiredSpectrum s : scans) {
			assertNotNull(s.getSpectrumName());
			assertTrue(s.getIsolationWindowLower()<=s.getIsolationWindowUpper());
			assertEquals(100.0, s.getIsolationWindowLower(), 0.01);
			assertEquals(1600.0, s.getIsolationWindowUpper(), 0.01);
			float[] inten=s.getIntensityArray();
			for (float v : inten)
				assertTrue(v>=0.01f, "negative intensity");
			assertArraysAligned(s.getMassArray(), s.getIonMobilityArray().get(), inten);
		}
	}

	private static void sanityCheckPrecursorScansDDA(List<PrecursorScan> scans) {
		for (AcquiredSpectrum s : scans) {
			assertNotNull(s.getSpectrumName());
			assertTrue(s.getIsolationWindowLower()<=s.getIsolationWindowUpper());
			assertEquals(0.0, s.getIsolationWindowLower(), 0.01);
			assertEquals(2000.0, s.getIsolationWindowUpper(), 0.01);
			float[] inten=s.getIntensityArray();
			for (float v : inten)
				assertTrue(v>=0.01f, "negative intensity");
			assertArraysAligned(s.getMassArray(), s.getIonMobilityArray().get(), inten);
		}
	}

	private static void sanityCheckFragmentScans(List<FragmentScan> scans) {
		for (FragmentScan s : scans) {
			assertNotNull(s.getSpectrumName());
			assertNotNull(s.getPrecursorName());
			assertTrue(s.getIsolationWindowLower()<s.getIsolationWindowUpper());
			float[] inten=s.getIntensityArray();
			for (float v : inten)
				assertTrue(v>0.00f, "negative intensity");
			assertArraysAligned(s.getMassArray(), s.getIonMobilityArray().get(), inten);

			final double lo=10;
			final double hi=2000;
			for (double mz : s.getMassArray()) {
				assertTrue(mz>=lo&&mz<=hi, "m/z outside isolation window: "+mz+" not in ["+lo+","+hi+"]");
			}

			final double imslo=0.0;
			final double imshi=2.0;
			for (double ims : s.getIonMobilityArray().get()) {
				assertTrue(ims>imslo&&ims<imshi, "ims outside isolation window: "+ims+" not in ["+imslo+","+imshi+"]");
			}
		}
	}

	private static void assertArraysAligned(double[] mz, float[] ims, float[] inten) {
		assertEquals(mz.length, ims.length, "mz vs ims length mismatch");
		assertEquals(mz.length, inten.length, "mz vs intensity length mismatch");
	}
}
