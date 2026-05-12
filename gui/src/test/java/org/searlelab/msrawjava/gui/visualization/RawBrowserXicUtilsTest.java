package org.searlelab.msrawjava.gui.visualization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
		assertTrue(parsed.precursorTargets().stream().anyMatch(t -> t.label().equals("PEPTIDE++ [M]")));
		assertTrue(parsed.fragmentTargets().stream().anyMatch(t -> t.label().contains(" b1+")));
	}

	@Test
	void parseXicTargets_ordersFragmentIonsForLegend() {
		RawBrowserXicUtils.ParsedXicTargets parsed=RawBrowserXicUtils.parseXicTargets("PEPTIDE+3");
		assertEquals(24, parsed.fragmentTargets().size());
		assertEquals("PEPTIDE+++ b1+", parsed.fragmentTargets().get(0).label());
		assertEquals("PEPTIDE+++ b6+", parsed.fragmentTargets().get(5).label());
		assertEquals("PEPTIDE+++ y1+", parsed.fragmentTargets().get(6).label());
		assertEquals("PEPTIDE+++ y6+", parsed.fragmentTargets().get(11).label());
		assertEquals("PEPTIDE+++ b1++", parsed.fragmentTargets().get(12).label());
		assertEquals("PEPTIDE+++ b6++", parsed.fragmentTargets().get(17).label());
		assertEquals("PEPTIDE+++ y1++", parsed.fragmentTargets().get(18).label());
		assertEquals("PEPTIDE+++ y6++", parsed.fragmentTargets().get(23).label());
	}

	@Test
	void parseXicTargets_supportsFormulaAndNegativePeptideTokens() {
		RawBrowserXicUtils.ParsedXicTargets parsed=RawBrowserXicUtils.parseXicTargets("[C2H6SiO]6--, PEPTIDE--");
		assertTrue(parsed.precursorTargets().stream().anyMatch(t -> t.label().equals("C12H36O6Si6--")));
		assertTrue(parsed.precursorTargets().stream().anyMatch(t -> t.label().equals("PEPTIDE-- [M]")));
		assertFalse(parsed.fragmentTargets().stream().anyMatch(t -> t.label().equals("C12H36O6Si6--")));
		assertTrue(parsed.fragmentTargets().stream().anyMatch(t -> t.label().equals("PEPTIDE-- b1-")));
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
	void sanitizeXicText_retainsFormulaGroupingCharacters() {
		String sanitized=RawBrowserXicUtils.sanitizeXicText("{CH3}2, [C2H6SiO]6--");
		assertEquals("{CH3}2, [C2H6SiO]6--", sanitized);
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

	@Test
	void extractWeightedPointWithinTolerance_returnsIntensityWeightedDeltaMz() {
		double[] mz=new double[] {499.95, 500.10, 500.50};
		float[] intensity=new float[] {10.0f, 30.0f, 100.0f};

		RawBrowserXicUtils.XicPointExtraction point=RawBrowserXicUtils.extractWeightedPointWithinTolerance(mz, intensity, 500.0, 0.2);

		assertEquals(40.0, point.intensity, 1e-8);
		assertEquals(500.0625, point.observedMz, 1e-8);
		assertEquals(0.0625, point.deltaMz, 1e-8);
		assertTrue(point.hasSignal());
	}

	@Test
	void extractWeightedPointWithinTolerance_marksNoSignalAsMissingDelta() {
		RawBrowserXicUtils.XicPointExtraction point=RawBrowserXicUtils.extractWeightedPointWithinTolerance(new double[] {501.0}, new float[] {25.0f}, 500.0,
				0.1);

		assertEquals(0.0, point.intensity, 1e-8);
		assertTrue(Double.isNaN(point.observedMz));
		assertTrue(Double.isNaN(point.deltaMz));
		assertFalse(point.hasSignal());
	}

	@Test
	void deltaForDisplay_convertsOnlyPpmToleranceToPpmScale() {
		assertEquals(20.0, RawBrowserXicUtils.deltaForDisplay(0.01, 500.0, new XicToleranceOption("10 ppm", XicToleranceOption.Unit.PPM, 10.0)), 1e-8);
		assertEquals(0.01, RawBrowserXicUtils.deltaForDisplay(0.01, 500.0, new XicToleranceOption("0.4 m/z", XicToleranceOption.Unit.DA, 0.4)), 1e-8);
	}

	@Test
	void isTargetInScanWindow_requiresMzWithinFiniteWindowButDefaultsOpenForMissingBounds() {
		assertTrue(RawBrowserXicUtils.isTargetInScanWindow(500.0, 400.0, 600.0));
		assertTrue(RawBrowserXicUtils.isTargetInScanWindow(500.0, Double.NaN, 600.0));
		assertTrue(RawBrowserXicUtils.isTargetInScanWindow(500.0, 600.0, 400.0));
		assertFalse(RawBrowserXicUtils.isTargetInScanWindow(399.9, 400.0, 600.0));
		assertFalse(RawBrowserXicUtils.isTargetInScanWindow(600.1, 400.0, 600.0));
	}
}
