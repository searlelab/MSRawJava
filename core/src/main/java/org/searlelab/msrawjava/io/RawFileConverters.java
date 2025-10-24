package org.searlelab.msrawjava.io;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.searlelab.msrawjava.algorithms.CycleAssembler;
import org.searlelab.msrawjava.algorithms.StaggeredDemultiplexer;
import org.searlelab.msrawjava.io.thermo.ThermoRawFile;
import org.searlelab.msrawjava.io.tims.BrukerTIMSFile;
import org.searlelab.msrawjava.io.tims.TIMSPeakPicker;
import org.searlelab.msrawjava.logging.ProgressIndicator;
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
	public static boolean writeThermo(Path rawFilePath, Path outputDirPath, OutputType outType, ProgressIndicator progress) throws Exception {
		ThermoRawFile rawFile=new ThermoRawFile();

		rawFile.openFile(rawFilePath);
		return writeStandard(rawFile, outputDirPath, outType, progress);
	}

	public static boolean writeStandard(StripeFileInterface rawFile, Path outputDirPath, OutputType outType, ProgressIndicator progress) throws Exception {
		OutputSpectrumFile outFile=outType.getOutputSpectrumFile();

		try {
			String originalFileName=rawFile.getFile().getName();
			progress.update("Started converting "+originalFileName+"...");

			outFile.setFileName(originalFileName, rawFile.toString());
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
				if (progress.isCanceled()) return false;
				ArrayList<FragmentScan> ms2s=rawFile.getStripes(new Range(0.0f, Float.MAX_VALUE), start, stop, false);
				if (progress.isCanceled()) return false;

				outFile.addSpectra(ms1s, ms2s);

				progress.update("Found "+ms1s.size()+" MS1s and "+ms2s.size()+" MS2s in range: "+String.format("%.1f", start/60f)+" to "
						+String.format("%.1f", (start+sectionTime)/60f)+" minutes", (i+1)*100f/(sections+1));
				start=stop;
				if (progress.isCanceled()) return false;
			}

			outFile.saveAsFile(outType.getOutputFilePath(outputDirPath, originalFileName).toFile());
			outFile.close();

			progress.update("Finished converting "+originalFileName+"!", 1.0f);
			return true;

		} finally {
			rawFile.close();
			outFile.close();
		}
	}

	public static boolean writeDemux(StripeFileInterface rawFile, Path outputDirPath, OutputType outType, ProgressIndicator progress) throws Exception {
		OutputSpectrumFile outFile=outType.getOutputSpectrumFile();

		try {
			String originalFileName=rawFile.getOriginalFileName();
			progress.update("Started converting "+originalFileName+"...");

			outFile.setFileName(originalFileName, rawFile.getFile().getAbsolutePath());
			Map<Range, WindowData> ranges=rawFile.getRanges();
			for (Map.Entry<Range, WindowData> entry : ranges.entrySet()) {
				Range key=entry.getKey();
				WindowData val=entry.getValue();
				System.out.println(key+" --> "+val);
			}
			System.out.println(ranges.size()+" total");
			
			
			outFile.setRanges(new HashMap<Range, WindowData>(ranges));
			outFile.addMetadata(rawFile.getMetadata());

			ArrayList<Range> acquiredWindows=new ArrayList<>(ranges.keySet());
			acquiredWindows.sort(null);

			StaggeredDemultiplexer demultiplexer=new StaggeredDemultiplexer(acquiredWindows);

			CycleAssembler assembler=new CycleAssembler(acquiredWindows);
			ArrayDeque<ArrayList<FragmentScan>> last4=new ArrayDeque<>(4);

			// A tiny helper to publish one cycle (ms2-only) to the writer.
			final Consumer<ArrayList<FragmentScan>> publishOriginalCycle=(cycle) -> {
				try {
					if (cycle!=null&&!cycle.isEmpty()) {
						outFile.addSpectra(new ArrayList<PrecursorScan>(), cycle);
					}
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			};
			final Consumer<ArrayList<FragmentScan>> publishDemuxedCycle=(cycleDemuxed) -> {
				try {
					if (cycleDemuxed!=null&&!cycleDemuxed.isEmpty()) {
						outFile.addSpectra(new ArrayList<PrecursorScan>(), cycleDemuxed);
					}
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			};

			// Read in coarse time sections (unchanged), but use them only for:
			// 1) Progress reporting and MS1 publishing
			// 2) Feeding MS2 scans to the cycle assembler
			final int sections=NUMBER_OF_REPORTING_SECTIONS;
			final float gradientLength=rawFile.getGradientLength();
			float start=0f;
			final float sectionTime=gradientLength/sections;

			for (int i=0; i<sections; i++) {
				float stop=(i==sections-1)?Float.MAX_VALUE:start+sectionTime;

				// Publish MS1s as-is (unchanged semantics)
				ArrayList<PrecursorScan> ms1s=rawFile.getPrecursors(start, stop);
				if (progress.isCanceled()) return false;

				// Gather MS2s for cycle assembly (don’t directly publish them)
				ArrayList<FragmentScan> ms2s=rawFile.getStripes(new Range(0.0f, Float.MAX_VALUE), start, stop, false);
				if (progress.isCanceled()) return false;

				// Feed MS2 scans into the cycle assembler in arrival order
				for (FragmentScan fs : ms2s) {
					assembler.add(fs);

					// Every time we complete one or more cycles, process them
					ArrayList<ArrayList<FragmentScan>> finished=assembler.drainCompleted();
					for (ArrayList<FragmentScan> cycle : finished) {
						last4.addLast(cycle);
						if (last4.size()>4) last4.removeFirst();

						if (last4.size()==4) {
							// Prepare inputs in order: M2, M1, C0, P1, P2
							ArrayList<FragmentScan> cM2=last4.stream().skip(0).findFirst().get();
							ArrayList<FragmentScan> cM1=last4.stream().skip(1).findFirst().get();
							ArrayList<FragmentScan> cP1=last4.stream().skip(2).findFirst().get();
							ArrayList<FragmentScan> cP2=last4.stream().skip(3).findFirst().get();

							// Run demultiplexing for the middle cycle
							ArrayList<FragmentScan> demuxed=demultiplexer.demultiplex(cM2, cM1, cP1, cP2);

							// Publish the *middle* cycle, replaced with demultiplexed scans
							publishDemuxedCycle.accept(demuxed);

							// Remove the oldest cycle (we just published it), keep the last 4
							last4.removeFirst();
						}
					}
				}

				// Publish the MS1s for this section (as before)
				outFile.addSpectra(ms1s, new ArrayList<FragmentScan>());

				progress.update("Processed "+String.format("%.1f–%.1f", start/60f, Math.min(stop, start+sectionTime)/60f)+" min: "+ms1s.size()+" MS1, "
						+ms2s.size()+" MS2", (i+1)*100f/(sections+1));

				start=stop;
				if (progress.isCanceled()) return false;
			}

			// End-of-file flush:
			// 1) Close any partial cycle the assembler was holding.
			assembler.flushPartial();
			ArrayList<ArrayList<FragmentScan>> tailFinished=assembler.drainCompleted();
			for (ArrayList<FragmentScan> cycle : tailFinished) {
				last4.addLast(cycle);
				if (last4.size()>4) last4.removeFirst();

				if (last4.size()==4) {
					ArrayList<FragmentScan> cM2=last4.stream().skip(0).findFirst().get();
					ArrayList<FragmentScan> cM1=last4.stream().skip(1).findFirst().get();
					ArrayList<FragmentScan> cP1=last4.stream().skip(2).findFirst().get();
					ArrayList<FragmentScan> cP2=last4.stream().skip(3).findFirst().get();

					ArrayList<FragmentScan> demuxed=demultiplexer.demultiplex(cM2, cM1, cP1, cP2);
					publishDemuxedCycle.accept(demuxed);
					last4.removeFirst();
				}
			}

			// Save & close
			outFile.saveAsFile(outType.getOutputFilePath(outputDirPath, originalFileName).toFile());
			outFile.close();

			progress.update("Finished converting "+originalFileName+"!", 1.0f);
			return true;

		} finally {
			try {
				rawFile.close();
			} catch (Throwable t) {
				/* ignore */ }
			try {
				outFile.close();
			} catch (Throwable t) {
				/* ignore */ }
		}
	}

	public static boolean writeTims(Path timsFilePath, Path outputDirPath, OutputType outType, ProgressIndicator progress) throws Exception {
		return writeTims(timsFilePath, outputDirPath, outType, progress, 3, 1);
	}

	/**
	 * Reads a Bruker timsTOF .d, peak-picks across the ion-mobility dimension, and renumbers scans in order with the
	 * given MS1/MS2 thresholds using parallel workers, streams to the chosen writer, and saves the file. Processes IMS
	 * using a thread pool for speed.
	 */
	public static boolean writeTims(Path timsFilePath, Path outputDirPath, OutputType outType, ProgressIndicator progress, float minimumMS1Intensity,
			float minimumMS2Intensity) throws Exception {
		BrukerTIMSFile timsFile=new BrukerTIMSFile();
		timsFile.openFile(timsFilePath);

		String originalFileName=timsFilePath.getFileName().toString();
		progress.update("Started converting "+originalFileName+"...");

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
				if (progress.isCanceled()) return false;
				ArrayList<FragmentScan> ms2s=timsFile.getStripes(new Range(0.0f, Float.MAX_VALUE), start, stop, false);
				if (progress.isCanceled()) return false;

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
						peaks=TIMSPeakPicker.peakPickAcrossIMS(peaks);

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
						peaks=TIMSPeakPicker.peakPickAcrossIMS(peaks);

						if (timsFile.isPASEFDDA()&&peaks.isEmpty()) return null; // don't worry about scan gaps
						return ms2s.get(idx).rebuild(sn, peaks);
					}));
				}
				if (progress.isCanceled()) return false;

				// Collect results (join compute here)
				ArrayList<PrecursorScan> sortedMS1s=new ArrayList<>(ms1s.size());
				for (Future<PrecursorScan> f : ms1Futures) {
					PrecursorScan ps=getOrNull(f);
					if (ps!=null) sortedMS1s.add(ps);
				}
				if (progress.isCanceled()) return false;

				ArrayList<FragmentScan> sortedMS2s=new ArrayList<>(ms2s.size());
				for (Future<FragmentScan> f : ms2Futures) {
					FragmentScan fs=getOrNull(f);
					if (fs!=null) sortedMS2s.add(fs);
				}
				if (progress.isCanceled()) return false;

				// Serialize DB writes on a single writer thread 
				writeFutures.add(CompletableFuture.runAsync(() -> {
					try {
						outFile.addSpectra(sortedMS1s, sortedMS2s);
					} catch (Throwable t) {
						firstWriteError.compareAndSet(null, t);
						throw new CompletionException(t);
					}
				}, writer));

				progress.update("Found "+ms1s.size()+" MS1s and "+ms2s.size()+" MS2s in range: "+String.format("%.1f", start/60f)+" to "
						+String.format("%.1f", (start+sectionTime)/60f)+" minutes", (i+1)*100f/(sections+1));
				if (progress.isCanceled()) return false;

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

			progress.update("Finished converting "+originalFileName+"!", 1.0f);

			return true;
		} finally {
			pool.shutdown();
			pool.awaitTermination(365, TimeUnit.DAYS);

			timsFile.close();
			outFile.close();
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
