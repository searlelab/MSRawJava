package org.searlelab.msrawjava.io.encyclopedia;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.msrawjava.io.ConversionParameters;
import org.searlelab.msrawjava.io.OutputType;
import org.searlelab.msrawjava.io.RawFileConverters;
import org.searlelab.msrawjava.logging.LoggingProgressIndicator;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.WindowData;
import org.searlelab.msrawjava.threading.ProcessingThreadPool;

class EncyclopeDIAFileRoundTripIT {

	private static final Path INPUT=Path.of("src", "test", "resources", "rawdata", "HeLa_16mzst_29to31min.dia");

	@TempDir
	Path tmp;

	@Test
	void diaToDia_roundTripPreservesBasicCounts() throws Exception {
		Assumptions.assumeTrue(Files.exists(INPUT), "Fixture .dia not present: "+INPUT);

		Path out1=tmp.resolve("round1");
		Path out2=tmp.resolve("round2");
		Files.createDirectories(out1);
		Files.createDirectories(out2);

		ProcessingThreadPool pool=ProcessingThreadPool.createDefault();
		try {
			ConversionParameters params=ConversionParameters.builder().outType(OutputType.EncyclopeDIA).build();
			LoggingProgressIndicator indicator=new LoggingProgressIndicator(LoggingProgressIndicator.Mode.SILENT, false);

			Path round1File=runConvert(pool, params, indicator, INPUT, out1);
			DiaCounts round1=readCounts(round1File);

			Path round2File=runConvert(pool, params, indicator, round1File, out2);
			DiaCounts round2=readCounts(round2File);

			assertEquals(round1.rangeCount, round2.rangeCount, "Range counts should be stable after re-encode");
			assertEquals(round1.precursorCount, round2.precursorCount, "Precursor counts should be stable after re-encode");
			assertEquals(round1.fragmentCount, round2.fragmentCount, "Fragment counts should be stable after re-encode");
			assertEquals(round1.gradientLengthSec, round2.gradientLengthSec, 0.001f, "Gradient length should remain stable");
		} finally {
			pool.close();
		}
	}

	private static Path runConvert(ProcessingThreadPool pool, ConversionParameters params, LoggingProgressIndicator indicator, Path input, Path outDir)
			throws Exception {
		EncyclopeDIAFile dia=new EncyclopeDIAFile();
		dia.openFile(input.toFile());
		try {
			RawFileConverters.writeStandard(pool, dia, outDir, params, indicator);
		} finally {
			dia.close();
		}
		return OutputType.EncyclopeDIA.getOutputFilePath(outDir, input.getFileName().toString());
	}

	private static DiaCounts readCounts(Path file) throws Exception {
		EncyclopeDIAFile dia=new EncyclopeDIAFile();
		dia.openFile(file.toFile());
		try {
			Map<Range, WindowData> ranges=dia.getRanges();
			int precursors=dia.getPrecursors(0f, Float.MAX_VALUE).size();
			int fragments=dia.getStripes(new Range(0f, Float.MAX_VALUE), 0f, Float.MAX_VALUE, false).size();
			float gradient=dia.getGradientLength();
			return new DiaCounts(ranges.size(), precursors, fragments, gradient);
		} finally {
			dia.close();
		}
	}

	private static final class DiaCounts {
		private final int rangeCount;
		private final int precursorCount;
		private final int fragmentCount;
		private final float gradientLengthSec;

		private DiaCounts(int rangeCount, int precursorCount, int fragmentCount, float gradientLengthSec) {
			this.rangeCount=rangeCount;
			this.precursorCount=precursorCount;
			this.fragmentCount=fragmentCount;
			this.gradientLengthSec=gradientLengthSec;
		}
	}
}
