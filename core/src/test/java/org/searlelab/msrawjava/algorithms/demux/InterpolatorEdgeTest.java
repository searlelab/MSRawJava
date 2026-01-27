package org.searlelab.msrawjava.algorithms.demux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

class InterpolatorEdgeTest {

	@Test
	void cubicHermiteDoubleTwoPointsClampsNegative() {
		CubicHermiteInterpolator interp=new CubicHermiteInterpolator();
		double[] times= {0.0, 1.0};
		double[] intensities= {-5.0, 5.0};
		double value=interp.interpolate(times, intensities, 0.5);
		assertEquals(0.0, value, 1e-6);
	}

	@Test
	void cubicHermiteDoubleOutOfRangeClampsBoundary() {
		CubicHermiteInterpolator interp=new CubicHermiteInterpolator();
		double[] times= {1.0, 2.0, 3.0};
		double[] intensities= {-10.0, 20.0, 30.0};
		assertEquals(0.0, interp.interpolate(times, intensities, 0.0), 1e-6);
		assertEquals(30.0, interp.interpolate(times, intensities, 4.0), 1e-6);
	}

	@Test
	void cubicHermiteDuplicateTimesFallsBackToLinear() {
		CubicHermiteInterpolator interp=new CubicHermiteInterpolator();
		double[] times= {1.0, 1.0, 2.0};
		double[] intensities= {10.0, 30.0, 50.0};
		double value=interp.interpolate(times, intensities, 1.5);
		assertEquals(35.0, value, 1e-6);
	}

	@Test
	void cubicHermiteDoubleSinglePointClampsNegative() {
		CubicHermiteInterpolator interp=new CubicHermiteInterpolator();
		double[] times= {2.0};
		double[] intensities= {-3.0};
		double value=interp.interpolate(times, intensities, 2.0);
		assertEquals(0.0, value, 1e-6);
	}

	@Test
	void cubicHermiteFallbackWhenSplineFails() {
		CubicHermiteInterpolator interp=new CubicHermiteInterpolator();
		double[] times= {1.0, 3.0, 2.0, 4.0}; // not strictly increasing
		double[] intensities= {10.0, 30.0, 20.0, 40.0};
		double value=interp.interpolate(times, intensities, 2.5);
		assertTrue(value>=0.0);
	}

	@Test
	void cubicHermiteDoubleLengthMismatchThrows() {
		CubicHermiteInterpolator interp=new CubicHermiteInterpolator();
		double[] times= {1.0, 2.0};
		double[] intensities= {10.0};
		assertThrows(IllegalArgumentException.class, () -> interp.interpolate(times, intensities, 1.5));
	}

	@Test
	void cubicHermiteLinearInterpolateHelperIsUsed() throws Exception {
		CubicHermiteInterpolator interp=new CubicHermiteInterpolator();
		Method method=CubicHermiteInterpolator.class.getDeclaredMethod("linearInterpolate", double[].class, double[].class, double.class);
		method.setAccessible(true);
		double[] times= {1.0, 3.0, 5.0};
		double[] intensities= {10.0, 30.0, 50.0};
		double value=(double)method.invoke(interp, times, intensities, 4.0);
		assertEquals(40.0, value, 1e-6);
	}

	@Test
	void logQuadraticDuplicateInnerTimesReturnsAverage() {
		LogQuadraticInterpolator interp=new LogQuadraticInterpolator();
		double[] times= {1.0, 2.0, 2.0, 3.0};
		double[] intensities= {10.0, 20.0, 40.0, 50.0};
		double value=interp.interpolate(times, intensities, 2.0);
		assertTrue(value>=35.0&&value<=45.0, "Expected value to reflect inner-point averaging, got "+value);
	}

	@Test
	void logQuadraticZeroLogIntensityReturnsZero() {
		LogQuadraticInterpolator interp=new LogQuadraticInterpolator();
		double zeroLogIntensity=1.0-Math.E;
		double[] times= {1.0, 2.0, 3.0, 4.0};
		double[] intensities= {zeroLogIntensity, zeroLogIntensity, zeroLogIntensity, zeroLogIntensity};
		double value=interp.interpolate(times, intensities, 2.5);
		assertEquals(0.0, value, 1e-6);
	}
}
