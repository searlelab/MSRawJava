package org.searlelab.msrawjava.io.thermo;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.msrawjava.io.thermo.rpc.CloseReply;
import org.searlelab.msrawjava.io.thermo.rpc.MetadataReply;
import org.searlelab.msrawjava.io.thermo.rpc.OpenReply;
import org.searlelab.msrawjava.io.thermo.rpc.RangesReply;
import org.searlelab.msrawjava.io.thermo.rpc.RunSummary;
import org.searlelab.msrawjava.io.thermo.rpc.Spectrum;
import org.searlelab.msrawjava.io.thermo.rpc.ThermoRawServiceGrpc;
import org.searlelab.msrawjava.io.thermo.rpc.TicReply;
import org.searlelab.msrawjava.io.thermo.rpc.WindowRange;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.WindowData;

import io.grpc.Attributes;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import sun.misc.Unsafe;

class ThermoRawFileStubTest {

	@TempDir
	Path tmp;

	@Test
	void readsMetadataSummaryRangesAndSpectraWithStubbedChannel() throws Exception {
		FakeManagedChannel channel=new FakeManagedChannel(false);
		ThermoRawServiceGrpc.ThermoRawServiceBlockingStub stub=newBlockingStub(channel);

		ThermoRawFile rawFile=new ThermoRawFile();
		setField(rawFile, "rawPath", tmp.resolve("file.raw"));
		setField(rawFile, "channel", channel);
		setField(rawFile, "stub", stub);
		setField(rawFile, "sessionId", "session-1");

		assertEquals("file.raw", rawFile.getFile().getName());
		assertEquals("file.raw", rawFile.getOriginalFileName());
		assertTrue(rawFile.isOpen());

		Map<String, String> metadata=rawFile.getMetadata();
		assertEquals("TestModel", metadata.get("instrument"));

		ThermoRawFile.RunSummary summary=rawFile.getRunSummary();
		assertEquals(120.0, summary.gradientLengthSeconds, 1e-6);
		assertEquals(500.0, summary.totalIonCurrent, 1e-6);
		assertEquals(120.0f, rawFile.getGradientLength(), 1e-6);
		assertEquals(500.0f, rawFile.getTIC(), 1e-6);

		Map<Range, WindowData> ranges=rawFile.getRanges();
		assertEquals(2, ranges.size());

		float[] rt=rawFile.getTICTrace().getX();
		float[] tic=rawFile.getTICTrace().getY();
		assertArrayEquals(new float[] {10.0f, 20.0f}, rt, 1e-6f);
		assertArrayEquals(new float[] {100.0f, 200.0f}, tic, 1e-6f);

		List<PrecursorScan> precursors=rawFile.getPrecursors(0, 100);
		assertEquals(2, precursors.size());
		assertTrue(precursors.get(0).getScanStartTime()<=precursors.get(1).getScanStartTime());

		List<FragmentScan> stripes=rawFile.getStripes(new Range(400.0, 402.0), 0, 100, true);
		assertEquals(2, stripes.size());
		assertEquals(0.0f, stripes.get(0).getIntensityArray()[0], 1e-6f);
		assertEquals((float)Math.sqrt(9.0f), stripes.get(1).getIntensityArray()[0], 1e-6f);

		List<FragmentScan> stripesSingle=rawFile.getStripes(500.0, 0, 100, false);
		assertEquals(2, stripesSingle.size());
		assertEquals(-4.0f, stripesSingle.get(0).getIntensityArray()[0], 1e-6f);

		rawFile.close();
		assertTrue(channel.shutdownCalled);
	}

	@Test
	void closeForcesShutdownNowOnTimeout() throws Exception {
		FakeManagedChannel channel=new FakeManagedChannel(true);
		ThermoRawServiceGrpc.ThermoRawServiceBlockingStub stub=newBlockingStub(channel);

		ThermoRawFile rawFile=new ThermoRawFile();
		setField(rawFile, "rawPath", tmp.resolve("file.raw"));
		setField(rawFile, "channel", channel);
		setField(rawFile, "stub", stub);
		setField(rawFile, "sessionId", "session-1");

		rawFile.close();

		assertTrue(channel.shutdownCalled);
		assertTrue(channel.shutdownNowCalled);
	}

	@Test
	void openFileThrowsWhenServerUnavailableButCoversSetup() throws Exception {
		setLauncherFuture(allocateLauncher(1));
		ThermoRawFile rawFile=new ThermoRawFile();
		try {
			rawFile.openFile(tmp.resolve("missing.raw").toFile());
		} catch (Exception expected) {
			assertNotNull(expected);
		} finally {
			resetLauncherFuture();
		}
	}

	private static ThermoRawServiceGrpc.ThermoRawServiceBlockingStub newBlockingStub(Channel channel) throws Exception {
		Constructor<ThermoRawServiceGrpc.ThermoRawServiceBlockingStub> ctor=ThermoRawServiceGrpc.ThermoRawServiceBlockingStub.class
				.getDeclaredConstructor(Channel.class, CallOptions.class);
		ctor.setAccessible(true);
		return ctor.newInstance(channel, CallOptions.DEFAULT);
	}

	private static void setField(Object target, String name, Object value) throws Exception {
		Field field=target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}

	private static void setLauncherFuture(GrpcServerLauncher launcher) {
		try {
			Field field=ThermoServerPool.class.getDeclaredField("launcherFuture");
			field.setAccessible(true);
			field.set(null, java.util.concurrent.CompletableFuture.completedFuture(launcher));
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Failed to set launcherFuture", e);
		}
	}

	private static void resetLauncherFuture() {
		try {
			Field field=ThermoServerPool.class.getDeclaredField("launcherFuture");
			field.setAccessible(true);
			field.set(null, null);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Failed to reset launcherFuture", e);
		}
	}

	private static GrpcServerLauncher allocateLauncher(int port) throws Exception {
		Unsafe unsafe=getUnsafe();
		GrpcServerLauncher launcher=(GrpcServerLauncher)unsafe.allocateInstance(GrpcServerLauncher.class);
		setField(launcher, "port", port);
		setField(launcher, "proc", null);
		setField(launcher, "workDir", null);
		return launcher;
	}

	private static Unsafe getUnsafe() throws Exception {
		Field field=Unsafe.class.getDeclaredField("theUnsafe");
		field.setAccessible(true);
		return (Unsafe)field.get(null);
	}

	private static final class FakeManagedChannel extends ManagedChannel {
		private final FakeChannel delegate=new FakeChannel();
		private boolean terminated;
		private boolean shutdown;
		private boolean shutdownNow;
		private final boolean firstAwaitReturnsFalse;
		private boolean awaitCalled;
		boolean shutdownCalled;
		boolean shutdownNowCalled;

		private FakeManagedChannel(boolean firstAwaitReturnsFalse) {
			this.firstAwaitReturnsFalse=firstAwaitReturnsFalse;
		}

		@Override
		public ManagedChannel shutdown() {
			shutdown=true;
			shutdownCalled=true;
			return this;
		}

		@Override
		public boolean isShutdown() {
			return shutdown;
		}

		@Override
		public boolean isTerminated() {
			return terminated;
		}

		@Override
		public ManagedChannel shutdownNow() {
			shutdownNow=true;
			shutdownNowCalled=true;
			shutdownCalled=true;
			return this;
		}

		@Override
		public boolean awaitTermination(long timeout, TimeUnit unit) {
			if (!awaitCalled&&firstAwaitReturnsFalse) {
				awaitCalled=true;
				return false;
			}
			terminated=true;
			return true;
		}

		@Override
		public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(MethodDescriptor<ReqT, RespT> methodDescriptor, CallOptions callOptions) {
			return delegate.newCall(methodDescriptor, callOptions);
		}

		@Override
		public String authority() {
			return "fake";
		}
	}

	private static final class FakeChannel extends Channel {
		@Override
		public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions) {
			String name=method.getFullMethodName();
			if (name.endsWith("/Open")) {
				OpenReply reply=OpenReply.newBuilder().setSessionId("session-1").build();
				return new FakeClientCall<>(List.of((RespT)reply));
			}
			if (name.endsWith("/Close")) {
				CloseReply reply=CloseReply.newBuilder().setOk(true).build();
				return new FakeClientCall<>(List.of((RespT)reply));
			}
			if (name.endsWith("/GetMetadata")) {
				MetadataReply reply=MetadataReply.newBuilder().putKv("instrument", "TestModel").build();
				return new FakeClientCall<>(List.of((RespT)reply));
			}
			if (name.endsWith("/GetRunSummary")) {
				RunSummary reply=RunSummary.newBuilder().setGradientLengthSeconds(120.0).setTotalIonCurrent(500.0).build();
				return new FakeClientCall<>(List.of((RespT)reply));
			}
			if (name.endsWith("/GetRanges")) {
				RangesReply reply=RangesReply.newBuilder()
						.addWindows(WindowRange.newBuilder().setLo(400.0).setHi(401.0).setAverageDutyCycleSeconds(0.1).setNumberOfMsms(2))
						.addWindows(WindowRange.newBuilder().setLo(401.0).setHi(402.0).setAverageDutyCycleSeconds(0.2).setNumberOfMsms(3)).build();
				return new FakeClientCall<>(List.of((RespT)reply));
			}
			if (name.endsWith("/GetMs1Tic")) {
				TicReply reply=TicReply.newBuilder().addAllRtSeconds(Arrays.asList(10.0, 20.0)).addAllTic(Arrays.asList(100.0, 200.0)).build();
				return new FakeClientCall<>(List.of((RespT)reply));
			}
			if (name.endsWith("/GetPrecursors")) {
				Spectrum s1=Spectrum.newBuilder().addMz(100.0).addIntensity(10.0f).setScanNumber(2).setRtSeconds(30.0).setIsoLower(499.0).setIsoUpper(501.0)
						.setIonInjectionTimeS(0.2).setSpectrumName("s2").build();
				Spectrum s2=Spectrum.newBuilder().addMz(101.0).addIntensity(11.0f).setScanNumber(1).setRtSeconds(10.0).setIsoLower(499.0).setIsoUpper(501.0)
						.setIonInjectionTimeS(0.1).setSpectrumName("s1").build();
				return new FakeClientCall<>(List.of((RespT)s1, (RespT)s2));
			}
			if (name.endsWith("/GetStripes")) {
				Spectrum s1=Spectrum.newBuilder().addMz(400.5).addIntensity(-4.0f).setScanNumber(3).setRtSeconds(20.0).setIsoLower(400.0).setIsoUpper(401.0)
						.setCharge(2).setSpectrumName("f1").setPrecursorName("p1").setIonInjectionTimeS(0.3).setScanWindowLower(399.5).setScanWindowUpper(401.5)
						.build();
				Spectrum s2=Spectrum.newBuilder().addMz(401.5).addIntensity(9.0f).setScanNumber(4).setRtSeconds(25.0).setIsoLower(401.0).setIsoUpper(402.0)
						.setCharge(2).setSpectrumName("f2").setPrecursorName("p2").setIonInjectionTimeS(0.4).setScanWindowLower(400.5).setScanWindowUpper(402.5)
						.build();
				return new FakeClientCall<>(List.of((RespT)s1, (RespT)s2));
			}
			throw new IllegalArgumentException("Unhandled method: "+name);
		}

		@Override
		public String authority() {
			return "fake";
		}
	}

	private static final class FakeClientCall<ReqT, RespT> extends ClientCall<ReqT, RespT> {
		private final List<RespT> responses;
		private Listener<RespT> listener;
		private int requested;
		private int index;
		private boolean closed;

		private FakeClientCall(List<RespT> responses) {
			this.responses=responses;
		}

		@Override
		public void start(Listener<RespT> listener, Metadata headers) {
			this.listener=listener;
			deliverIfRequested();
		}

		@Override
		public void request(int numMessages) {
			requested+=numMessages;
			deliverIfRequested();
		}

		@Override
		public void cancel(String message, Throwable cause) {
			if (!closed) {
				closed=true;
				listener.onClose(Status.CANCELLED.withDescription(message).withCause(cause), new Metadata());
			}
		}

		@Override
		public void halfClose() {
			deliverIfRequested();
		}

		@Override
		public void sendMessage(ReqT message) {
		}

		@Override
		public Attributes getAttributes() {
			return Attributes.EMPTY;
		}

		private void deliverIfRequested() {
			if (listener==null||closed) return;
			while (requested>0&&index<responses.size()) {
				listener.onMessage(responses.get(index++));
				requested--;
			}
			if (index>=responses.size()&&!closed) {
				closed=true;
				listener.onClose(Status.OK, new Metadata());
			}
		}
	}
}
