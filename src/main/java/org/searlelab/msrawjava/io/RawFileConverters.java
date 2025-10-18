package org.searlelab.msrawjava.io;

import java.nio.file.Path;
import java.util.ArrayList;
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

import org.searlelab.msrawjava.Logger;
import org.searlelab.msrawjava.io.thermo.ThermoRawFile;
import org.searlelab.msrawjava.io.tims.BrukerTIMSFile;
import org.searlelab.msrawjava.io.tims.TIMSPeakPicker;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.Peak;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.WindowData;

/**
 * RawFileConverters reads raw files using native readers, normalizes spectra and metadata into the shared model, and
 * directs output to the chosen writer (mzML, MGF, or EncyclopeDIA).
 */
public class RawFileConverters {
	private static final int NUMBER_OF_REPORTING_SECTIONS=20;

	/**
	 * Reads a Thermo RAW, batches spectra over time, attaches metadata and DIA ranges, streams MS1/MS2 to the chosen
	 * writer, and saves the file.
	 */
	public static void writeThermo(Path rawFilePath, Path outputDirPath, OutputType outType) throws Exception {
		ThermoRawFile rawFile=new ThermoRawFile();
		try {
			rawFile.openFile(rawFilePath);

			String originalFileName=rawFilePath.getFileName().toString();
			OutputSpectrumFile outFile=outType.getOutputSpectrumFile();
			outFile.setFileName(originalFileName, rawFilePath.toString());
			outFile.setRanges(new HashMap<Range, WindowData>(rawFile.getRanges()));
			outFile.addMetadata(rawFile.getMetadata());

			float gradientLength=rawFile.getGradientLength();
			int sections=NUMBER_OF_REPORTING_SECTIONS;
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

				outFile.addSpectra(ms1s, ms2s);
				start=stop;
			}

			outFile.saveAsFile(outType.getOutputFilePath(outputDirPath, originalFileName).toFile());
			outFile.close();

		} finally {
			rawFile.close();
		}
	}

	/**
	 * Reads a Bruker timsTOF .d, peak-picks across the ion-mobility dimension, and renumbers scans in order with the
	 * given MS1/MS2 thresholds using parallel workers, streams to the chosen writer, and saves the file. Processes IMS
	 * using a thread pool for speed.
	 */
	public static void writeTims(Path timsFilePath, Path outputDirPath, OutputType outType, float minimumMS1Intensity, float minimumMS2Intensity)
			throws Exception {
		BrukerTIMSFile timsFile=new BrukerTIMSFile();
		timsFile.openFile(timsFilePath);

		String originalFileName=timsFilePath.getFileName().toString();
		OutputSpectrumFile outFile=outType.getOutputSpectrumFile();

		int workers=Math.max(1, Runtime.getRuntime().availableProcessors()-1);
		ExecutorService pool=Executors.newFixedThreadPool(workers, namedFactory("tims-worker"));
		ExecutorService writer=Executors.newSingleThreadExecutor(namedFactory("sqlite-writer"));

		List<CompletableFuture<Void>> writeFutures=new ArrayList<>();
		AtomicReference<Throwable> firstWriteError=new AtomicReference<>();

		outFile.setRanges(new HashMap<Range, WindowData>(timsFile.getRanges()));
		outFile.setFileName(originalFileName, timsFilePath.toString());
		outFile.addMetadata(timsFile.getMetadata());

		try {
			int scanNumber=1;
			float gradientLength=timsFile.getGradientLength();
			int sections=NUMBER_OF_REPORTING_SECTIONS;
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
						//peaks=TIMSPeakPicker.peakPickAcrossIMS(peaks, 2.0f*minimumMS1Intensity);
						peaks=TIMSPeakPicker.peakPickLikeSage(peaks);
						
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

						//peaks=TIMSPeakPicker.peakPickAcrossIMS(peaks, 2.0f*minimumMS2Intensity);
						peaks=TIMSPeakPicker.peakPickLikeSage(peaks);

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
						outFile.addSpectra(sortedMS1s, sortedMS2s);
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

			outFile.saveAsFile(outType.getOutputFilePath(outputDirPath, originalFileName).toFile());
			outFile.close();
		} finally {
			timsFile.close();

			pool.shutdown();
			pool.awaitTermination(365, TimeUnit.DAYS);
		}
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
