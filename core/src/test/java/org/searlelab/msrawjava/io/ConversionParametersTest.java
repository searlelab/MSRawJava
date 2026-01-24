package org.searlelab.msrawjava.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.msrawjava.algorithms.demux.DemuxConfig;
import org.searlelab.msrawjava.model.MassTolerance;
import org.searlelab.msrawjava.model.PPMMassTolerance;

class ConversionParametersTest {

	@TempDir
	Path tmp;

	@Test
	void storesProvidedValues_andAllowsNullOutputDir() {
		ArrayList<File> files=new ArrayList<>();
		files.add(new File("a.raw"));
		files.add(new File("b.d"));

		OutputType type=OutputType.mzML;
		Path out=tmp.resolve("out");
		float ms1=7.5f;
		float ms2=3.25f;
		MassTolerance tol=new PPMMassTolerance(5.0);
		DemuxConfig demux=new DemuxConfig();

		ConversionParameters p=new ConversionParameters(files, type, out, ms1, ms2, true, tol, demux, tmp.resolve("log.txt"), true, false, true);
		assertEquals(files, p.getFileList());
		assertEquals(type, p.getOutType());
		assertEquals(out, p.getOutputDirPath());
		assertEquals(ms1, p.getMinimumMS1Intensity());
		assertEquals(ms2, p.getMinimumMS2Intensity());
		assertTrue(p.isDemultiplex());
		assertEquals(tol, p.getDemuxTolerance());
		assertEquals(demux, p.getDemuxConfig());
		assertEquals(tmp.resolve("log.txt"), p.getLogFilePath());
		assertTrue(p.isBatch());
		assertFalse(p.isSilent());
		assertTrue(p.isNoAnsi());

		ConversionParameters p2=new ConversionParameters(files, type, null, ms1, ms2, false, tol, demux, null, false, true, false);
		assertNull(p2.getOutputDirPath(), "null output directory should be allowed");
		assertFalse(p2.isDemultiplex());
		assertTrue(p2.isSilent());
	}
}
