package org.searlelab.msrawjava.algorithms.demux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.ejml.data.DMatrixRMaj;
import org.junit.jupiter.api.Test;

class NNLSCacheEdgeTest {

	@Test
	void backSubstitutionHandlesZeroDiagonal() throws Exception {
		DMatrixRMaj R=new DMatrixRMaj(new double[][] {{0.0, 2.0}, {0.0, 0.0}});
		DMatrixRMaj b=new DMatrixRMaj(new double[][] {{1.0}, {2.0}});
		DMatrixRMaj x=new DMatrixRMaj(2, 1);

		Method method=NNLSCache.class.getDeclaredMethod("backSubstitution", DMatrixRMaj.class, DMatrixRMaj.class, DMatrixRMaj.class);
		method.setAccessible(true);
		method.invoke(null, R, b, x);

		assertEquals(0.0, x.get(0, 0), 1e-6);
		assertEquals(0.0, x.get(1, 0), 1e-6);
	}

	@Test
	void cacheHitRatioZeroWhenEmpty() {
		NNLSCache.clearCache();
		assertEquals(0.0, NNLSCache.getCacheHitRatio(), 1e-9);
		assertEquals(0, NNLSCache.getCacheSize());
	}

	@Test
	void warmupCacheAvoidsDuplicateEntries() {
		NNLSCache.clearCache();
		DMatrixRMaj baseMatrix=new DMatrixRMaj(new double[][] {{1.0, 0.0}, {0.0, 1.0}});

		NNLSCache.warmupCache(baseMatrix, 2);
		int sizeAfterFirst=NNLSCache.getCacheSize();
		NNLSCache.warmupCache(baseMatrix, 2);
		assertEquals(sizeAfterFirst, NNLSCache.getCacheSize());
		assertTrue(sizeAfterFirst>0);
	}
}
