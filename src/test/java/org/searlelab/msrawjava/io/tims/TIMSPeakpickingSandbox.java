package org.searlelab.msrawjava.io.tims;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.model.Peak;

public class TIMSPeakpickingSandbox {

    @Test
    public void peakpickingTest() throws Exception {
    	Path path=Path.of("src", "test", "resources", "spectra", "one_tims_ms1.csv");
    	List<String> lines = Files.readAllLines(path);
    	ArrayList<Peak> peaks=new ArrayList<Peak>();

        for (int i = 1; i < lines.size(); i++) { // skip header
            String[] parts = lines.get(i).split(",");
            double mz = Double.parseDouble(parts[0]);
            float ims = Float.parseFloat(parts[1]);
            float intensity = Float.parseFloat(parts[2]);
            if (intensity<3.0f) continue;
            peaks.add(new Peak(mz, intensity, ims));
        }
        Collections.sort(peaks);
    	
        long time=System.currentTimeMillis();
        ArrayList<Peak> picked=TIMSPeakPicker.peakPickAcrossIMS(peaks, 2.0f*3.0f);
        System.out.println((System.currentTimeMillis()-time)+" msec");
        System.out.println(peaks.size()+"\t"+picked.size());
        assertTrue(picked.size()/(float)peaks.size()<0.2);
        assertTrue(picked.size()/(float)peaks.size()>0.001);
    }
}
