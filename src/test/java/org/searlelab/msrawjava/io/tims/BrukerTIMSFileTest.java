package org.searlelab.msrawjava.io.tims;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.msrawjava.model.AcquiredSpectrum;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.WindowData;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class BrukerTIMSFileTest {

    private static final Path D_PATH = Path.of("src","test","resources","rawdata","230711_idleflow_400-1000mz_25mz_diaPasef_10sec.d");

    @TempDir
    Path tmp; // unused; just to be consistent with other ITs

    @Test
    void rangesRtAndTargetedStripeExtraction() throws Exception {
        Assumptions.assumeTrue(Files.exists(D_PATH), "Fixture .d not present: " + D_PATH);

        BrukerTIMSFile file = new BrukerTIMSFile();
        file.openFile(D_PATH);

        // RT range sanity
        Range rtRange = file.getRtRange();
        assertTrue(rtRange.getStart() <= rtRange.getStop());
        assertTrue(rtRange.getStop() > 0.0f, "Non-zero end RT expected");

        // DIA windows present
        Map<Range, WindowData> rangeMap = file.getRanges();
        Assumptions.assumeTrue(!rangeMap.isEmpty(), "Fixture should be DIA");
        ArrayList<Range> ranges = new ArrayList<>(rangeMap.keySet());
        Collections.sort(ranges);

        // Choose the middle DIA window; use its center as target m/z
        Range mid = ranges.get(ranges.size()/2);
        double centerMz = 0.5 * (mid.getStart() + mid.getStop());

        // Extract a small set of MS2 spectra around that target with and without sqrt transform
        ArrayList<FragmentScan> ms2_linear = file.getStripes(centerMz, rtRange.getStart(), rtRange.getStop(), false);
        ArrayList<FragmentScan> ms2_sqrt   = file.getStripes(centerMz, rtRange.getStart(), rtRange.getStop(), true);

        // Counts should be > 0 and identical regardless of transform
        assertFalse(ms2_linear.isEmpty(), "Expected at least one MS2 for the target window");
        assertEquals(ms2_linear.size(), ms2_sqrt.size(), "sqrt transform should not change # of spectra");

        // Each returned spectrum’s isolation window should contain the target center
        for (AcquiredSpectrum s : ms2_linear) {
            double isoCenter = 0.5 * (s.getIsolationWindowLower() + s.getIsolationWindowUpper());
            assertTrue(mid.contains((float) isoCenter), "Isolation center must fall within chosen DIA range");
        }

        // MS1 count matches histogram key 0
        Map<Integer,Integer> hist = file.msmsTypeHistogram();
        ArrayList<PrecursorScan> ms1s = file.getPrecursors(0f, Float.MAX_VALUE);
        assertEquals(hist.getOrDefault(0, 0), ms1s.size());

        file.close();
    }
}
