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
import org.searlelab.msrawjava.algorithms.demux.DemuxConfig;
import org.searlelab.msrawjava.io.thermo.ThermoRawFile;
import org.searlelab.msrawjava.io.tims.BrukerTIMSFile;
import org.searlelab.msrawjava.io.tims.TIMSPeakPicker;
import org.searlelab.msrawjava.logging.Logger;
import org.searlelab.msrawjava.logging.ProgressIndicator;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.MassTolerance;
import org.searlelab.msrawjava.model.PeakWithIMS;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.WindowData;
import org.searlelab.msrawjava.threading.ProcessingThreadPool;

/**
 * RawFileConverters reads raw files using native readers, normalizes spectra and metadata into the shared model, and
 * directs output to the chosen writer (mzML, MGF, or EncyclopeDIA).
 */
public class RawFileConverters {
	private static final int NUMBER_OF_REPORTING_SECTIONS=100;

	/**
	 * Reads a Thermo RAW, batches spectra over time, attaches metadata and DIA ranges, streams MS1/MS2 to the chosen
	 * writer, and saves the file.
	 */
	public static boolean writeThermo(ProcessingThreadPool pool, Path rawFilePath, Path outputDirPath, ConversionParameters params, ProgressIndicator progress)
			throws Exception {
		ThermoRawFile rawFile=new ThermoRawFile();

		rawFile.openFile(rawFilePath);
		return writeStandard(pool, rawFile, outputDirPath, params, progress);
	}

	public static boolean writeStandard(ProcessingThreadPool pool, StripeFileInterface rawFile, Path outputDirPath, ConversionParameters params,
			ProgressIndicator progress) throws Exception {
		OutputSpectrumFile outFile=params.getOutType().getOutputSpectrumFile();
		ExecutorService writer=null;

		try {
			String originalFileName=rawFile.getFile().getName();
			progress.update("Started converting "+originalFileName+"...", 0.0f);
			long startTime=System.currentTimeMillis();

			outFile.setFileName(originalFileName, rawFile.toString());
			outFile.setRanges(new HashMap<Range, WindowData>(rawFile.getRanges()));
			outFile.addMetadata(rawFile.getMetadata());

			writer=Executors.newSingleThreadExecutor(namedFactory("sqlite-writer"));
			ArrayDeque<CompletableFuture<Void>> writeFutures=new ArrayDeque<>();
			AtomicReference<Throwable> firstWriteError=new AtomicReference<>();
			final ExecutorService writerRef=writer;
			final ArrayDeque<CompletableFuture<Void>> writeFuturesRef=writeFutures;
			final AtomicReference<Throwable> firstWriteErrorRef=firstWriteError;

			float gradientLength=rawFile.getGradientLength();
			int sections=NUMBER_OF_REPORTING_SECTIONS;
			float start=0.0f;
			float sectionTime=gradientLength/sections;
			for (int i=0; i<sections; i++) {
				float stop=(i==sections-1)?Float.MAX_VALUE:start+sectionTime;

				ArrayList<PrecursorScan> ms1s=rawFile.getPrecursors(start, stop);
				if (progress.isCanceled()) return false;
				ArrayList<FragmentScan> ms2s=rawFile.getStripes(new Range(0.0f, Float.MAX_VALUE), start, stop, false);
				if (progress.isCanceled()) return false;

				submitWrite(outFile, ms1s, ms2s, writerRef, writeFuturesRef, firstWriteErrorRef);

				float totalProgress=(i+1)/(float)(sections+NUMBER_OF_REPORTING_SECTIONS/20f); // only go up to 95%	
				progress.update("Found "+ms1s.size()+" MS1s and "+ms2s.size()+" MS2s in range: "+String.format("%.1f", start/60f)+" to "
						+String.format("%.1f", (start+sectionTime)/60f)+" minutes", totalProgress);

				drainWriteFutures(writeFuturesRef, 1, firstWriteErrorRef);

				start=stop;
				if (progress.isCanceled()) return false;
			}

			drainWriteFutures(writeFuturesRef, 0, firstWriteErrorRef);
			writerRef.shutdown();
			writerRef.awaitTermination(365, TimeUnit.DAYS);
			try {
				CompletableFuture.allOf(writeFuturesRef.toArray(new CompletableFuture[0])).join();
			} catch (CompletionException ce) {
				Throwable error=firstWriteErrorRef.get();
				if (error instanceof Exception) {
					throw (Exception)error;
				}
				throw new Exception(error);
			} finally {
				if (!writer.awaitTermination(30, TimeUnit.SECONDS)) {
					writer.shutdownNow();
				}
			}

			Path outputPath=params.getOutputFilePathOverride();
			if (outputPath==null) {
				outputPath=params.getOutType().getOutputFilePath(outputDirPath, originalFileName);
			}
			outFile.saveAsFile(outputPath.toFile());
			outFile.close();

			String message="Total conversion took "+(System.currentTimeMillis()-startTime)/1000f+" seconds.";
			Logger.logLine(message);
			progress.update(message);
			progress.update("Finished converting "+originalFileName+"!", 1.0f);
			return true;

		} finally {
			rawFile.close();
			outFile.close();
			if (writer!=null) {
				try {
					writer.shutdownNow();
				} catch (Throwable t) {
					/* ignore */
				}
			}
		}
	}

	public static boolean writeDemux(ProcessingThreadPool pool, StripeFileInterface rawFile, Path outputDirPath, ConversionParameters params,
			ProgressIndicator progress) throws Exception {
		OutputSpectrumFile outFile=params.getOutType().getOutputSpectrumFile();

		ExecutorService writer=null;
		try {
			String originalFileName=rawFile.getOriginalFileName();
			progress.update("Started converting "+originalFileName+"...", 0.0f);
			long startTime=System.currentTimeMillis();

			outFile.setFileName(originalFileName, rawFile.getFile().getAbsolutePath());
			Map<Range, WindowData> ranges=rawFile.getRanges();

			outFile.setRanges(new HashMap<Range, WindowData>(ranges));
			outFile.addMetadata(rawFile.getMetadata());

			ArrayList<Range> acquiredWindows=new ArrayList<>(ranges.keySet());
			acquiredWindows.sort(null);

			DemuxConfig demuxConfig=params.getDemuxConfig()==null?new DemuxConfig():params.getDemuxConfig();
			MassTolerance tolerance=params.getDemuxTolerance();
			StaggeredDemultiplexer demultiplexer=new StaggeredDemultiplexer(acquiredWindows, tolerance, demuxConfig);

			CycleAssembler assembler=new CycleAssembler(acquiredWindows);
			ArrayDeque<ArrayList<FragmentScan>> last5=new ArrayDeque<>(5);
			ArrayDeque<Future<ArrayList<FragmentScan>>> demuxQueue=new ArrayDeque<>();
			ExecutorService compute=pool.computePool();
			int workers=getWorkerCount(compute);
			int maxInflight=Math.max(1, workers*2);
			writer=Executors.newSingleThreadExecutor(namedFactory("demux-writer"));
			ArrayDeque<CompletableFuture<Void>> writeFutures=new ArrayDeque<>();
			AtomicReference<Throwable> firstWriteError=new AtomicReference<>();
			final ExecutorService writerRef=writer;
			final ArrayDeque<CompletableFuture<Void>> writeFuturesRef=writeFutures;
			final AtomicReference<Throwable> firstWriteErrorRef=firstWriteError;

			final Consumer<ArrayList<FragmentScan>> publishDemuxedCycle=(cycleDemuxed) -> {
				if (cycleDemuxed!=null&&!cycleDemuxed.isEmpty()) {
					submitWrite(outFile, new ArrayList<PrecursorScan>(), cycleDemuxed, writerRef, writeFuturesRef, firstWriteErrorRef);
				}
			};

			// Read in coarse time sections (unchanged), but use them only for:
			// 1) Progress reporting and MS1 publishing
			// 2) Feeding MS2 scans to the cycle assembler
			final int sections=NUMBER_OF_REPORTING_SECTIONS;
			final float gradientLength=rawFile.getGradientLength();
			float start=0f;
			final float sectionTime=gradientLength/sections;
			int currentScanNumber=1;

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
						last5.addLast(cycle);
						if (last5.size()>5) last5.removeFirst();

						if (last5.size()==5) {
							// Prepare inputs in order: M2, M1, C0, P1, P2
							ArrayList<FragmentScan> cM2=last5.stream().skip(0).findFirst().get();
							ArrayList<FragmentScan> cM1=last5.stream().skip(1).findFirst().get();
							ArrayList<FragmentScan> center=last5.stream().skip(2).findFirst().get();
							ArrayList<FragmentScan> cP1=last5.stream().skip(3).findFirst().get();
							ArrayList<FragmentScan> cP2=last5.stream().skip(4).findFirst().get();

							int baseScanNumber=currentScanNumber;
							currentScanNumber=currentScanNumber+2*cM1.size(); // split each window in two
							demuxQueue.addLast(compute.submit(() -> demultiplexer.demultiplex(cM2, cM1, center, cP1, cP2, baseScanNumber)));

							// Remove the oldest cycle (we just published it), keep the last 4
							last5.removeFirst();
						}
					}
				}

				// Publish the MS1s for this section (as before)
				submitWrite(outFile, ms1s, new ArrayList<FragmentScan>(), writerRef, writeFuturesRef, firstWriteErrorRef);

				float totalProgress=(i+1)/(float)(sections+NUMBER_OF_REPORTING_SECTIONS/20f); // only go up to 95%	
				progress.update("Processed "+String.format("%.1f–%.1f", start/60f, Math.min(stop, start+sectionTime)/60f)+" min: "+ms1s.size()+" MS1, "
						+ms2s.size()+" MS2", totalProgress);

				while (demuxQueue.size()>maxInflight) {
					ArrayList<FragmentScan> demuxed=getDemuxResult(demuxQueue.removeFirst());
					publishDemuxedCycle.accept(demuxed);
				}
				drainWriteFutures(writeFuturesRef, maxInflight, firstWriteErrorRef);

				start=stop;
				if (progress.isCanceled()) return false;
			}

			// End-of-file flush:
			// 1) Close any partial cycle the assembler was holding.
			assembler.flushPartial();
			ArrayList<ArrayList<FragmentScan>> tailFinished=assembler.drainCompleted();
			for (ArrayList<FragmentScan> cycle : tailFinished) {
				last5.addLast(cycle);
				if (last5.size()>5) last5.removeFirst();

				if (last5.size()==5) {
					ArrayList<FragmentScan> cM2=last5.stream().skip(0).findFirst().get();
					ArrayList<FragmentScan> cM1=last5.stream().skip(1).findFirst().get();
					ArrayList<FragmentScan> center=last5.stream().skip(2).findFirst().get();
					ArrayList<FragmentScan> cP1=last5.stream().skip(3).findFirst().get();
					ArrayList<FragmentScan> cP2=last5.stream().skip(4).findFirst().get();

					// only flush if it's a full cycle
					if (cM2.size()==cM1.size()&&cM2.size()==cP1.size()&&cM2.size()==cP2.size()) {
						int baseScanNumber=currentScanNumber;
						currentScanNumber=currentScanNumber+2*cM1.size();
						demuxQueue.addLast(compute.submit(() -> demultiplexer.demultiplex(cM2, cM1, center, cP1, cP2, baseScanNumber)));
						last5.removeFirst();
					}
				}
			}

			while (!demuxQueue.isEmpty()) {
				ArrayList<FragmentScan> demuxed=getDemuxResult(demuxQueue.removeFirst());
				publishDemuxedCycle.accept(demuxed);
			}
			drainWriteFutures(writeFuturesRef, 0, firstWriteErrorRef);

			writerRef.shutdown();
			writerRef.awaitTermination(365, TimeUnit.DAYS);
			try {
				CompletableFuture.allOf(writeFuturesRef.toArray(new CompletableFuture[0])).join();
			} catch (CompletionException ce) {
				Throwable error=firstWriteErrorRef.get();
				if (error instanceof Exception) {
					throw (Exception)error;
				}
				throw new Exception(error);
			} finally {
				if (!writerRef.awaitTermination(30, TimeUnit.SECONDS)) {
					writerRef.shutdownNow();
				}
			}

			// Save & close
			Path outputPath=params.getOutputFilePathOverride();
			if (outputPath==null) {
				outputPath=params.getOutType().getOutputFilePath(outputDirPath, originalFileName);
			}
			outFile.saveAsFile(outputPath.toFile());
			outFile.close();

			String message="Total conversion took "+(System.currentTimeMillis()-startTime)/1000f+" seconds.";
			Logger.logLine(message);
			progress.update(message);
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
			if (writer!=null) {
				try {
					writer.shutdownNow();
				} catch (Throwable t) {
					/* ignore */ }
			}
		}
	}

	public static boolean writeTims(ProcessingThreadPool pool, Path timsFilePath, Path outputDirPath, ConversionParameters params, ProgressIndicator progress)
			throws Exception {
		float minimumMS1Intensity=params.getMinimumMS1Intensity();
		float minimumMS2Intensity=params.getMinimumMS2Intensity();
		BrukerTIMSFile timsFile=new BrukerTIMSFile();
		timsFile.openFile(timsFilePath);

		String originalFileName=timsFilePath.getFileName().toString();
		progress.update("Started converting "+originalFileName+"...", 0.0f);
		long startTime=System.currentTimeMillis();

		OutputSpectrumFile outFile=params.getOutType().getOutputSpectrumFile();

		ExecutorService computePool=pool.computePool();
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
					ms1Futures.add(computePool.submit(() -> {
						ArrayList<PeakWithIMS> peaks=ms1s.get(idx).getPeaks(minimumMS1Intensity);
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
					ms2Futures.add(computePool.submit(() -> {
						ArrayList<PeakWithIMS> peaks=ms2s.get(idx).getPeaks(minimumMS2Intensity);
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
				float totalProgress=(i+1)/(float)(sections+NUMBER_OF_REPORTING_SECTIONS/20f); // only go up to 95%	

				progress.update("Found "+ms1s.size()+" MS1s and "+ms2s.size()+" MS2s in range: "+String.format("%.1f", start/60f)+" to "
						+String.format("%.1f", (start+sectionTime)/60f)+" minutes", totalProgress);
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

			outFile.saveAsFile(params.getOutType().getOutputFilePath(outputDirPath, originalFileName).toFile());
			outFile.close();

			String message="Total conversion took "+(System.currentTimeMillis()-startTime)/1000f+" seconds.";
			Logger.logLine(message);
			progress.update(message);
			progress.update("Finished converting "+originalFileName+"!", 1.0f);

			return true;
		} finally {

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

	private static int getWorkerCount(ExecutorService pool) {
		if (pool instanceof java.util.concurrent.ThreadPoolExecutor) {
			return ((java.util.concurrent.ThreadPoolExecutor)pool).getCorePoolSize();
		}
		int cores=Runtime.getRuntime().availableProcessors();
		return Math.max(1, cores-1);
	}

	private static void submitWrite(OutputSpectrumFile outFile, ArrayList<PrecursorScan> ms1s, ArrayList<FragmentScan> ms2s, ExecutorService writer,
			ArrayDeque<CompletableFuture<Void>> writeFutures, AtomicReference<Throwable> firstWriteError) {
		writeFutures.add(CompletableFuture.runAsync(() -> {
			try {
				outFile.addSpectra(ms1s, ms2s);
			} catch (Throwable t) {
				firstWriteError.compareAndSet(null, t);
				throw new CompletionException(t);
			}
		}, writer));
	}

	private static void drainWriteFutures(ArrayDeque<CompletableFuture<Void>> writeFutures, int maxInflight, AtomicReference<Throwable> firstWriteError)
			throws Exception {
		while (writeFutures.size()>maxInflight) {
			CompletableFuture<Void> future=writeFutures.removeFirst();
			try {
				future.join();
			} catch (CompletionException ce) {
				Throwable error=firstWriteError.get();
				if (error instanceof Exception) {
					throw (Exception)error;
				}
				throw new Exception(error);
			}
		}
	}

	private static ArrayList<FragmentScan> getDemuxResult(Future<ArrayList<FragmentScan>> future) {
		try {
			return future.get();
		} catch (Exception e) {
			Logger.logLine("Error in demux");
			Logger.errorException(e);
			return new ArrayList<>();
		}
	}

	private static <T> T getOrNull(Future<T> f) {
		try {
			return f.get();
		} catch (Exception e) {
			return null;
		}
	}
}
