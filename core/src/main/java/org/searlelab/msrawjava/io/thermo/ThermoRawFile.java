package org.searlelab.msrawjava.io.thermo;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.searlelab.msrawjava.io.StripeFileInterface;
import org.searlelab.msrawjava.io.thermo.rpc.CloseRequest;
import org.searlelab.msrawjava.io.thermo.rpc.MetadataReply;
import org.searlelab.msrawjava.io.thermo.rpc.OpenRequest;
import org.searlelab.msrawjava.io.thermo.rpc.OpenReply;
import org.searlelab.msrawjava.io.thermo.rpc.PrecursorsRequest;
import org.searlelab.msrawjava.io.thermo.rpc.RangesReply;
import org.searlelab.msrawjava.io.thermo.rpc.Session;
import org.searlelab.msrawjava.io.thermo.rpc.Spectrum;
import org.searlelab.msrawjava.io.thermo.rpc.SpectrumSummary;
import org.searlelab.msrawjava.io.thermo.rpc.SummariesReply;
import org.searlelab.msrawjava.io.thermo.rpc.StripesRequest;
import org.searlelab.msrawjava.io.thermo.rpc.ThermoRawServiceGrpc;
import org.searlelab.msrawjava.io.thermo.rpc.TicReply;
import org.searlelab.msrawjava.io.thermo.rpc.TicRequest;
import org.searlelab.msrawjava.io.thermo.rpc.WindowRange;
import org.searlelab.msrawjava.io.utils.Pair;
import org.searlelab.msrawjava.logging.Logger;
import org.searlelab.msrawjava.model.AcquiredSpectrum;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.ScanSummary;
import org.searlelab.msrawjava.model.WindowData;

import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;

/**
 * ThermoRawFile is a thin, blocking gRPC client over the local Thermo server that normalizes RAW access into the
 * project’s common model. It manages a channel and session, opens a RAW path, retrieves run metadata and summary (TIC,
 * gradient length), enumerates DIA window definitions as {@link java.util.Map}&lt;Range,WindowData&gt;, and streams
 * MS1/MS2 content as PrecursorScan and FragmentScan objects.
 */
public final class ThermoRawFile implements StripeFileInterface, Closeable {
	private static final String INVALID_INSTRUMENT_INDEX_TEXT="instrument index";
	private Path rawPath=null;
	private ManagedChannel channel=null;
	private ThermoRawServiceGrpc.ThermoRawServiceBlockingStub stub=null;
	private String sessionId=null;

	public ThermoRawFile() {
	}

	@Override
	public String getOriginalFileName() {
		return rawPath.getFileName().toString();
	}

	@Override
	public File getFile() {
		return rawPath.toFile();
	}

	@Override
	public boolean isOpen() {
		return true;
	}

	@Override
	public void openFile(File userFile) throws IOException, SQLException {
		this.openFile(userFile.toPath());
	}

	public void openFile(Path rawFile) throws IOException, SQLException {
		if (stub!=null) {
			close();
		}

		this.rawPath=rawFile;

		int port;
		try {
			port=ThermoServerPool.port();
		} catch (InterruptedException ie) {
			throw new RuntimeException("Error setting up Thermo file reading server", ie);
		}

		this.channel=NettyChannelBuilder.forAddress("127.0.0.1", port).usePlaintext().maxInboundMessageSize(64*1024*1024).build();
		this.stub=ThermoRawServiceGrpc.newBlockingStub(channel);

		try {
			OpenReply rep=stub.open(OpenRequest.newBuilder().setPath(rawFile.toAbsolutePath().toString()).build());
			this.sessionId=rep.getSessionId();
		} catch (StatusRuntimeException e) {
			String detail=e.getStatus()!=null?e.getStatus().getDescription():e.getMessage();
			if (detail!=null&&detail.toLowerCase(Locale.ROOT).contains(INVALID_INSTRUMENT_INDEX_TEXT)) {
				String name=rawFile.getFileName()!=null?rawFile.getFileName().toString():"RAW file";
				throw new IOException("Unable to open Thermo RAW '"+name+"': file did not have a valid instrument index.", e);
			}
			throw e;
		}
	}

	public Map<String, String> getMetadata() throws IOException, SQLException {
		Session req=Session.newBuilder().setSessionId(sessionId).build();
		MetadataReply reply=stub.getMetadata(req);
		return reply.getKvMap();
	}

	public static final class RunSummary {
		public final double gradientLengthSeconds;
		public final double totalIonCurrent;

		public RunSummary(double gls, double tic) {
			this.gradientLengthSeconds=gls;
			this.totalIonCurrent=tic;
		}
	}

	public RunSummary getRunSummary() {
		Session req=Session.newBuilder().setSessionId(sessionId).build();
		org.searlelab.msrawjava.io.thermo.rpc.RunSummary resp=stub.getRunSummary(req);
		return new RunSummary(resp.getGradientLengthSeconds(), resp.getTotalIonCurrent());
	}

	@Override
	public float getTIC() {
		return (float)getRunSummary().totalIonCurrent;
	}

	@Override
	public float getGradientLength() {
		return (float)getRunSummary().gradientLengthSeconds;
	}

	@Override
	public Map<Range, WindowData> getRanges() {
		Session req=Session.newBuilder().setSessionId(sessionId).build();
		RangesReply resp=stub.getRanges(req);

		LinkedHashMap<Range, WindowData> out=new LinkedHashMap<Range, WindowData>(resp.getWindowsCount());
		for (WindowRange w : resp.getWindowsList()) {
			Range key=new Range(w.getLo(), w.getHi());
			Optional<Range> rtRange=Optional.empty();
			if (w.getRtEndSeconds()>0||w.getRtStartSeconds()>0) {
				rtRange=Optional.of(new Range(w.getRtStartSeconds(), w.getRtEndSeconds()));
			}
			WindowData val=new WindowData((float)w.getAverageDutyCycleSeconds(), w.getNumberOfMsms(), Optional.empty(), rtRange);
			out.put(key, val);
		}
		return out;
	}

	@Override
	public Pair<float[], float[]> getTICTrace() throws IOException, SQLException {
		TicRequest req=TicRequest.newBuilder().setSessionId(sessionId).setRtMin(0).setRtMax(Float.MAX_VALUE).build();
		TicReply ticData=stub.getMs1Tic(req);
		List<Double> rtSec=ticData.getRtSecondsList();
		List<Double> tic=ticData.getTicList();

		double[] rtSecDoubleArray=rtSec.stream().mapToDouble(d -> d).toArray();
		double[] ticDoubleArray=tic.stream().mapToDouble(d -> d).toArray();

		float[] rtSecFloatArray=new float[rtSecDoubleArray.length];
		for (int i=0; i<rtSecDoubleArray.length; i++) {
			rtSecFloatArray[i]=(float)rtSecDoubleArray[i];
		}
		float[] ticFloatArray=new float[ticDoubleArray.length];
		for (int i=0; i<ticDoubleArray.length; i++) {
			ticFloatArray[i]=(float)ticDoubleArray[i];
		}
		return new Pair<float[], float[]>(rtSecFloatArray, ticFloatArray);
	}

	@Override
	public ArrayList<PrecursorScan> getPrecursors(float rtStart, float rtEnd) throws IOException {
		PrecursorsRequest req=PrecursorsRequest.newBuilder().setSessionId(sessionId).setRtMin(rtStart/60f).setRtMax(rtEnd/60f).setProfile(false).build();

		ArrayList<PrecursorScan> out=new ArrayList<>();
		java.util.Iterator<Spectrum> it=stub.getPrecursors(req);

		while (it.hasNext()) {
			Spectrum s=it.next();
			double[] mz=s.getMzList().stream().mapToDouble(d -> d).toArray();
			float[] intensity=new float[s.getIntensityCount()];
			for (int i=0; i<intensity.length; i++) {
				intensity[i]=s.getIntensity(i);
			}

			String spectrumName=buildDefaultSpectrumName(s.getScanNumber());
			out.add(new PrecursorScan(spectrumName, s.getScanNumber(), (float)s.getRtSeconds(), 0, s.getIsoLower(), s.getIsoUpper(),
					(float)s.getIonInjectionTimeS(), mz, intensity, null));
		}
		out.sort(Comparator.comparingDouble(PrecursorScan::getScanStartTime));
		return out;
	}

	@Override
	public ArrayList<FragmentScan> getStripes(Range targetMzRange, float minRT, float maxRT, boolean sqrt) throws IOException {
		StripesRequest req=StripesRequest.newBuilder().setSessionId(sessionId).setRtMin(minRT/60f).setRtMax(maxRT/60f).setMzLo(targetMzRange.getStart())
				.setMzHi(targetMzRange.getStop()).setProfile(false).build();

		ArrayList<FragmentScan> out=new ArrayList<>();
		java.util.Iterator<Spectrum> it=stub.getStripes(req);

		while (it.hasNext()) {
			Spectrum s=it.next();
			double[] mz=s.getMzList().stream().mapToDouble(d -> d).toArray();
			float[] intensity=new float[s.getIntensityCount()];
			for (int i=0; i<intensity.length; i++) {
				float v=s.getIntensity(i);
				intensity[i]=sqrt?(float)Math.sqrt(Math.max(0f, v)):v;
			}
			double precursorMz=(s.getIsoLower()+s.getIsoUpper())/2.0; // FIXME // works most of the time but not always if there were an offset
			String spectrumName=buildDefaultSpectrumName(s.getScanNumber());
			out.add(new FragmentScan(spectrumName, s.getPrecursorName(), s.getScanNumber(), precursorMz, (float)s.getRtSeconds(), 0,
					(float)s.getIonInjectionTimeS(), s.getIsoLower(), s.getIsoUpper(), mz, intensity, null, (byte)s.getCharge(), s.getScanWindowLower(),
					s.getScanWindowUpper()));
		}
		out.sort(Comparator.comparingDouble(FragmentScan::getScanStartTime));
		return out;
	}

	@Override
	public ArrayList<FragmentScan> getStripes(double targetMz, float minRT, float maxRT, boolean sqrt) throws IOException {
		double half=1e-4;
		return getStripes(new Range(targetMz-half, targetMz+half), minRT, maxRT, sqrt);
	}

	@Override
	public ArrayList<ScanSummary> getScanSummaries(float minRT, float maxRT) throws IOException {
		Session req=Session.newBuilder().setSessionId(sessionId).build();
		SummariesReply reply=stub.getScanSummaries(req);
		ArrayList<ScanSummary> out=new ArrayList<>(reply.getSummariesCount());
		for (SpectrumSummary s : reply.getSummariesList()) {
			boolean precursor=s.getMsLevel()==1;
			String spectrumName=buildDefaultSpectrumName(s.getScanNumber());
			out.add(new ScanSummary(spectrumName, s.getScanNumber(), (float)s.getRtSeconds(), 0, (float)s.getTic(),
					precursor?-1.0:(s.getIsoLower()+s.getIsoUpper())/2.0, precursor, (float)s.getIonInjectionTimeS(), s.getIsoLower(), s.getIsoUpper(),
					s.getScanWindowLower(), s.getScanWindowUpper(), (byte)s.getCharge()));
		}
		out.sort(Comparator.comparingDouble(ScanSummary::getScanStartTime));
		return out;
	}

	@Override
	public AcquiredSpectrum getSpectrum(ScanSummary summary) throws IOException {
		if (summary==null) return null;
		float rt=summary.getScanStartTime();
		float delta=1.0f;
		if (summary.isPrecursor()) {
			ArrayList<PrecursorScan> scans=getPrecursors(rt-delta, rt+delta);
			for (PrecursorScan scan : scans) {
				if (scan.getSpectrumIndex()==summary.getSpectrumIndex()) return scan;
			}
			return scans.isEmpty()?null:scans.get(0);
		}
		Range range=new Range((float)summary.getIsolationWindowLower(), (float)summary.getIsolationWindowUpper());
		ArrayList<FragmentScan> scans=getStripes(range, rt-delta, rt+delta, false);
		for (FragmentScan scan : scans) {
			if (scan.getSpectrumIndex()==summary.getSpectrumIndex()) return scan;
		}
		return scans.isEmpty()?null:scans.get(0);
	}

	@Override
	public void close() {
		try {
			if (stub!=null&&sessionId!=null) {
				stub.withDeadlineAfter(3, TimeUnit.SECONDS).close(CloseRequest.newBuilder().setSessionId(sessionId).build());
			}
		} catch (Exception ignored) {
			Logger.errorException(ignored);
		} finally {
			try {
				if (channel!=null) {
					channel.shutdown();
					if (!channel.awaitTermination(2, TimeUnit.SECONDS)) {
						channel.shutdownNow();
						channel.awaitTermination(2, TimeUnit.SECONDS);
					}
				}
			} catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
			} catch (Exception ignored) {
				Logger.errorException(ignored);
			}
		}
	}

	private static String buildDefaultSpectrumName(int scanNumber) {
		return "scan="+scanNumber;
	}
}
