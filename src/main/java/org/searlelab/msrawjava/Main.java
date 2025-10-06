package org.searlelab.msrawjava;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import org.searlelab.msrawjava.io.encyclopedia.EncyclopeDIAFile;
import org.searlelab.msrawjava.io.thermo.ThermoRawFile;
import org.searlelab.msrawjava.io.thermo.ThermoServerPool;
import org.searlelab.msrawjava.io.tims.TIMSPeakPicker;
import org.searlelab.msrawjava.io.tims.TIMSStripeFile;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.Peak;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.WindowData;

public class Main {

	public static void main(String[] args) throws Exception {
		Path startingPath=Paths.get("/Users/searle.brian/Documents/temp/thermo");
		VendorFiles files=VendorFileFinder.collectRawAndD(startingPath);

		if (files.getThermoFiles().size()>0) {
			Logger.logLine("Found "+files.getThermoFiles().size()+" total Thermo Raw files");
			try {
				Logger.logLine("Setting up Thermo .raw reader...");
				ThermoServerPool.port();
				
				for (Path path : files.getThermoFiles()) {
					Logger.logLine("Processing .raw "+path);
					Path outFilePath=changeExtension(path, EncyclopeDIAFile.DIA_EXTENSION);
		
					Logger.logLine("Writing "+outFilePath);
					writeThermo(path, outFilePath);
					Logger.logLine("Finished writing "+outFilePath);
				}
	
	        } finally {
	        	ThermoServerPool.shutdown();
	        }
		}

		if (files.getBrukerDirs().size()>0) {
			Logger.logLine("Found "+files.getBrukerDirs().size()+" total timsTOF files");
			for (Path path : files.getBrukerDirs()) {
				Logger.logLine("Processing .d "+path);
				Path outFilePath=changeExtension(path, EncyclopeDIAFile.DIA_EXTENSION);
	
				Logger.logLine("Writing "+outFilePath);
				writeTims(path, outFilePath, 3.0f, 1.0f);
				Logger.logLine("Finished writing "+outFilePath);
			}
		}
	}
	
	public static void writeThermo(Path rawFilePath, Path outFilePath) throws Exception {
        ThermoRawFile rawFile=null;
		try {
	        rawFile=new ThermoRawFile(rawFilePath);
	        
			String originalFileName = rawFilePath.getFileName().toString();
			EncyclopeDIAFile outFile=new EncyclopeDIAFile(originalFileName);
			outFile.openFile();
			
	        ArrayList<PrecursorScan> ms1s=rawFile.getPrecursors(0, Float.MAX_VALUE);
	        Logger.logLine("Found "+ms1s.size()+" MS1s");
	        outFile.addPrecursor(ms1s);
	        
	        float gradientLength=rawFile.getGradientLength();
	        int sections=getNumberOfSections(gradientLength);
	        float start=0.0f;
	        float sectionTime=gradientLength/sections;
	        for (int i = 0; i < sections; i++) {
	        	float stop;
	        	if (i==sections-1) {
	        		stop=Float.MAX_VALUE;
	        	} else {
	        		stop=start+sectionTime;
	        	}
	            ArrayList<FragmentScan> ms2s=rawFile.getStripes(new Range(0.0f, Float.MAX_VALUE), start, stop, false);
	            int percent=Math.round((i+1)*100f/sections);
	            Logger.logLine(percent+"% Found "+ms2s.size()+" MS2s in range: "+String.format("%.1f", start/60)+" to "+String.format("%.1f", (start+sectionTime)/60f)+" minutes");
	            outFile.addStripe(ms2s);
	            start=stop;
			}
			
			outFile.setRanges(new HashMap<Range, WindowData>(rawFile.getRanges()));
			outFile.setFileName(originalFileName, originalFileName, rawFilePath.toString());
			outFile.addMetadata(rawFile.getMetadata());
	        
	        rawFile.close();
	        outFile.createIndices();
	        outFile.saveAsFile(outFilePath.toFile());
	        outFile.close();

        } finally {
	        if (rawFile!=null) rawFile.close();
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

        float gradientLength=timsFile.getGradientLength();
        int sections=getNumberOfSections(gradientLength);
        float start=0.0f;
        float sectionTime=gradientLength/sections;
        for (int i = 0; i < sections; i++) {
        	float stop;
        	if (i==sections-1) {
        		stop=Float.MAX_VALUE;
        	} else {
        		stop=start+sectionTime;
        	}
            ArrayList<FragmentScan> ms2s=timsFile.getStripes(new Range(0.0f, Float.MAX_VALUE), start, stop, false);
            int percent=Math.round((i+1)*100f/sections);
            Logger.logLine(percent+"% Found "+ms2s.size()+" MS2s in range: "+String.format("%.1f", start/60f)+" to "+String.format("%.1f", (start+sectionTime)/60f)+" minutes");
            
            ArrayList<FragmentScan> sortedMS2s=new ArrayList<FragmentScan>(ms2s.size());
            for (int j = 0; j < ms2s.size(); j++) {
            	ArrayList<Peak> peaks=ms2s.get(j).getPeaks(minimumMS2Intensity);
            	Collections.sort(peaks);
            	peaks=TIMSPeakPicker.peakPickAcrossIMS(peaks, 2.0f*minimumMS2Intensity);
            	if (timsFile.isPASEFDDA()) {
            		if (peaks.size()==0) continue;
            	}
    			FragmentScan ms2 = ms2s.get(j).rebuild(scanNumber, peaks);
				sortedMS2s.add(ms2);
				scanNumber++;
    		}
            outFile.addStripe(sortedMS2s);
            start=stop;
		}
		
		outFile.setRanges(new HashMap<Range, WindowData>(timsFile.getRanges()));
		outFile.setFileName(originalFileName, originalFileName, timsFilePath.toString());
		outFile.addMetadata(timsFile.getMetadata());
        
        timsFile.close();
        outFile.createIndices();
        outFile.saveAsFile(outFilePath.toFile());
        outFile.close();
	}
	
	public static Path changeExtension(Path f, String newExtension) {
	    String filename = f.getFileName().toString();
		int i = filename.lastIndexOf('.');
	    String name = filename.substring(0, i);
	    return f.getParent().resolve(name+newExtension);
	}
	
	private static int getNumberOfSections(float gradientTimeInSec) {
		if (gradientTimeInSec<=5*60) return 3;
		if (gradientTimeInSec<=10*60) return 5;
		if (gradientTimeInSec<=20*60) return 10;
		if (gradientTimeInSec<=125*60) return 20;
		return 30;
	}
}
