package org.searlelab.msrawjava.algorithms.demux;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class InterpolatorTest {

	@Test
	void testCubicHermiteLinearData() {
		CubicHermiteInterpolator interp = new CubicHermiteInterpolator();

		float[] times = {1.0f, 2.0f, 3.0f, 4.0f};
		float[] intensities = {10.0f, 20.0f, 30.0f, 40.0f};

		// Linear data should interpolate linearly
		assertEquals(15.0f, interp.interpolate(times, intensities, 1.5f), 1.0f);
		assertEquals(25.0f, interp.interpolate(times, intensities, 2.5f), 1.0f);
	}

	@Test
	void testCubicHermiteExtrapolation() {
		CubicHermiteInterpolator interp = new CubicHermiteInterpolator();

		float[] times = {1.0f, 2.0f, 3.0f};
		float[] intensities = {100.0f, 200.0f, 150.0f};

		// Extrapolation should clamp to boundary values
		assertEquals(100.0f, interp.interpolate(times, intensities, 0.0f), 0.1f);
		assertEquals(150.0f, interp.interpolate(times, intensities, 5.0f), 0.1f);
	}

	@Test
	void testCubicHermiteNonNegative() {
		CubicHermiteInterpolator interp = new CubicHermiteInterpolator();

		float[] times = {1.0f, 2.0f, 3.0f, 4.0f};
		float[] intensities = {100.0f, 10.0f, 5.0f, 100.0f};

		// Even with cubic spline potentially going negative, result should be clamped
		for (float t = 1.0f; t <= 4.0f; t += 0.1f) {
			float result = interp.interpolate(times, intensities, t);
			assertTrue(result >= 0, "Intensity at t=" + t + " should be non-negative, got " + result);
		}
	}

	@Test
	void testCubicHermiteSinglePoint() {
		CubicHermiteInterpolator interp = new CubicHermiteInterpolator();

		float[] times = {2.0f};
		float[] intensities = {50.0f};

		assertEquals(50.0f, interp.interpolate(times, intensities, 1.0f), 0.1f);
		assertEquals(50.0f, interp.interpolate(times, intensities, 2.0f), 0.1f);
		assertEquals(50.0f, interp.interpolate(times, intensities, 3.0f), 0.1f);
	}

	@Test
	void testCubicHermiteTwoPoints() {
		CubicHermiteInterpolator interp = new CubicHermiteInterpolator();

		float[] times = {1.0f, 3.0f};
		float[] intensities = {10.0f, 30.0f};

		// Two points should use linear interpolation
		assertEquals(20.0f, interp.interpolate(times, intensities, 2.0f), 0.1f);
	}

	@Test
	void testLogQuadraticLinearData() {
		LogQuadraticInterpolator interp = new LogQuadraticInterpolator();

		float[] times = {1.0f, 2.0f, 3.0f, 4.0f};
		float[] intensities = {10.0f, 20.0f, 30.0f, 40.0f};

		// Should interpolate reasonably
		float midPoint = interp.interpolate(times, intensities, 2.5f);
		assertTrue(midPoint > 20.0f && midPoint < 35.0f,
				"Mid-point should be between 20 and 35, got " + midPoint);
	}

	@Test
	void testLogQuadraticPeakShape() {
		LogQuadraticInterpolator interp = new LogQuadraticInterpolator();

		// Simulate a chromatographic peak
		float[] times = {1.0f, 2.0f, 3.0f, 4.0f, 5.0f};
		float[] intensities = {10.0f, 50.0f, 100.0f, 50.0f, 10.0f};

		// At peak center, intensity should be close to max
		float atPeak = interp.interpolate(times, intensities, 3.0f);
		assertEquals(100.0f, atPeak, 5.0f);

		// On shoulders, should be between
		float atShoulder = interp.interpolate(times, intensities, 2.5f);
		assertTrue(atShoulder > 50.0f && atShoulder < 100.0f,
				"Shoulder value should be between 50 and 100, got " + atShoulder);
	}

	@Test
	void testLogQuadraticNonNegative() {
		LogQuadraticInterpolator interp = new LogQuadraticInterpolator();

		float[] times = {1.0f, 2.0f, 3.0f, 4.0f};
		float[] intensities = {0.0f, 100.0f, 0.0f, 100.0f};

		// Result should never go negative
		for (float t = 1.0f; t <= 4.0f; t += 0.1f) {
			float result = interp.interpolate(times, intensities, t);
			assertTrue(result >= 0, "Intensity at t=" + t + " should be non-negative, got " + result);
		}
	}

	@Test
	void testLogQuadraticExtrapolation() {
		LogQuadraticInterpolator interp = new LogQuadraticInterpolator();

		float[] times = {1.0f, 2.0f, 3.0f};
		float[] intensities = {100.0f, 200.0f, 150.0f};

		// Extrapolation should clamp to boundary values
		assertEquals(100.0f, interp.interpolate(times, intensities, 0.0f), 0.1f);
		assertEquals(150.0f, interp.interpolate(times, intensities, 5.0f), 0.1f);
	}

	@Test
	void testBothInterpolatorsEmptyInput() {
		CubicHermiteInterpolator cubic = new CubicHermiteInterpolator();
		LogQuadraticInterpolator logQuad = new LogQuadraticInterpolator();

		float[] emptyTimes = {};
		float[] emptyIntensities = {};

		assertEquals(0.0f, cubic.interpolate(emptyTimes, emptyIntensities, 1.0f), 0.001f);
		assertEquals(0.0f, logQuad.interpolate(emptyTimes, emptyIntensities, 1.0f), 0.001f);
	}

	@Test
	void testBothInterpolatorsNullInput() {
		CubicHermiteInterpolator cubic = new CubicHermiteInterpolator();
		LogQuadraticInterpolator logQuad = new LogQuadraticInterpolator();

		float[] nullFloatArray = null;
		assertEquals(0.0f, cubic.interpolate(nullFloatArray, nullFloatArray, 1.0f), 0.001f);
		assertEquals(0.0f, logQuad.interpolate(nullFloatArray, nullFloatArray, 1.0f), 0.001f);
	}

	@Test
	void testInterpolatorNames() {
		assertEquals("CubicHermite", new CubicHermiteInterpolator().getName());
		assertEquals("LogQuadratic", new LogQuadraticInterpolator().getName());
	}
}
