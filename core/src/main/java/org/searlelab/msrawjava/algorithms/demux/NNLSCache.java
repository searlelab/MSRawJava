package org.searlelab.msrawjava.algorithms.demux;

import java.util.concurrent.ConcurrentHashMap;

import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.decomposition.qr.QRDecompositionHouseholderColumn_DDRM;
import org.ejml.dense.row.factory.LinearSolverFactory_DDRM;
import org.ejml.interfaces.linsol.LinearSolverDense;

/**
 * Static cache for QR decompositions used in NNLS solving.
 *
 * In the staggered DIA demultiplexing algorithm, the same design matrix subsets
 * are encountered repeatedly. With a local approximation size of k, there are
 * at most 2^k possible row subsets of the design matrix. By caching the QR
 * decompositions of these subsets, we can significantly speed up the NNLS solving.
 *
 * For k=7: 128 possible submatrices
 * For k=8: 256 possible submatrices
 * For k=9: 512 possible submatrices
 *
 * The cache is thread-safe and shared across all StaggeredDemultiplexer instances.
 */
public class NNLSCache {

	/**
	 * Key for cached decompositions, combining the matrix dimensions and row mask.
	 */
	private static class CacheKey {
		final int numRows;
		final int numCols;
		final int rowMask;

		CacheKey(int numRows, int numCols, int rowMask) {
			this.numRows = numRows;
			this.numCols = numCols;
			this.rowMask = rowMask;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof CacheKey)) return false;
			CacheKey other = (CacheKey) obj;
			return numRows == other.numRows && numCols == other.numCols && rowMask == other.rowMask;
		}

		@Override
		public int hashCode() {
			int result = numRows;
			result = 31 * result + numCols;
			result = 31 * result + rowMask;
			return result;
		}
	}

	/**
	 * Cached QR decomposition data.
	 */
	private static class CachedQR {
		final DMatrixRMaj Q;
		final DMatrixRMaj R;

		CachedQR(DMatrixRMaj Q, DMatrixRMaj R) {
			this.Q = Q;
			this.R = R;
		}
	}

	// Thread-safe cache
	private static final ConcurrentHashMap<CacheKey, CachedQR> qrCache = new ConcurrentHashMap<>();

	// Statistics
	private static volatile long cacheHits = 0;
	private static volatile long cacheMisses = 0;

	private NNLSCache() {
		// Static utility class
	}

	/**
	 * Solves the least squares problem using cached QR decomposition if available.
	 *
	 * @param A       the design matrix
	 * @param b       the observation vector
	 * @param rowMask bitmask indicating which rows are active
	 * @return the least squares solution
	 */
	public static DMatrixRMaj solveLeastSquaresCached(DMatrixRMaj A, DMatrixRMaj b, int rowMask) {
		CacheKey key = new CacheKey(A.numRows, A.numCols, rowMask);

		CachedQR cached = qrCache.get(key);
		if (cached != null) {
			cacheHits++;
			return solveWithQR(cached.Q, cached.R, b);
		}

		cacheMisses++;

		// Compute and cache QR decomposition
		QRDecompositionHouseholderColumn_DDRM qr = new QRDecompositionHouseholderColumn_DDRM();
		if (!qr.decompose(A.copy())) {
			// Fallback to direct solve
			return solveDirect(A, b);
		}

		DMatrixRMaj Q = qr.getQ(null, true);
		DMatrixRMaj R = qr.getR(null, true);

		// Cache the decomposition
		qrCache.put(key, new CachedQR(Q, R));

		return solveWithQR(Q, R, b);
	}

	/**
	 * Solves Ax = b using precomputed QR decomposition.
	 * A = QR, so Rx = Q^T b
	 */
	private static DMatrixRMaj solveWithQR(DMatrixRMaj Q, DMatrixRMaj R, DMatrixRMaj b) {
		int n = R.numCols;

		// Compute Q^T * b
		DMatrixRMaj Qt = new DMatrixRMaj(Q.numCols, Q.numRows);
		CommonOps_DDRM.transpose(Q, Qt);

		DMatrixRMaj Qtb = new DMatrixRMaj(Qt.numRows, 1);
		CommonOps_DDRM.mult(Qt, b, Qtb);

		// Solve R * x = Q^T * b using back substitution
		DMatrixRMaj x = new DMatrixRMaj(n, 1);
		backSubstitution(R, Qtb, x);

		return x;
	}

	/**
	 * Back substitution for upper triangular system Rx = b.
	 */
	private static void backSubstitution(DMatrixRMaj R, DMatrixRMaj b, DMatrixRMaj x) {
		int n = Math.min(R.numRows, R.numCols);

		for (int i = n - 1; i >= 0; i--) {
			double sum = b.get(i, 0);
			for (int j = i + 1; j < n; j++) {
				sum -= R.get(i, j) * x.get(j, 0);
			}
			double Rii = R.get(i, i);
			if (Math.abs(Rii) > 1e-12) {
				x.set(i, 0, sum / Rii);
			} else {
				x.set(i, 0, 0);
			}
		}
	}

	/**
	 * Direct least squares solve without caching.
	 */
	private static DMatrixRMaj solveDirect(DMatrixRMaj A, DMatrixRMaj b) {
		int m = A.numRows;
		int n = A.numCols;

		LinearSolverDense<DMatrixRMaj> solver = LinearSolverFactory_DDRM.leastSquares(m, n);
		DMatrixRMaj x = new DMatrixRMaj(n, 1);

		if (solver.setA(A.copy())) {
			solver.solve(b, x);
		}

		return x;
	}

	/**
	 * Pre-populates the cache for all possible row subsets of a design matrix.
	 * Call this during initialization for best performance.
	 *
	 * @param baseMatrix the full k×k design matrix
	 * @param k          the local approximation size
	 */
	public static void warmupCache(DMatrixRMaj baseMatrix, int k) {
		int numSubsets = 1 << k; // 2^k

		for (int mask = 1; mask < numSubsets; mask++) {
			// Count set bits to determine number of active rows
			int activeRows = Integer.bitCount(mask);
			if (activeRows == 0) continue;

			// Build submatrix with active rows
			DMatrixRMaj subMatrix = new DMatrixRMaj(activeRows, baseMatrix.numCols);
			int rowIdx = 0;
			for (int i = 0; i < k && i < baseMatrix.numRows; i++) {
				if ((mask & (1 << i)) != 0) {
					for (int j = 0; j < baseMatrix.numCols; j++) {
						subMatrix.set(rowIdx, j, baseMatrix.get(i, j));
					}
					rowIdx++;
				}
			}

			// Compute and cache QR decomposition
			CacheKey key = new CacheKey(activeRows, baseMatrix.numCols, mask);
			if (!qrCache.containsKey(key)) {
				QRDecompositionHouseholderColumn_DDRM qr = new QRDecompositionHouseholderColumn_DDRM();
				if (qr.decompose(subMatrix.copy())) {
					DMatrixRMaj Q = qr.getQ(null, true);
					DMatrixRMaj R = qr.getR(null, true);
					qrCache.put(key, new CachedQR(Q, R));
				}
			}
		}
	}

	/**
	 * Clears the cache.
	 */
	public static void clearCache() {
		qrCache.clear();
		cacheHits = 0;
		cacheMisses = 0;
	}

	/**
	 * Returns the number of cached entries.
	 */
	public static int getCacheSize() {
		return qrCache.size();
	}

	/**
	 * Returns cache hit count.
	 */
	public static long getCacheHits() {
		return cacheHits;
	}

	/**
	 * Returns cache miss count.
	 */
	public static long getCacheMisses() {
		return cacheMisses;
	}

	/**
	 * Returns cache hit ratio (0.0 to 1.0).
	 */
	public static double getCacheHitRatio() {
		long total = cacheHits + cacheMisses;
		return total > 0 ? (double) cacheHits / total : 0.0;
	}
}
