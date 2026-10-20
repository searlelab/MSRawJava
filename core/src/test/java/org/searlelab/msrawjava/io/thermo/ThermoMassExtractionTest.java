package org.searlelab.msrawjava.io.thermo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.benchmarks.ReplayMemory;
import org.searlelab.msrawjava.io.thermo.rpc.Spectrum;
import org.searlelab.msrawjava.io.thermo.rpc.ThermoRawServiceGrpc;
import org.searlelab.msrawjava.model.AcquiredSpectrum;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;

class ThermoMassExtractionTest {
	@Test
	void conversionPreservesOrderedRawBitsAndReturnsIndependentArrays() throws Exception {
		Spectrum.Builder builder=Spectrum.newBuilder().setScanNumber(17).setRtSeconds(12.5).setIonInjectionTimeS(0.25)
				.setIsoLower(400).setIsoTarget(407).setIsoUpper(416).setScanWindowLower(100).setScanWindowUpper(1500).setCharge(2).setPrecursorName("parent");
		for (double mz : new double[] {0.0, -0.0, Double.longBitsToDouble(0x7ff8000000000017L), Double.POSITIVE_INFINITY,
				Double.NEGATIVE_INFINITY, Math.nextUp(500.0), 500.0, 500.0}) builder.addMz(mz).addIntensity(9);
		Spectrum spectrum=builder.build();
		ThermoRawSpectrumReader reader=reader(spectrum);
		for (boolean sqrt : new boolean[] {false, true}) {
			AcquiredSpectrum first=reader.getStripes(new Range(400, 416), 0, 30, sqrt).get(0);
			AcquiredSpectrum second=reader.getStripes(new Range(400, 416), 0, 30, sqrt).get(0);
			assertFieldsAndBits(spectrum, first, sqrt);
			assertNotSame(first.getMassArray(), second.getMassArray());
			first.getMassArray()[0]=900;
			assertEquals(0L, Double.doubleToRawLongBits(second.getMassArray()[0]));
		}
		assertFieldsAndBits(spectrum, reader.getPrecursors(0, 30).get(0), false);
		assertEquals(0, reader(Spectrum.getDefaultInstance()).getPrecursors(0, 30).get(0).getMassArray().length);
		assertEquals(0, reader(Spectrum.getDefaultInstance()).getStripes(new Range(0, 1), 0, 30, false).get(0).getMassArray().length);
	}

	private static void assertFieldsAndBits(Spectrum source, AcquiredSpectrum scan, boolean sqrt) {
		assertEquals(source.getMzCount(), scan.getMassArray().length);
		assertEquals("scan="+source.getScanNumber(), scan.getSpectrumName());
		assertEquals(source.getScanNumber(), scan.getSpectrumIndex());
		assertEquals(Float.floatToRawIntBits((float)source.getRtSeconds()), Float.floatToRawIntBits(scan.getScanStartTime()));
		assertEquals(Float.floatToRawIntBits((float)source.getIonInjectionTimeS()), Float.floatToRawIntBits(scan.getIonInjectionTime()));
		assertEquals(scan instanceof PrecursorScan?source.getIsoLower():source.getScanWindowLower(), scan.getScanWindowLower());
		assertEquals(scan instanceof PrecursorScan?source.getIsoUpper():source.getScanWindowUpper(), scan.getScanWindowUpper());
		for (int i=0; i<source.getMzCount(); i++) {
			assertEquals(Double.doubleToRawLongBits(source.getMz(i)), Double.doubleToRawLongBits(scan.getMassArray()[i]), "mass "+i);
			assertEquals(Float.floatToRawIntBits(sqrt?3.0f:9.0f), Float.floatToRawIntBits(scan.getIntensityArray()[i]));
		}
	}

	static ThermoRawSpectrumReader reader(Spectrum spectrum) throws Exception {
		ThermoRawFile owner=new ThermoRawFile();
		Channel channel=new Channel() {
			@Override
			public String authority() { return "in-memory-replay"; }

			@Override
			@SuppressWarnings("unchecked")
			public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(MethodDescriptor<ReqT, RespT> method, CallOptions options) {
				if (!(method.getFullMethodName().endsWith("/GetPrecursors")||method.getFullMethodName().endsWith("/GetStripes"))) throw new AssertionError(method.getFullMethodName());
				return new ClientCall<ReqT, RespT>() {
					Listener<RespT> listener;
					boolean sent;
					@Override public void start(Listener<RespT> listener, Metadata headers) { this.listener=listener; }
					@Override public void request(int n) {
						if (!sent) {
							sent=true;
							listener.onMessage((RespT)spectrum);
							listener.onClose(Status.OK, new Metadata());
						}
					}
					@Override public void cancel(String message, Throwable cause) { }
					@Override public void halfClose() { }
					@Override public void sendMessage(ReqT message) { }
				};
			}
		};
		Field stub=ThermoRawFile.class.getDeclaredField("stub");
		stub.setAccessible(true); stub.set(owner, ThermoRawServiceGrpc.newBlockingStub(channel));
		Field session=ThermoRawFile.class.getDeclaredField("sessionId");
		session.setAccessible(true); session.set(owner, "replay");
		return new ThermoRawSpectrumReader(owner);
	}

	public static void main(String[] args) throws Exception {
		System.err.println("THERMO_READER_ORIGIN="+ThermoRawSpectrumReader.class.getProtectionDomain().getCodeSource().getLocation());
		int n=Integer.parseInt(args[0]);
		Spectrum.Builder builder=Spectrum.newBuilder().setScanNumber(1).setIsoLower(400).setIsoUpper(416);
		for (int i=0; i<n; i++) builder.addMz(100+i*0.01).addIntensity(i%100);
		ThermoRawSpectrumReader reader=reader(builder.build());
		com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
		long tid=Thread.currentThread().getId(), nanos=0, cpu=0, bytes=0, checksum=0;
		int warmup=Integer.parseInt(args[1]), repeats=Integer.parseInt(args[2]);
		ReplayMemory memory=new ReplayMemory();
		for (int i=-warmup; i<repeats; i++) {
			if (i==0) System.err.println("MEASURE_START_MS="+System.currentTimeMillis());
			long a=bean.getThreadAllocatedBytes(tid), c=bean.getCurrentThreadCpuTime(), t=System.nanoTime();
			List<? extends AcquiredSpectrum> result=reader.getPrecursors(0, 30);
			checksum+=result.get(0).getMassArray().length;
			if (i>=0) { nanos+=System.nanoTime()-t; cpu+=bean.getCurrentThreadCpuTime()-c; bytes+=bean.getThreadAllocatedBytes(tid)-a; }
			memory.sample();
		}
		System.err.println("MEASURE_END_MS="+System.currentTimeMillis());
		System.out.printf(Locale.ROOT,"{\"fixture\":\"protobuf-%d\",\"ns\":%d,\"cpu_ns\":%d,\"bytes\":%d,\"checksum\":%d%s}%n",n,nanos,cpu,bytes,checksum,memory.jsonFields());
	}
}
