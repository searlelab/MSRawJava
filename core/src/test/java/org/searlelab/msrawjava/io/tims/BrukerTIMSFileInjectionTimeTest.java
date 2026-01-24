package org.searlelab.msrawjava.io.tims;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BrukerTIMSFileInjectionTimeTest {

	@Test
	void convertsAccumulationTimeMsToSeconds() {
		assertEquals(0.0f, BrukerTIMSFile.accumulationTimeSeconds(0.0), 1e-6f);
		assertEquals(0.025f, BrukerTIMSFile.accumulationTimeSeconds(25.0), 1e-6f);
		assertEquals(1.5f, BrukerTIMSFile.accumulationTimeSeconds(1500.0), 1e-6f);
	}
}
