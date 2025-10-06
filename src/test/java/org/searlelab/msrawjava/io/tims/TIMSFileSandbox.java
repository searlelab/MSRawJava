package org.searlelab.msrawjava.io.tims;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.WindowData;

public class TIMSFileSandbox {

    private static final Path D_PATH =//Path.of("src", "test", "resources", "rawdata", "230711_idleflow_400-1000mz_25mz_diaPasef_10sec.d");
    		//Paths.get("/Users/searle.brian/Documents/temp/bruker/PIQ001_EVOSEP01_TIMS03_PRO_HT_BOB2_NP3_Pooled_Loading_Curve_100SPD_0065_S3-H2_1_644.d");
            Paths.get("/Users/searle.brian/Documents/temp/bruker/20181024_RFdemoPlasma110_100ng_100samplesday_S4-A11_1_2631.d");

    @Test
    public void smokeReadAllMS1ThenMS2() throws Exception {
    	long startTime=System.currentTimeMillis();
    	
        BrukerTIMSFile file=new BrukerTIMSFile(D_PATH);
        Map<Integer, Integer> histogram=file.msmsTypeHistogram();
        for (Map.Entry<Integer, Integer> entry : histogram.entrySet()) {
			Integer key = entry.getKey();
			Integer val = entry.getValue();
			System.out.println("Type: "+key+" --> "+val);
		}
        
        ArrayList<PrecursorScan> ms1s=file.getPrecursors(0, Float.MAX_VALUE);
        System.out.println("Found "+ms1s.size()+" MS1s");
        assertEquals(histogram.get(0), ms1s.size());
        for (PrecursorScan ms1 : ms1s) {
        	if (ms1.getSpectrumName().equals("2668")) {
            	System.out.println(ms1.getSpectrumName()+" --> "+ms1.getScanStartTime()+"\t"+ms1.getMassArray().length);
            	for (int i = 0; i < ms1.getMassArray().length; i++) {
					System.out.println(ms1.getMassArray()[i]+","+ms1.getIonMobilityArray()[i]+","+ms1.getIntensityArray()[i]);
				}
            	
            	System.exit(0);
        	}
		}
        
        Map<Range, WindowData> rangeMap = file.getRanges();
		ArrayList<Range> ranges = new ArrayList<Range>(rangeMap.keySet());
        if (ranges.size()==0) {
        	// for DDA
        	ranges.add(new Range(0, Float.MAX_VALUE));
        }
        Collections.sort(ranges);
        
        int count=0;
		for (Range range : ranges) {
            ArrayList<FragmentScan> ms2s=file.getStripes(range, 0.0f, Float.MAX_VALUE, false);
            for (FragmentScan ms2 : ms2s) {
            	assertTrue(range.contains((ms2.getIsolationWindowUpper()+ms2.getIsolationWindowLower())/2.0f), "Range: "+range.toString()+" does not match MS2: "+new Range(ms2.getIsolationWindowLower(), ms2.getIsolationWindowUpper()).toString());
            	//System.out.println(ms2.getSpectrumName()+" --> "+ms2.getScanStartTime()+"\t"+ms2.getMassArray().length);
			}

            count++;
            System.out.println(count+") Found "+ms2s.size()+" MS2s in range: "+range);
		}
        
        file.close();

    	long stopTime=System.currentTimeMillis();
    	System.out.println(((stopTime-startTime)/1000.0f)+" seconds to read "+size(D_PATH.toFile())/1073741824f+" GB");
    }

	private static long size(File file) {
		if (file.isFile()) {
			return file.length();
		} else if (file.isDirectory()) {
			long size = 0;
			File[] files = file.listFiles();
			for (File f : files) {
				size += size(f);
			}
			return size;
		}
		return 0;
	}

	public static float sum(float[] v) {
		float sum=0.0f;
		for (int i=0; i<v.length; i++) {
			sum+=v[i];
		}
		return sum;
	}
}