package org.searlelab.msrawjava.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;

class MZMLOutputFileTest {

	@TempDir
	Path tmp;

	private static PrecursorScan ms1(String name, int index, float rtSeconds, double scanLo, double scanHi, double[] mz, float[] inten, float[] ims) {
		return new PrecursorScan(name, index, rtSeconds, 0, scanLo, scanHi, null, mz, inten, ims);
	}

	private static FragmentScan ms2(String name, int index, float rtSeconds, double isoLo, double isoHi, byte z, double[] mz, float[] inten, float[] ims) {
		return new FragmentScan(name, "prec", index, (isoLo+isoHi)/2.0, rtSeconds, 0, null, isoLo, isoHi, mz, inten, ims, z, 0.0, 3000.0);
	}

	@Test
	void writesMinimalMzML_withOneMS1_andOneMS2() throws Exception {
		MZMLOutputFile writer=new MZMLOutputFile();
		assertEquals(".mzML", writer.getFileExtension());

		writer.openFile();
		writer.setFileName("tiny_run", "/data/tiny_run");

		ArrayList<PrecursorScan> ms1s=new ArrayList<>();
		ms1s.add(ms1("ms1-1", 1, 1.234f, 100.0, 1000.0, new double[] {100.0, 200.0}, new float[] {50.0f, 0.0f}, null));

		ArrayList<FragmentScan> ms2s=new ArrayList<>();
		ms2s.add(ms2("ms2-2", 2, 2.5f, 499.9, 500.1, (byte)2, new double[] {150.0, 250.0}, new float[] {10.0f, 0.0f}, null));

		writer.addSpectra(ms1s, ms2s);

		Path out=tmp.resolve("tiny.mzML");
		writer.saveAsFile(out.toFile());
		writer.close();

		String xml=Files.readString(out, StandardCharsets.UTF_8);

		// Root and spectrum list
		assertTrue(xml.contains("<mzML "), "mzML root element should be present");
		assertFalse(xml.contains("SPECTRUM_LIST_COUNT_PLACEHOLDER"), "placeholder should be replaced with count");
		assertTrue(xml.contains("<spectrumList"), "spectrumList must be present");

		// Two spectrum blocks (ids scan=1 and scan=2)
		assertTrue(xml.contains("id=\"scan=1\""));
		assertTrue(xml.contains("id=\"scan=2\""));

		// MS levels
		assertTrue(xml.contains("name=\"ms level\" value=\"1\""));
		assertTrue(xml.contains("name=\"ms level\" value=\"2\""));

		// MS1 metadata (scan start time unit seconds)
		assertTrue(xml.contains("name=\"scan start time\""));
		assertTrue(xml.contains("unitAccession=\"UO:0000010\""), "scan start time should be expressed in seconds");

		// MS2 precursor info (isolation window + selected ion)
		assertTrue(xml.contains("isolationWindow"), "MS2 should contain isolation window");
		assertTrue(xml.contains("selected ion m/z"), "MS2 should contain selected ion m/z");
		assertTrue(xml.contains("name=\"charge state\" value=\"2\""), "MS2 should include charge state");

		// Binary arrays: m/z and intensity arrays present
		assertTrue(xml.contains("<binaryDataArrayList count=\"2\">"));
		assertTrue(xml.contains("name=\"m/z array\""));
		assertTrue(xml.contains("name=\"intensity array\""));
		assertTrue(xml.contains("name=\"64-bit float\""));
		assertTrue(xml.contains("name=\"32-bit float\""));
	}

	@Test
	void writesIonInjectionTimeInMilliseconds() throws Exception {
		MZMLOutputFile writer=new MZMLOutputFile();
		writer.openFile();
		writer.setFileName("iit_run", "/data/iit_run");

		ArrayList<PrecursorScan> ms1s=new ArrayList<>();
		ms1s.add(new PrecursorScan("ms1-1", 1, 1.0f, 0, 100.0, 1000.0, 0.25f, new double[] {100.0}, new float[] {10.0f}, null));

		ArrayList<FragmentScan> ms2s=new ArrayList<>();
		ms2s.add(
				new FragmentScan("ms2-2", "prec", 2, 500.0, 2.0f, 0, 0.5f, 499.9, 500.1, new double[] {150.0}, new float[] {5.0f}, null, (byte)2, 0.0, 3000.0));

		writer.addSpectra(ms1s, ms2s);

		Path out=tmp.resolve("iit.mzML");
		writer.saveAsFile(out.toFile());
		writer.close();

		String xml=Files.readString(out, StandardCharsets.UTF_8);

		assertTrue(xml.contains("name=\"ion injection time\""), "ion injection time should be present");
		assertTrue(xml.contains("unitAccession=\"UO:0000028\""), "ion injection time should be in milliseconds");
		assertTrue(xml.contains("value=\"250.000\""), "MS1 ion injection time should be 250.000 ms");
		assertTrue(xml.contains("value=\"500.000\""), "MS2 ion injection time should be 500.000 ms");
	}

	@Test
	void handlesNullSourcePathWhenWritingHeader() throws Exception {
		MZMLOutputFile writer=new MZMLOutputFile();
		writer.openFile();
		writer.setFileName("null_source", null);

		ArrayList<PrecursorScan> ms1s=new ArrayList<>();
		ms1s.add(ms1("ms1-1", 1, 1.0f, 100.0, 1000.0, new double[] {100.0}, new float[] {10.0f}, null));

		writer.addSpectra(ms1s, new ArrayList<>());

		Path out=tmp.resolve("null_source.mzML");
		writer.saveAsFile(out.toFile());
		writer.close();

		String xml=Files.readString(out, StandardCharsets.UTF_8);
		assertTrue(xml.contains("<sourceFile "), "sourceFile element should be present");
		assertTrue(xml.contains("location=\"file:///\""), "null source path should default to file:///");
	}
}
