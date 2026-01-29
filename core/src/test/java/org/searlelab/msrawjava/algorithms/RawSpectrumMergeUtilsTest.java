package org.searlelab.msrawjava.algorithms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.model.AcquiredSpectrum;
import org.searlelab.msrawjava.model.MassTolerance;
import org.searlelab.msrawjava.model.PrecursorScan;

import gnu.trove.list.array.TDoubleArrayList;

class RawSpectrumMergeUtilsTest {

	@Test
	void mergeSpectra_emptyList_returnsEmptySpectrum() {
		AcquiredSpectrum merged=RawSpectrumMergeUtils.mergeSpectra(Collections.emptyList(), new FixedTolerance(0.1));
		assertNotNull(merged);
		assertEquals(0, merged.getMassArray().length);
		assertEquals(0, merged.getIntensityArray().length);
	}

	@Test
	void mergeSpectra_largeList_usesBinnedPath() {
		List<AcquiredSpectrum> spectra=new ArrayList<>();
		for (int i=0; i<51; i++) {
			spectra.add(scan("s"+i, new double[] {100.0+i}, new float[] {1.0f}, null));
		}
		AcquiredSpectrum merged=RawSpectrumMergeUtils.mergeSpectra(spectra, new FixedTolerance(0.1));
		assertTrue(merged.getMassArray().length>0);
		assertTrue(merged.getIntensityArray().length>0);
	}

	@Test
	void binnedMergeSpectra_handlesEmptyBins() {
		List<AcquiredSpectrum> spectra=List.of(scan("empty", new double[0], new float[0], null));
		AcquiredSpectrum merged=RawSpectrumMergeUtils.binnedMergeSpectra(spectra, 0.1);
		assertEquals(0, merged.getMassArray().length);
		assertEquals(0, merged.getIntensityArray().length);
	}

	@Test
	void binnedMergeSpectra_accumulatesIntensityAndIms() {
		double[] mz= {100.0};
		float[] intens1= {10.0f};
		float[] intens2= {30.0f};
		float[] ims1= {1.0f};
		float[] ims2= {2.0f};
		List<AcquiredSpectrum> spectra=List.of(scan("a", mz, intens1, ims1), scan("b", mz, intens2, ims2));

		AcquiredSpectrum merged=RawSpectrumMergeUtils.binnedMergeSpectra(spectra, 0.5);
		assertEquals(1, merged.getMassArray().length);
		assertEquals(40.0f, merged.getIntensityArray()[0], 1e-6f);
		float weighted=(ims1[0]*intens1[0]+ims2[0]*intens2[0])/(intens1[0]+intens2[0]);
		assertEquals(weighted, merged.getIonMobilityArray().orElseThrow()[0], 1e-6f);
	}

	@Test
	void accurateMergeSpectra_mergesWithinTolerance() {
		List<AcquiredSpectrum> spectra=List.of(scan("a", new double[] {100.0}, new float[] {10.0f}, null),
				scan("b", new double[] {100.2}, new float[] {5.0f}, null));
		AcquiredSpectrum merged=RawSpectrumMergeUtils.accurateMergeSpectra(spectra, new FixedTolerance(0.5));
		assertEquals(1, merged.getMassArray().length);
		assertEquals(15.0f, merged.getIntensityArray()[0], 1e-6f);
	}

	@Test
	void getIndex_accountsForNeighborTolerance() throws Exception {
		Method method=RawSpectrumMergeUtils.class.getDeclaredMethod("getIndex", TDoubleArrayList.class, double.class, MassTolerance.class);
		method.setAccessible(true);

		TDoubleArrayList peaks=new TDoubleArrayList();
		assertEquals(-1, (int)method.invoke(null, peaks, 100.0, new FixedTolerance(0.1)));

		peaks.add(100.0);
		peaks.add(101.0);
		// insertion point between 100 and 101, tolerance should match lower neighbor
		int idx=(int)method.invoke(null, peaks, 100.05, new FixedTolerance(0.1));
		assertEquals(0, idx);
	}

	private static PrecursorScan scan(String name, double[] mz, float[] intensity, float[] ims) {
		return new PrecursorScan(name, 0, 0.0f, 0, 0.0, Double.MAX_VALUE, null, mz, intensity, ims);
	}

	private static final class FixedTolerance extends MassTolerance {
		private final double tol;

		FixedTolerance(double tol) {
			this.tol=tol;
		}

		@Override
		public double getToleranceInMz(double m1, double m2) {
			return tol;
		}
	}
}
