package org.searlelab.msrawjava.io.thermo;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.FragmentScan;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke test: can we open a Thermo RAW, read MS1s and MS2s in a few windows,
 * and get sane counts and RT ranges without throwing?
 */
public class ThermoRawFileSmokeIT {

    @Test
    void openAndReadExplorisDia() throws Exception {
        // Use the file you staged in test resources
        Path raw = Path.of("src/test/resources/rawdata/Exploris_DIA_16mzst.raw");
        int expectedPrecursors=3;
        int expectedMS2s=114;
        
        System.out.println("Begin file reading...");
        ThermoRawFile f=null;
        try {
        	f = new ThermoRawFile(raw);
	        System.out.println("Begin MS1 reading...");
	        ArrayList<PrecursorScan> ms1s = f.getPrecursors(0, Float.POSITIVE_INFINITY);
	        assertNotNull(ms1s, "MS1 list should not be null");
	        assertTrue(ms1s.size() > 0, "Expected at least one MS1 spectrum");
	
	        assertEquals(expectedPrecursors, ms1s.size(), "Expect "+expectedPrecursors+" MS2s");
	        for (PrecursorScan ms1 : ms1s) {
				//assertTrue(sum(ms1.getIntensityArray())>0.0f, "Expect TIC>0");
				for (double mz : ms1.getMassArray()) {
					assertTrue(mz>0.0f, "Expect every m/z>0");
				}
				System.out.println("name: "+ms1.getSpectrumName()+", index: "+ms1.getSpectrumIndex()+", IIT: "+ms1.getIonInjectionTime()+", intensity array: "+ms1.getIntensityArray());
			}
	
	        System.out.println("Begin MS2 reading...");
	        ArrayList<FragmentScan> ms2s = f.getStripes(new Range(0, Float.POSITIVE_INFINITY), 0, Float.POSITIVE_INFINITY, false);
	        assertNotNull(ms2s, "MS2 list should not be null");
	        assertTrue(ms2s.size() > 0, "Expected at least one MS2 spectrum");
	        
	        assertEquals(expectedMS2s, ms2s.size(), "Expect "+expectedPrecursors+" MS2s");
	        for (FragmentScan ms2 : ms2s) {
				//assertTrue(sum(ms2.getIntensityArray())>0.0f, "Expect TIC>0");
				for (double mz : ms2.getMassArray()) {
					assertTrue(mz>0.0f, "Expect every m/z>0");
				}
				System.out.println("name: "+ms2.getSpectrumName()+", precursor: "+ms2.getPrecursorName()+", index: "+ms2.getSpectrumIndex()+", range: "+ms2.getIsolationWindowLower()+" to "+ms2.getIsolationWindowUpper()+", z: "+ms2.getCharge()+", IIT: "+ms2.getIonInjectionTime()+", intensity array: "+ms2.getIntensityArray());
			}
	        System.out.println("Finished! Closing down.");
	
        } finally {
	        if (f!=null) f.close();
        	ThermoServerPool.shutdown();
        }
        System.out.println("Closed!");
    }
    
	public static float sum(float[] v) {
		float sum=0.0f;
		for (int i=0; i<v.length; i++) {
			sum+=v[i];
		}
		return sum;
	}
}