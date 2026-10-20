package org.searlelab.msrawjava.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.io.DataOutputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.benchmarks.ReplayMemory;
import org.searlelab.msrawjava.io.tims.PeakPickerReplay;

class FragmentTrimReplayTest {
	private static FragmentScan scan(boolean ims) throws Exception {
		List<String> lines=Files.readAllLines(Path.of("src/test/resources/spectra/one_tims_ms1.csv"));
		double[] masses=new double[lines.size()-1];
		float[] intensities=new float[masses.length], mobilities=ims?new float[masses.length]:null;
		for (int i=1; i<lines.size(); i++) {
			String[] fields=lines.get(i).split(",");
			masses[i-1]=Double.parseDouble(fields[0]); intensities[i-1]=Float.parseFloat(fields[2]);
			if (ims) mobilities[i-1]=Float.parseFloat(fields[1]);
		}
		return new FragmentScan("csv-order", null, 17, 501.5, 12.5f, 2, null, 490, 501.5, 510, masses, intensities, mobilities, (byte)2, 100, 1500);
	}

	static String digest() throws Exception {
		MessageDigest digest=MessageDigest.getInstance("SHA-256");
		try (DataOutputStream out=new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
			for (boolean ims : new boolean[] {false, true}) {
				FragmentScan source=scan(ims);
				for (Range range : List.of(new Range(0, Float.MAX_VALUE), new Range(450, 550), new Range(0, 1), new Range(Float.NaN, Float.NaN), new Range(500, 500))) {
					PeakPickerReplay.writeScan(out, source.trimMasses(range));
				}
			}
		}
		StringBuilder hex=new StringBuilder();
		for (byte b : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", b&255));
		return hex.toString();
	}

	@Test
	void completeTrimmedSpectraMatchFrozenBaseline() throws Exception {
		assertEquals(Files.readString(Path.of("src/test/resources/spectra/fragment-trim-golden.sha256")).trim(), digest());
	}

	@Test
	void returnedArraysAreIndependentEvenWhenEveryPointMatches() throws Exception {
		for (boolean ims : new boolean[] {false, true}) {
			FragmentScan source=scan(ims), result=source.trimMasses(new Range(0, Float.MAX_VALUE));
			assertNotSame(source, result);
			assertNotSame(source.getMassArray(), result.getMassArray());
			assertNotSame(source.getIntensityArray(), result.getIntensityArray());
			assertEquals(source.getIonMobilityArray().isPresent(), result.getIonMobilityArray().isPresent());
			if (ims) assertNotSame(source.getIonMobilityArray().get(), result.getIonMobilityArray().get());
		}
	}

	@Test
	void boundariesUnsortedDuplicatesAndNonfiniteValuesKeepTheirRawBits() {
		double[] masses={Double.NaN, 500, Math.nextUp(500.0), -0.0, Double.NEGATIVE_INFINITY, 500, Math.nextDown(500.0), 0.0, Double.POSITIVE_INFINITY};
		float[] intensities={1, -0.0f, 3, 4, 5, Float.intBitsToFloat(0x7fc00017), 7, 8, 9};
		for (boolean ims : new boolean[] {false, true}) {
			FragmentScan source=new FragmentScan("boundaries", null, 1, 500, 1, 0, null, 490, 510, masses, intensities, ims?intensities.clone():null, (byte)0, 0, 1000);
			for (Range range : List.of(new Range(500, 500), new Range(-0.0, 0.0), new Range(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY), new Range(Double.NaN, Double.NaN))) {
				FragmentScan result=source.trimMasses(range);
				int next=0;
				for (int i=0; i<masses.length; i++) {
					if (!range.contains(masses[i])) continue;
					assertEquals(Double.doubleToRawLongBits(masses[i]), Double.doubleToRawLongBits(result.getMassArray()[next]));
					assertEquals(Float.floatToRawIntBits(intensities[i]), Float.floatToRawIntBits(result.getIntensityArray()[next]));
					if (ims) assertEquals(Float.floatToRawIntBits(intensities[i]), Float.floatToRawIntBits(result.getIonMobilityArray().get()[next]));
					next++;
				}
				assertEquals(next, result.getMassArray().length);
			}
		}
	}

	@Test
	void customRangeIsEvaluatedOncePerMassInOriginalOrder() {
		ArrayList<Double> calls=new ArrayList<>();
		Range custom=new Range(0, 1000) {
			@Override public boolean contains(double mass) { calls.add(mass); return calls.size()%2==1; }
		};
		FragmentScan source=new FragmentScan("custom", null, 1, 500, 1, 0, null, 490, 510, new double[] {300, 100, 200}, new float[] {3, 1, 2}, null, (byte)0, 0, 1000);
		assertArrayEquals(new double[] {300, 200}, source.trimMasses(custom).getMassArray());
		assertEquals(List.of(300.0, 100.0, 200.0), calls);
	}

	public static void main(String[] args) throws Exception {
		System.err.println("FRAGMENT_ORIGIN="+FragmentScan.class.getProtectionDomain().getCodeSource().getLocation());
		if (args[0].equals("golden")) { System.out.println(digest()); return; }
		FragmentScan source=scan(Boolean.parseBoolean(args[0]));
		Range range=new Range(Double.parseDouble(args[1]), Double.parseDouble(args[2]));
		int warmup=Integer.parseInt(args[3]), repeats=Integer.parseInt(args[4]);
		com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
		long tid=Thread.currentThread().getId(), nanos=0, cpu=0, bytes=0, checksum=0;
		ReplayMemory memory=new ReplayMemory();
		for (int i=-warmup; i<repeats; i++) {
			if (i==0) System.err.println("MEASURE_START_MS="+System.currentTimeMillis());
			long a=bean.getThreadAllocatedBytes(tid), c=bean.getCurrentThreadCpuTime(), t=System.nanoTime();
			FragmentScan result=source.trimMasses(range);
			checksum+=result.getMassArray().length;
			if (i>=0) { nanos+=System.nanoTime()-t; cpu+=bean.getCurrentThreadCpuTime()-c; bytes+=bean.getThreadAllocatedBytes(tid)-a; }
			memory.sample();
		}
		System.err.println("MEASURE_END_MS="+System.currentTimeMillis());
		System.out.printf(Locale.ROOT, "{\"fixture\":\"trim-%s-%s-%s\",\"ns\":%d,\"cpu_ns\":%d,\"bytes\":%d,\"checksum\":%d%s}%n",args[0],args[1],args[2],nanos,cpu,bytes,checksum,memory.jsonFields());
	}
}
