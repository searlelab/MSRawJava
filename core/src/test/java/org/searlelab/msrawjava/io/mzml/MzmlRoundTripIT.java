package org.searlelab.msrawjava.io.mzml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.msrawjava.io.ConversionParameters;
import org.searlelab.msrawjava.io.OutputType;
import org.searlelab.msrawjava.io.RawFileConverters;
import org.searlelab.msrawjava.io.encyclopedia.EncyclopeDIAFile;
import org.searlelab.msrawjava.io.utils.Pair;
import org.searlelab.msrawjava.logging.LoggingProgressIndicator;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.ScanSummary;
import org.searlelab.msrawjava.model.WindowData;
import org.searlelab.msrawjava.threading.ProcessingThreadPool;

/**
 * Round-trip integration test: DIA -> mzML -> DIA, then compare the two DIA files for consistency.
 */
class MzmlRoundTripIT {

	private static final Path INPUT=Path.of("src", "test", "resources", "rawdata", "HeLa_16mzst_29to31min.dia");

	@TempDir
	Path tmp;

	@Test
	void diaToMzmlToDia_roundTripPreservesCounts() throws Exception {
		Assumptions.assumeTrue(Files.exists(INPUT), "Fixture .dia not present: "+INPUT);

		Path mzmlDir=tmp.resolve("mzml");
		Path diaDir=tmp.resolve("dia");
		Files.createDirectories(mzmlDir);
		Files.createDirectories(diaDir);

		ProcessingThreadPool pool=ProcessingThreadPool.createDefault();
		try {
			LoggingProgressIndicator indicator=new LoggingProgressIndicator(LoggingProgressIndicator.Mode.SILENT, false);

			// Step 1: Open original .dia and get counts
			Counts origCounts=readDiaCounts(INPUT);

			// Step 2: DIA -> mzML
			ConversionParameters mzmlParams=ConversionParameters.builder().outType(OutputType.mzML).build();
			EncyclopeDIAFile origDia=new EncyclopeDIAFile();
			origDia.openFile(INPUT.toFile());
			try {
				RawFileConverters.writeStandard(pool, origDia, mzmlDir, mzmlParams, indicator);
			} finally {
				origDia.close();
			}
			Path mzmlFile=OutputType.mzML.getOutputFilePath(mzmlDir, INPUT.getFileName().toString());
			assertTrue(Files.exists(mzmlFile), "mzML output should exist");
			assertTrue(Files.size(mzmlFile)>0L, "mzML output should not be empty");

			// Step 3: mzML -> DIA
			ConversionParameters diaParams=ConversionParameters.builder().outType(OutputType.EncyclopeDIA).build();
			MzmlFile mzml=new MzmlFile();
			mzml.openFile(mzmlFile.toFile());
			try {
				RawFileConverters.writeStandard(pool, mzml, diaDir, diaParams, indicator);
			} finally {
				mzml.close();
			}
			Path roundTripDia=OutputType.EncyclopeDIA.getOutputFilePath(diaDir, mzmlFile.getFileName().toString());
			assertTrue(Files.exists(roundTripDia), "Round-trip .dia output should exist");
			assertTrue(Files.size(roundTripDia)>0L, "Round-trip .dia output should not be empty");

			// Step 4: Compare counts
			Counts roundTripCounts=readDiaCounts(roundTripDia);

			assertEquals(origCounts.precursorCount, roundTripCounts.precursorCount, "Precursor count should be preserved through mzML round trip");
			assertEquals(origCounts.fragmentCount, roundTripCounts.fragmentCount, "Fragment count should be preserved through mzML round trip");
			assertEquals(origCounts.rangeCount, roundTripCounts.rangeCount, "Range count should be preserved through mzML round trip");
			assertEquals(origCounts.ticPointCount, roundTripCounts.ticPointCount, "TIC point count should be preserved through mzML round trip");
			assertEquals(origCounts.fragmentWindowHistogram, roundTripCounts.fragmentWindowHistogram,
					"MS2 counts per isolation window should be preserved through mzML round trip");
			assertTrue(roundTripCounts.metadataTotalPrecursorTic!=null&&!roundTripCounts.metadataTotalPrecursorTic.isBlank(),
					"Round-trip DIA should include totalPrecursorTIC metadata");

			assertEquals(origCounts.precursorRtMin, roundTripCounts.precursorRtMin, 0.001f, "Precursor RT min should be preserved");
			assertEquals(origCounts.precursorRtMax, roundTripCounts.precursorRtMax, 0.001f, "Precursor RT max should be preserved");
			assertEquals(origCounts.fragmentRtMin, roundTripCounts.fragmentRtMin, 0.001f, "Fragment RT min should be preserved");
			assertEquals(origCounts.fragmentRtMax, roundTripCounts.fragmentRtMax, 0.001f, "Fragment RT max should be preserved");
			assertTrue(origCounts.totalTic>0.0f, "Original total TIC should be positive");
			assertTrue(roundTripCounts.totalTic>0.0f, "Round-trip total TIC should be positive");
			assertRelativelyClose(origCounts.totalTic, origCounts.ticTraceSum, 1e-6, "Original DIA getTIC should match MS1 TIC trace sum");
			assertRelativelyClose(roundTripCounts.totalTic, roundTripCounts.ticTraceSum, 1e-6, "Round-trip DIA getTIC should match MS1 TIC trace sum");
			assertRelativelyClose(origCounts.ticTraceSum, roundTripCounts.ticTraceSum, 1e-5, "Round-trip MS1 TIC total drift should stay minimal");
			assertRelativelyClose(Double.parseDouble(roundTripCounts.metadataTotalPrecursorTic), roundTripCounts.ticTraceSum, 1e-6,
					"Round-trip metadata totalPrecursorTIC should match MS1 TIC trace sum");
			for (int i=0; i<origCounts.ticPointCount; i++) {
				assertEquals(origCounts.ticRts[i], roundTripCounts.ticRts[i], 0.002f, "TIC RT at index "+i+" should be preserved");
				assertRelativelyClose(origCounts.ticValues[i], roundTripCounts.ticValues[i], 1e-5, "TIC value drift at index "+i+" should stay minimal");
			}
			assertEquals(origCounts.gradientLength, roundTripCounts.gradientLength, 1.0f, "Gradient length should be approximately preserved");

			assertEquals(origCounts.ranges.size(), roundTripCounts.ranges.size(), "Window definitions should be preserved");
			for (int i=0; i<origCounts.ranges.size(); i++) {
				RangeFingerprint orig=origCounts.ranges.get(i);
				RangeFingerprint rt=roundTripCounts.ranges.get(i);
				assertEquals(orig.start, rt.start, 0.001f, "Window start should be preserved");
				assertEquals(orig.stop, rt.stop, 0.001f, "Window stop should be preserved");
			}
		} finally {
			pool.close();
		}
	}

	private static Counts readDiaCounts(Path file) throws Exception {
		EncyclopeDIAFile dia=new EncyclopeDIAFile();
		dia.openFile(file.toFile());
		try {
			Map<Range, WindowData> ranges=dia.getRanges();
			ArrayList<ScanSummary> summaries=dia.getScanSummaries(0f, Float.MAX_VALUE);
			int precursors=0;
			int fragments=0;
			float precursorRtMin=Float.MAX_VALUE;
			float precursorRtMax=-Float.MAX_VALUE;
			float fragmentRtMin=Float.MAX_VALUE;
			float fragmentRtMax=-Float.MAX_VALUE;
			HashMap<String, Integer> fragmentWindowHistogram=new HashMap<>();
				for (ScanSummary summary : summaries) {
					if (summary.isPrecursor()) {
						precursors++;
						precursorRtMin=Math.min(precursorRtMin, summary.getScanStartTime());
						precursorRtMax=Math.max(precursorRtMax, summary.getScanStartTime());
				} else {
					fragments++;
					fragmentRtMin=Math.min(fragmentRtMin, summary.getScanStartTime());
					fragmentRtMax=Math.max(fragmentRtMax, summary.getScanStartTime());
					String key=windowKey(summary.getIsolationWindowLower(), summary.getIsolationWindowUpper());
						fragmentWindowHistogram.merge(key, 1, Integer::sum);
					}
				}
				Pair<float[], float[]> ticTrace=dia.getTICTrace();
				String metadataTotalPrecursorTic=dia.getMetadata().get(EncyclopeDIAFile.TOTAL_PRECURSOR_TIC_ATTRIBUTE);
				float totalTic=dia.getTIC();
				float gradient=dia.getGradientLength();
				return new Counts(ranges.size(), precursors, fragments, gradient, totalTic, ticTrace.x.length, precursorRtMin, precursorRtMax, fragmentRtMin,
						fragmentRtMax, fragmentWindowHistogram, sortedRangeFingerprints(ranges), metadataTotalPrecursorTic, ticTrace.x, ticTrace.y,
						sum(ticTrace.y));
		} finally {
			dia.close();
		}
	}

	private static double sum(float[] values) {
		double total=0.0;
		for (float value : values) {
			total+=value;
		}
		return total;
	}

	private static void assertRelativelyClose(double expected, double actual, double maxRelativeError, String message) {
		double scale=Math.max(1.0, Math.abs(expected));
		double relative=Math.abs(expected-actual)/scale;
		assertTrue(relative<=maxRelativeError, message+" (relative error="+relative+")");
	}

	private static String windowKey(double lower, double upper) {
		return String.format(Locale.ROOT, "%.4f|%.4f", lower, upper);
	}

	private static ArrayList<RangeFingerprint> sortedRangeFingerprints(Map<Range, WindowData> ranges) {
		ArrayList<RangeFingerprint> out=new ArrayList<>();
		for (Map.Entry<Range, WindowData> e : ranges.entrySet()) {
			Range r=e.getKey();
			out.add(new RangeFingerprint(r.getStart(), r.getStop()));
		}
		out.sort((a, b) -> {
			int c=Float.compare(a.start, b.start);
			if (c!=0) return c;
			return Float.compare(a.stop, b.stop);
		});
		return out;
	}

	private static final class Counts {
		final int rangeCount;
		final int precursorCount;
		final int fragmentCount;
		final float gradientLength;
		final float totalTic;
		final int ticPointCount;
		final float precursorRtMin;
		final float precursorRtMax;
		final float fragmentRtMin;
		final float fragmentRtMax;
		final Map<String, Integer> fragmentWindowHistogram;
		final List<RangeFingerprint> ranges;
		final String metadataTotalPrecursorTic;
		final float[] ticRts;
		final float[] ticValues;
		final double ticTraceSum;

		Counts(int rangeCount, int precursorCount, int fragmentCount, float gradientLength, float totalTic, int ticPointCount, float precursorRtMin,
				float precursorRtMax, float fragmentRtMin, float fragmentRtMax, Map<String, Integer> fragmentWindowHistogram, List<RangeFingerprint> ranges,
				String metadataTotalPrecursorTic, float[] ticRts, float[] ticValues, double ticTraceSum) {
			this.rangeCount=rangeCount;
			this.precursorCount=precursorCount;
			this.fragmentCount=fragmentCount;
			this.gradientLength=gradientLength;
			this.totalTic=totalTic;
			this.ticPointCount=ticPointCount;
			this.precursorRtMin=precursorRtMin;
			this.precursorRtMax=precursorRtMax;
			this.fragmentRtMin=fragmentRtMin;
			this.fragmentRtMax=fragmentRtMax;
			this.fragmentWindowHistogram=fragmentWindowHistogram;
			this.ranges=ranges;
			this.metadataTotalPrecursorTic=metadataTotalPrecursorTic;
			this.ticRts=ticRts;
			this.ticValues=ticValues;
			this.ticTraceSum=ticTraceSum;
		}
	}

	private static final class RangeFingerprint {
		final float start;
		final float stop;

		RangeFingerprint(float start, float stop) {
			this.start=start;
			this.stop=stop;
		}
	}
}
