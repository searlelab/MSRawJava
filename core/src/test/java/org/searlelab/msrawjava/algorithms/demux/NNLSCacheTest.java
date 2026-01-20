package org.searlelab.msrawjava.algorithms.demux;

import static org.junit.jupiter.api.Assertions.*;

import org.ejml.data.DMatrixRMaj;
import org.junit.jupiter.api.Test;

class NNLSCacheTest {

	@Test
	void testCacheHitsAndMisses() {
		NNLSCache.clearCache();
		assertEquals(0, NNLSCache.getCacheSize());
		assertEquals(0, NNLSCache.getCacheHits());
		assertEquals(0, NNLSCache.getCacheMisses());

		DMatrixRMaj A = new DMatrixRMaj(new double[][] {
			{1.0, 0.0},
			{0.0, 1.0}
		});
		DMatrixRMaj b = new DMatrixRMaj(new double[][] {{3.0}, {4.0}});

		DMatrixRMaj x1 = NNLSCache.solveLeastSquaresCached(A, b, 3);
		assertEquals(1, NNLSCache.getCacheMisses());
		assertEquals(0, NNLSCache.getCacheHits());
		assertEquals(1, NNLSCache.getCacheSize());
		assertEquals(3.0, x1.get(0, 0), 1e-6);
		assertEquals(4.0, x1.get(1, 0), 1e-6);

		DMatrixRMaj x2 = NNLSCache.solveLeastSquaresCached(A, b, 3);
		assertEquals(1, NNLSCache.getCacheMisses());
		assertEquals(1, NNLSCache.getCacheHits());
		assertEquals(0.5, NNLSCache.getCacheHitRatio(), 1e-6);
		assertEquals(3.0, x2.get(0, 0), 1e-6);
		assertEquals(4.0, x2.get(1, 0), 1e-6);
	}

	@Test
	void testWarmupCachePopulates() {
		NNLSCache.clearCache();

		DMatrixRMaj baseMatrix = new DMatrixRMaj(new double[][] {
			{1.0, 0.0, 0.0},
			{0.0, 1.0, 0.0},
			{0.0, 0.0, 1.0}
		});

		NNLSCache.warmupCache(baseMatrix, 3);

		int size = NNLSCache.getCacheSize();
		assertTrue(size > 0 && size <= 7, "Expected some cached entries, got " + size);
		assertEquals(0, NNLSCache.getCacheHits());
		assertEquals(0, NNLSCache.getCacheMisses());
	}
}
