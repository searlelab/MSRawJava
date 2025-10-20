package org.searlelab.msrawjava.io.tims;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.algorithms.MatrixMath;
import org.searlelab.msrawjava.model.AcquiredSpectrum;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PPMMassTolerance;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.WindowData;

class BrukerTIMSFileTest {
	public static void main(String[] args) throws Exception {
		Path path=Paths.get("/Users/searle.brian/Documents/temp/bruker/2025-07-05_17-56-24_One-column-separation.d");
		BrukerTIMSFile f = new BrukerTIMSFile();
        f.openFile(path);
        
        double center=(1585.7584+2*1.00727647)/2.0f;//793.88651;
        PPMMassTolerance tolerance=new PPMMassTolerance(50);
        double width=tolerance.getToleranceInMz(center, center);
        
        
        Range targetMzRange=new Range(center-width/2.0, center+width/2.0);
        System.out.println((center-width/2.0)+" to "+(center+width/2.0));
		ArrayList<FragmentScan> scans=f.getStripes(targetMzRange, 0, Float.MAX_VALUE, false);
        System.out.println(scans.size()+" --> "+center);
        for (FragmentScan scan : scans) {
			System.out.println(scan.getScanStartTime()/60f+"min, "+scan.getSpectrumName());
		}
        
        f.close();
	}
	
	
    @Test
    void rangesRtAndTargetedDDAExtraction() throws Exception {
        Path path = Path.of("src","test","resources","rawdata","dda_test.d");
        Assumptions.assumeTrue(Files.exists(path), "Fixture .d not present: " + path);

		BrukerTIMSFile file = new BrukerTIMSFile();
        file.openFile(path);
		assertTrue(MatrixMath.sum(file.getTICTrace().y)>0);

        // RT range sanity
        Range rtRange = file.getRtRange();
        assertTrue(rtRange.getStart() <= rtRange.getStop());
        assertTrue(rtRange.getStop() > 0.0f, "Non-zero end RT expected");
        
        double[] precursors=file.getPrecursorMzs();
        Assumptions.assumeTrue(precursors.length>0, "Fixture should be DDA");
        
        for (double centerMz : precursors) {
            // Extract a small set of MS2 spectra around that target with and without sqrt transform
            ArrayList<FragmentScan> ms2_linear = file.getStripes(centerMz, 0.0f, Float.MAX_VALUE, false);
            ArrayList<FragmentScan> ms2_sqrt   = file.getStripes(centerMz, 0.0f, Float.MAX_VALUE, true);

            // Counts should be > 0 and identical regardless of transform
            assertFalse(ms2_linear.isEmpty(), "Expected at least one MS2 for the target window");
            assertEquals(ms2_linear.size(), ms2_sqrt.size(), "sqrt transform should not change # of spectra");

            // Each returned spectrum’s isolation window should contain the target center
            for (AcquiredSpectrum s : ms2_linear) {
            	Range range=new Range(s.getIsolationWindowLower(), s.getIsolationWindowUpper());
                assertTrue(range.contains((float) centerMz), "DDA window must contain precursor: "+range.toString()+" missing "+centerMz);
            }

            // MS1 count matches histogram key 0
            Map<Integer,Integer> hist = file.msmsTypeHistogram();
            ArrayList<PrecursorScan> ms1s = file.getPrecursors(0f, Float.MAX_VALUE);
            assertEquals(hist.getOrDefault(0, 0), ms1s.size());
		}

        file.close();
    }

    @Test
    void rangesRtAndTargetedDIAExtraction() throws Exception {
        Path path = Path.of("src","test","resources","rawdata","230711_idleflow_400-1000mz_25mz_diaPasef_10sec.d");
        Assumptions.assumeTrue(Files.exists(path), "Fixture .d not present: " + path);

		BrukerTIMSFile file = new BrukerTIMSFile();
        file.openFile(path);
		assertTrue(MatrixMath.sum(file.getTICTrace().y)>0);

        // RT range sanity
        Range rtRange = file.getRtRange();
        assertTrue(rtRange.getStart() <= rtRange.getStop());
        assertTrue(rtRange.getStop() > 0.0f, "Non-zero end RT expected");

        // DIA windows present
        Map<Range, WindowData> rangeMap = file.getRanges();
        Assumptions.assumeTrue(!rangeMap.isEmpty(), "Fixture should be DIA");
        ArrayList<Range> ranges = new ArrayList<>(rangeMap.keySet());
        Collections.sort(ranges);

        for (Range range : ranges) {
            double centerMz = 0.5 * (range.getStart() + range.getStop());

            // Extract a small set of MS2 spectra around that target with and without sqrt transform
            ArrayList<FragmentScan> ms2_linear = file.getStripes(centerMz, rtRange.getStart(), rtRange.getStop(), false);
            ArrayList<FragmentScan> ms2_sqrt   = file.getStripes(centerMz, rtRange.getStart(), rtRange.getStop(), true);

            // Counts should be > 0 and identical regardless of transform
            assertFalse(ms2_linear.isEmpty(), "Expected at least one MS2 for the target window");
            assertEquals(ms2_linear.size(), ms2_sqrt.size(), "sqrt transform should not change # of spectra");

            // Each returned spectrum’s isolation window should contain the target center
            for (AcquiredSpectrum s : ms2_linear) {
                double isoCenter = 0.5 * (s.getIsolationWindowLower() + s.getIsolationWindowUpper());
                assertTrue(range.contains((float) isoCenter), "Isolation center must fall within chosen DIA range");
            }

            // MS1 count matches histogram key 0
            Map<Integer,Integer> hist = file.msmsTypeHistogram();
            ArrayList<PrecursorScan> ms1s = file.getPrecursors(0f, Float.MAX_VALUE);
            assertEquals(hist.getOrDefault(0, 0), ms1s.size());
		}

        file.close();
	}
}
