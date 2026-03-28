package org.searlelab.msrawjava.gui.visualization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class RawBrowserXicUtilsTest {

	@Test
	void tokenizeQueryTokens_isBracketAwareAndPreservesNamedModSpaces() {
		List<String> tokens=RawBrowserXicUtils
				.tokenizeQueryTokens("445.34, _LTDC[Carbamidomethyl (C)]VVM[Oxidation (M)]R_+2  PEPTIDE++");
		assertEquals(List.of("445.34", "_LTDC[Carbamidomethyl (C)]VVM[Oxidation (M)]R_+2", "PEPTIDE++"), tokens);
	}

	@Test
	void parseXicTargets_supportsMixedNumericAndPeptideTokens() {
		RawBrowserXicUtils.ParsedXicTargets parsed=RawBrowserXicUtils.parseXicTargets("445.34, PEPTIDE+2");
		assertEquals(4, parsed.precursorTargets().size());
		assertEquals(13, parsed.fragmentTargets().size());

		assertEquals("XIC 445.3400", parsed.precursorTargets().get(0).label());
		assertEquals("XIC 445.3400", parsed.fragmentTargets().get(0).label());
		assertTrue(parsed.precursorTargets().stream().anyMatch(t -> t.label().equals("PEPTIDE+2 [M]")));
		assertTrue(parsed.fragmentTargets().stream().anyMatch(t -> t.label().contains(" b1+")));
	}

	@Test
	void parseXicTargets_ordersFragmentIonsForLegend() {
		RawBrowserXicUtils.ParsedXicTargets parsed=RawBrowserXicUtils.parseXicTargets("PEPTIDE+3");
		assertEquals(24, parsed.fragmentTargets().size());
		assertEquals("PEPTIDE+3 b1+", parsed.fragmentTargets().get(0).label());
		assertEquals("PEPTIDE+3 b6+", parsed.fragmentTargets().get(5).label());
		assertEquals("PEPTIDE+3 y1+", parsed.fragmentTargets().get(6).label());
		assertEquals("PEPTIDE+3 y6+", parsed.fragmentTargets().get(11).label());
		assertEquals("PEPTIDE+3 b1++", parsed.fragmentTargets().get(12).label());
		assertEquals("PEPTIDE+3 b6++", parsed.fragmentTargets().get(17).label());
		assertEquals("PEPTIDE+3 y1++", parsed.fragmentTargets().get(18).label());
		assertEquals("PEPTIDE+3 y6++", parsed.fragmentTargets().get(23).label());
	}

	@Test
	void sanitizeXicPasteChunk_normalizesLineBreaksAndDelimiterRuns() {
		String chunk="445.34,\nPEPTIDER++\t[Oxidation (M)]   ,, 500.2";
		String sanitized=RawBrowserXicUtils.sanitizeXicPasteChunk(chunk);
		assertEquals("445.34, PEPTIDER++ [Oxidation (M)], 500.2", sanitized);
	}

	@Test
	void sanitizeXicText_retainsPeptideCharacters() {
		String input="_PEPTIDER++_, 500.2\nRLSISS[+79.966331]";
		String sanitized=RawBrowserXicUtils.sanitizeXicText(input);
		assertEquals("_PEPTIDER++_, 500.2 RLSISS[+79.966331]", sanitized);
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
