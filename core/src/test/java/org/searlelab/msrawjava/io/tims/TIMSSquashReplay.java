package org.searlelab.msrawjava.io.tims;

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.searlelab.msrawjava.model.AcquiredSpectrum;
import org.searlelab.msrawjava.model.PeakWithIMS;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.ScanSummary;

/**
 * Explicit acquisition-dependent validation and manual benchmark; never run by Surefire.
 * Run from core with test-classes, classes and runtime dependencies on the classpath:
 * {@code TIMSSquashReplay validate <source.d> <new-output-directory>}, then five fresh JVMs with
 * {@code TIMSSquashReplay bench <output-directory> <fork-number-0-through-4> 20 30}.
 */
public class TIMSSquashReplay {
	private static final int MAGIC=0x53515331;
	private static volatile long checksum;

	private static final class Sample {
		final String id;
		final int level, count;
		final PeakPickerFixture fixture;

		Sample(String id, int level, ArrayList<PeakWithIMS> points) {
			this.id=id;
			this.level=level;
			this.count=points.size();
			this.fixture=new PeakPickerFixture(points);
		}

		Sample(DataInputStream in) throws Exception {
			id=in.readUTF(); level=in.readInt(); count=in.readInt(); fixture=new PeakPickerFixture(in);
		}

		void write(DataOutputStream out) throws Exception {
			out.writeUTF(id); out.writeInt(level); out.writeInt(count); fixture.write(out);
		}
	}

	private static String identity(int level, String name, double low, double high) {
		// DIA summaries use frame ID as index, whereas spectra use frame*100+windowGroup.
		// Both expose the exact frame/IMS-scan name and isolation bounds; reconcile those instead.
		return level+":"+name+":"+Double.toHexString(low)+":"+Double.toHexString(high);
	}

	private static String identity(ScanSummary s) {
		return identity(s.isPrecursor()?1:2, s.getSpectrumName(), s.getIsolationWindowLower(), s.getIsolationWindowUpper());
	}

	private static String identity(int level, AcquiredSpectrum s) {
		return identity(level, s.getSpectrumName(), s.getIsolationWindowLower(), s.getIsolationWindowUpper());
	}

	private static String hash(Path path) throws Exception {
		MessageDigest digest=MessageDigest.getInstance("SHA-256");
		try (InputStream in=Files.newInputStream(path)) {
			byte[] buffer=new byte[1024*1024];
			for (int n; (n=in.read(buffer))!=-1;) digest.update(buffer, 0, n);
		}
		return PeakPickerReplay.hex(digest.digest());
	}

	private static String quote(String s) {
		return "\""+s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")+"\"";
	}

	private static void validate(Path source, Path directory) throws Exception {
		Files.createDirectory(directory); // Never overwrite an earlier evidence directory.
		Map<String, String> fingerprints=new LinkedHashMap<>();
		for (String name : List.of("analysis.tdf", "analysis.tdf_bin")) fingerprints.put(name, hash(source.resolve(name)));
		long[] spectra=new long[3], points=new long[3], outputs=new long[3], caps=new long[3], empty=new long[3], visits=new long[3];
		Map<String, Sample> representative=new LinkedHashMap<>();
		List<PriorityQueue<Sample>> dense=new ArrayList<>();
		Comparator<Sample> density=Comparator.comparingInt((Sample s) -> s.count).thenComparing(s -> s.id);
		for (int i=0; i<3; i++) dense.add(new PriorityQueue<>(density));
		TreeSet<String> expected=new TreeSet<>(), seen=new TreeSet<>();
		long started=System.nanoTime();
		try (BrukerTIMSFile reader=new BrukerTIMSFile(); BufferedWriter rows=Files.newBufferedWriter(directory.resolve("spectra.tsv"))) {
			reader.openFile(source);
			ArrayList<ScanSummary> summaries=reader.getScanSummaries(0, Float.MAX_VALUE);
			if (summaries.isEmpty()) throw new AssertionError("No scan summaries");
			float first=Float.MAX_VALUE, last=0;
			for (ScanSummary s : summaries) {
				if (!expected.add(identity(s))) throw new AssertionError("Duplicate summary "+identity(s));
				first=Math.min(first, s.getScanStartTime()); last=Math.max(last, s.getScanStartTime());
			}
			double[] windows=new double[5];
			for (int i=0; i<5; i++) windows[i]=first+(last-first)*(0.1+0.2*i);
			rows.write("identity\tspectrum_index\trt_seconds\tms_level\tinput_points\toutput_points\tcap_hit\tscan_visits\n");
			// SQL BETWEEN is inclusive. Query adjacent boundaries and keep only [start,stop) using
			// the same float RT exposed by summaries. The overlap also avoids float-cast gaps.
			for (float start=0; start<=last; start+=5f) {
				float stop=start+5f;
				List<ArrayList<? extends AcquiredSpectrum>> batches=List.of(
						reader.getPrecursors(Math.nextDown(start), Math.nextUp(stop)),
						reader.getStripes(new Range(0, Float.MAX_VALUE), Math.nextDown(start), Math.nextUp(stop), false));
				for (int level=1; level<=2; level++) {
					for (AcquiredSpectrum scan : batches.get(level-1)) {
						float rt=scan.getScanStartTime();
						if (rt<start||rt>=stop) continue;
						String id=identity(level, scan);
						if (!expected.contains(id)||!seen.add(id)) throw new AssertionError("Unexpected/duplicate spectrum "+id);
						ArrayList<PeakWithIMS> input=new ArrayList<>();
						float threshold=level==1?3f:1f;
						double[] masses=scan.getMassArray();
						float[] intensities=scan.getIntensityArray(), ims=scan.getIonMobilityArray().orElseThrow();
						for (int i=0; i<masses.length; i++) {
							// Matches PrecursorScan/FragmentScan.getPeaks: strictly above the conversion threshold.
							if (intensities[i]>threshold) input.add(new PeakWithIMS(masses[i], intensities[i], ims[i]));
						}
						Sample sample=new Sample(id, level, input);
						ArrayList<PeakWithIMS> reference=sample.fixture.freshPoints(), actual=sample.fixture.freshPoints();
						long[] scanned={0};
						ArrayList<PeakWithIMS> result;
						try {
							ArrayList<PeakWithIMS> oracle=TIMSSquashReference.squash(reference, scanned);
							result=TIMSPeakPicker.squashAcrossIMS(actual);
							TIMSSquashReference.assertSamePoints(oracle, result, id+" output");
							TIMSSquashReference.assertSamePoints(reference, actual, id+" input");
						} catch (AssertionError failure) {
							try (DataOutputStream out=new DataOutputStream(Files.newOutputStream(directory.resolve("failure.bin")))) { sample.write(out); }
							Files.writeString(directory.resolve("failure.txt"), id+"\n"+failure);
							throw failure;
						}
						spectra[level]++; points[level]+=input.size(); outputs[level]+=result.size(); visits[level]+=scanned[0];
						boolean cap=result.size()==TIMSPeakPicker.MAX_PEAKS;
						if (cap) caps[level]++;
						if (input.isEmpty()) empty[level]++;
						rows.write(id+"\t"+scan.getSpectrumIndex()+"\t"+rt+"\t"+level+"\t"+input.size()+"\t"+result.size()+"\t"+cap+"\t"+scanned[0]+"\n");
						for (double window : windows) if (rt>=window&&rt<window+2) representative.put(id, sample);
						PriorityQueue<Sample> top=dense.get(level);
						top.add(sample); if (top.size()>5) top.remove();
					}
				}
				rows.flush();
				System.out.println("Validated through "+stop+" s: MS1="+spectra[1]+", MS2="+spectra[2]);
			}
			TreeSet<String> missing=new TreeSet<>(expected); missing.removeAll(seen);
			if (!missing.isEmpty()) throw new AssertionError("Missing spectra: "+missing);
		} catch (Exception|AssertionError failure) {
			Files.writeString(directory.resolve("status.json"), "{\"passed\":false,\"error\":"+quote(failure.toString())+"}\n");
			throw failure;
		}
		for (var fingerprint : fingerprints.entrySet()) {
			if (!fingerprint.getValue().equals(hash(source.resolve(fingerprint.getKey())))) throw new AssertionError("Source changed: "+fingerprint.getKey());
		}
		try (DataOutputStream out=new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(directory.resolve("workloads.bin.gz"))))) {
			out.writeInt(MAGIC);
			out.writeInt(representative.size()+dense.get(1).size()+dense.get(2).size());
			for (Sample s : representative.values()) { out.writeUTF("representative-ms"+s.level); s.write(out); }
			for (int level=1; level<=2; level++) {
				ArrayList<Sample> sorted=new ArrayList<>(dense.get(level)); sorted.sort(density.reversed());
				for (Sample s : sorted) { out.writeUTF("dense-ms"+level); s.write(out); }
			}
		}
		StringBuilder json=new StringBuilder("{\"passed\":true,\"mismatches\":0,\"source\":").append(quote(source.toString()));
		json.append(",\"expected_spectra\":").append(expected.size()).append(",\"processed_spectra\":").append(seen.size());
		json.append(",\"elapsed_ns\":").append(System.nanoTime()-started).append(",\"fingerprints\":{");
		boolean comma=false;
		for (var f : fingerprints.entrySet()) {
			if (comma) json.append(','); comma=true;
			json.append(quote(f.getKey())).append(":{\"sha256\":").append(quote(f.getValue()))
					.append(",\"bytes\":").append(Files.size(source.resolve(f.getKey()))).append('}');
		}
		json.append("},\"levels\":[");
		for (int level=1; level<=2; level++) {
			if (level>1) json.append(',');
			json.append("{\"ms_level\":").append(level).append(",\"spectra\":").append(spectra[level])
					.append(",\"input_points\":").append(points[level]).append(",\"output_points\":").append(outputs[level])
					.append(",\"cap_hits\":").append(caps[level]).append(",\"empty_inputs\":").append(empty[level])
					.append(",\"bounded_scan_visits\":").append(visits[level]).append('}');
		}
		Files.writeString(directory.resolve("status.json"), json.append("]}\n").toString());
	}

	private static void bench(Path directory, int fork, int warmup, int repetitions) throws Exception {
		Map<String, ArrayList<Sample>> groups=new LinkedHashMap<>();
		try (DataInputStream in=new DataInputStream(new GZIPInputStream(Files.newInputStream(directory.resolve("workloads.bin.gz"))))) {
			if (in.readInt()!=MAGIC) throw new IllegalArgumentException("Unknown corpus format");
			int count=in.readInt();
			for (int i=0; i<count; i++) groups.computeIfAbsent(in.readUTF(), key -> new ArrayList<>()).add(new Sample(in));
			if (in.read()!=-1) throw new IllegalArgumentException("Trailing corpus data");
		}
		com.sun.management.ThreadMXBean thread=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
		if (!thread.isThreadAllocatedMemorySupported()||!thread.isCurrentThreadCpuTimeSupported()) throw new IllegalStateException("No allocation/CPU counters");
		thread.setThreadAllocatedMemoryEnabled(true);
		thread.setThreadCpuTimeEnabled(true);
		long tid=Thread.currentThread().getId();
		List<String> methods=List.of("sort-only", "optimized", "reference", "existing");
		try (BufferedWriter out=Files.newBufferedWriter(directory.resolve("bench-"+fork+".tsv"))) {
			out.write("group\tmethod\tspectra_per_rep\tinput_points_per_rep\toutput_points_per_rep\tmedian_ns_per_rep\tmedian_cpu_ns_per_rep\tmedian_bytes_per_rep\tvisits_per_input\twarmup\trepetitions\n");
			for (var group : groups.entrySet()) {
				long inputCount=group.getValue().stream().mapToLong(s -> s.count).sum();
				long[] scanned={0};
				for (Sample s : group.getValue()) TIMSSquashReference.squash(s.fixture.freshPoints(), scanned);
				for (int m=0; m<methods.size(); m++) {
					String method=methods.get((m+fork)%methods.size());
					long[] times=new long[repetitions], cpuTimes=new long[repetitions], allocations=new long[repetitions];
					long outputCount=0;
					for (int r=-warmup; r<repetitions; r++) {
						long nanos=0, cpu=0, bytes=0, count=0;
						for (Sample s : group.getValue()) {
							ArrayList<PeakWithIMS> input=s.fixture.freshPoints();
							long beforeBytes=thread.getThreadAllocatedBytes(tid), beforeCpu=thread.getCurrentThreadCpuTime(), before=System.nanoTime();
							ArrayList<PeakWithIMS> result;
							if (method.equals("sort-only")) { input.sort(null); result=input; }
							else if (method.equals("optimized")) result=TIMSPeakPicker.squashAcrossIMS(input);
							else if (method.equals("reference")) result=TIMSSquashReference.squash(input);
							else result=TIMSPeakPicker.peakPickAcrossIMS(input);
							nanos+=System.nanoTime()-before; cpu+=thread.getCurrentThreadCpuTime()-beforeCpu; bytes+=thread.getThreadAllocatedBytes(tid)-beforeBytes;
							count+=result.size();
							checksum^=result.size();
							if (!result.isEmpty()) checksum^=Double.doubleToLongBits(result.get(0).mz);
						}
						if (r>=0) { times[r]=nanos; cpuTimes[r]=cpu; allocations[r]=bytes; outputCount=count; }
					}
					Arrays.sort(times); Arrays.sort(cpuTimes); Arrays.sort(allocations);
					out.write(String.format(Locale.ROOT, "%s\t%s\t%d\t%d\t%d\t%d\t%d\t%d\t%.8f\t%d\t%d%n", group.getKey(), method,
							group.getValue().size(), inputCount, outputCount, times[repetitions/2], cpuTimes[repetitions/2], allocations[repetitions/2],
							inputCount==0?0:(double)scanned[0]/inputCount, warmup, repetitions));
					out.flush();
					System.out.println("Fork "+fork+" completed "+group.getKey()+" "+method);
				}
			}
		}
		System.out.println("checksum="+checksum);
	}

	public static void main(String[] args) throws Exception {
		if (args.length==3&&args[0].equals("validate")) validate(Path.of(args[1]), Path.of(args[2]));
		else if (args.length==5&&args[0].equals("bench")) bench(Path.of(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]));
		else throw new IllegalArgumentException("validate <source.d> <new-output-directory> | bench <output-directory> <fork> <warmup> <repetitions>");
	}
}
