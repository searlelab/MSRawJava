package org.searlelab.msrawjava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class COREPreferencesTest {

	@Test
	void setGetAndReset_roundTripsValues() {
		boolean originalVerbose=COREPreferences.isVerboseCoreLogging();
		double originalDemux=COREPreferences.getDemuxTolerancePpm();
		float originalMs1=COREPreferences.getMinimumMS1Intensity();
		float originalMs2=COREPreferences.getMinimumMS2Intensity();

		try {
			COREPreferences.setVerboseCoreLogging(false);
			COREPreferences.setDemuxTolerancePpm(42.5);
			COREPreferences.setMinimumMS1Intensity(9.5f);
			COREPreferences.setMinimumMS2Intensity(4.25f);

			assertFalse(COREPreferences.isVerboseCoreLogging());
			assertEquals(42.5, COREPreferences.getDemuxTolerancePpm(), 1e-9);
			assertEquals(9.5f, COREPreferences.getMinimumMS1Intensity(), 1e-6f);
			assertEquals(4.25f, COREPreferences.getMinimumMS2Intensity(), 1e-6f);

			COREPreferences.setVerboseCoreLogging(true);
			assertTrue(COREPreferences.isVerboseCoreLogging());
			COREPreferences.getDemuxTolerancePpm();
			COREPreferences.getMinimumMS1Intensity();
			COREPreferences.getMinimumMS2Intensity();

			COREPreferences.resetAll();
			assertEquals(10.0, COREPreferences.getDemuxTolerancePpm(), 1e-9);
			assertEquals(3.0f, COREPreferences.getMinimumMS1Intensity(), 1e-6f);
			assertEquals(1.0f, COREPreferences.getMinimumMS2Intensity(), 1e-6f);
		} finally {
			COREPreferences.setDemuxTolerancePpm(originalDemux);
			COREPreferences.setMinimumMS1Intensity(originalMs1);
			COREPreferences.setMinimumMS2Intensity(originalMs2);
			COREPreferences.setVerboseCoreLogging(originalVerbose);
		}
	}
}
