package org.searlelab.msrawjava.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.model.DemultiplexedFragmentScan;
import org.searlelab.msrawjava.model.FragmentScan;

class RawFileConvertersSpectrumNamingTest {

	@Test
	void demultiplexedFragmentScan_usesCanonicalThermoNameAndTypedMetadata() {
		FragmentScan source=fragment("legacy name", 3, 400.0, 408.0);
		DemultiplexedFragmentScan demuxed=new DemultiplexedFragmentScan(source, 2, 1);

		assertEquals(2, demuxed.getOriginalSpectrumIndex());
		assertEquals(1, demuxed.getDemuxCode());
		assertEquals("originalScan=2 demux=1 scan=3", demuxed.getSpectrumName());

		DemultiplexedFragmentScan renumbered=demuxed.renumber(7);
		assertEquals(2, renumbered.getOriginalSpectrumIndex());
		assertEquals(1, renumbered.getDemuxCode());
		assertEquals("originalScan=2 demux=1 scan=7", renumbered.getSpectrumName());
	}

	@Test
	void brukerMergedPrefix_putsMergedIndexFirst() {
		assertEquals("merged=7 frame=1 scanStart=1 scanEnd=774", RawFileConverters.addBrukerMergedPrefix("frame=1 scanStart=1 scanEnd=774", 7));
		assertEquals("merged=7 frame=1 scanStart=1 scanEnd=774",
				RawFileConverters.addBrukerMergedPrefix("merged=7 frame=1 scanStart=1 scanEnd=774", 8));
	}

	private static FragmentScan fragment(String name, int index, double isoLo, double isoHi) {
		return new FragmentScan(name, "prec", index, (isoLo+isoHi)/2.0, 1.0f, 0, 0.01f, isoLo, isoHi, new double[0], new float[0], null, (byte)0, 100.0,
				900.0);
	}
}
