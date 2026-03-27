package org.searlelab.msrawjava.gui.visualization;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class RawBrowserXicUtilsTest {

	@Test
	void sanitizeXicText_filtersUnsupportedCharacters() {
		String input="445.34, abc\n500.2\t600.1#@!";
		String sanitized=RawBrowserXicUtils.sanitizeXicText(input);
		assertEquals("445.34, 500.2 600.1", sanitized);
	}

	@Test
	void sanitizeXicText_collapsesCommaAndSpaceRuns() {
		String input=" 445.34,, , ,   500.2   ,,, 600.1 ";
		String sanitized=RawBrowserXicUtils.sanitizeXicText(input);
		assertEquals("445.34, 500.2, 600.1", sanitized);
	}

	@Test
	void sanitizeXicPasteChunk_keepsDelimiterSeparationWithoutTrim() {
		String chunk=",  , \n\t500.2,,, 600.1  ";
		String sanitized=RawBrowserXicUtils.sanitizeXicPasteChunk(chunk);
		assertEquals(", 500.2, 600.1 ", sanitized);
	}

	@Test
	void parseTargetMzs_supportsMixedDelimitersAndDeduplicates() {
		List<Double> parsed=RawBrowserXicUtils.parseTargetMzs("445.34, 500.2\n600.1 500.2 0 foo");
		assertEquals(List.of(445.34, 500.2, 600.1), parsed);
	}

	@Test
	void sumIntensityWithinTolerance_withPpmLikeTolerance() {
		double[] mz=new double[] {100.0, 100.01, 100.1};
		float[] intensity=new float[] {10.0f, 20.0f, 30.0f};
		double sum=RawBrowserXicUtils.sumIntensityWithinTolerance(mz, intensity, 100.0, 0.01);
		assertEquals(30.0, sum, 1e-8);
	}

	@Test
	void sumIntensityWithinTolerance_withDaTolerance() {
		double[] mz=new double[] {500.0, 500.35, 500.41, 501.0};
		float[] intensity=new float[] {100.0f, 40.0f, 25.0f, 10.0f};
		double sum=RawBrowserXicUtils.sumIntensityWithinTolerance(mz, intensity, 500.0, 0.4);
		assertEquals(140.0, sum, 1e-8);
	}
}
