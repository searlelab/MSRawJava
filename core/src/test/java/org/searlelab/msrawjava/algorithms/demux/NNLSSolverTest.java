package org.searlelab.msrawjava.algorithms.demux;

import static org.junit.jupiter.api.Assertions.*;

import org.ejml.data.DMatrixRMaj;
import org.junit.jupiter.api.Test;

class NNLSSolverTest {

	@Test
	void testSimplePositiveSolution() {
		NNLSSolver solver = new NNLSSolver();

		// Simple system: A = [[1, 0], [0, 1]], b = [3, 4]
		// Solution should be x = [3, 4]
		DMatrixRMaj A = new DMatrixRMaj(new double[][] {
			{1, 0},
			{0, 1}
		});
		DMatrixRMaj b = new DMatrixRMaj(new double[][] {{3}, {4}});

		DMatrixRMaj x = solver.solve(A, b);

		assertEquals(3.0, x.get(0, 0), 0.01);
		assertEquals(4.0, x.get(1, 0), 0.01);
	}

	@Test
	void testNonNegativityConstraint() {
		NNLSSolver solver = new NNLSSolver();

		// System where unconstrained solution would be negative
		// A = [[1, 1]], b = [5]
		// Unconstrained: infinitely many solutions along x1 + x2 = 5
		// NNLS should find non-negative solution
		DMatrixRMaj A = new DMatrixRMaj(new double[][] {
			{1, 1},
			{1, 0}
		});
		DMatrixRMaj b = new DMatrixRMaj(new double[][] {{5}, {3}});

		DMatrixRMaj x = solver.solve(A, b);

		// All elements should be >= 0
		assertTrue(x.get(0, 0) >= -0.001, "x[0] should be non-negative");
		assertTrue(x.get(1, 0) >= -0.001, "x[1] should be non-negative");
	}

	@Test
	void testOverdeterminedSystem() {
		NNLSSolver solver = new NNLSSolver();

		// Overdetermined system (more equations than unknowns)
		// This is typical in demultiplexing
		DMatrixRMaj A = new DMatrixRMaj(new double[][] {
			{1, 1},
			{1, 0},
			{0, 1}
		});
		DMatrixRMaj b = new DMatrixRMaj(new double[][] {{10}, {6}, {5}});

		DMatrixRMaj x = solver.solve(A, b);

		// Solution should be approximately [5.5, 4.5] based on least squares
		assertEquals(2, x.numRows);
		assertTrue(x.get(0, 0) >= 0);
		assertTrue(x.get(1, 0) >= 0);
	}

	@Test
	void testDemuxLikePattern() {
		NNLSSolver solver = new NNLSSolver();

		// Simulate demux-like banded matrix pattern
		// Each spectrum sees 2 adjacent sub-windows
		DMatrixRMaj A = new DMatrixRMaj(new double[][] {
			{1, 1, 0, 0, 0, 0, 0},
			{0, 1, 1, 0, 0, 0, 0},
			{0, 0, 1, 1, 0, 0, 0},
			{0, 0, 0, 1, 1, 0, 0},
			{0, 0, 0, 0, 1, 1, 0},
			{0, 0, 0, 0, 0, 1, 1},
			{0, 0, 0, 0, 0, 0, 1}
		});

		// Signal where sub-windows 3 and 4 have intensity
		// Observed: 0+0=0, 0+0=0, 0+10=10, 10+20=30, 20+0=20, 0+0=0, 0=0
		DMatrixRMaj b = new DMatrixRMaj(new double[][] {{0}, {0}, {10}, {30}, {20}, {0}, {0}});

		DMatrixRMaj x = solver.solve(A, b);

		// Solution should recover sub-window intensities
		// Expected approximately: [0, 0, 0, 10, 20, 0, 0]
		assertEquals(7, x.numRows);

		// All should be non-negative
		for (int i = 0; i < x.numRows; i++) {
			assertTrue(x.get(i, 0) >= -0.001, "x[" + i + "] should be non-negative");
		}

		// Central elements should have most of the intensity
		assertTrue(x.get(3, 0) > 5, "Sub-window 3 should have significant intensity");
		assertTrue(x.get(4, 0) > 10, "Sub-window 4 should have significant intensity");
	}

	@Test
	void testZeroInput() {
		NNLSSolver solver = new NNLSSolver();

		DMatrixRMaj A = new DMatrixRMaj(new double[][] {
			{1, 1},
			{0, 1}
		});
		DMatrixRMaj b = new DMatrixRMaj(new double[][] {{0}, {0}});

		DMatrixRMaj x = solver.solve(A, b);

		// Solution should be all zeros
		assertEquals(0.0, x.get(0, 0), 0.001);
		assertEquals(0.0, x.get(1, 0), 0.001);
	}

	@Test
	void testMultipleSolve() {
		NNLSSolver solver = new NNLSSolver();

		DMatrixRMaj A = new DMatrixRMaj(new double[][] {
			{1, 1, 0},
			{0, 1, 1},
			{0, 0, 1}
		});

		// Multiple observation vectors
		DMatrixRMaj B = new DMatrixRMaj(new double[][] {
			{10, 5, 0},
			{15, 8, 3},
			{5, 3, 3}
		});

		DMatrixRMaj X = solver.solveMultiple(A, B);

		assertEquals(3, X.numRows);  // 3 sub-windows
		assertEquals(3, X.numCols);  // 3 transitions

		// All values should be non-negative
		for (int i = 0; i < X.numRows; i++) {
			for (int j = 0; j < X.numCols; j++) {
				assertTrue(X.get(i, j) >= -0.001,
						"X[" + i + "," + j + "] should be non-negative");
			}
		}
	}
}
