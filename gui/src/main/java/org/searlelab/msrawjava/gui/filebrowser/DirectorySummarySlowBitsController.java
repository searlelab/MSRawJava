package org.searlelab.msrawjava.gui.filebrowser;

import java.awt.Point;
import java.awt.Rectangle;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

import org.searlelab.msrawjava.gui.GuiProcessingActivity;
import org.searlelab.msrawjava.io.StructuredMetadataProvider;
import org.searlelab.msrawjava.io.VendorFile;
import org.searlelab.msrawjava.io.encyclopedia.EncyclopeDIAFile;
import org.searlelab.msrawjava.io.mzml.MzmlFile;
import org.searlelab.msrawjava.io.thermo.ThermoRawFile;
import org.searlelab.msrawjava.io.thermo.ThermoServerPool;
import org.searlelab.msrawjava.io.tims.BrukerTIMSFile;
import org.searlelab.msrawjava.io.utils.Pair;
import org.searlelab.msrawjava.logging.Logger;

class DirectorySummarySlowBitsController {
	private static final int sparkResolution=128;
	static final SparkData FAILED=new SparkData(new float[0]);
	private static final ConcurrentHashMap<Path, DirectorySummaryMetrics> SLOW_BITS_CACHE=new ConcurrentHashMap<>();
	private static final java.util.Set<String> EXPECTED_SLOW_BITS_FAILURES_LOGGED=ConcurrentHashMap.newKeySet();
	private static final String SLOW_BITS_CANCELLED_BY_USER_SUMMARY=DirectorySummarySlowBitsFailures.SLOW_BITS_CANCELLED_BY_USER_SUMMARY;
	private static final long SLOW_BITS_STALL_THRESHOLD_NANOS=1_000_000_000L;
	private static final long SLOW_BITS_READER_RETRY_NANOS=750_000_000L;
	private static final boolean SLOW_BITS_TIMING_ENABLED=Boolean.getBoolean("msrawjava.gui.slowbits.timing");
	private static final AtomicInteger SLOW_BITS_THREAD_ID=new AtomicInteger(1);

	private final JTable table;
	private final JScrollPane tableScrollPane;
	private final DirectorySummaryModel model;
	private final Runnable repaintProgress;
	private final int slowBitsWorkerCount=Math.max(1, Runtime.getRuntime().availableProcessors()-2);
	private final ExecutorService pool=Executors.newFixedThreadPool(slowBitsWorkerCount, r -> {
		Thread t=new Thread(r, "dir-summary-slow-bits-"+SLOW_BITS_THREAD_ID.getAndIncrement());
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});
	private volatile boolean closed=false;
	private final AtomicInteger slowBitsTotal=new AtomicInteger(0);
	private final AtomicInteger slowBitsDone=new AtomicInteger(0);
	private final java.util.Set<DirectorySummaryRow> slowBitsRunning=ConcurrentHashMap.newKeySet();
	private final ConcurrentHashMap<DirectorySummaryRow, SlowBitsLaunchPlanner.Lane> slowBitsRunningLane=new ConcurrentHashMap<>();
	private final ConcurrentHashMap<DirectorySummaryRow, Long> slowBitsRunningStartNanos=new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Path, Long> slowBitsRetryAfterNanos=new ConcurrentHashMap<>();
	private final java.util.Set<Path> slowBitsDeprioritized=ConcurrentHashMap.newKeySet();
	private final java.util.Set<Path> slowBitsStallWarned=ConcurrentHashMap.newKeySet();
	private final java.util.Set<Path> slowBitsReaderNotReadyWarned=ConcurrentHashMap.newKeySet();
	private final AtomicBoolean slowBitsDispatchPending=new AtomicBoolean(false);
	private final Runnable processingActivityListener=this::requestSlowBitsDispatch;

	DirectorySummarySlowBitsController(JTable table, JScrollPane tableScrollPane, DirectorySummaryModel model, Runnable repaintProgress) {
		this.table=table;
		this.tableScrollPane=tableScrollPane;
		this.model=model;
		this.repaintProgress=repaintProgress;
	}

	void start() {
		closed=false;
		GuiProcessingActivity.addListener(processingActivityListener);
		requestSlowBitsDispatch();
	}

	void stop() {
		closed=true;
		GuiProcessingActivity.removeListener(processingActivityListener);
		slowBitsRunning.clear();
		slowBitsRunningLane.clear();
		slowBitsRunningStartNanos.clear();
		slowBitsRetryAfterNanos.clear();
		slowBitsDeprioritized.clear();
		slowBitsReaderNotReadyWarned.clear();
		DirectorySummarySlowBitsFailures.shutdownSlowBitsPool(pool);
	}

	int totalCount() {
		return slowBitsTotal.get();
	}

	int doneCount() {
		return slowBitsDone.get();
	}

	void initializeSlowBitsProgress(List<DirectorySummaryRow> rows) {
		int total=rows==null?0:rows.size();
		slowBitsTotal.set(Math.max(0, total));
		slowBitsDone.set(0);

		if (rows!=null) {
			for (DirectorySummaryRow row : rows) {
				if (row==null||row.path==null) continue;
				DirectorySummaryMetrics cached=SLOW_BITS_CACHE.get(row.path);
				if (cached!=null) {
					row.applyMetrics(cached);
					markSlowBitsDone(row);
				}
			}
		}
	}

	private void markSlowBitsDone(DirectorySummaryRow row) {
		if (row==null||!row.markSlowBitsReady()) return;
		if (row.path!=null) {
			slowBitsDeprioritized.remove(row.path);
			slowBitsRetryAfterNanos.remove(row.path);
			slowBitsReaderNotReadyWarned.remove(row.path);
		}
		slowBitsDone.incrementAndGet();
		SwingUtilities.invokeLater(repaintProgress);
	}


	void requestSlowBitsDispatch() {
		if (closed) return;
		if (!slowBitsDispatchPending.compareAndSet(false, true)) return;
		SwingUtilities.invokeLater(() -> {
			slowBitsDispatchPending.set(false);
			dispatchSlowBitsNow();
		});
	}

	private void dispatchSlowBitsNow() {
		if (closed) return;
		if (!SwingUtilities.isEventDispatchThread()) {
			requestSlowBitsDispatch();
			return;
		}
		List<DirectorySummaryRow> rows=model.snapshotRows();
		if (rows.isEmpty()) return;

		long nowNanos=System.nanoTime();
		int[] visibleRange=currentVisibleViewRange();
		int firstVisible=visibleRange[0];
		int lastVisible=visibleRange[1];

		ArrayList<SlowBitsLaunchPlanner.RowState> states=new ArrayList<>(rows.size());
		HashMap<Integer, DirectorySummaryRow> rowsByModelIndex=new HashMap<>(rows.size());
		for (int modelIndex=0; modelIndex<rows.size(); modelIndex++) {
			DirectorySummaryRow row=rows.get(modelIndex);
			if (row==null) continue;
			rowsByModelIndex.put(Integer.valueOf(modelIndex), row);
			int viewIndex=safeConvertRowIndexToView(table, modelIndex);
			boolean hidden=(viewIndex<0);
			boolean inViewport=!hidden&&firstVisible>=0&&lastVisible>=0&&viewIndex>=firstVisible&&viewIndex<=lastVisible;
			int distanceFromViewport=hidden?Integer.MAX_VALUE:distanceFromViewport(viewIndex, firstVisible, lastVisible);
			boolean running=slowBitsRunning.contains(row);
			SlowBitsLaunchPlanner.Lane runningLane=running?slowBitsRunningLane.get(row):null;
			Long startNanos=slowBitsRunningStartNanos.get(row);
			long runningNanos=(running&&startNanos!=null)?Math.max(0L, nowNanos-startNanos.longValue()):0L;
			boolean deprioritized=row.path!=null&&slowBitsDeprioritized.contains(row.path);
			long retryAfterNanos=(row.path==null)?0L:slowBitsRetryAfterNanos.getOrDefault(row.path, 0L).longValue();
			boolean launchEligible=!row.isSlowBitsReady()&&nowNanos>=retryAfterNanos;
			states.add(new SlowBitsLaunchPlanner.RowState(modelIndex, row.vendor, hidden, inViewport, row.isSlowBitsReady(), running, runningLane,
					distanceFromViewport, runningNanos, deprioritized, launchEligible));
		}

		SlowBitsLaunchPlanner.Plan plan=SlowBitsLaunchPlanner.plan(states, effectiveSlowBitsWorkerCount(slowBitsWorkerCount),
				SLOW_BITS_STALL_THRESHOLD_NANOS);
		for (Integer stalledModelIndex : plan.stalledVisibleModelRows()) {
			DirectorySummaryRow stalledRow=rowsByModelIndex.get(stalledModelIndex);
			if (stalledRow==null||stalledRow.path==null) continue;
			slowBitsDeprioritized.add(stalledRow.path);
			if (slowBitsStallWarned.add(stalledRow.path)) {
				Logger.logLine("Slow bits still loading after 1s for visible row: "+stalledRow.path);
			}
		}

		for (SlowBitsLaunchPlanner.Launch launch : plan.launches()) {
			DirectorySummaryRow row=rowsByModelIndex.get(Integer.valueOf(launch.modelIndex()));
			if (row==null||row.isSlowBitsReady()) continue;
			if (!isReaderReadyForSlowBits(row.vendor)) {
				deferSlowBitsForReaderNotReady(row);
				continue;
			}
			if (!slowBitsRunning.add(row)) continue;
			slowBitsRunningLane.put(row, launch.lane());
			slowBitsRunningStartNanos.put(row, Long.valueOf(System.nanoTime()));
			if (row.path!=null) slowBitsRetryAfterNanos.remove(row.path);
			try {
				pool.submit(() -> {
					try {
						computeSlowBits(row);
					} finally {
						slowBitsRunning.remove(row);
						slowBitsRunningLane.remove(row);
						slowBitsRunningStartNanos.remove(row);
						requestSlowBitsDispatch();
					}
				});
			} catch (RejectedExecutionException ignore) {
				slowBitsRunning.remove(row);
				slowBitsRunningLane.remove(row);
				slowBitsRunningStartNanos.remove(row);
			}
		}
	}

	private int[] currentVisibleViewRange() {
		int rowCount=table.getRowCount();
		if (rowCount<=0) return new int[] {-1, -1};
		Rectangle vr=tableScrollPane.getViewport().getViewRect();
		int first=table.rowAtPoint(new Point(0, vr.y));
		int last=table.rowAtPoint(new Point(0, vr.y+Math.max(0, vr.height-1)));
		if (first<0) first=0;
		if (last<0) last=rowCount-1;
		if (first>last) {
			int tmp=first;
			first=last;
			last=tmp;
		}
		return new int[] {first, last};
	}

	private static int distanceFromViewport(int viewIndex, int firstVisible, int lastVisible) {
		if (viewIndex<0) return Integer.MAX_VALUE;
		if (firstVisible<0||lastVisible<0) return 0;
		if (viewIndex<firstVisible) return firstVisible-viewIndex;
		if (viewIndex>lastVisible) return viewIndex-lastVisible;
		return 0;
	}

	static int safeConvertRowIndexToView(JTable table, int modelIndex) {
		if (table==null) return -1;
		if (modelIndex<0) return -1;
		try {
			return table.convertRowIndexToView(modelIndex);
		} catch (IndexOutOfBoundsException ignore) {
			return -1;
		} catch (IllegalArgumentException ignore) {
			return -1;
		}
	}

	static int effectiveSlowBitsWorkerCount(int normalWorkerCount) {
		return GuiProcessingActivity.isForegroundWorkActive()?1:Math.max(1, normalWorkerCount);
	}

	private boolean isReaderReadyForSlowBits(VendorFile vendor) {
		if (vendor==VendorFile.THERMO) {
			if (ThermoServerPool.isReady()) return true;
			ThermoServerPool.startAsync();
			return false;
		}
		return true;
	}

	private void deferSlowBitsForReaderNotReady(DirectorySummaryRow row) {
		if (row==null||row.path==null) return;
		slowBitsDeprioritized.add(row.path);
		slowBitsRetryAfterNanos.put(row.path, Long.valueOf(System.nanoTime()+SLOW_BITS_READER_RETRY_NANOS));
		if (slowBitsReaderNotReadyWarned.add(row.path)) {
			Logger.logLine("Deferring slow bits until reader is ready: "+row.path);
		}
	}

	private void computeSlowBits(DirectorySummaryRow row) {
		SlowBitsTiming timing=SlowBitsTiming.start(row);
		try {
			if (closed) {
				timing.status="closed";
				return;
			}
			// Per-file fault isolation: if anything fails, we just skip updating that row
			DirectorySummaryMetrics cached=SLOW_BITS_CACHE.get(row.path);
			if (cached!=null) {
				timing.cacheHit=true;
				timing.status="cache";
				row.applyMetrics(cached);
				markSlowBitsDone(row);
				safeRowUpdate(row);
				return;
			}
			if (row.vendor==VendorFile.ENCYCLOPEDIA) {
				EncyclopeDIAFile dia=null;
				try {
					dia=new EncyclopeDIAFile();
					long start=System.nanoTime();
					dia.openFile(row.path.toFile());
					timing.openNanos+=System.nanoTime()-start;
					start=System.nanoTime();
					Pair<float[], float[]> tic=dia.getTICTrace();
					timing.ticTraceNanos+=System.nanoTime()-start;
					start=System.nanoTime();
					row.acquiredDate=parseAcquiredDate(dia.getMetadata().get(EncyclopeDIAFile.RUN_START_TIME)).orElse(null);
					timing.acquiredDateNanos+=System.nanoTime()-start;
					start=System.nanoTime();
					row.totalTIC=dia.getTIC();
					row.gradientMin=dia.getGradientLength()/60f;
					timing.runSummaryNanos+=System.nanoTime()-start;
					row.spark=SparkData.fromTIC(tic.x, tic.y, sparkResolution);
					SLOW_BITS_CACHE.put(row.path, row.toMetrics());
					markSlowBitsDone(row);
					safeRowUpdate(row);
				} catch (Throwable ignore) {
					timing.status=failureStatus(ignore);
					logSlowBitsFailure(row, ignore);
					row.spark=FAILED;
					markSlowBitsDone(row);
					safeRowUpdate(row);
				} finally {
					long start=System.nanoTime();
					try {
						if (dia!=null) dia.close();
					} catch (Throwable t) {
						logSlowBitsFailure(row, t);
					} finally {
						timing.closeNanos+=System.nanoTime()-start;
					}
				}
			} else if (row.vendor==VendorFile.MZML) {
				MzmlFile mzml=new MzmlFile();
				try {
					long start=System.nanoTime();
					mzml.openFile(row.path.toFile());
					timing.openNanos+=System.nanoTime()-start;
					start=System.nanoTime();
					Pair<float[], float[]> tic=mzml.getTICTrace();
					timing.ticTraceNanos+=System.nanoTime()-start;
					start=System.nanoTime();
					row.acquiredDate=getStructuredRunStartTime(mzml).orElse(null);
					timing.acquiredDateNanos+=System.nanoTime()-start;
					start=System.nanoTime();
					row.totalTIC=mzml.getTIC();
					row.gradientMin=mzml.getGradientLength()/60f;
					timing.runSummaryNanos+=System.nanoTime()-start;
					row.spark=SparkData.fromTIC(tic.x, tic.y, sparkResolution);
					SLOW_BITS_CACHE.put(row.path, row.toMetrics());
					markSlowBitsDone(row);
					safeRowUpdate(row);
				} catch (Throwable ignore) {
					timing.status=failureStatus(ignore);
					logSlowBitsFailure(row, ignore);
					row.spark=FAILED;
					markSlowBitsDone(row);
					safeRowUpdate(row);
				} finally {
					long start=System.nanoTime();
					try {
						mzml.close();
					} catch (Throwable t) {
						logSlowBitsFailure(row, t);
					} finally {
						timing.closeNanos+=System.nanoTime()-start;
					}
				}
			} else if (row.vendor==VendorFile.THERMO) {
				ThermoRawFile raw=new ThermoRawFile();
				boolean skipClose=false;
				try {
					long start=System.nanoTime();
					raw.openFile(row.path);
					timing.openNanos+=System.nanoTime()-start;
					start=System.nanoTime();
					Pair<float[], float[]> tic=raw.getTICTrace();
					timing.ticTraceNanos+=System.nanoTime()-start;
					start=System.nanoTime();
					ThermoRawFile.RunSummary summary=raw.getRunSummary();
					timing.runSummaryNanos+=System.nanoTime()-start;
					start=System.nanoTime();
					Optional<Date> acquired=raw.getRunStartTimeIfKnown();
					row.acquiredDate=acquired.orElse(null);
					timing.acquisitionDateSource=acquired.isPresent()?"open_reply":"missing";
					timing.acquiredDateNanos+=System.nanoTime()-start;
					row.totalTIC=(float)summary.totalIonCurrent;
					row.gradientMin=(float)(summary.gradientLengthSeconds/60.0);
					row.spark=SparkData.fromTIC(tic.x, tic.y, sparkResolution);
					SLOW_BITS_CACHE.put(row.path, row.toMetrics());
					markSlowBitsDone(row);
					safeRowUpdate(row);
				} catch (Throwable ignore) {
					timing.status=failureStatus(ignore);
					timing.acquisitionDateSource="error";
					if (DirectorySummarySlowBitsFailures.isThermoReaderUnavailable(ignore)) {
						if (DirectorySummarySlowBitsFailures.shouldSkipThermoRetryOnClose(closed)) {
							skipClose=true;
							return;
						}
						ThermoServerPool.startAsync();
						deferSlowBitsForReaderNotReady(row);
						safeRowUpdate(row);
						return;
					}
					logSlowBitsFailure(row, ignore);
					row.spark=FAILED;
					markSlowBitsDone(row);
					safeRowUpdate(row);
				} finally {
					long start=System.nanoTime();
					try {
						if (!skipClose) raw.close();
					} catch (Throwable t) {
						logSlowBitsFailure(row, t);
					} finally {
						timing.closeNanos+=System.nanoTime()-start;
					}
				}
			} else {
				BrukerTIMSFile raw=new BrukerTIMSFile();
				try {
					long start=System.nanoTime();
					raw.openFile(row.path);
					timing.openNanos+=System.nanoTime()-start;
					start=System.nanoTime();
					Pair<float[], float[]> tic=raw.getTICTrace();
					timing.ticTraceNanos+=System.nanoTime()-start;
					start=System.nanoTime();
					row.acquiredDate=getStructuredRunStartTime(raw).orElse(null);
					timing.acquiredDateNanos+=System.nanoTime()-start;
					start=System.nanoTime();
					row.totalTIC=raw.getTIC();
					row.gradientMin=raw.getGradientLength()/60f;
					timing.runSummaryNanos+=System.nanoTime()-start;
					row.spark=SparkData.fromTIC(tic.x, tic.y, sparkResolution);
					SLOW_BITS_CACHE.put(row.path, row.toMetrics());
					markSlowBitsDone(row);
					safeRowUpdate(row);
				} catch (Throwable ignore) {
					timing.status=failureStatus(ignore);
					logSlowBitsFailure(row, ignore);
					row.spark=FAILED;
					markSlowBitsDone(row);
					safeRowUpdate(row);
				} finally {
					long start=System.nanoTime();
					try {
						raw.close();
					} catch (Throwable t) {
						logSlowBitsFailure(row, t);
					} finally {
						timing.closeNanos+=System.nanoTime()-start;
					}
				}
			}
		} finally {
			timing.log(row);
		}
	}

	private static String failureStatus(Throwable failure) {
		if (failure==null) return "error";
		return "error:"+failure.getClass().getSimpleName();
	}

	private static class SlowBitsTiming {
		private final long totalStartNanos;
		private boolean cacheHit=false;
		private long openNanos=0L;
		private long ticTraceNanos=0L;
		private long runSummaryNanos=0L;
		private long acquiredDateNanos=0L;
		private long closeNanos=0L;
		private String status="ok";
		private String acquisitionDateSource="n/a";

		private SlowBitsTiming() {
			totalStartNanos=System.nanoTime();
		}

		static SlowBitsTiming start(DirectorySummaryRow row) {
			SlowBitsTiming timing=new SlowBitsTiming();
			if (row!=null&&row.vendor==VendorFile.THERMO) {
				timing.acquisitionDateSource="unknown";
			}
			return timing;
		}

		void log(DirectorySummaryRow row) {
			if (!SLOW_BITS_TIMING_ENABLED) return;
			long totalNanos=System.nanoTime()-totalStartNanos;
			Logger.logLine("slowbits-timing\tvendor="+safeVendor(row)+"\tpath="+safePath(row)+"\tthread="+Thread.currentThread().getName()+"\tstatus="
					+status+"\tcache_hit="+cacheHit+"\ttotal_ms="+millis(totalNanos)+"\topen_ms="+millis(openNanos)+"\ttic_trace_ms="
					+millis(ticTraceNanos)+"\trun_summary_ms="+millis(runSummaryNanos)+"\tacquired_date_ms="+millis(acquiredDateNanos)
					+"\tclose_ms="+millis(closeNanos)+"\tacquisition_date_source="+acquisitionDateSource);
		}

		private static String safeVendor(DirectorySummaryRow row) {
			return row==null||row.vendor==null?"<unknown>":row.vendor.name();
		}

		private static String safePath(DirectorySummaryRow row) {
			return row==null||row.path==null?"<unknown>":row.path.toString();
		}

		private static String millis(long nanos) {
			return String.format(Locale.ROOT, "%.3f", nanos/1_000_000.0);
		}
	}

	static Optional<Date> parseAcquiredDate(String raw) {
		if (raw==null||raw.isBlank()) return Optional.empty();
		String value=raw.trim();
		try {
			return Optional.of(Date.from(Instant.parse(value)));
		} catch (DateTimeParseException ignored) {
		}
		try {
			return Optional.of(Date.from(OffsetDateTime.parse(value).toInstant()));
		} catch (DateTimeParseException ignored) {
		}
		try {
			return Optional.of(Date.from(LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atZone(ZoneId.systemDefault())
					.toInstant()));
		} catch (DateTimeParseException ignored) {
		}
		return Optional.empty();
	}

	private static Optional<Date> getStructuredRunStartTime(StructuredMetadataProvider provider) {
		if (provider==null) return Optional.empty();
		try {
			return provider.getRunStartTime();
		} catch (Exception e) {
			Logger.errorException(e);
			return Optional.empty();
		}
	}

	private void logSlowBitsFailure(DirectorySummaryRow row, Throwable failure) {
		if (failure==null) return;
		String file=(row!=null&&row.path!=null)?row.path.toString():"<unknown>";
		String summary=DirectorySummarySlowBitsFailures.expectedSlowBitsFailureSummary(failure);
		if (summary==null) {
			Logger.errorLine("Unclassified slow-bits failure for "+file+": "+String.valueOf(failure));
			Logger.errorException(failure);
			return;
		}
		String dedupeKey=summary+"|"+file;
		if (EXPECTED_SLOW_BITS_FAILURES_LOGGED.add(dedupeKey)) {
			if (SLOW_BITS_CANCELLED_BY_USER_SUMMARY.equals(summary)) {
				Logger.logLine("Previous request cancelled by user for "+file);
			} else {
				Logger.logLine("Preview unavailable for "+file+": "+summary);
			}
		}
	}

	private void safeRowUpdate(DirectorySummaryRow row) {
		if (closed) return;
		SwingUtilities.invokeLater(() -> model.rowUpdated(row));
	}

}
