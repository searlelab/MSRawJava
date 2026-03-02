package org.searlelab.msrawjava.io.tims;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.model.AcquiredSpectrum;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.ScanSummary;

class BrukerTIMSFileSummaryTest {

	@Test
	void scanSummariesAndSpectrumFetch_workForDdaFixture() throws Exception {
		Path path=Path.of("src", "test", "resources", "rawdata", "dda_test.d");
		Assumptions.assumeTrue(Files.exists(path), "Fixture .d not present: "+path);

		BrukerTIMSFile file=new BrukerTIMSFile();
		file.openFile(path);

		ArrayList<ScanSummary> summaries=file.getScanSummaries(0.0f, Float.MAX_VALUE);
		assertTrue(!summaries.isEmpty(), "Expected scan summaries");

		ScanSummary ms1=summaries.stream().filter(ScanSummary::isPrecursor).findFirst().orElse(null);
		ScanSummary ms2=summaries.stream().filter(s -> !s.isPrecursor()).findFirst().orElse(null);
		assertNotNull(ms1, "Expected an MS1 summary");
		assertNotNull(ms2, "Expected an MS2 summary");
		assertTrue(ms1.getSpectrumName().startsWith("frame="));
		assertTrue(ms1.getSpectrumName().contains("scanStart="));
		assertTrue(ms1.getSpectrumName().contains("scanEnd="));
		assertTrue(ms2.getSpectrumName().startsWith("frame="));
		assertTrue(ms2.getSpectrumName().contains("scanStart="));
		assertTrue(ms2.getSpectrumName().contains("scanEnd="));

		AcquiredSpectrum ms1Spec=file.getSpectrum(ms1);
		assertNotNull(ms1Spec);
		assertEquals(PrecursorScan.class, ms1Spec.getClass());

		AcquiredSpectrum ms2Spec=file.getSpectrum(ms2);
		assertNotNull(ms2Spec);
		assertEquals(FragmentScan.class, ms2Spec.getClass());

		file.close();
	}

	@Test
	void accumulationTimeSeconds_convertsMilliseconds() throws Exception {
		Method method=BrukerTIMSFile.class.getDeclaredMethod("accumulationTimeSeconds", double.class);
		method.setAccessible(true);
		float seconds=(float)method.invoke(null, 1500.0);
		assertEquals(1.5f, seconds, 1e-6f);
	}

	@Test
	void scanSummariesAndSpectrumFetch_workForDiaFixture() throws Exception {
		Path path=Path.of("src", "test", "resources", "rawdata", "230711_idleflow_400-1000mz_25mz_diaPasef_10sec.d");
		Assumptions.assumeTrue(Files.exists(path), "Fixture .d not present: "+path);

		BrukerTIMSFile file=new BrukerTIMSFile();
		file.openFile(path);

		ArrayList<ScanSummary> summaries=file.getScanSummaries(0.0f, Float.MAX_VALUE);
		assertTrue(!summaries.isEmpty(), "Expected scan summaries");

		ScanSummary ms1=summaries.stream().filter(ScanSummary::isPrecursor).findFirst().orElse(null);
		ScanSummary ms2=summaries.stream().filter(s -> !s.isPrecursor()).findFirst().orElse(null);
		assertNotNull(ms1, "Expected an MS1 summary");
		assertNotNull(ms2, "Expected an MS2 summary");
		assertTrue(ms1.getSpectrumName().startsWith("frame="));
		assertTrue(ms1.getSpectrumName().contains("scanStart="));
		assertTrue(ms1.getSpectrumName().contains("scanEnd="));
		assertTrue(ms2.getSpectrumName().startsWith("frame="));
		assertTrue(ms2.getSpectrumName().contains("scanStart="));
		assertTrue(ms2.getSpectrumName().contains("scanEnd="));

		assertNotNull(file.getSpectrum(ms1));
		AcquiredSpectrum ms2Spec=file.getSpectrum(ms2);
		assertNotNull(ms2Spec);
		assertEquals(FragmentScan.class, ms2Spec.getClass());

		file.close();
	}

	@Test
	void getSpectrum_fallbacksAndNull() throws Exception {
		Path path=Path.of("src", "test", "resources", "rawdata", "dda_test.d");
		Assumptions.assumeTrue(Files.exists(path), "Fixture .d not present: "+path);

		BrukerTIMSFile file=new BrukerTIMSFile();
		file.openFile(path);

		ArrayList<ScanSummary> summaries=file.getScanSummaries(0.0f, Float.MAX_VALUE);
		ScanSummary ms1=summaries.stream().filter(ScanSummary::isPrecursor).findFirst().orElse(null);
		ScanSummary ms2=summaries.stream().filter(s -> !s.isPrecursor()).findFirst().orElse(null);
		assertNotNull(ms1);
		assertNotNull(ms2);

		assertNull(file.getSpectrum(null), "Null summary should return null");

		ScanSummary missingMs1=new ScanSummary("missing", -999, ms1.getScanStartTime(), 0, ms1.getPrecursorMz(), true, ms1.getIonInjectionTime(),
				ms1.getIsolationWindowLower(), ms1.getIsolationWindowUpper(), ms1.getScanWindowLower(), ms1.getScanWindowUpper(), ms1.getCharge());
		AcquiredSpectrum ms1Spec=file.getSpectrum(missingMs1);
		assertNotNull(ms1Spec);
		assertEquals(PrecursorScan.class, ms1Spec.getClass());

		ScanSummary missingMs2=new ScanSummary("missing2", -999, ms2.getScanStartTime(), 0, ms2.getPrecursorMz(), false, ms2.getIonInjectionTime(),
				ms2.getIsolationWindowLower(), ms2.getIsolationWindowUpper(), ms2.getScanWindowLower(), ms2.getScanWindowUpper(), ms2.getCharge());
		AcquiredSpectrum ms2Spec=file.getSpectrum(missingMs2);
		assertNotNull(ms2Spec);
		assertEquals(FragmentScan.class, ms2Spec.getClass());

		file.close();
	}

	@Test
	void getScanSummaries_usesDefaultScanWindowWhenMetadataInvalid() throws Exception {
		Path path=Path.of("src", "test", "resources", "rawdata", "dda_test.d");
		Assumptions.assumeTrue(Files.exists(path), "Fixture .d not present: "+path);

		BrukerTIMSFile file=new BrukerTIMSFile();
		file.openFile(path);

		Map<String, String> fakeMeta=new LinkedHashMap<>();
		fakeMeta.put("meta.MzAcqRangeLower", "not-a-number");
		fakeMeta.put("meta.MzAcqRangeUpper", "also-bad");

		Field metadataField=BrukerTIMSFile.class.getDeclaredField("metadata");
		metadataField.setAccessible(true);
		metadataField.set(file, fakeMeta);

		ArrayList<ScanSummary> summaries=file.getScanSummaries(0.0f, Float.MAX_VALUE);
		assertTrue(!summaries.isEmpty());
		ScanSummary first=summaries.get(0);
		assertEquals(0.0, first.getScanWindowLower(), 1e-6);
		assertEquals(2000.0, first.getScanWindowUpper(), 1e-6);

		file.close();
	}
}
