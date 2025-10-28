package org.searlelab.msrawjava.io.tims;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.model.PeakInterface;
import org.searlelab.msrawjava.model.PeakWithIMS;

public class TIMSPeakpickerTest {

    private ArrayList<PeakWithIMS> makeCluster(double mz, float[] ims, float[] intensities) {
        ArrayList<PeakWithIMS> peaks = new ArrayList<>();
        for (int i = 0; i < ims.length; i++) {
            peaks.add(new PeakWithIMS(mz, intensities[i], ims[i]));
        }
        return peaks;
    }

    @Test
    void groupsByMzWithinTolerance_sortsByIMS_turnsOffAllPeaks_andIgnoresMinIntensityParam() {
        ArrayList<PeakWithIMS> all1 = new ArrayList<>();
        all1.addAll(makeCluster(500.0, new float[]{1.5f, 1.0f, 2.0f}, new float[]{50f, 10f, 20f}));
        all1.addAll(makeCluster(700.0, new float[]{0.9f, 1.8f}, new float[]{30f, 5f}));
        all1.addAll(makeCluster(600.0, new float[]{1.2f, 1.4f}, new float[]{8f, 7f}));

        ArrayList<ArrayList<PeakWithIMS>> groups1 = TIMSPeakPicker.getIMSChromatograms(new ArrayList<>(all1), 0.0f);

        assertEquals(3, groups1.size(), "Should create one IMS chromatogram per distinct m/z cluster");

        ArrayList<PeakWithIMS> g0 = groups1.get(0);
        assertFalse(g0.isEmpty());
        for (PeakWithIMS p : g0) assertEquals(500.0, p.mz, 1e-9);

        float lastIms = -Float.MAX_VALUE;
        for (PeakWithIMS p : g0) {
            assertTrue(p.ims >= lastIms - 1e-12);
            lastIms = p.ims;
        }

        for (PeakInterface p : all1) assertFalse(p.isAvailable(), "All peaks must be toggled off after assignment");

        int total = 0;
        HashSet<PeakWithIMS> seen = new HashSet<>();
        for (List<PeakWithIMS> g : groups1) {
            total += g.size();
            for (PeakWithIMS p : g) {
                assertTrue(seen.add(p), "Peak should not appear in more than one group");
            }
        }
        assertEquals(all1.size(), total, "All peaks must be consumed into groups");

        ArrayList<PeakWithIMS> all2 = new ArrayList<>();
        all2.addAll(makeCluster(500.0, new float[]{1.5f, 1.0f, 2.0f}, new float[]{50f, 10f, 20f}));
        all2.addAll(makeCluster(700.0, new float[]{0.9f, 1.8f}, new float[]{30f, 5f}));
        all2.addAll(makeCluster(600.0, new float[]{1.2f, 1.4f}, new float[]{8f, 7f}));

        ArrayList<ArrayList<PeakWithIMS>> groups2 = TIMSPeakPicker.getIMSChromatograms(new ArrayList<>(all2), 9999.0f);
        assertEquals(groups1.size(), groups2.size(), "minIntensity param should not affect grouping count");
        for (int i = 0; i < groups1.size(); i++) {
            assertEquals(groups1.get(i).size(), groups2.get(i).size(), "minIntensity param should not affect group sizes");
        }
    }

	@Test
	public void peakpickingTest() throws Exception {
		Path path=Path.of("src", "test", "resources", "spectra", "one_tims_ms1.csv");
		List<String> lines=Files.readAllLines(path);
		ArrayList<PeakWithIMS> peaks=new ArrayList<PeakWithIMS>();

		for (int i=1; i<lines.size(); i++) { // skip header
			String[] parts=lines.get(i).split(",");
			double mz=Double.parseDouble(parts[0]);
			float ims=Float.parseFloat(parts[1]);
			float intensity=Float.parseFloat(parts[2]);
			if (intensity<3.0f) continue;
			peaks.add(new PeakWithIMS(mz, intensity, ims));
		}
		Collections.sort(peaks);

		long time=System.currentTimeMillis();
		ArrayList<PeakWithIMS> picked=TIMSPeakPicker.peakPickAcrossIMS(peaks);
		System.out.println((System.currentTimeMillis()-time)+" msec");
		System.out.println(peaks.size()+"\t"+picked.size());
		assertTrue(picked.size()/(float)peaks.size()<0.3);
		assertTrue(picked.size()/(float)peaks.size()>0.01);
	}
}
