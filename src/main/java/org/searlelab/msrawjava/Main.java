package org.searlelab.msrawjava;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.searlelab.msrawjava.io.encyclopedia.EncyclopeDIAFile;
import org.searlelab.msrawjava.io.tims.TIMSPeakPicker;
import org.searlelab.msrawjava.io.tims.TIMSStripeFile;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.Peak;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.WindowData;

public class Main {

	public static void main(String[] args) throws Exception {
		Path startingPath=Paths.get("/Users/searle.brian/Documents/temp/bruker/");
		VendorFiles files=VendorFileFinder.collectRawAndD(startingPath);
		
		for (Path path : files.getdDirs()) {
			Logger.logLine("Processing "+path);
			Path outFilePath=changeExtension(path, EncyclopeDIAFile.DIA_EXTENSION);

			Logger.logLine("Writing "+outFilePath);
			writeTims(path, outFilePath, 3.0f, 1.0f);
		}
	}
	
	public static void writeTims(Path timsFilePath, Path outFilePath, float minimumMS1Intensity, float minimumMS2Intensity) throws Exception {
        TIMSStripeFile timsFile=new TIMSStripeFile(timsFilePath);
        
		String originalFileName = timsFilePath.getFileName().toString();
		EncyclopeDIAFile outFile=new EncyclopeDIAFile(originalFileName);
		outFile.openFile();
		
        ArrayList<PrecursorScan> ms1s=timsFile.getPrecursors(0, Float.MAX_VALUE);
        Logger.logLine("Found "+ms1s.size()+" MS1s");
        int scanNumber=1;
        ArrayList<PrecursorScan> sortedMS1s=new ArrayList<PrecursorScan>(ms1s.size());
        for (int i = 0; i < ms1s.size(); i++) {
        	ArrayList<Peak> peaks=ms1s.get(i).getPeaks(minimumMS1Intensity);
        	Collections.sort(peaks);
        	peaks=TIMSPeakPicker.peakPickAcrossIMS(peaks, 2.0f*minimumMS1Intensity);
			PrecursorScan ms1 = ms1s.get(i).rebuild(scanNumber, peaks);
			sortedMS1s.add(ms1);
			scanNumber++;
		}
        
        outFile.addPrecursor(sortedMS1s);
        
        Map<Range, WindowData> rangeMap = timsFile.getRanges();
		ArrayList<Range> ranges = new ArrayList<Range>(rangeMap.keySet());
        if (ranges.size()==0) {
        	// for DDA
        	ranges.add(new Range(0, Float.MAX_VALUE));
        }
        Collections.sort(ranges);
        
        int count=0;
		for (Range range : ranges) {
            ArrayList<FragmentScan> ms2s=timsFile.getStripes(range, 0.0f, Float.MAX_VALUE, false);

            count++;
            Logger.logLine(count+") Found "+ms2s.size()+" MS2s in range: "+range);
            ArrayList<FragmentScan> sortedMS2s=new ArrayList<FragmentScan>(ms2s.size());
            for (int i = 0; i < ms2s.size(); i++) {
            	ArrayList<Peak> peaks=ms2s.get(i).getPeaks(minimumMS2Intensity);
            	Collections.sort(peaks);
            	peaks=TIMSPeakPicker.peakPickAcrossIMS(peaks, 2.0f*minimumMS2Intensity);
    			FragmentScan ms2 = ms2s.get(i).rebuild(scanNumber, peaks);
    			if (ms2.getMassArray().length>0) {
    				sortedMS2s.add(ms2);
    				scanNumber++;
    			}
    		}
            outFile.addStripe(sortedMS2s);
		}
		
		outFile.setRanges(new HashMap<Range, WindowData>(rangeMap));
		outFile.setFileName(originalFileName, originalFileName, timsFilePath.toString());
		outFile.addMetadata(timsFile.getMetadata());
        
        timsFile.close();
        outFile.saveAsFile(outFilePath.toFile());
        outFile.close();
	}
	
	public static Path changeExtension(Path f, String newExtension) {
	    String filename = f.getFileName().toString();
		int i = filename.lastIndexOf('.');
	    String name = filename.substring(0, i);
	    return f.getParent().resolve(name+newExtension);
	}
}
