package org.searlelab.msrawjava.io.mzml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.msrawjava.io.ConversionParameters;
import org.searlelab.msrawjava.io.OutputType;
import org.searlelab.msrawjava.io.RawFileConverters;
import org.searlelab.msrawjava.io.encyclopedia.EncyclopeDIAFile;
import org.searlelab.msrawjava.logging.LoggingProgressIndicator;
import org.searlelab.msrawjava.model.Range;
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

			// Step 4: Compare counts
			Counts roundTripCounts=readDiaCounts(roundTripDia);

			assertEquals(origCounts.precursorCount, roundTripCounts.precursorCount, "Precursor count should be preserved through mzML round trip");
			assertEquals(origCounts.fragmentCount, roundTripCounts.fragmentCount, "Fragment count should be preserved through mzML round trip");
			assertEquals(origCounts.rangeCount, roundTripCounts.rangeCount, "Range count should be preserved through mzML round trip");
			assertEquals(origCounts.gradientLength, roundTripCounts.gradientLength, 1.0f, "Gradient length should be approximately preserved");
		} finally {
			pool.close();
		}
	}

	private static Counts readDiaCounts(Path file) throws Exception {
		EncyclopeDIAFile dia=new EncyclopeDIAFile();
		dia.openFile(file.toFile());
		try {
			Map<Range, WindowData> ranges=dia.getRanges();
			int precursors=dia.getPrecursors(0f, Float.MAX_VALUE).size();
			int fragments=dia.getStripes(new Range(0f, Float.MAX_VALUE), 0f, Float.MAX_VALUE, false).size();
			float gradient=dia.getGradientLength();
			return new Counts(ranges.size(), precursors, fragments, gradient);
		} finally {
			dia.close();
		}
	}

	private static final class Counts {
		final int rangeCount;
		final int precursorCount;
		final int fragmentCount;
		final float gradientLength;

		Counts(int rangeCount, int precursorCount, int fragmentCount, float gradientLength) {
			this.rangeCount=rangeCount;
			this.precursorCount=precursorCount;
			this.fragmentCount=fragmentCount;
			this.gradientLength=gradientLength;
		}
	}
}
