package org.searlelab.msrawjava.tools;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.searlelab.msrawjava.io.StructuredMetadataProvider;
import org.searlelab.msrawjava.io.StripeFileInterface;
import org.searlelab.msrawjava.io.VendorFile;
import org.searlelab.msrawjava.io.VendorFileFinder;
import org.searlelab.msrawjava.io.VendorFiles;
import org.searlelab.msrawjava.io.encyclopedia.EncyclopeDIAFile;
import org.searlelab.msrawjava.io.mzml.MzmlFile;
import org.searlelab.msrawjava.io.thermo.ThermoRawFile;
import org.searlelab.msrawjava.io.thermo.ThermoServerPool;
import org.searlelab.msrawjava.io.tims.BrukerTIMSFile;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.WindowData;

/**
 * Headless harness for timing the same reader calls used by the GUI directory-summary slow bits.
 *
 * Run after test compilation, for example:
 * mvn -pl core -am -Dskip.build.natives=true -DskipTests test-compile exec:java \
 *   -Dexec.classpathScope=test \
 *   -Dexec.mainClass=org.searlelab.msrawjava.tools.DirectorySlowBitsBenchmark \
 *   -Dexec.args=/path/to/raw-directory
 */
public final class DirectorySlowBitsBenchmark {
	private DirectorySlowBitsBenchmark() {
	}

	public static void main(String[] args) throws Exception {
		if (args.length!=1) {
			System.err.println("Usage: DirectorySlowBitsBenchmark <directory-or-raw-file>");
			System.exit(2);
		}

		Path start=Path.of(args[0]);
		long discoverStart=System.nanoTime();
		VendorFiles files=VendorFileFinder.findAndAddRawAndD(start, true, true);
		long discoverNanos=System.nanoTime()-discoverStart;

		List<Entry> entries=new ArrayList<>();
		add(entries, files.getThermoFiles(), VendorFile.THERMO);
		add(entries, files.getBrukerDirs(), VendorFile.BRUKER);
		add(entries, files.getDiaFiles(), VendorFile.ENCYCLOPEDIA);
		add(entries, files.getMzmlFiles(), VendorFile.MZML);

		try {
			System.out.println("vendor\tpath\tdiscover_ms\topen_ms\tmetadata_date_ms\tranges_ms\ttic_trace_ms\trun_summary_ms\tclose_ms\ttotal_ms\tstatus");
			for (Entry entry : entries) {
				System.out.println(timeEntry(entry, discoverNanos));
			}
		} finally {
			ThermoServerPool.shutdown();
		}
	}

	private static void add(List<Entry> entries, List<Path> paths, VendorFile vendor) {
		for (Path path : paths) {
			entries.add(new Entry(vendor, path));
		}
	}

	private static String timeEntry(Entry entry, long discoverNanos) {
		TimedReader timed=new TimedReader(entry.vendor, entry.path);
		long totalStart=System.nanoTime();
		try {
			timed.open();
			timed.metadataDate();
			timed.ranges();
			timed.ticTrace();
			timed.runSummary();
			timed.status="ok";
		} catch (Throwable t) {
			timed.status=t.getClass().getSimpleName()+":"+String.valueOf(t.getMessage()).replace('\t', ' ').replace('\n', ' ');
		} finally {
			timed.close();
			timed.totalNanos=System.nanoTime()-totalStart;
		}
		return entry.vendor.name()+"\t"+entry.path+"\t"+millis(discoverNanos)+"\t"+millis(timed.openNanos)+"\t"+millis(timed.metadataDateNanos)+"\t"
				+millis(timed.rangesNanos)+"\t"+millis(timed.ticTraceNanos)+"\t"+millis(timed.runSummaryNanos)+"\t"+millis(timed.closeNanos)+"\t"
				+millis(timed.totalNanos)+"\t"+timed.status;
	}

	private static String millis(long nanos) {
		return String.format(java.util.Locale.ROOT, "%.3f", nanos/1_000_000.0);
	}

	private static final class Entry {
		private final VendorFile vendor;
		private final Path path;

		private Entry(VendorFile vendor, Path path) {
			this.vendor=vendor;
			this.path=path;
		}
	}

	private static final class TimedReader {
		private final VendorFile vendor;
		private final Path path;
		private StripeFileInterface reader;
		private long openNanos;
		private long metadataDateNanos;
		private long rangesNanos;
		private long ticTraceNanos;
		private long runSummaryNanos;
		private long closeNanos;
		private long totalNanos;
		private String status="not-run";

		private TimedReader(VendorFile vendor, Path path) {
			this.vendor=vendor;
			this.path=path;
		}

		private void open() throws Exception {
			long start=System.nanoTime();
			if (vendor==VendorFile.THERMO) {
				ThermoRawFile raw=new ThermoRawFile();
				raw.openFile(path);
				reader=raw;
			} else if (vendor==VendorFile.BRUKER) {
				BrukerTIMSFile raw=new BrukerTIMSFile();
				raw.openFile(path);
				reader=raw;
			} else if (vendor==VendorFile.ENCYCLOPEDIA) {
				EncyclopeDIAFile dia=new EncyclopeDIAFile();
				dia.openFile(path.toFile());
				reader=dia;
			} else if (vendor==VendorFile.MZML) {
				MzmlFile mzml=new MzmlFile();
				mzml.openFile(path.toFile());
				reader=mzml;
			} else {
				throw new IllegalArgumentException("Unsupported vendor: "+vendor);
			}
			openNanos=System.nanoTime()-start;
		}

		private void metadataDate() throws Exception {
			long start=System.nanoTime();
			if (reader instanceof StructuredMetadataProvider) {
				((StructuredMetadataProvider)reader).getRunStartTime();
			}
			metadataDateNanos=System.nanoTime()-start;
		}

		private void ranges() throws Exception {
			long start=System.nanoTime();
			Map<Range, WindowData> ignored=reader.getRanges();
			if (ignored==null) throw new IllegalStateException("ranges were null");
			rangesNanos=System.nanoTime()-start;
		}

		private void ticTrace() throws Exception {
			long start=System.nanoTime();
			reader.getTICTrace();
			ticTraceNanos=System.nanoTime()-start;
		}

		private void runSummary() throws Exception {
			long start=System.nanoTime();
			if (reader instanceof ThermoRawFile) {
				((ThermoRawFile)reader).getRunSummary();
			} else {
				reader.getGradientLength();
				reader.getTIC();
			}
			runSummaryNanos=System.nanoTime()-start;
		}

		private void close() {
			long start=System.nanoTime();
			try {
				if (reader!=null) reader.close();
			} catch (Throwable t) {
				if ("ok".equals(status)||"not-run".equals(status)) {
					status="close:"+t.getClass().getSimpleName()+":"+String.valueOf(t.getMessage()).replace('\t', ' ').replace('\n', ' ');
				}
			}
			closeNanos=System.nanoTime()-start;
		}
	}
}
