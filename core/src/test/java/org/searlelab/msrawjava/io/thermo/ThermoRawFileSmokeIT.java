package org.searlelab.msrawjava.io.thermo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.function.Consumer;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.msrawjava.algorithms.MatrixMath;
import org.searlelab.msrawjava.io.OutputType;
import org.searlelab.msrawjava.io.RawFileConverters;
import org.searlelab.msrawjava.model.AcquiredSpectrum;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;

/**
 * Smoke test: can we open a Thermo RAW, read MS1s and MS2s in a few windows, and get sane counts and RT ranges without
 * throwing?
 */
public class ThermoRawFileSmokeIT {
	private static final boolean printFullReport=false;

	@Test
	void openAndRead(@TempDir Path outDir) throws Exception {
		long startTime=System.currentTimeMillis();

		try {
			System.out.println("Setting up reader...");
			ThermoServerPool.port();
			System.out.println("Setup time: "+(System.currentTimeMillis()-startTime)/1000f+" sec");

			testFile(Path.of("src/test/resources/rawdata/Exploris_DIA_16mzst.raw"), 3, 114);
			testFile(Path.of("src/test/resources/rawdata/Astral_GPFDIA_2mz.raw"), 4, 134);
			testFile(Path.of("src/test/resources/rawdata/Stellar_DIA_4mz.raw"), 3, 375);
			testFile(Path.of("src/test/resources/rawdata/Stellar_DDA.raw"), 3, 60);

			writeRawSmokeMGF(Path.of("src/test/resources/rawdata/Exploris_DIA_16mzst.raw"), outDir);
			writeRawSmokeMZML(Path.of("src/test/resources/rawdata/Stellar_DDA.raw"), outDir);
		} finally {
			ThermoServerPool.shutdown();
		}

		System.out.println("Total time: "+(System.currentTimeMillis()-startTime)/1000f+" sec");
	}

	void writeRawSmokeMZML(Path raw, Path outDir) throws Exception {
		Assumptions.assumeTrue(Files.exists(raw), "Fixture .raw not present: "+raw);
		RawFileConverters.writeThermo(raw, outDir, OutputType.mzml);
		Path mzml=firstWithExt(outDir, ".mzml");
		assertNotNull(mzml, "Output .mzML should exist");
		assertTrue(Files.size(mzml)>0, "mzML should not be empty");

		String xml=Files.readString(mzml, StandardCharsets.UTF_8);

		// SpectrumList count equals total spectra written
		assertTrue(xml.contains("<spectrumList"), "spectrumList element should be present");

		// Presence of both ms levels
		long lvl1=xml.lines().filter(l -> l.contains("name=\"ms level\" value=\"1\"")).count();
		long lvl2=xml.lines().filter(l -> l.contains("name=\"ms level\" value=\"2\"")).count();
		assertTrue(lvl1>0, "ms level 1 lines should have MS1s");
		assertTrue(lvl2>0, "ms level 2 lines should have MS2s");

		// At least one precursor block present for MS2
		assertTrue(xml.contains("<precursorList"), "MS2 precursor information should be present");
		assertTrue(xml.contains("selected ion m/z"), "Selected ion m/z should be present");
		assertTrue(xml.contains("<binaryDataArrayList count=\"2\">"), "Binary arrays should be present");
	}

	void writeRawSmokeMGF(Path raw, Path outDir) throws Exception {
		Assumptions.assumeTrue(Files.exists(raw), "Fixture .raw not present: "+raw);
		RawFileConverters.writeThermo(raw, outDir, OutputType.mgf);
		Path mgf=firstWithExt(outDir, ".mgf");
		assertNotNull(mgf, "Output .mgf should exist");
		assertTrue(Files.size(mgf)>0, "MGF should not be empty");

		String content=readHead(mgf, 16384);
		assertTrue(content.contains("BEGIN IONS"));
		assertTrue(content.contains("END IONS"));
	}

	private static Path firstWithExt(Path dir, String ext) throws IOException {
		try (var s=Files.list(dir)) {
			return s.filter(p -> p.getFileName().toString().toLowerCase().endsWith(ext)).findFirst().orElse(null);
		}
	}

	private static String readHead(Path file, int max) throws IOException {
		byte[] b=Files.readAllBytes(file);
		int n=Math.min(b.length, max);
		return new String(b, 0, n, StandardCharsets.UTF_8);
	}

	private void testFile(Path raw, int expectedPrecursors, int expectedMS2s) throws Exception, IOException {
		long startTime=System.currentTimeMillis();
		System.out.println("Begin reading "+raw.toString()+"..."+" Processing time: "+(System.currentTimeMillis()-startTime)/1000f+" sec");
		ThermoRawFile f=null;
		try {
			f=new ThermoRawFile();
			f.openFile(raw);

			assertEquals(raw.toString(), f.getOriginalFileName());
			assertTrue(f.getGradientLength()>0.0f);
			assertTrue(f.getRanges().size()>0);
			assertTrue(f.getMetadata().size()>0);
			
			assertTrue(MatrixMath.sum(f.getTICTrace().y)>0);

			System.out.println("Begin MS1 reading..."+" Processing time: "+(System.currentTimeMillis()-startTime)/1000f+" sec");
			ArrayList<PrecursorScan> ms1s=f.getPrecursors(0, Float.POSITIVE_INFINITY);
			assertNotNull(ms1s, "MS1 list should not be null");
			assertTrue(ms1s.size()>0, "Expected at least one MS1 spectrum");

			// RT monotonicity
			float lastRt=-1f;
			for (AcquiredSpectrum ms1 : ms1s) {
				assertTrue(ms1.getScanStartTime()>=lastRt-1e-6, "MS1 RT should be non-decreasing");
				lastRt=ms1.getScanStartTime();
			}

			assertEquals(expectedPrecursors, ms1s.size(), "Expect "+expectedPrecursors+" MS2s");
			for (AcquiredSpectrum ms1 : ms1s) {
				assertTrue(sum(ms1.getIntensityArray())>0.0f, "Expect TIC>0");
				for (double mz : ms1.getMassArray()) {
					assertTrue(mz>0.0f, "Expect every m/z>0");
				}
				assertEquals(ms1.getMassArray().length, ms1.getIntensityArray().length);

				if (printFullReport) System.out.println("name: "+ms1.getSpectrumName()+", rtInSec: "+ms1.getScanStartTime()+", index: "+ms1.getSpectrumIndex()
						+", range: "+ms1.getIsolationWindowLower()+" to "+ms1.getIsolationWindowUpper()+", IIT: "+ms1.getIonInjectionTime()+", TIC: "
						+sum(ms1.getIntensityArray())+", N: "+ms1.getMassArray().length);
			}

			System.out.println("Begin MS2 reading..."+" Processing time: "+(System.currentTimeMillis()-startTime)/1000f+" sec");
			ArrayList<FragmentScan> ms2s=f.getStripes(new Range(0, Float.POSITIVE_INFINITY), 0, Float.POSITIVE_INFINITY, false);
			assertNotNull(ms2s, "MS2 list should not be null");
			assertTrue(ms2s.size()>0, "Expected at least one MS2 spectrum");

			// RT monotonicity
			lastRt=-1f;
			for (FragmentScan ms2 : ms2s) {
				assertTrue(ms2.getScanStartTime()>=lastRt-1e-6, "MS2 RT should be non-decreasing");
				lastRt=ms2.getScanStartTime();
			}

			assertEquals(expectedMS2s, ms2s.size(), "Expect "+expectedPrecursors+" MS2s");
			for (FragmentScan ms2 : ms2s) {
				assertTrue(sum(ms2.getIntensityArray())>0.0f, "Expect TIC>0");
				for (double mz : ms2.getMassArray()) {
					assertTrue(mz>0.0f, "Expect every m/z>0");
				}
				assertEquals(ms2.getMassArray().length, ms2.getIntensityArray().length);

				if (printFullReport)
					System.out.println("name: "+ms2.getSpectrumName()+", rtInSec: "+ms2.getScanStartTime()+", precursor: "+ms2.getPrecursorName()+", index: "
							+ms2.getSpectrumIndex()+", range: "+ms2.getIsolationWindowLower()+" to "+ms2.getIsolationWindowUpper()+", z: "+ms2.getCharge()
							+", IIT: "+ms2.getIonInjectionTime()+", TIC: "+sum(ms2.getIntensityArray())+", N: "+ms2.getMassArray().length);
			}
			System.out.println("Finished! MS1:"+ms1s.size()+", MS2:"+ms2s.size()+" Closing down."+" Processing time: "
					+(System.currentTimeMillis()-startTime)/1000f+" sec");

			assertTrue(f.isOpen());
			assertEquals(raw.toFile(), f.getFile());

		} finally {
			if (f!=null) f.close();
		}

		System.out.println("Closed! Processing time: "+(System.currentTimeMillis()-startTime)/1000f+" sec");
	}

	public static float sum(float[] v) {
		float sum=0.0f;
		for (int i=0; i<v.length; i++) {
			sum+=v[i];
		}
		return sum;
	}
}