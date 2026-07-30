package org.searlelab.msrawjava.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.io.VendorFile;

class ConversionPaneDemuxTest {
	@Test
	void brukerDemuxRequestIsSuppressedForGuiJobs() {
		assertEquals(Optional.of(false), ConversionPane.resolveEffectiveDemultiplex(VendorFile.BRUKER, Optional.of(true)));
	}

	@Test
	void nonBrukerDemuxRequestIsPreserved() {
		assertEquals(Optional.of(true), ConversionPane.resolveEffectiveDemultiplex(VendorFile.MZML, Optional.of(true)));
		assertEquals(Optional.empty(), ConversionPane.resolveEffectiveDemultiplex(VendorFile.ENCYCLOPEDIA, Optional.empty()));
	}
}
