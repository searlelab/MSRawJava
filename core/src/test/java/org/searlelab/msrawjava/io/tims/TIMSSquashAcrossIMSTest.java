package org.searlelab.msrawjava.io.tims;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.model.PeakWithIMS;

class TIMSSquashAcrossIMSTest {
	private static ArrayList<PeakWithIMS> points(PeakWithIMS... points) {
		return new ArrayList<>(List.of(points));
	}

	@Test
	void weightsHalfMaxCoordinatesAcrossAllIMSButSumsAndConsumesEveryPositivePoint() {
		ArrayList<PeakWithIMS> input=points(new PeakWithIMS(500, 100, 1), new PeakWithIMS(500.001, 50, 2),
				new PeakWithIMS(499.999, 25, 3));
		PeakWithIMS result=TIMSPeakPicker.squashAcrossIMS(input).get(0);
		assertEquals((500.0*100+500.001*50)/150, result.mz);
		assertEquals((float)(200.0/150), result.ims);
		assertEquals(175, result.intensity);
		assertTrue(input.stream().noneMatch(PeakWithIMS::isAvailable));
	}

	@Test
	void halfMaxUsesInclusiveFloatComparisonAndAdjacentValues() {
		float below=Math.nextDown(50f), above=Math.nextUp(50f);
		ArrayList<PeakWithIMS> input=points(new PeakWithIMS(500, 100, 1), new PeakWithIMS(500, below, 4),
				new PeakWithIMS(500, 50, 2), new PeakWithIMS(500, above, 3));
		PeakWithIMS result=TIMSPeakPicker.squashAcrossIMS(input).get(0);
		assertEquals((float)((100.0+50.0*2+(double)above*3)/(150.0+above)), result.ims);
		assertEquals((float)(150.0+below+above), result.intensity);
		assertEquals(0f, 0.5f*Float.MIN_VALUE);
		ArrayList<PeakWithIMS> tiny=points(new PeakWithIMS(500, Float.MIN_VALUE, 1), new PeakWithIMS(500, Float.MIN_VALUE, 3));
		PeakWithIMS subnormal=TIMSPeakPicker.squashAcrossIMS(tiny).get(0);
		assertEquals(2f, subnormal.ims);
		assertEquals(2*Float.MIN_VALUE, subnormal.intensity);
	}

	@Test
	void doubleArithmeticPreservesSmallIntensityContributionsAndAvoidsFloatProductOverflow() {
		ArrayList<PeakWithIMS> input=points(new PeakWithIMS(500, 16777216, 0.5f));
		for (int i=0; i<2048; i++) input.add(new PeakWithIMS(500, 1, 1));
		assertEquals(16779264f, TIMSPeakPicker.squashAcrossIMS(input).get(0).intensity);
		PeakWithIMS high=TIMSPeakPicker.squashAcrossIMS(points(new PeakWithIMS(500, Float.MAX_VALUE, 2))).get(0);
		assertEquals(500, high.mz);
		assertEquals(2, high.ims);
	}

	@Test
	void excludesNaNNonpositiveAndInitiallyUnavailablePoints() {
		PeakWithIMS off=new PeakWithIMS(500, 1000, 2);
		off.turnOff();
		ArrayList<PeakWithIMS> input=points(off, new PeakWithIMS(500, Float.NaN, 1), new PeakWithIMS(500, 0, 1),
				new PeakWithIMS(500, -0.0f, 1), new PeakWithIMS(500, -1, 1), new PeakWithIMS(500, 10, 1));
		assertEquals(10, TIMSPeakPicker.squashAcrossIMS(input).get(0).intensity);
		for (PeakWithIMS p : input) assertEquals(p!=off&&!(p.intensity>0), p.isAvailable());
		assertTrue(TIMSPeakPicker.squashAcrossIMS(points(new PeakWithIMS(500, Float.NaN, 1))).isEmpty());
	}

	@Test
	void includesExactMzEdgesAndExcludesAdjacentOutsideValues() {
		double width=TIMSPeakPicker.tolerance.getToleranceInMz(500, 500)/2;
		double left=500-width, right=500+width;
		ArrayList<PeakWithIMS> input=points(new PeakWithIMS(500, 100, 1), new PeakWithIMS(left, 2, 1),
				new PeakWithIMS(right, 3, 1), new PeakWithIMS(Math.nextDown(left), 4, 1),
				new PeakWithIMS(Math.nextUp(right), 5, 1));
		ArrayList<PeakWithIMS> result=TIMSPeakPicker.squashAcrossIMS(input);
		assertEquals(3, result.size());
		assertEquals(105, result.get(1).intensity);
		TIMSSquashReference.verify(new PeakPickerFixture(points(new PeakWithIMS(500, 100, 1),
				new PeakWithIMS(Math.nextUp(left), 1, 1), new PeakWithIMS(Math.nextDown(right), 1, 1))), "inside edges");
	}

	@Test
	void fixedWindowDoesNotChainThroughNeighborsOrRecenterOnWeightedMean() {
		double w=TIMSPeakPicker.tolerance.getToleranceInMz(500, 500)/2;
		ArrayList<PeakWithIMS> input=points(new PeakWithIMS(500, 100, 1), new PeakWithIMS(500+0.9*w, 90, 2),
				new PeakWithIMS(500+1.4*w, 80, 3), new PeakWithIMS(500+2.2*w, 70, 4));
		ArrayList<PeakWithIMS> result=TIMSPeakPicker.squashAcrossIMS(input);
		assertEquals(2, result.size());
		assertEquals(190, result.get(0).intensity);
		assertEquals(150, result.get(1).intensity);
	}

	@Test
	void aliasesCountOnceWhileEqualObjectsAndSingletonsRemainDistinct() {
		assertTrue(TIMSPeakPicker.squashAcrossIMS(new ArrayList<>()).isEmpty());
		PeakWithIMS p=new PeakWithIMS(500, 10, 1);
		ArrayList<PeakWithIMS> input=points(p, p, new PeakWithIMS(500, 10, 1));
		assertEquals(20, TIMSPeakPicker.squashAcrossIMS(input).get(0).intensity);
		assertFalse(p.isAvailable());
		assertTrue(TIMSPeakPicker.squashAcrossIMS(input).isEmpty());
		PeakWithIMS singleton=new PeakWithIMS(500, 10, 1);
		PeakWithIMS result=TIMSPeakPicker.squashAcrossIMS(points(singleton)).get(0);
		assertNotSame(singleton, result);
		assertEquals(500, result.mz);
		assertEquals(1, result.ims);
	}

	@Test
	void sortsInputStablyWithoutReplacingReferencesOrLosingAliases() {
		PeakWithIMS low=new PeakWithIMS(400, 10, 1), high=new PeakWithIMS(600, 10, 1);
		PeakWithIMS alias=new PeakWithIMS(500, 10, 1), off=new PeakWithIMS(500, 10, 1);
		off.turnOff();
		ArrayList<PeakWithIMS> input=points(high, alias, off, alias, low);
		TIMSPeakPicker.squashAcrossIMS(input);
		List<PeakWithIMS> expected=List.of(low, alias, off, alias, high);
		for (int i=0; i<input.size(); i++) assertSame(expected.get(i), input.get(i));
		assertTrue(input.stream().noneMatch(PeakWithIMS::isAvailable));
	}

	@Test
	void capPreservesUnconsumedAvailabilityAndUsesDescendingMzTies() {
		ArrayList<PeakWithIMS> input=new ArrayList<>();
		for (int i=0; i<TIMSPeakPicker.MAX_PEAKS+7; i++) input.add(new PeakWithIMS(100+i, 10, 1));
		ArrayList<PeakWithIMS> result=TIMSPeakPicker.squashAcrossIMS(input);
		assertEquals(TIMSPeakPicker.MAX_PEAKS, result.size());
		for (int i=0; i<input.size(); i++) assertEquals(i<7, input.get(i).isAvailable());
		double w=TIMSPeakPicker.tolerance.getToleranceInMz(500, 500)/2;
		result=TIMSPeakPicker.squashAcrossIMS(points(new PeakWithIMS(500, 10, 1),
				new PeakWithIMS(500+0.9*w, 10, 2), new PeakWithIMS(500+1.8*w, 10, 3)));
		assertEquals(2, result.size());
		assertEquals(10, result.get(0).intensity);
		assertEquals(20, result.get(1).intensity);
	}

	@Test
	void ordersAdjacentFloatIntensitiesWithoutRoundingIntoTheSameLevel() {
		double w=TIMSPeakPicker.tolerance.getToleranceInMz(500, 500)/2;
		ArrayList<PeakWithIMS> input=points(new PeakWithIMS(500, 1, 1), new PeakWithIMS(500+0.9*w, Math.nextUp(1f), 2),
				new PeakWithIMS(500+1.8*w, 1, 3));
		// Only the slightly stronger middle point's window can consume both neighbors together.
		assertEquals(1, TIMSPeakPicker.squashAcrossIMS(input).size());
	}

	@Test
	void positiveInfinityRemainsEligibleWhileNaNRemainsAvailable() {
		PeakWithIMS infinite=new PeakWithIMS(500, Float.POSITIVE_INFINITY, 1), finite=new PeakWithIMS(500, 10, 2);
		PeakWithIMS nan=new PeakWithIMS(500, Float.NaN, 3);
		ArrayList<PeakWithIMS> input=points(infinite, finite, nan);
		PeakPickerFixture fixture=new PeakPickerFixture(input);
		assertEquals(Float.POSITIVE_INFINITY, TIMSPeakPicker.squashAcrossIMS(input).get(0).intensity);
		assertFalse(infinite.isAvailable());
		assertFalse(finite.isAvailable());
		assertTrue(nan.isAvailable());
		TIMSSquashReference.verify(fixture, "infinite intensity");
	}

	@Test
	void shuffledInputsAndRandomizedDenseOverlapsMatchIndependentOracle() {
		Random random=new Random(20261125);
		for (int iteration=0; iteration<160; iteration++) {
			ArrayList<PeakWithIMS> input=new ArrayList<>();
			for (int j=0; j<iteration*5; j++) {
				double mz=100+(j%7)*100+random.nextInt(40)*0.0001;
				float intensity=iteration%5==0?Math.scalb(1f+random.nextFloat(), random.nextInt(240)-120):random.nextInt(12)-2;
				PeakWithIMS p=new PeakWithIMS(mz, intensity, random.nextInt(9)*0.25f);
				if (j%17==0) p.turnOff();
				input.add(p);
				if (j%23==0) input.add(p);
			}
			PeakPickerFixture original=new PeakPickerFixture(input);
			ArrayList<PeakWithIMS> expected=TIMSSquashReference.verify(original, "random "+iteration);
			Collections.shuffle(input, random);
			ArrayList<PeakWithIMS> shuffled=TIMSSquashReference.verify(new PeakPickerFixture(input), "shuffle "+iteration);
			TIMSSquashReference.assertSamePoints(expected, shuffled, "deterministic "+iteration);
		}
	}

	@Test
	void recordedMs1AndMs2AndAdversarialFixturesMatchOracle() throws Exception {
		for (var entry : PeakPickerReplay.fixtures().entrySet()) {
			// Historical nonfinite m/z cases freeze the old picker, outside the finite-m/z comparisons here.
			if (!entry.getKey().equals("boundaries-nonfinite")) TIMSSquashReference.verify(entry.getValue(), entry.getKey());
		}
		ArrayList<PeakWithIMS> dense=new ArrayList<>();
		for (int i=0; i<12000; i++) dense.add(new PeakWithIMS(500+i*0.000001, 1+i%101, 0.5f+i%9*0.1f));
		TIMSSquashReference.verify(new PeakPickerFixture(dense), "dense overlap");
	}
}
