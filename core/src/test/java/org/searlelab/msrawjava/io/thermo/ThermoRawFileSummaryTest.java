package org.searlelab.msrawjava.io.thermo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.io.thermo.rpc.Session;
import org.searlelab.msrawjava.io.thermo.rpc.Spectrum;
import org.searlelab.msrawjava.io.thermo.rpc.SpectrumSummary;
import org.searlelab.msrawjava.io.thermo.rpc.SummariesReply;
import org.searlelab.msrawjava.io.thermo.rpc.ThermoRawServiceGrpc;
import org.searlelab.msrawjava.model.AcquiredSpectrum;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.ScanSummary;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;

class ThermoRawFileSummaryTest {

	@Test
	void getScanSummaries_mapsAndSorts() throws Exception {
		SummariesReply reply=SummariesReply.newBuilder().addSummaries(summary(2, 5.0, 2, 400.0, 500.0, 2, "ms2"))
				.addSummaries(summary(1, 1.0, 1, 300.0, 400.0, 0, "ms1")).build();

		ThermoRawFile file=buildFile(reply, List.of(), List.of());
		ArrayList<ScanSummary> summaries=file.getScanSummaries(0.0f, 100.0f);
		assertEquals(2, summaries.size());
		assertEquals(1.0f, summaries.get(0).getScanStartTime(), 1e-6f);
		assertEquals(5.0f, summaries.get(1).getScanStartTime(), 1e-6f);

		ScanSummary ms1=summaries.get(0);
		assertEquals(1, ms1.getSpectrumIndex());
		assertEquals(-1.0, ms1.getPrecursorMz(), 1e-6);
		assertEquals(300.0, ms1.getIsolationWindowLower(), 1e-6);
		assertEquals(400.0, ms1.getIsolationWindowUpper(), 1e-6);

		ScanSummary ms2=summaries.get(1);
		assertEquals(2, ms2.getSpectrumIndex());
		assertEquals(450.0, ms2.getPrecursorMz(), 1e-6);
		assertEquals(400.0, ms2.getIsolationWindowLower(), 1e-6);
		assertEquals(500.0, ms2.getIsolationWindowUpper(), 1e-6);
		assertEquals(2, ms2.getCharge());
	}

	@Test
	void getSpectrum_returnsMatchingPrecursorAndFragment() throws Exception {
		List<Spectrum> precursors=new ArrayList<>();
		precursors.add(spectrum("p1", 10, 1.0, 0.0, 0.0, 0, 100.0, 900.0));
		precursors.add(spectrum("p2", 11, 2.0, 0.0, 0.0, 0, 100.0, 900.0));

		List<Spectrum> stripes=new ArrayList<>();
		stripes.add(spectrum("f1", 20, 3.0, 400.0, 500.0, 2, 400.0, 500.0));
		stripes.add(spectrum("f2", 21, 4.0, 400.0, 500.0, 2, 400.0, 500.0));

		ThermoRawFile file=buildFile(SummariesReply.newBuilder().build(), precursors, stripes);

		ScanSummary precursorSummary=new ScanSummary("p2", 11, 2.0f, 0, -1.0, true, null, 0.0, 0.0, 100.0, 900.0, (byte)0);
		AcquiredSpectrum precursor=file.getSpectrum(precursorSummary);
		assertNotNull(precursor);
		assertEquals(11, precursor.getSpectrumIndex());
		assertEquals(PrecursorScan.class, precursor.getClass());

		ScanSummary fragmentSummary=new ScanSummary("f2", 21, 4.0f, 0, 450.0, false, null, 400.0, 500.0, 400.0, 500.0, (byte)2);
		AcquiredSpectrum fragment=file.getSpectrum(fragmentSummary);
		assertNotNull(fragment);
		assertEquals(21, fragment.getSpectrumIndex());
		assertEquals(FragmentScan.class, fragment.getClass());
	}

	@Test
	void getSpectrum_returnsNullForNullSummary() throws Exception {
		ThermoRawFile file=buildFile(SummariesReply.newBuilder().build(), List.of(), List.of());
		assertEquals(null, file.getSpectrum(null));
	}

	@Test
	void getSpectrum_returnsFirstPrecursorWhenNoMatch() throws Exception {
		List<Spectrum> precursors=new ArrayList<>();
		precursors.add(spectrum("p1", 10, 1.0, 0.0, 0.0, 0, 100.0, 900.0));
		precursors.add(spectrum("p2", 11, 2.0, 0.0, 0.0, 0, 100.0, 900.0));

		ThermoRawFile file=buildFile(SummariesReply.newBuilder().build(), precursors, List.of());
		ScanSummary summary=new ScanSummary("pX", 99, 2.0f, 0, -1.0, true, null, 0.0, 0.0, 100.0, 900.0, (byte)0);
		AcquiredSpectrum spectrum=file.getSpectrum(summary);
		assertNotNull(spectrum);
		assertEquals(10, spectrum.getSpectrumIndex());
	}

	@Test
	void getSpectrum_returnsFirstFragmentWhenNoMatch() throws Exception {
		List<Spectrum> stripes=new ArrayList<>();
		stripes.add(spectrum("f1", 20, 3.0, 400.0, 500.0, 2, 400.0, 500.0));
		stripes.add(spectrum("f2", 21, 4.0, 400.0, 500.0, 2, 400.0, 500.0));

		ThermoRawFile file=buildFile(SummariesReply.newBuilder().build(), List.of(), stripes);
		ScanSummary summary=new ScanSummary("fX", 99, 4.0f, 0, 450.0, false, null, 400.0, 500.0, 400.0, 500.0, (byte)2);
		AcquiredSpectrum spectrum=file.getSpectrum(summary);
		assertNotNull(spectrum);
		assertEquals(20, spectrum.getSpectrumIndex());
	}

	@Test
	void getSpectrum_returnsNullWhenNoScansAvailable() throws Exception {
		ThermoRawFile file=buildFile(SummariesReply.newBuilder().build(), List.of(), List.of());
		ScanSummary summary=new ScanSummary("pX", 99, 2.0f, 0, -1.0, true, null, 0.0, 0.0, 100.0, 900.0, (byte)0);
		assertEquals(null, file.getSpectrum(summary));
	}

	private static SpectrumSummary summary(int scanNumber, double rtSeconds, int msLevel, double isoLo, double isoHi, int charge, String name) {
		return SpectrumSummary.newBuilder().setScanNumber(scanNumber).setRtSeconds(rtSeconds).setMsLevel(msLevel).setIsoLower(isoLo).setIsoUpper(isoHi)
				.setCharge(charge).setSpectrumName(name).setIonInjectionTimeS(0.01).setScanWindowLower(isoLo).setScanWindowUpper(isoHi).build();
	}

	private static Spectrum spectrum(String name, int scanNumber, double rtSeconds, double isoLo, double isoHi, int charge, double scanLo, double scanHi) {
		return Spectrum.newBuilder().setSpectrumName(name).setScanNumber(scanNumber).setRtSeconds(rtSeconds).setIsoLower(isoLo).setIsoUpper(isoHi)
				.setCharge(charge).setScanWindowLower(scanLo).setScanWindowUpper(scanHi).addMz(100.0).addIntensity(10.0f).build();
	}

	private static ThermoRawFile buildFile(SummariesReply summaries, List<Spectrum> precursors, List<Spectrum> stripes) throws Exception {
		FakeChannel channel=new FakeChannel(summaries, precursors, stripes);
		ThermoRawServiceGrpc.ThermoRawServiceBlockingStub stub=ThermoRawServiceGrpc.newBlockingStub(channel);
		ThermoRawFile file=new ThermoRawFile();
		setField(file, "stub", stub);
		setField(file, "sessionId", "session");
		return file;
	}

	private static void setField(Object target, String name, Object value) throws Exception {
		Field f=target.getClass().getDeclaredField(name);
		f.setAccessible(true);
		f.set(target, value);
	}

	private static final class FakeChannel extends Channel {
		private final SummariesReply summaries;
		private final List<Spectrum> precursors;
		private final List<Spectrum> stripes;

		private FakeChannel(SummariesReply summaries, List<Spectrum> precursors, List<Spectrum> stripes) {
			this.summaries=summaries;
			this.precursors=precursors;
			this.stripes=stripes;
		}

		@Override
		public String authority() {
			return "fake";
		}

		@Override
		public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions) {
			return new FakeClientCall<>(method, summaries, precursors, stripes);
		}
	}

	private static final class FakeClientCall<ReqT, RespT> extends ClientCall<ReqT, RespT> {
		private final MethodDescriptor<ReqT, RespT> method;
		private final SummariesReply summaries;
		private final List<Spectrum> precursors;
		private final List<Spectrum> stripes;
		private Listener<RespT> listener;

		private FakeClientCall(MethodDescriptor<ReqT, RespT> method, SummariesReply summaries, List<Spectrum> precursors, List<Spectrum> stripes) {
			this.method=method;
			this.summaries=summaries;
			this.precursors=precursors;
			this.stripes=stripes;
		}

		@Override
		public void start(Listener<RespT> listener, Metadata headers) {
			this.listener=listener;
		}

		@Override
		public void request(int numMessages) {
		}

		@Override
		public void cancel(String message, Throwable cause) {
			if (listener!=null) {
				listener.onClose(Status.CANCELLED, new Metadata());
			}
		}

		@Override
		public void halfClose() {
			String methodName=method.getFullMethodName();
			if (methodName.endsWith("/GetScanSummaries")) {
				listener.onMessage(cast(summaries));
				listener.onClose(Status.OK, new Metadata());
				return;
			}
			if (methodName.endsWith("/GetPrecursors")) {
				stream(precursors, listener);
				return;
			}
			if (methodName.endsWith("/GetStripes")) {
				stream(stripes, listener);
				return;
			}
			listener.onClose(Status.UNIMPLEMENTED, new Metadata());
		}

		@Override
		public void sendMessage(ReqT message) {
			if (message instanceof Session) {
				// no-op
			}
		}

		@Override
		public boolean isReady() {
			return true;
		}

		private void stream(List<Spectrum> spectra, Listener<RespT> listener) {
			for (Spectrum s : spectra) {
				listener.onMessage(cast(s));
			}
			listener.onClose(Status.OK, new Metadata());
		}

		@SuppressWarnings("unchecked")
		private RespT cast(Object value) {
			return (RespT)value;
		}
	}
}
