package org.searlelab.msrawjava.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import gnu.trove.list.array.TDoubleArrayList;

class MassToleranceTest {

	private TestMassTolerance tolerance;

	/**
	 * Concrete implementation for testing the abstract MassTolerance class.
	 * Uses a fixed absolute tolerance for simplicity.
	 */
	private static class TestMassTolerance extends MassTolerance {
		private final double absoluteTolerance;

		TestMassTolerance(double absoluteTolerance) {
			this.absoluteTolerance=absoluteTolerance;
		}

		@Override
		public double getToleranceInMz(double m1, double m2) {
			return absoluteTolerance;
		}
	}

	@BeforeEach
	void setUp() {
		tolerance=new TestMassTolerance(0.5); // 0.5 Da tolerance
	}

	// compareTo tests
	@Test
	void compareToReturnsNegativeWhenFirstIsLess() {
		// 100.0 + 0.5 < 101.0, so 100.0 is "less than" 101.0
		assertEquals(-1, tolerance.compareTo(100.0, 101.0));
	}

	@Test
	void compareToReturnsPositiveWhenFirstIsGreater() {
		// 101.0 - 0.5 > 100.0, so 101.0 is "greater than" 100.0
		assertEquals(1, tolerance.compareTo(101.0, 100.0));
	}

	@Test
	void compareToReturnsZeroWhenWithinTolerance() {
		// 100.0 and 100.3 are within 0.5 Da tolerance
		assertEquals(0, tolerance.compareTo(100.0, 100.3));
		assertEquals(0, tolerance.compareTo(100.3, 100.0));
	}

	@Test
	void compareToReturnsZeroAtExactBoundary() {
		// 100.0 + 0.5 = 100.5, so 100.0 and 100.5 should be equal
		assertEquals(0, tolerance.compareTo(100.0, 100.5));
	}

	@Test
	void compareToReturnsNegativeJustOutsideBoundary() {
		// 100.0 + 0.5 < 100.6
		assertEquals(-1, tolerance.compareTo(100.0, 100.6));
	}

	// getIndices with double[] tests
	@Test
	void getIndicesDoubleArrayFindsExactMatch() {
		double[] peaks= {100.0, 200.0, 300.0, 400.0, 500.0};
		int[] indices=tolerance.getIndices(peaks, 200.0);
		assertArrayEquals(new int[] {1}, indices);
	}

	@Test
	void getIndicesDoubleArrayFindsMatchesWithinTolerance() {
		double[] peaks= {100.0, 200.0, 200.3, 200.4, 300.0};
		int[] indices=tolerance.getIndices(peaks, 200.2);
		// Should find indices 1, 2, 3 (all within 0.5 of 200.2)
		Arrays.sort(indices);
		assertArrayEquals(new int[] {1, 2, 3}, indices);
	}

	@Test
	void getIndicesDoubleArrayReturnsEmptyWhenNoMatch() {
		double[] peaks= {100.0, 200.0, 300.0};
		int[] indices=tolerance.getIndices(peaks, 250.0);
		assertEquals(0, indices.length);
	}

	@Test
	void getIndicesDoubleArrayHandlesEmptyArray() {
		double[] peaks= {};
		int[] indices=tolerance.getIndices(peaks, 100.0);
		assertEquals(0, indices.length);
	}

	@Test
	void getIndicesDoubleArrayHandlesTargetBelowAllPeaks() {
		double[] peaks= {100.0, 200.0, 300.0};
		int[] indices=tolerance.getIndices(peaks, 50.0);
		assertEquals(0, indices.length);
	}

	@Test
	void getIndicesDoubleArrayHandlesTargetAboveAllPeaks() {
		double[] peaks= {100.0, 200.0, 300.0};
		int[] indices=tolerance.getIndices(peaks, 350.0);
		assertEquals(0, indices.length);
	}

	@Test
	void getIndicesDoubleArrayFindsMatchAtArrayStart() {
		double[] peaks= {100.0, 200.0, 300.0};
		int[] indices=tolerance.getIndices(peaks, 100.0);
		assertArrayEquals(new int[] {0}, indices);
	}

	@Test
	void getIndicesDoubleArrayFindsMatchAtArrayEnd() {
		double[] peaks= {100.0, 200.0, 300.0};
		int[] indices=tolerance.getIndices(peaks, 300.0);
		assertArrayEquals(new int[] {2}, indices);
	}

	// getIndices with TDoubleArrayList tests
	@Test
	void getIndicesTDoubleArrayListFindsExactMatch() {
		TDoubleArrayList peaks=new TDoubleArrayList(new double[] {100.0, 200.0, 300.0, 400.0, 500.0});
		int[] indices=tolerance.getIndices(peaks, 200.0);
		assertArrayEquals(new int[] {1}, indices);
	}

	@Test
	void getIndicesTDoubleArrayListFindsMatchesWithinTolerance() {
		TDoubleArrayList peaks=new TDoubleArrayList(new double[] {100.0, 200.0, 200.3, 200.4, 300.0});
		int[] indices=tolerance.getIndices(peaks, 200.2);
		Arrays.sort(indices);
		assertArrayEquals(new int[] {1, 2, 3}, indices);
	}

	@Test
	void getIndicesTDoubleArrayListReturnsEmptyWhenNoMatch() {
		TDoubleArrayList peaks=new TDoubleArrayList(new double[] {100.0, 200.0, 300.0});
		int[] indices=tolerance.getIndices(peaks, 250.0);
		assertEquals(0, indices.length);
	}

	@Test
	void getIndicesTDoubleArrayListHandlesEmptyList() {
		TDoubleArrayList peaks=new TDoubleArrayList();
		int[] indices=tolerance.getIndices(peaks, 100.0);
		assertEquals(0, indices.length);
	}

	// getIndices with PeakInterface[] tests
	@Test
	void getIndicesPeakArrayFindsExactMatch() {
		PeakInterface[] peaks= {new Peak(100.0, 1.0f), new Peak(200.0, 2.0f), new Peak(300.0, 3.0f)};
		PeakInterface target=new Peak(200.0, 0.0f);
		int[] indices=tolerance.getIndices(peaks, target);
		assertArrayEquals(new int[] {1}, indices);
	}

	@Test
	void getIndicesPeakArrayFindsMatchesWithinTolerance() {
		PeakInterface[] peaks= {new Peak(100.0, 1.0f), new Peak(200.0, 2.0f), new Peak(200.3, 2.5f), new Peak(200.4, 2.7f), new Peak(300.0, 3.0f)};
		PeakInterface target=new Peak(200.2, 0.0f);
		int[] indices=tolerance.getIndices(peaks, target);
		Arrays.sort(indices);
		assertArrayEquals(new int[] {1, 2, 3}, indices);
	}

	@Test
	void getIndicesPeakArrayReturnsEmptyWhenNoMatch() {
		PeakInterface[] peaks= {new Peak(100.0, 1.0f), new Peak(200.0, 2.0f), new Peak(300.0, 3.0f)};
		PeakInterface target=new Peak(250.0, 0.0f);
		int[] indices=tolerance.getIndices(peaks, target);
		assertEquals(0, indices.length);
	}

	@Test
	void getIndicesPeakArrayHandlesEmptyArray() {
		PeakInterface[] peaks= {};
		PeakInterface target=new Peak(100.0, 0.0f);
		int[] indices=tolerance.getIndices(peaks, target);
		assertEquals(0, indices.length);
	}

	// getIndices with List<PeakInterface> tests
	@Test
	void getIndicesPeakListFindsExactMatch() {
		List<PeakInterface> peaks=new ArrayList<>();
		peaks.add(new Peak(100.0, 1.0f));
		peaks.add(new Peak(200.0, 2.0f));
		peaks.add(new Peak(300.0, 3.0f));
		PeakInterface target=new Peak(200.0, 0.0f);
		int[] indices=tolerance.getIndices(peaks, target);
		assertArrayEquals(new int[] {1}, indices);
	}

	@Test
	void getIndicesPeakListFindsMatchesWithinTolerance() {
		List<PeakInterface> peaks=new ArrayList<>();
		peaks.add(new Peak(100.0, 1.0f));
		peaks.add(new Peak(200.0, 2.0f));
		peaks.add(new Peak(200.3, 2.5f));
		peaks.add(new Peak(200.4, 2.7f));
		peaks.add(new Peak(300.0, 3.0f));
		PeakInterface target=new Peak(200.2, 0.0f);
		int[] indices=tolerance.getIndices(peaks, target);
		Arrays.sort(indices);
		assertArrayEquals(new int[] {1, 2, 3}, indices);
	}

	@Test
	void getIndicesPeakListReturnsEmptyWhenNoMatch() {
		List<PeakInterface> peaks=new ArrayList<>();
		peaks.add(new Peak(100.0, 1.0f));
		peaks.add(new Peak(200.0, 2.0f));
		peaks.add(new Peak(300.0, 3.0f));
		PeakInterface target=new Peak(250.0, 0.0f);
		int[] indices=tolerance.getIndices(peaks, target);
		assertEquals(0, indices.length);
	}

	@Test
	void getIndicesPeakListHandlesEmptyList() {
		List<PeakInterface> peaks=new ArrayList<>();
		PeakInterface target=new Peak(100.0, 0.0f);
		int[] indices=tolerance.getIndices(peaks, target);
		assertEquals(0, indices.length);
	}

	// Tests with different tolerance values
	@Test
	void tightToleranceFindsFewerMatches() {
		TestMassTolerance tightTolerance=new TestMassTolerance(0.1);
		double[] peaks= {100.0, 200.0, 200.05, 200.15, 300.0};
		int[] indices=tightTolerance.getIndices(peaks, 200.0);
		Arrays.sort(indices);
		// Should only find 200.0 and 200.05 (within 0.1 of 200.0)
		assertArrayEquals(new int[] {1, 2}, indices);
	}

	@Test
	void wideToleranceFindsMoreMatches() {
		TestMassTolerance wideTolerance=new TestMassTolerance(2.0);
		double[] peaks= {100.0, 200.0, 201.0, 202.0, 300.0};
		int[] indices=wideTolerance.getIndices(peaks, 200.5);
		Arrays.sort(indices);
		// Should find 200.0, 201.0, 202.0 (all within 2.0 of 200.5)
		assertArrayEquals(new int[] {1, 2, 3}, indices);
	}

	@Test
	void zeroToleranceRequiresExactMatch() {
		TestMassTolerance zeroTolerance=new TestMassTolerance(0.0);
		double[] peaks= {100.0, 200.0, 200.001, 300.0};

		// Exact match should be found
		int[] exactIndices=zeroTolerance.getIndices(peaks, 200.0);
		assertArrayEquals(new int[] {1}, exactIndices);

		// Near miss should not be found
		int[] nearMissIndices=zeroTolerance.getIndices(peaks, 200.0005);
		assertEquals(0, nearMissIndices.length);
	}
}
