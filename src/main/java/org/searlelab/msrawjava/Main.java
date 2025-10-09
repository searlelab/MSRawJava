package org.searlelab.msrawjava;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.searlelab.msrawjava.io.encyclopedia.EncyclopeDIAFile;
import org.searlelab.msrawjava.io.thermo.ThermoRawFile;
import org.searlelab.msrawjava.io.thermo.ThermoServerPool;
import org.searlelab.msrawjava.io.tims.BrukerTIMSFile;
import org.searlelab.msrawjava.io.tims.TIMSPeakPicker;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.Peak;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.WindowData;

public class Main {

	public static void main(String[] args) throws Exception {
		System.out.println("Welcome to MSRawJava");
		if (args.length==0||Arrays.asList(args).contains("-h")) {
			System.out.println("Help (-h):");
			System.out.println("  Specify Thermo .raw or Bruker .d files or any directories that contain those files.");
			return;
		}
		
		
		VendorFiles files=new VendorFiles();
		for (String arg : args) {
			File f=new File(arg);
			if (f.exists()&&f.canRead()) {
				VendorFileFinder.findAndAddRawAndD(f.toPath(), files);
			}
		}

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
			rawFile=new ThermoRawFile();
			rawFile.openFile(rawFilePath);

			String originalFileName=rawFilePath.getFileName().toString();
			EncyclopeDIAFile outFile=new EncyclopeDIAFile(originalFileName);
			outFile.openFile();
			
			float gradientLength=rawFile.getGradientLength();
			int sections=getNumberOfSections(gradientLength);
			float start=0.0f;
			float sectionTime=gradientLength/sections;
			for (int i=0; i<sections; i++) {
				float stop;
				if (i==sections-1) {
					stop=Float.MAX_VALUE;
				} else {
					stop=start+sectionTime;
				}

				ArrayList<PrecursorScan> ms1s=rawFile.getPrecursors(start, stop);
				ArrayList<FragmentScan> ms2s=rawFile.getStripes(new Range(0.0f, Float.MAX_VALUE), start, stop, false);
				int percent=Math.round((i+1)*100f/sections);
				Logger.logLine(percent+"% Found "+ms1s.size()+" MS1s and "+ms2s.size()+" MS2s in range: "+String.format("%.1f", start/60f)+" to "
						+String.format("%.1f", (start+sectionTime)/60f)+" minutes");

				outFile.addPrecursor(ms1s);
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
		BrukerTIMSFile timsFile=new BrukerTIMSFile();
		timsFile.openFile(timsFilePath);

		String originalFileName=timsFilePath.getFileName().toString();
		EncyclopeDIAFile outFile=new EncyclopeDIAFile(originalFileName);
		outFile.openFile();

		int workers=Math.max(1, Runtime.getRuntime().availableProcessors()-1);
		ExecutorService pool=Executors.newFixedThreadPool(workers, namedFactory("tims-worker"));
		ExecutorService writer=Executors.newSingleThreadExecutor(namedFactory("sqlite-writer"));

		List<CompletableFuture<Void>> writeFutures=new ArrayList<>();
		AtomicReference<Throwable> firstWriteError=new AtomicReference<>();

		try {
			int scanNumber=1;
			float gradientLength=timsFile.getGradientLength();
			int sections=getNumberOfSections(gradientLength);
			float start=0.0f;
			float sectionTime=gradientLength/sections;

			for (int i=0; i<sections; i++) {
				float stop=(i==sections-1)?Float.MAX_VALUE:start+sectionTime;

				ArrayList<PrecursorScan> ms1s=timsFile.getPrecursors(start, stop);
				ArrayList<FragmentScan> ms2s=timsFile.getStripes(new Range(0.0f, Float.MAX_VALUE), start, stop, false);

				int percent=Math.round((i+1)*100f/sections);
				Logger.logLine(percent+"% Found "+ms1s.size()+" MS1s and "+ms2s.size()+" MS2s in range: "+String.format("%.1f", start/60f)+" to "
						+String.format("%.1f", (start+sectionTime)/60f)+" minutes");

				// Pre-allocate scan numbers (missing scans are OK)
				final int baseMs1Scan=scanNumber;
				scanNumber+=ms1s.size();
				final int baseMs2Scan=scanNumber;
				scanNumber+=ms2s.size();

				// Submit MS1 tasks
				ArrayList<Future<PrecursorScan>> ms1Futures=new ArrayList<>(ms1s.size());
				for (int j=0; j<ms1s.size(); j++) {
					final int idx=j;
					final int sn=baseMs1Scan+j;
					ms1Futures.add(pool.submit(() -> {
						ArrayList<Peak> peaks=ms1s.get(idx).getPeaks(minimumMS1Intensity);
						Collections.sort(peaks);
						peaks=TIMSPeakPicker.peakPickAcrossIMS(peaks, 2.0f*minimumMS1Intensity);
						return ms1s.get(idx).rebuild(sn, peaks); // never null for MS1
					}));
				}

				// Submit MS2 tasks
				ArrayList<Future<FragmentScan>> ms2Futures=new ArrayList<>(ms2s.size());
				for (int j=0; j<ms2s.size(); j++) {
					final int idx=j;
					final int sn=baseMs2Scan+j;
					ms2Futures.add(pool.submit(() -> {
						ArrayList<Peak> peaks=ms2s.get(idx).getPeaks(minimumMS2Intensity);
						Collections.sort(peaks);
						peaks=TIMSPeakPicker.peakPickAcrossIMS(peaks, 2.0f*minimumMS2Intensity);
						if (timsFile.isPASEFDDA()&&peaks.isEmpty()) return null; // don't worry about scan gaps
						return ms2s.get(idx).rebuild(sn, peaks);
					}));
				}

				// Collect results (join compute here)
				ArrayList<PrecursorScan> sortedMS1s=new ArrayList<>(ms1s.size());
				for (Future<PrecursorScan> f : ms1Futures) {
					PrecursorScan ps=getOrNull(f);
					if (ps!=null) sortedMS1s.add(ps);
				}

				ArrayList<FragmentScan> sortedMS2s=new ArrayList<>(ms2s.size());
				for (Future<FragmentScan> f : ms2Futures) {
					FragmentScan fs=getOrNull(f);
					if (fs!=null) sortedMS2s.add(fs);
				}

				// Serialize DB writes on a single writer thread 
				writeFutures.add(CompletableFuture.runAsync(() -> {
					try {
						outFile.addPrecursor(sortedMS1s);
						outFile.addStripe(sortedMS2s);
					} catch (Throwable t) {
						firstWriteError.compareAndSet(null, t);
						throw new CompletionException(t);
					}
				}, writer));

				start=stop;
			}

			// Finish any pending writes before finalizing the DB
			writer.shutdown();
			writer.awaitTermination(365, TimeUnit.DAYS);
			try {
				CompletableFuture.allOf(writeFutures.toArray(new CompletableFuture[0])).join(); // wait for all writes (no per-iteration blocking)
			} catch (CompletionException ce) {
				throw (Exception)firstWriteError.get();
			} finally {
				if (!writer.awaitTermination(30, TimeUnit.SECONDS)) {
					writer.shutdownNow();
				}
			}

			outFile.setRanges(new HashMap<Range, WindowData>(timsFile.getRanges()));
			outFile.setFileName(originalFileName, originalFileName, timsFilePath.toString());
			outFile.addMetadata(timsFile.getMetadata());

			outFile.createIndices();
			outFile.saveAsFile(outFilePath.toFile());
			outFile.close();
		} finally {
			timsFile.close();

			pool.shutdown();
			pool.awaitTermination(365, TimeUnit.DAYS);
		}
	}

	public static Path changeExtension(Path f, String newExtension) {
		String filename=f.getFileName().toString();
		int i=filename.lastIndexOf('.');
		String name=filename.substring(0, i);
		return f.getParent().resolve(name+newExtension);
	}

	private static int getNumberOfSections(float gradientTimeInSec) {
		return 20;
	}

	private static ThreadFactory namedFactory(String base) {
		AtomicInteger n=new AtomicInteger(1);
		return r -> {
			Thread t=new Thread(r, base+"-"+n.getAndIncrement());
			t.setDaemon(true);
			return t;
		};
	}

	private static <T> T getOrNull(Future<T> f) {
		try {
			return f.get();
		} catch (Exception e) {
			return null;
		}
	}
}
