package org.searlelab.msrawjava.io.tims;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.algorithms.MatrixMath;
import org.searlelab.msrawjava.model.AcquiredSpectrum;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.WindowData;

class BrukerTIMSFileTest {
	public static void main(String[] args) throws Exception {
		//Path path=Paths.get("/Users/searle.brian/Documents/temp/bruker/2025-07-05_17-56-24_One-column-separation.d");
		Path path=Paths.get("/Users/searle.brian/Documents/temp/jgreenwald/20251013_Batch2_50_16HBE_WTV3_B2_DIA_Slot1-7_1_12472.d");
		BrukerTIMSFile f=new BrukerTIMSFile();
		f.openFile(path);

		double center=(1585.7584+2*1.00727647)/2.0f;//793.88651;
		ArrayList<FragmentScan> scans=f.getStripes(center, 0, Float.MAX_VALUE, false);
		System.out.println(scans.size()+" --> "+center);
		for (FragmentScan scan : scans) {
			double[] massArray=scan.getMassArray();
			System.out.println(
					scan.getScanStartTime()/60f+"min, "+scan.getSpectrumName()+", "+Arrays.stream(massArray).min()+" to "+Arrays.stream(massArray).max());
		}

		f.close();
	}

	@Test
	void rangesRtAndTargetedDDAExtraction() throws Exception {
		Path path=Path.of("src", "test", "resources", "rawdata", "dda_test.d");
		Assumptions.assumeTrue(Files.exists(path), "Fixture .d not present: "+path);

		BrukerTIMSFile file=new BrukerTIMSFile();
		file.openFile(path);
		assertTrue(MatrixMath.sum(file.getTICTrace().y)>0);
		assertTrue(file.getTIC()>0);

		// RT range sanity
		Range rtRange=file.getRtRange();
		assertTrue(rtRange.getStart()<=rtRange.getStop());
		assertTrue(rtRange.getStop()>0.0f, "Non-zero end RT expected");

		double[] precursors=file.getPrecursorMzs();
		Assumptions.assumeTrue(precursors.length>0, "Fixture should be DDA");

		for (double centerMz : precursors) {
			// Extract a small set of MS2 spectra around that target with and without sqrt transform
			ArrayList<FragmentScan> ms2_linear=file.getStripes(centerMz, 0.0f, Float.MAX_VALUE, false);
			ArrayList<FragmentScan> ms2_sqrt=file.getStripes(centerMz, 0.0f, Float.MAX_VALUE, true);

			// Counts should be > 0 and identical regardless of transform
			assertFalse(ms2_linear.isEmpty(), "Expected at least one MS2 for the target window");
			assertEquals(ms2_linear.size(), ms2_sqrt.size(), "sqrt transform should not change # of spectra");

			// Each returned spectrum’s isolation window should contain the target center
			for (AcquiredSpectrum s : ms2_linear) {
				Range range=new Range(s.getIsolationWindowLower(), s.getIsolationWindowUpper());
				assertTrue(range.contains((float)centerMz), "DDA window must contain precursor: "+range.toString()+" missing "+centerMz);
			}

			// MS1 count matches histogram key 0
			Map<Integer, Integer> hist=file.msmsTypeHistogram();
			ArrayList<PrecursorScan> ms1s=file.getPrecursors(0f, Float.MAX_VALUE);
			assertEquals(hist.getOrDefault(0, 0), ms1s.size());
		}

		file.close();
	}

	@Test
	void rangesRtAndTargetedDIAExtraction() throws Exception {
		Path path=Path.of("src", "test", "resources", "rawdata", "230711_idleflow_400-1000mz_25mz_diaPasef_10sec.d");
		Assumptions.assumeTrue(Files.exists(path), "Fixture .d not present: "+path);

		BrukerTIMSFile file=new BrukerTIMSFile();
		file.openFile(path);
		assertTrue(MatrixMath.sum(file.getTICTrace().y)>0);
		assertTrue(file.getTIC()>0);

		// RT range sanity
		Range rtRange=file.getRtRange();
		assertTrue(rtRange.getStart()<=rtRange.getStop());
		assertTrue(rtRange.getStop()>0.0f, "Non-zero end RT expected");

		// DIA windows present
		Map<Range, WindowData> rangeMap=file.getRanges();
		Assumptions.assumeTrue(!rangeMap.isEmpty(), "Fixture should be DIA");
		ArrayList<Range> ranges=new ArrayList<>(rangeMap.keySet());
		Collections.sort(ranges);

		for (Range range : ranges) {
			double centerMz=0.5*(range.getStart()+range.getStop());

			// Extract a small set of MS2 spectra around that target with and without sqrt transform
			ArrayList<FragmentScan> ms2_linear=file.getStripes(centerMz, rtRange.getStart(), rtRange.getStop(), false);
			ArrayList<FragmentScan> ms2_sqrt=file.getStripes(centerMz, rtRange.getStart(), rtRange.getStop(), true);

			// Counts should be > 0 and identical regardless of transform
			assertFalse(ms2_linear.isEmpty(), "Expected at least one MS2 for the target window");
			assertEquals(ms2_linear.size(), ms2_sqrt.size(), "sqrt transform should not change # of spectra");

			// Each returned spectrum’s isolation window should contain the target center
			for (AcquiredSpectrum s : ms2_linear) {
				double isoCenter=0.5*(s.getIsolationWindowLower()+s.getIsolationWindowUpper());
				assertTrue(range.contains((float)isoCenter), "Isolation center must fall within chosen DIA range");
			}

			// MS1 count matches histogram key 0
			Map<Integer, Integer> hist=file.msmsTypeHistogram();
			ArrayList<PrecursorScan> ms1s=file.getPrecursors(0f, Float.MAX_VALUE);
			assertEquals(hist.getOrDefault(0, 0), ms1s.size());
		}

		file.close();
	}

	@Test
	void initialStateIsNotOpen() {
		BrukerTIMSFile file=new BrukerTIMSFile();
		assertFalse(file.isOpen(), "File should not be open initially");
	}

	@Test
	void closeIsIdempotent() throws Exception {
		BrukerTIMSFile file=new BrukerTIMSFile();

		// Close without opening should not throw
		file.close();
		assertFalse(file.isOpen());

		// Multiple closes should not throw
		file.close();
		file.close();
		assertFalse(file.isOpen());
	}

	@Test
	void operationsOnClosedFileThrow() {
		BrukerTIMSFile file=new BrukerTIMSFile();

		// Operations on closed file should throw IOException
		assertThrows(IOException.class, () -> file.getTIC());
		assertThrows(IOException.class, () -> file.getTICTrace());
		assertThrows(IOException.class, () -> file.getGradientLength());
		assertThrows(IOException.class, () -> file.getMetadata());
		assertThrows(IOException.class, () -> file.getPrecursors(0f, 100f));
		assertThrows(IOException.class, () -> file.getStripes(500.0, 0f, 100f, false));
		assertThrows(IOException.class, () -> file.getStripes(new Range(400f, 600f), 0f, 100f, false));
	}

	@Test
	void getFile_returnsFileObject() throws Exception {
		Path path=Path.of("src", "test", "resources", "rawdata", "dda_test.d");
		Assumptions.assumeTrue(Files.exists(path), "Fixture .d not present: "+path);

		BrukerTIMSFile file=new BrukerTIMSFile();
		file.openFile(path);

		File fileObj=file.getFile();
		assertNotNull(fileObj);
		assertEquals(path.toFile(), fileObj);

		file.close();
	}

	@Test
	void getOriginalFileName_returnsDirectoryName() throws Exception {
		Path path=Path.of("src", "test", "resources", "rawdata", "dda_test.d");
		Assumptions.assumeTrue(Files.exists(path), "Fixture .d not present: "+path);

		BrukerTIMSFile file=new BrukerTIMSFile();
		file.openFile(path);

		String name=file.getOriginalFileName();
		assertNotNull(name);
		assertEquals("dda_test.d", name);

		file.close();
	}

	@Test
	void isPASEFDDA_detectsDDAFile() throws Exception {
		Path path=Path.of("src", "test", "resources", "rawdata", "dda_test.d");
		Assumptions.assumeTrue(Files.exists(path), "Fixture .d not present: "+path);

		BrukerTIMSFile file=new BrukerTIMSFile();
		file.openFile(path);

		assertTrue(file.isPASEFDDA(), "DDA file should be detected as PASEF DDA");
		assertFalse(file.isPASEFDIA(), "DDA file should not be detected as PASEF DIA");

		file.close();
	}

	@Test
	void isPASEFDIA_detectsDIAFile() throws Exception {
		Path path=Path.of("src", "test", "resources", "rawdata", "230711_idleflow_400-1000mz_25mz_diaPasef_10sec.d");
		Assumptions.assumeTrue(Files.exists(path), "Fixture .d not present: "+path);

		BrukerTIMSFile file=new BrukerTIMSFile();
		file.openFile(path);

		assertTrue(file.isPASEFDIA(), "DIA file should be detected as PASEF DIA");
		assertFalse(file.isPASEFDDA(), "DIA file should not be detected as PASEF DDA");

		file.close();
	}

	@Test
	void isOpen_tracksState() throws Exception {
		Path path=Path.of("src", "test", "resources", "rawdata", "dda_test.d");
		Assumptions.assumeTrue(Files.exists(path), "Fixture .d not present: "+path);

		BrukerTIMSFile file=new BrukerTIMSFile();
		assertFalse(file.isOpen(), "Should not be open initially");

		file.openFile(path);
		assertTrue(file.isOpen(), "Should be open after opening");

		file.close();
		assertFalse(file.isOpen(), "Should not be open after closing");
	}

	@Test
	void getMetadata_returnsComprehensiveInfo() throws Exception {
		Path path=Path.of("src", "test", "resources", "rawdata", "dda_test.d");
		Assumptions.assumeTrue(Files.exists(path), "Fixture .d not present: "+path);

		BrukerTIMSFile file=new BrukerTIMSFile();
		file.openFile(path);

		Map<String, String> metadata=file.getMetadata();
		assertNotNull(metadata);
		assertFalse(metadata.isEmpty(), "Metadata should not be empty");

		// Verify expected keys are present
		assertTrue(metadata.containsKey("file.path"), "Should contain file.path");
		assertTrue(metadata.containsKey("file.name"), "Should contain file.name");
		assertTrue(metadata.containsKey("frames.total"), "Should contain frames.total");
		assertTrue(metadata.containsKey("rt.start.s"), "Should contain rt.start.s");
		assertTrue(metadata.containsKey("rt.end.s"), "Should contain rt.end.s");

		// Verify file path and name
		assertTrue(metadata.get("file.path").contains("dda_test.d"));
		assertEquals("dda_test.d", metadata.get("file.name"));

		file.close();
	}

	@Test
	void getMetadata_cachedAfterFirstCall() throws Exception {
		Path path=Path.of("src", "test", "resources", "rawdata", "dda_test.d");
		Assumptions.assumeTrue(Files.exists(path), "Fixture .d not present: "+path);

		BrukerTIMSFile file=new BrukerTIMSFile();
		file.openFile(path);

		Map<String, String> metadata1=file.getMetadata();
		Map<String, String> metadata2=file.getMetadata();

		// Should return same instance (cached)
		assertTrue(metadata1==metadata2, "Metadata should be cached");

		file.close();
	}

	@Test
	void getRanges_returnsEmptyForDDA() throws Exception {
		Path path=Path.of("src", "test", "resources", "rawdata", "dda_test.d");
		Assumptions.assumeTrue(Files.exists(path), "Fixture .d not present: "+path);

		BrukerTIMSFile file=new BrukerTIMSFile();
		file.openFile(path);

		Map<Range, WindowData> ranges=file.getRanges();
		assertNotNull(ranges);
		// DDA files don't have DIA windows
		assertTrue(ranges.isEmpty(), "DDA file should have empty ranges");

		file.close();
	}

	@Test
	void getRanges_returnsWindowsForDIA() throws Exception {
		Path path=Path.of("src", "test", "resources", "rawdata", "230711_idleflow_400-1000mz_25mz_diaPasef_10sec.d");
		Assumptions.assumeTrue(Files.exists(path), "Fixture .d not present: "+path);

		BrukerTIMSFile file=new BrukerTIMSFile();
		file.openFile(path);

		Map<Range, WindowData> ranges=file.getRanges();
		assertNotNull(ranges);
		assertFalse(ranges.isEmpty(), "DIA file should have isolation windows");

		// Verify each range has window data
		for (Map.Entry<Range, WindowData> entry : ranges.entrySet()) {
			Range range=entry.getKey();
			WindowData data=entry.getValue();

			assertTrue(range.getStart()<range.getStop(), "Range start should be less than stop");
			assertTrue(data.getNumberOfMSMS()>0, "Should have positive window count");
			assertTrue(data.getAverageDutyCycle()>=0, "Duty cycle should be non-negative");
		}

		file.close();
	}

	@Test
	void getGradientLength_returnsPositiveValue() throws Exception {
		Path path=Path.of("src", "test", "resources", "rawdata", "dda_test.d");
		Assumptions.assumeTrue(Files.exists(path), "Fixture .d not present: "+path);

		BrukerTIMSFile file=new BrukerTIMSFile();
		file.openFile(path);

		float gradientLength=file.getGradientLength();
		assertTrue(gradientLength>0, "Gradient length should be positive");

		// Should match RT range
		Range rtRange=file.getRtRange();
		float expectedLength=rtRange.getStop()-rtRange.getStart();
		assertEquals(expectedLength, gradientLength, 0.001f);

		file.close();
	}

	@Test
	void msmsTypeHistogram_showsExpectedTypes() throws Exception {
		Path path=Path.of("src", "test", "resources", "rawdata", "dda_test.d");
		Assumptions.assumeTrue(Files.exists(path), "Fixture .d not present: "+path);

		BrukerTIMSFile file=new BrukerTIMSFile();
		file.openFile(path);

		Map<Integer, Integer> hist=file.msmsTypeHistogram();
		assertNotNull(hist);
		assertFalse(hist.isEmpty(), "Histogram should not be empty");

		// DDA files should have type 0 (MS1) and type 8 (DDA MS2)
		assertTrue(hist.containsKey(0), "Should contain MS1 frames (type 0)");
		if (file.isPASEFDDA()) {
			assertTrue(hist.containsKey(8), "DDA file should contain type 8 frames");
		}

		// All counts should be positive
		for (Integer count : hist.values()) {
			assertTrue(count>0, "Frame counts should be positive");
		}

		file.close();
	}

	@Test
	void getPrecursors_respectsRTFilter() throws Exception {
		Path path=Path.of("src", "test", "resources", "rawdata", "dda_test.d");
		Assumptions.assumeTrue(Files.exists(path), "Fixture .d not present: "+path);

		BrukerTIMSFile file=new BrukerTIMSFile();
		file.openFile(path);

		Range rtRange=file.getRtRange();
		float mid=(rtRange.getStart()+rtRange.getStop())/2f;

		// Get all precursors
		ArrayList<PrecursorScan> allPrecursors=file.getPrecursors(0f, Float.MAX_VALUE);

		// Get first half
		ArrayList<PrecursorScan> firstHalf=file.getPrecursors(0f, mid);

		// Get second half
		ArrayList<PrecursorScan> secondHalf=file.getPrecursors(mid, Float.MAX_VALUE);

		// First half should be smaller or equal to all
		assertTrue(firstHalf.size()<=allPrecursors.size());
		assertTrue(secondHalf.size()<=allPrecursors.size());

		// All precursors in first half should have RT <= mid
		for (PrecursorScan scan : firstHalf) {
			assertTrue(scan.getScanStartTime()<=mid, "RT should be <= mid");
		}

		// All precursors in second half should have RT >= mid
		for (PrecursorScan scan : secondHalf) {
			assertTrue(scan.getScanStartTime()>=mid, "RT should be >= mid");
		}

		file.close();
	}

	@Test
	void getStripes_withRange_respectsRTFilter() throws Exception {
		Path path=Path.of("src", "test", "resources", "rawdata", "230711_idleflow_400-1000mz_25mz_diaPasef_10sec.d");
		Assumptions.assumeTrue(Files.exists(path), "Fixture .d not present: "+path);

		BrukerTIMSFile file=new BrukerTIMSFile();
		file.openFile(path);

		Range rtRange=file.getRtRange();
		Range mzRange=new Range(500f, 600f);
		float mid=(rtRange.getStart()+rtRange.getStop())/2f;

		// Get all fragments in m/z range
		ArrayList<FragmentScan> allFragments=file.getStripes(mzRange, 0f, Float.MAX_VALUE, false);

		// Get first half
		ArrayList<FragmentScan> firstHalf=file.getStripes(mzRange, 0f, mid, false);

		// First half should be smaller or equal to all
		assertTrue(firstHalf.size()<=allFragments.size());

		// All fragments in first half should have RT <= mid
		for (FragmentScan scan : firstHalf) {
			assertTrue(scan.getScanStartTime()<=mid, "RT should be <= mid");
		}

		file.close();
	}

	@Test
	void openFile_withFilePath_works() throws Exception {
		Path path=Path.of("src", "test", "resources", "rawdata", "dda_test.d");
		Assumptions.assumeTrue(Files.exists(path), "Fixture .d not present: "+path);

		BrukerTIMSFile file=new BrukerTIMSFile();
		file.openFile(path.toFile());

		assertTrue(file.isOpen());
		assertEquals(path.toFile(), file.getFile());

		file.close();
	}

	@Test
	void reopeningFile_closesExisting() throws Exception {
		Path path=Path.of("src", "test", "resources", "rawdata", "dda_test.d");
		Assumptions.assumeTrue(Files.exists(path), "Fixture .d not present: "+path);

		BrukerTIMSFile file=new BrukerTIMSFile();
		file.openFile(path);
		assertTrue(file.isOpen());

		// Reopen same file
		file.openFile(path);
		assertTrue(file.isOpen(), "Should still be open after reopening");

		file.close();
	}
}
