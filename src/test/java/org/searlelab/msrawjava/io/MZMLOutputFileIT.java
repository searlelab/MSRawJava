package org.searlelab.msrawjava.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.msrawjava.io.tims.BrukerTIMSFile;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.WindowData;

class MZMLOutputFileIT {

	private static final Path D_PATH=Path.of("src", "test", "resources", "rawdata", "230711_idleflow_400-1000mz_25mz_diaPasef_10sec.d");

	@TempDir
	Path tmp;

	@Test
	void writesMzML_forSmallSubsetOfRealData() throws Exception {
		Assumptions.assumeTrue(Files.exists(D_PATH), "Fixture .d not present: "+D_PATH);

		BrukerTIMSFile file=new BrukerTIMSFile();
		file.openFile(D_PATH);

		ArrayList<PrecursorScan> ms1s=file.getPrecursors(0f, Float.MAX_VALUE);

		Map<Range, WindowData> rangeMap=file.getRanges();
		ArrayList<Range> ranges=new ArrayList<>(rangeMap.keySet());
		if (ranges.isEmpty()) {
			ranges.add(new Range(0f, Float.MAX_VALUE));
		}
		Collections.sort(ranges);

		ArrayList<FragmentScan> ms2s=new ArrayList<>();
		final int MAX_MS1=3, MAX_MS2=7;
		// Trim MS1 count
		if (ms1s.size()>MAX_MS1) {
			ms1s=new ArrayList<>(ms1s.subList(0, MAX_MS1));
		}
		// Grab MS2 from first ranges
		for (Range r : ranges) {
			var chunk=file.getStripes(r, 0.0f, Float.MAX_VALUE, false);
			for (FragmentScan s : chunk) {
				ms2s.add(s);
				if (ms2s.size()>=MAX_MS2) break;
			}
			if (ms2s.size()>=MAX_MS2) break;
		}

		Assumptions.assumeTrue(!ms1s.isEmpty()&&!ms2s.isEmpty(), "Need both MS1 and MS2 to exercise writer");

		MZMLOutputFile writer=new MZMLOutputFile();
		writer.openFile();
		writer.setFileName(D_PATH.getFileName().toString(), D_PATH.toString());
		writer.addSpectra(ms1s, ms2s);

		Path out=tmp.resolve("subset.mzML");
		writer.saveAsFile(out.toFile());
		writer.close();
		file.close();

		String xml=Files.readString(out, StandardCharsets.UTF_8);

		// SpectrumList count equals total spectra written
		int total=ms1s.size()+ms2s.size();
		assertTrue(xml.contains("<spectrumList"), "spectrumList element should be present");
		assertTrue(xml.contains("count=\""+total+"\""), "spectrumList count should match total spectra ("+total+")");

		// Presence of both ms levels
		long lvl1=xml.lines().filter(l -> l.contains("name=\"ms level\" value=\"1\"")).count();
		long lvl2=xml.lines().filter(l -> l.contains("name=\"ms level\" value=\"2\"")).count();
		assertEquals(ms1s.size(), lvl1, "ms level 1 lines should match MS1 count");
		assertEquals(ms2s.size(), lvl2, "ms level 2 lines should match MS2 count");

		// At least one precursor block present for MS2
		assertTrue(xml.contains("<precursorList"), "MS2 precursor information should be present");
		assertTrue(xml.contains("selected ion m/z"), "Selected ion m/z should be present");
		assertTrue(xml.contains("<binaryDataArrayList count=\"2\">"), "Binary arrays should be present");
	}
}
