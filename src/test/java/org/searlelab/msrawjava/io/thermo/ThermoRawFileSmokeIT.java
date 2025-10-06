package org.searlelab.msrawjava.io.thermo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;

/**
 * Smoke test: can we open a Thermo RAW, read MS1s and MS2s in a few windows,
 * and get sane counts and RT ranges without throwing?
 */
public class ThermoRawFileSmokeIT {
	private static final boolean printFullReport=false;

    @Test
    void openAndRead() throws Exception {
		//raw=Paths.get("/Users/searle.brian/Downloads/adl_testing/HeLa_BCS_MAPMS_DIA_90min_01.raw");

		long startTime=System.currentTimeMillis();

        try {
	        System.out.println("Setting up reader...");
			ThermoServerPool.port();
	        System.out.println("Setup time: "+(System.currentTimeMillis()-startTime)/1000f+" sec");
	        
	        testFile(Path.of("src/test/resources/rawdata/Exploris_DIA_16mzst.raw"), 3, 114);
	        testFile(Path.of("src/test/resources/rawdata/Astral_GPFDIA_2mz.raw"), 4, 134);
	        testFile(Path.of("src/test/resources/rawdata/Stellar_DIA_4mz.raw"), 3, 375);
	        testFile(Path.of("src/test/resources/rawdata/Stellar_DDA.raw"), 3, 60);
        } finally {
        	ThermoServerPool.shutdown();
        }
        
        System.out.println("Total time: "+(System.currentTimeMillis()-startTime)/1000f+" sec");
    }

	private void testFile(Path raw, int expectedPrecursors, int expectedMS2s) throws Exception, IOException {
		long startTime=System.currentTimeMillis();
		System.out.println("Begin reading "+raw.toString()+"..."+" Processing time: "+(System.currentTimeMillis()-startTime)/1000f+" sec");
        ThermoRawFile f=null;
        try {
        	f = new ThermoRawFile(raw);
	        assertEquals(raw.toString(), f.getOriginalFileName());
	        assertTrue(f.getGradientLength()>0.0f);
	        assertTrue(f.getRanges().size()>0);
	        
	        System.out.println("Begin MS1 reading..."+" Processing time: "+(System.currentTimeMillis()-startTime)/1000f+" sec");
	        ArrayList<PrecursorScan> ms1s = f.getPrecursors(0, Float.POSITIVE_INFINITY);
	        assertNotNull(ms1s, "MS1 list should not be null");
	        assertTrue(ms1s.size() > 0, "Expected at least one MS1 spectrum");
	
	        assertEquals(expectedPrecursors, ms1s.size(), "Expect "+expectedPrecursors+" MS2s");
	        for (PrecursorScan ms1 : ms1s) {
				assertTrue(sum(ms1.getIntensityArray())>0.0f, "Expect TIC>0");
				for (double mz : ms1.getMassArray()) {
					assertTrue(mz>0.0f, "Expect every m/z>0");
				}
				assertEquals(ms1.getMassArray().length, ms1.getIntensityArray().length);
				
				if (printFullReport) System.out.println("name: "+ms1.getSpectrumName()+", rtInSec: "+ms1.getScanStartTime()+", index: "+ms1.getSpectrumIndex()+", range: "+ms1.getIsolationWindowLower()+" to "+ms1.getIsolationWindowUpper()+", IIT: "+ms1.getIonInjectionTime()+", TIC: "+sum(ms1.getIntensityArray())+", N: "+ms1.getMassArray().length);
			}
	
	        System.out.println("Begin MS2 reading..."+" Processing time: "+(System.currentTimeMillis()-startTime)/1000f+" sec");
	        ArrayList<FragmentScan> ms2s = f.getStripes(new Range(0, Float.POSITIVE_INFINITY), 0, Float.POSITIVE_INFINITY, false);
	        assertNotNull(ms2s, "MS2 list should not be null");
	        assertTrue(ms2s.size() > 0, "Expected at least one MS2 spectrum");
	        
	        assertEquals(expectedMS2s, ms2s.size(), "Expect "+expectedPrecursors+" MS2s");
	        for (FragmentScan ms2 : ms2s) {
				assertTrue(sum(ms2.getIntensityArray())>0.0f, "Expect TIC>0");
				for (double mz : ms2.getMassArray()) {
					assertTrue(mz>0.0f, "Expect every m/z>0");
				}
				assertEquals(ms2.getMassArray().length, ms2.getIntensityArray().length);
				
				if (printFullReport) System.out.println("name: "+ms2.getSpectrumName()+", rtInSec: "+ms2.getScanStartTime()+", precursor: "+ms2.getPrecursorName()+", index: "+ms2.getSpectrumIndex()+", range: "+ms2.getIsolationWindowLower()+" to "+ms2.getIsolationWindowUpper()+", z: "+ms2.getCharge()+", IIT: "+ms2.getIonInjectionTime()+", TIC: "+sum(ms2.getIntensityArray())+", N: "+ms2.getMassArray().length);
			}
	        System.out.println("Finished! MS1:"+ms1s.size()+", MS2:"+ms2s.size()+" Closing down."+" Processing time: "+(System.currentTimeMillis()-startTime)/1000f+" sec");
	  
	
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