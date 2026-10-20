package org.searlelab.msrawjava.io.tims;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PeakInterface;
import org.searlelab.msrawjava.model.PeakWithIMS;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;

/** Helpers support regular regression tests; main is a manual replay/capture harness without timing assertions. */
public class PeakPickerReplay {
	static final Path RESOURCES=Path.of("src/test/resources/spectra");

	static Map<String, PeakPickerFixture> fixtures() throws Exception {
		Map<String, PeakPickerFixture> fixtures=new LinkedHashMap<>();
		ArrayList<PeakWithIMS> csv=new ArrayList<>();
		List<String> lines=Files.readAllLines(RESOURCES.resolve("one_tims_ms1.csv"));
		for (String line : lines.subList(1, lines.size())) {
			String[] fields=line.split(",");
			float intensity=Float.parseFloat(fields[2]);
			if (intensity<3.0f) continue; // same selection as the existing TIMSPeakpickerTest
			csv.add(new PeakWithIMS(Double.parseDouble(fields[0]), intensity, Float.parseFloat(fields[1])));
		}
		fixtures.put("csv-ms1", new PeakPickerFixture(csv));
		fixtures.put("empty", new PeakPickerFixture(new ArrayList<>()));
		fixtures.put("singleton", new PeakPickerFixture(new ArrayList<>(List.of(new PeakWithIMS(500, 10, 1)))));
		Random random=new Random(731);
		for (int c=0; c<40; c++) {
			ArrayList<PeakWithIMS> points=new ArrayList<>();
			for (int i=0; i<100+c*13; i++) {
				double mz=400+(i%13)*10+random.nextInt(3)*0.0001;
				PeakWithIMS p=new PeakWithIMS(mz, random.nextInt(8)-1, 1+random.nextInt(7)*0.005f);
				if (i%17==0) p.turnOff();
				points.add(p);
				if (i%19==0) points.add(p); // repeated identity, not merely equal values
				if (i%23==0) points.add(new PeakWithIMS(p.mz, p.intensity, p.ims));
			}
			Collections.shuffle(points, random);
			fixtures.put("ties-aliases-"+c, new PeakPickerFixture(points));
		}
		ArrayList<PeakWithIMS> boundaries=new ArrayList<>();
		for (double mz : new double[] {0.0, -0.0, 500, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
			double tol=TIMSPeakPicker.tolerance.getToleranceInMz(mz, mz)/2.0;
			for (double boundary : new double[] {mz-tol, mz+tol}) {
				for (double value : new double[] {Math.nextDown(boundary), boundary, Math.nextUp(boundary)}) {
					boundaries.add(new PeakWithIMS(value, 3, 1));
				}
			}
			boundaries.add(new PeakWithIMS(mz, 20, 1));
		}
		boundaries.add(new PeakWithIMS(500, Float.NaN, 1));
		boundaries.add(new PeakWithIMS(500, 10, Float.NaN));
		boundaries.add(new PeakWithIMS(500, Float.POSITIVE_INFINITY, 1));
		boundaries.add(new PeakWithIMS(500, -0.0f, 1));
		fixtures.put("boundaries-nonfinite", new PeakPickerFixture(boundaries));
		ArrayList<PeakWithIMS> shrinking=new ArrayList<>();
		for (int i=0; i<10; i++) shrinking.add(new PeakWithIMS(500, 100-i, 1+i*0.0005f));
		shrinking.add(new PeakWithIMS(700, 5, 1));
		shrinking.add(new PeakWithIMS(700, 4, 1.001f));
		fixtures.put("large-then-small-smoothing", new PeakPickerFixture(shrinking));
		ArrayList<PeakWithIMS> mobilityEdges=new ArrayList<>();
		float im=1.0f, delta=im*(TIMSPeakPicker.IM_TOL_PCT/100.0f);
		mobilityEdges.add(new PeakWithIMS(500, 20, im));
		for (float edge : new float[] {im-delta, im+delta}) {
			for (float value : new float[] {Math.nextDown(edge), edge, Math.nextUp(edge)}) mobilityEdges.add(new PeakWithIMS(500, 3, value));
		}
		fixtures.put("mobility-boundaries", new PeakPickerFixture(mobilityEdges));
		ArrayList<PeakWithIMS> capped=new ArrayList<>();
		for (int i=0; i<TIMSPeakPicker.MAX_PEAKS+13; i++) capped.add(new PeakWithIMS(100+i, 1+i%7, 1));
		fixtures.put("peak-cap", new PeakPickerFixture(capped));
		Path nativeFixture=RESOURCES.resolve("tims-dia-range.bin.gz");
		if (Files.exists(nativeFixture)) {
			int ordinal=0;
			for (FragmentScan scan : readScans(nativeFixture)) {
				ArrayList<PeakWithIMS> points=new ArrayList<>();
				for (PeakInterface p : scan.getPeaks(1.0f)) points.add((PeakWithIMS)p);
				fixtures.put("dia-"+(ordinal++)+"-"+scan.getSpectrumIndex()+"-"+scan.getSpectrumName(), new PeakPickerFixture(points));
			}
		}
		return fixtures;
	}

	static String digest(PeakPickerFixture fixture) throws Exception {
		return digest(fixture, false);
	}

	static String digest(PeakPickerFixture fixture, boolean callerSort) throws Exception {
		return digest(fixture, callerSort, false);
	}

	static String baselineDigest(PeakPickerFixture fixture) throws Exception {
		return digest(fixture, false, true);
	}

	private static String digest(PeakPickerFixture fixture, boolean callerSort, boolean baseline) throws Exception {
		ArrayList<PeakWithIMS> input=fixture.freshPoints();
		IdentityHashMap<PeakWithIMS, Integer> ids=new IdentityHashMap<>();
		for (PeakWithIMS p : input) if (!ids.containsKey(p)) ids.put(p, ids.size());
		MessageDigest digest=MessageDigest.getInstance("SHA-256");
		try (DataOutputStream out=new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
			if (callerSort) Collections.sort(input);
			ArrayList<PeakWithIMS> picked=baseline?PeakPickerBaseline.peakPickAcrossIMS(input):TIMSPeakPicker.peakPickAcrossIMS(input);
			out.writeInt(picked.size());
			for (PeakWithIMS p : picked) writePeak(out, p);
			out.writeInt(input.size());
			for (PeakWithIMS p : input) {
				out.writeInt(ids.get(p));
				writePeak(out, p);
				out.writeBoolean(p.isAvailable());
			}
		}
		return hex(digest.digest());
	}

	private static void writePeak(DataOutput out, PeakWithIMS p) throws IOException {
		out.writeLong(Double.doubleToRawLongBits(p.mz));
		out.writeInt(Float.floatToRawIntBits(p.intensity));
		out.writeInt(Float.floatToRawIntBits(p.ims));
	}

	static String hex(byte[] bytes) {
		StringBuilder out=new StringBuilder();
		for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b&255));
		return out.toString();
	}

	static String rebuiltDigest() throws Exception {
		MessageDigest digest=MessageDigest.getInstance("SHA-256");
		try (DataOutputStream out=new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
			for (FragmentScan scan : readScans(RESOURCES.resolve("tims-dia-range.bin.gz"))) {
				ArrayList<PeakWithIMS> points=new ArrayList<>();
				for (PeakInterface p : scan.getPeaks(1.0f)) points.add((PeakWithIMS)p);
				ArrayList<PeakWithIMS> picked=TIMSPeakPicker.peakPickAcrossIMS(new PeakPickerFixture(points).freshPoints());
				writeScan(out, scan.rebuild(scan.getSpectrumIndex(), picked));
				// Exercise the MS1 rebuilding path on identical complete mobility points as well.
				PrecursorScan ms1=new PrecursorScan(scan.getSpectrumName(), scan.getSpectrumIndex(), scan.getScanStartTime(), scan.getFraction(),
						scan.getScanWindowLower(), scan.getScanWindowUpper(), scan.getIonInjectionTime(), scan.getMassArray(), scan.getIntensityArray(),
						scan.getIonMobilityArray().orElse(null)).rebuild(scan.getSpectrumIndex(), picked);
				writePrecursor(out, ms1);
			}
		}
		return hex(digest.digest());
	}

	public static void writePrecursor(DataOutput out, PrecursorScan ms1) throws IOException {
		out.writeUTF(ms1.getSpectrumName()); out.writeInt(ms1.getSpectrumIndex());
		out.writeInt(Float.floatToRawIntBits(ms1.getScanStartTime())); out.writeInt(ms1.getFraction());
		out.writeInt(Float.floatToRawIntBits(ms1.getIonInjectionTime()));
		out.writeLong(Double.doubleToRawLongBits(ms1.getScanWindowLower())); out.writeLong(Double.doubleToRawLongBits(ms1.getScanWindowUpper()));
		out.writeInt(Float.floatToRawIntBits(ms1.getTIC()));
		out.writeInt(ms1.getMassArray().length); out.writeBoolean(ms1.getIonMobilityArray().isPresent());
		for (int i=0; i<ms1.getMassArray().length; i++) {
			out.writeLong(Double.doubleToRawLongBits(ms1.getMassArray()[i]));
			out.writeInt(Float.floatToRawIntBits(ms1.getIntensityArray()[i]));
			if (ms1.getIonMobilityArray().isPresent()) out.writeInt(Float.floatToRawIntBits(ms1.getIonMobilityArray().get()[i]));
		}
	}

	public static void writeScan(DataOutput out, FragmentScan scan) throws IOException {
		out.writeUTF(scan.getSpectrumName());
		out.writeBoolean(scan.getPrecursorName()!=null);
		if (scan.getPrecursorName()!=null) out.writeUTF(scan.getPrecursorName());
		out.writeInt(scan.getSpectrumIndex());
		out.writeLong(Double.doubleToRawLongBits(scan.getPrecursorMZ()));
		out.writeInt(Float.floatToRawIntBits(scan.getScanStartTime()));
		out.writeInt(scan.getFraction());
		out.writeInt(Float.floatToRawIntBits(scan.getIonInjectionTime()));
		out.writeLong(Double.doubleToRawLongBits(scan.getIsolationWindowLower()));
		out.writeLong(Double.doubleToRawLongBits(scan.getIsolationWindowTarget()));
		out.writeLong(Double.doubleToRawLongBits(scan.getIsolationWindowUpper()));
		out.writeByte(scan.getCharge());
		out.writeLong(Double.doubleToRawLongBits(scan.getScanWindowLower()));
		out.writeLong(Double.doubleToRawLongBits(scan.getScanWindowUpper()));
		out.writeInt(Float.floatToRawIntBits(scan.getTIC()));
		out.writeInt(scan.getMassArray().length);
		out.writeBoolean(scan.getIonMobilityArray().isPresent());
		for (int i=0; i<scan.getMassArray().length; i++) {
			out.writeLong(Double.doubleToRawLongBits(scan.getMassArray()[i]));
			out.writeInt(Float.floatToRawIntBits(scan.getIntensityArray()[i]));
			if (scan.getIonMobilityArray().isPresent()) out.writeInt(Float.floatToRawIntBits(scan.getIonMobilityArray().get()[i]));
		}
	}

	public static FragmentScan readScan(DataInput in) throws IOException {
		String name=in.readUTF(), parent=in.readBoolean()?in.readUTF():null;
		int index=in.readInt();
		double precursor=Double.longBitsToDouble(in.readLong());
		float rt=Float.intBitsToFloat(in.readInt());
		int fraction=in.readInt();
		float injection=Float.intBitsToFloat(in.readInt());
		double lo=Double.longBitsToDouble(in.readLong()), target=Double.longBitsToDouble(in.readLong()), hi=Double.longBitsToDouble(in.readLong());
		byte charge=in.readByte();
		double scanLo=Double.longBitsToDouble(in.readLong()), scanHi=Double.longBitsToDouble(in.readLong());
		int tic=in.readInt();
		int n=in.readInt();
		double[] mz=new double[n];
		float[] intensity=new float[n], ims=in.readBoolean()?new float[n]:null;
		for (int i=0; i<n; i++) {
			mz[i]=Double.longBitsToDouble(in.readLong());
			intensity[i]=Float.intBitsToFloat(in.readInt());
			if (ims!=null) ims[i]=Float.intBitsToFloat(in.readInt());
		}
		FragmentScan scan=new FragmentScan(name, parent, index, precursor, rt, fraction, injection, lo, target, hi, mz, intensity, ims, charge, scanLo, scanHi);
		if (tic!=Float.floatToRawIntBits(scan.getTIC())) throw new IOException("TIC changed during fixture round-trip: "+name);
		return scan;
	}

	static ArrayList<FragmentScan> readScans(Path path) throws IOException {
		try (DataInputStream in=new DataInputStream(new GZIPInputStream(Files.newInputStream(path)))) {
			if (in.readInt()!=0x54494d31) throw new IOException("Unknown replay format");
			ArrayList<FragmentScan> scans=new ArrayList<>();
			int count=in.readInt();
			for (int i=0; i<count; i++) scans.add(readScan(in));
			if (in.read()!=-1) throw new IOException("Trailing fixture data");
			return scans;
		}
	}

	private static void captureNative() throws Exception {
		Path source=Path.of("src/test/resources/rawdata/230711_idleflow_400-1000mz_25mz_diaPasef_10sec.d");
		try (BrukerTIMSFile reader=new BrukerTIMSFile()) {
			reader.openFile(source);
			ArrayList<FragmentScan> all=reader.getStripes(new Range(0, Float.MAX_VALUE), 0, Float.MAX_VALUE, false);
			int scanIndex=all.stream().filter(s -> s.getMassArray().length>0).findFirst().orElseThrow().getSpectrumIndex();
			ArrayList<FragmentScan> selected=new ArrayList<>();
			for (FragmentScan s : all) if (s.getSpectrumIndex()==scanIndex) selected.add(s);
			try (DataOutputStream out=new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(RESOURCES.resolve("tims-dia-range.bin.gz"))))) {
				out.writeInt(0x54494d31);
				out.writeInt(selected.size());
				for (FragmentScan scan : selected) writeScan(out, scan);
			}
			System.out.println("Captured complete first nonempty DIA frame/window-group scan index: "+scanIndex+", windows="+selected.size());
			System.out.println("Reader metadata: "+reader.getMetadata());
		}
	}

	public static void main(String[] args) throws Exception {
		System.err.println("PICKER_ORIGIN="+TIMSPeakPicker.class.getProtectionDomain().getCodeSource().getLocation());
		System.err.println("NATIVE_RESOURCE_ORIGIN="+TimsNative.class.getResource("/META-INF/lib/osx-aarch64/libtimsrust_jni.dylib"));
		if (args[0].equals("capture-native")) {
			captureNative();
			return;
		}
		if (args[0].equals("rebuilt-golden")) {
			System.out.println(rebuiltDigest());
			return;
		}
		Map<String, PeakPickerFixture> fixtures=fixtures();
		if (args[0].equals("golden")) {
			for (Map.Entry<String, PeakPickerFixture> fixture : fixtures.entrySet()) System.out.println(fixture.getKey()+"\t"+digest(fixture.getValue()));
			return;
		}
		List<PeakPickerFixture> selected=new ArrayList<>();
		for (Map.Entry<String, PeakPickerFixture> f : fixtures.entrySet()) {
			if (args[1].equals("csv")?f.getKey().equals("csv-ms1"):(args[1].equals("dia")?f.getKey().startsWith("dia-"):f.getKey().equals(args[1]))) selected.add(f.getValue());
		}
		if (selected.isEmpty()) throw new IllegalArgumentException("No replay fixtures: "+args[1]);
		boolean presort=args[0].equals("bench-presort");
		int warmup=Integer.parseInt(args[2]), repetitions=Integer.parseInt(args[3]);
		com.sun.management.ThreadMXBean thread=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
		if (!thread.isThreadAllocatedMemorySupported()||!thread.isCurrentThreadCpuTimeSupported()) throw new IllegalStateException("Replay requires allocation and CPU counters");
		thread.setThreadAllocatedMemoryEnabled(true);
		thread.setThreadCpuTimeEnabled(true);
		long tid=Thread.currentThread().getId();
		long pickerNanos=0, cloneNanos=0, pickerCpu=0, cloneCpu=0, pickerBytes=0, cloneBytes=0, checksum=0, usedMax=0, committedMax=0;
		long collectionsBefore=collections(), gcBefore=gcMillis();
		for (int i=-warmup; i<repetitions; i++) {
			if (i==0) System.err.println("MEASURE_START_MS="+System.currentTimeMillis());
			for (PeakPickerFixture f : selected) {
				long a=thread.getThreadAllocatedBytes(tid), c=thread.getCurrentThreadCpuTime(), t=System.nanoTime();
				ArrayList<PeakWithIMS> input=f.freshPoints();
				long cloneEnd=System.nanoTime(), cloneCpuEnd=thread.getCurrentThreadCpuTime(), cloneAllocEnd=thread.getThreadAllocatedBytes(tid);
				if (presort) Collections.sort(input);
				ArrayList<PeakWithIMS> output=TIMSPeakPicker.peakPickAcrossIMS(input);
				long end=System.nanoTime(), cpuEnd=thread.getCurrentThreadCpuTime(), allocEnd=thread.getThreadAllocatedBytes(tid);
				checksum+=output.size();
				if (!output.isEmpty()) checksum^=Double.doubleToRawLongBits(output.get(0).mz);
				if (i>=0) {
					cloneNanos+=cloneEnd-t; pickerNanos+=end-cloneEnd;
					cloneCpu+=cloneCpuEnd-c; pickerCpu+=cpuEnd-cloneCpuEnd;
					cloneBytes+=cloneAllocEnd-a; pickerBytes+=allocEnd-cloneAllocEnd;
				}
			}
			usedMax=Math.max(usedMax, ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
			committedMax=Math.max(committedMax, ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getCommitted());
		}
		System.err.println("MEASURE_END_MS="+System.currentTimeMillis());
		System.out.printf(Locale.ROOT, "{\"fixture\":\"%s\",\"repetitions\":%d,\"spectra_per_repetition\":%d,\"picker_ns\":%d,\"clone_ns\":%d,\"picker_cpu_ns\":%d,\"clone_cpu_ns\":%d,\"picker_bytes\":%d,\"clone_bytes\":%d,\"sampled_heap_used_max\":%d,\"sampled_heap_committed_max\":%d,\"collections\":%d,\"gc_ms\":%d,\"checksum\":%d}%n",
				args[1], repetitions, selected.size(), pickerNanos, cloneNanos, pickerCpu, cloneCpu, pickerBytes, cloneBytes, usedMax, committedMax, collections()-collectionsBefore, gcMillis()-gcBefore, checksum);
	}

	private static long collections() {
		return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b -> Math.max(0, b.getCollectionCount())).sum();
	}

	private static long gcMillis() {
		return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b -> Math.max(0, b.getCollectionTime())).sum();
	}
}
