package org.searlelab.msrawjava.io.encyclopedia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.ScanSummary;
import org.searlelab.msrawjava.model.WindowData;

import gnu.trove.map.hash.TIntObjectHashMap;

class EncyclopeDIAFileAdditionalTest {

	@TempDir
	Path tmp;

	@Test
	void writesAndReloadsRangesFractionsAndIonMobility() throws Exception {
		EncyclopeDIAFile dia=new EncyclopeDIAFile();
		dia.openFile();

		Range mzRange=new Range(400.0f, 500.0f);
		Range imsRange=new Range(1.0f, 1.5f);
		Range rtRange=new Range(10.0f, 20.0f);
		WindowData window=new WindowData(0.25f, 2, Optional.of(imsRange), Optional.of(rtRange));
		HashMap<Range, WindowData> ranges=new HashMap<>();
		ranges.put(mzRange, window);
		dia.setRanges(ranges);

		TIntObjectHashMap<String> fractions=new TIntObjectHashMap<>();
		fractions.put(1, "FracA");
		fractions.put(2, "FracB");
		dia.setFractionNames(fractions);

		dia.addMetadata("Instrument", null);

		ArrayList<PrecursorScan> ms1s=new ArrayList<>();
		ms1s.add(new PrecursorScan("ms1", 1, 12.0f, 1, 100.0, 900.0, 5.0f, new double[] {100.0, 200.0}, new float[] {10.0f, 20.0f}, new float[] {1.1f, 1.2f}));

		ArrayList<FragmentScan> ms2s=new ArrayList<>();
		ms2s.add(new FragmentScan("ms2", "prec", 2, 450.0, 13.0f, 2, 4.0f, 400.0, 500.0, new double[] {150.0, 250.0}, new float[] {5.0f, 8.0f},
				new float[] {1.0f, 1.1f}, (byte)2, 0.0, 2000.0));

		dia.addSpectra(ms1s, ms2s);

		Path out=tmp.resolve("extra.dia");
		dia.saveAsFile(out.toFile());
		dia.close();

		EncyclopeDIAFile reopened=new EncyclopeDIAFile();
		reopened.openFile(out.toFile());

		Map<Range, WindowData> loadedRanges=reopened.getRanges();
		assertTrue(loadedRanges.containsKey(mzRange));
		WindowData loaded=loadedRanges.get(mzRange);
		assertEquals(imsRange, loaded.getIonMobilityRange().orElseThrow());
		assertEquals(rtRange, loaded.getRtRange().orElseThrow());

		HashMap<String, String> meta=reopened.getMetadata();
		assertEquals("unknown", meta.get("Instrument"));

		ArrayList<PrecursorScan> precursors=reopened.getPrecursors(0.0f, 100.0f);
		assertEquals(1, precursors.size());
		assertTrue(precursors.get(0).getIonMobilityArray().isPresent());
		assertEquals(2, precursors.get(0).getIonMobilityArray().orElseThrow().length);

		ArrayList<FragmentScan> stripes=reopened.getStripes(450.0, 0.0f, 100.0f, false);
		assertEquals(1, stripes.size());
		assertTrue(stripes.get(0).getIonMobilityArray().isPresent());

		reopened.addMetadata("ShouldNotPersist", "nope");
		HashMap<String, String> metaAfter=reopened.getMetadata();
		assertFalse(metaAfter.containsKey("ShouldNotPersist"));

		assertFractionNamesLoaded(reopened, 2);
		reopened.close();
	}

	@Test
	void getSpectrumAndWritableTempFilePaths_workOnReadOnly() throws Exception {
		File diaFile=createSimpleDia();
		EncyclopeDIAFile file=new EncyclopeDIAFile();
		file.openFile(diaFile);

		assertTrue(file.isOpen());
		assertEquals(diaFile, file.getFile());
		assertEquals("source.d", file.getOriginalFileName());

		ScanSummary precursorSummary=new ScanSummary("ms1", 1, 5.0f, 0, 0.0, true, null, 100.0, 900.0, 100.0, 900.0, (byte)0);
		ScanSummary fragmentSummary=new ScanSummary("ms2", 2, 6.0f, 0, 450.0, false, null, 400.0, 500.0, 400.0, 500.0, (byte)2);

		assertNotNull(file.getSpectrum(precursorSummary));
		assertNotNull(file.getSpectrum(fragmentSummary));

		invokeEnsureWritableTempFile(file);
		assertFalse(isReadOnly(file));
		assertNotNull(getTempFile(file));

		file.close();
	}

	@Test
	void scanSummariesAndTicHelpers_reportExpectedValues() throws Exception {
		File diaFile=createSimpleDia();
		EncyclopeDIAFile file=new EncyclopeDIAFile();
		file.openFile(diaFile);

		ArrayList<ScanSummary> summaries=file.getScanSummaries(0.0f, 100.0f);
		assertEquals(2, summaries.size());
		assertTrue(summaries.stream().anyMatch(ScanSummary::isPrecursor));
		assertTrue(summaries.stream().anyMatch(s -> !s.isPrecursor()));

		assertTrue(file.getGradientLength()>0.0f);
		assertTrue(file.getTIC()>=0.0f);
		assertNotNull(file.getTICTrace());

		file.close();
	}

	private static void assertFractionNamesLoaded(EncyclopeDIAFile file, int expectedSize) throws Exception {
		Field field=EncyclopeDIAFile.class.getDeclaredField("fractionNames");
		field.setAccessible(true);
		Object map=field.get(file);
		assertNotNull(map);
		int size=(int)map.getClass().getMethod("size").invoke(map);
		assertEquals(expectedSize, size);
	}

	private File createSimpleDia() throws Exception {
		EncyclopeDIAFile dia=new EncyclopeDIAFile();
		dia.openFile();
		dia.setFileName("source.d", "/data/source.d");

		ArrayList<PrecursorScan> ms1s=new ArrayList<>();
		ms1s.add(new PrecursorScan("ms1", 1, 5.0f, 0, 100.0, 900.0, null, new double[] {100.0}, new float[] {10.0f}, null));

		ArrayList<FragmentScan> ms2s=new ArrayList<>();
		ms2s.add(new FragmentScan("ms2", "prec", 2, 450.0, 6.0f, 0, null, 400.0, 500.0, new double[] {450.0}, new float[] {5.0f}, null, (byte)2, 0.0, 2000.0));

		dia.addSpectra(ms1s, ms2s);

		Path out=tmp.resolve("simple.dia");
		dia.saveAsFile(out.toFile());
		dia.close();
		return out.toFile();
	}

	private static void invokeEnsureWritableTempFile(EncyclopeDIAFile file) throws Exception {
		var method=EncyclopeDIAFile.class.getDeclaredMethod("ensureWritableTempFile");
		method.setAccessible(true);
		method.invoke(file);
	}

	private static boolean isReadOnly(EncyclopeDIAFile file) throws Exception {
		Field field=EncyclopeDIAFile.class.getDeclaredField("readOnly");
		field.setAccessible(true);
		return (boolean)field.get(file);
	}

	private static File getTempFile(EncyclopeDIAFile file) throws Exception {
		Field field=EncyclopeDIAFile.class.getDeclaredField("tempFile");
		field.setAccessible(true);
		return (File)field.get(file);
	}
}
