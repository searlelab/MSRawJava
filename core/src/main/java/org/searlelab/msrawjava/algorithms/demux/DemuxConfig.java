package org.searlelab.msrawjava.algorithms.demux;

/**
 * Configuration for staggered DIA demultiplexing.
 *
 * Controls the local approximation size (k) and interpolation method used for
 * retention time alignment before NNLS solving.
 */
public class DemuxConfig {

	/**
	 * Interpolation methods for retention time alignment.
	 */
	public enum InterpolationMethod {
		/** Cubic hermite spline interpolation (pwiz-compatible) */
		CUBIC_HERMITE,
		/** Log-quadratic interpolation (original MSRawJava approach) */
		LOG_QUADRATIC
	}

	/** Minimum allowed local approximation size */
	public static final int MIN_K = 7;
	/** Maximum allowed local approximation size */
	public static final int MAX_K = 9;
	/** Default local approximation size (matches pwiz) */
	public static final int DEFAULT_K = 7;
	/** Default setting for including edge sub-windows */
	public static final boolean DEFAULT_INCLUDE_EDGE_SUBWINDOWS = true;

	private final int k;
	private final InterpolationMethod interpolationMethod;
	private final boolean includeEdgeSubWindows;

	/**
	 * Creates a configuration with default settings (k=7, cubic hermite interpolation, include edges).
	 */
	public DemuxConfig() {
		this(DEFAULT_K, InterpolationMethod.CUBIC_HERMITE, DEFAULT_INCLUDE_EDGE_SUBWINDOWS);
	}

	/**
	 * Creates a configuration with the specified settings, including edge sub-windows by default.
	 *
	 * @param k local approximation size (7-9), determines how many nearby spectra
	 *          are used in the NNLS solve
	 * @param interpolationMethod method for retention time alignment
	 * @throws IllegalArgumentException if k is outside the valid range [7, 9]
	 */
	public DemuxConfig(int k, InterpolationMethod interpolationMethod) {
		this(k, interpolationMethod, DEFAULT_INCLUDE_EDGE_SUBWINDOWS);
	}

	/**
	 * Creates a configuration with all settings specified.
	 *
	 * @param k local approximation size (7-9), determines how many nearby spectra
	 *          are used in the NNLS solve
	 * @param interpolationMethod method for retention time alignment
	 * @param includeEdgeSubWindows if true, include first/last sub-windows (single coverage);
	 *                              if false, exclude them (requires dual coverage)
	 * @throws IllegalArgumentException if k is outside the valid range [7, 9]
	 */
	public DemuxConfig(int k, InterpolationMethod interpolationMethod, boolean includeEdgeSubWindows) {
		if (k < MIN_K || k > MAX_K) {
			throw new IllegalArgumentException(
					"k must be between " + MIN_K + " and " + MAX_K + ", got: " + k);
		}
		this.k = k;
		this.interpolationMethod = interpolationMethod;
		this.includeEdgeSubWindows = includeEdgeSubWindows;
	}

	/**
	 * Returns the local approximation size.
	 *
	 * This determines how many nearby spectra are used when building the local
	 * linear system for NNLS solving. Larger values may improve accuracy but
	 * increase computation (2^k possible submatrices to cache).
	 *
	 * @return k value in range [7, 9]
	 */
	public int getK() {
		return k;
	}

	/**
	 * Returns the interpolation method for retention time alignment.
	 *
	 * @return the configured interpolation method
	 */
	public InterpolationMethod getInterpolationMethod() {
		return interpolationMethod;
	}

	/**
	 * Returns whether edge sub-windows should be included in output.
	 *
	 * Edge sub-windows (first and last) have only single coverage from one
	 * acquired spectrum, while interior sub-windows have dual coverage.
	 * Including edges produces 2*(N+M) outputs; excluding them produces 2*(N+M)-2.
	 *
	 * @return true to include edge sub-windows, false to exclude them
	 */
	public boolean isIncludeEdgeSubWindows() {
		return includeEdgeSubWindows;
	}

	/**
	 * Returns the number of possible design matrix subsets for caching.
	 *
	 * With k rows, each row can be included or excluded, giving 2^k combinations.
	 *
	 * @return 2^k
	 */
	public int getNumCacheEntries() {
		return 1 << k; // 2^k
	}

	@Override
	public String toString() {
		return "DemuxConfig[k=" + k + ", interpolation=" + interpolationMethod +
				", includeEdges=" + includeEdgeSubWindows + "]";
	}

	/**
	 * Builder for creating DemuxConfig instances with fluent API.
	 */
	public static class Builder {
		private int k = DEFAULT_K;
		private InterpolationMethod interpolationMethod = InterpolationMethod.CUBIC_HERMITE;
		private boolean includeEdgeSubWindows = DEFAULT_INCLUDE_EDGE_SUBWINDOWS;

		public Builder k(int k) {
			this.k = k;
			return this;
		}

		public Builder interpolationMethod(InterpolationMethod method) {
			this.interpolationMethod = method;
			return this;
		}

		public Builder useCubicHermite() {
			this.interpolationMethod = InterpolationMethod.CUBIC_HERMITE;
			return this;
		}

		public Builder useLogQuadratic() {
			this.interpolationMethod = InterpolationMethod.LOG_QUADRATIC;
			return this;
		}

		public Builder includeEdgeSubWindows(boolean include) {
			this.includeEdgeSubWindows = include;
			return this;
		}

		public Builder excludeEdgeSubWindows() {
			this.includeEdgeSubWindows = false;
			return this;
		}

		public DemuxConfig build() {
			return new DemuxConfig(k, interpolationMethod, includeEdgeSubWindows);
		}
	}

	/**
	 * Returns a new builder for creating DemuxConfig instances.
	 */
	public static Builder builder() {
		return new Builder();
	}
}
