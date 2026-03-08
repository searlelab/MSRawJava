package org.searlelab.msrawjava.io.mzml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.model.AcquiredSpectrum;
import org.searlelab.msrawjava.model.ScanSummary;

/**
 * GUI non-regression guardrail for mzML random-access spectrum fetches.
 */
class MzmlRandomAccessIT {

	private static final Path INPUT=Path.of("src", "test", "resources", "rawdata", "HeLa_16mzst_29to31min.mzML");

	@Test
	void randomAccessSpectrumFetch_returnsExpectedSummarySpectrum() throws Exception {
		Assumptions.assumeTrue(Files.exists(INPUT), "Fixture mzML not present: "+INPUT);

		MzmlFile mzml=new MzmlFile();
		mzml.openFile(INPUT.toFile());
		try {
			ArrayList<ScanSummary> summaries=mzml.getScanSummaries(-Float.MAX_VALUE, Float.MAX_VALUE);
			assertTrue(summaries.size()>20, "Expected enough scans to exercise random access");

			ArrayList<ScanSummary> sampled=sampleEvenly(summaries, 64);
			for (ScanSummary summary : sampled) {
				AcquiredSpectrum spectrum=mzml.getSpectrum(summary);
				assertNotNull(spectrum, "Spectrum should be available for summary index "+summary.getSpectrumIndex());
				assertEquals(summary.getSpectrumIndex(), spectrum.getSpectrumIndex(), "SpectrumIndex should match summary");
				assertEquals(summary.getSpectrumName(), spectrum.getSpectrumName(), "SpectrumName should match summary");
			}
		} finally {
			mzml.close();
		}
	}

	private static ArrayList<ScanSummary> sampleEvenly(ArrayList<ScanSummary> summaries, int target) {
		ArrayList<ScanSummary> sampled=new ArrayList<>();
		if (summaries.isEmpty()) return sampled;
		if (summaries.size()<=target) {
			sampled.addAll(summaries);
			return sampled;
		}
		for (int i=0; i<target; i++) {
			int idx=(int)Math.round(i*(summaries.size()-1.0)/(target-1.0));
			sampled.add(summaries.get(idx));
		}
		return sampled;
	}
}
