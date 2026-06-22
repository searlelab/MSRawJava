package org.searlelab.msrawjava.io.thermo;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.searlelab.msrawjava.io.StructuredMetadataProvider;
import org.searlelab.msrawjava.io.StripeFileInterface;
import org.searlelab.msrawjava.io.mzml.InstrumentComponent;
import org.searlelab.msrawjava.io.mzml.InstrumentId;
import org.searlelab.msrawjava.io.thermo.rpc.CloseReply;
import org.searlelab.msrawjava.io.thermo.rpc.CloseRequest;
import org.searlelab.msrawjava.io.thermo.rpc.MetadataReply;
import org.searlelab.msrawjava.io.thermo.rpc.OpenRequest;
import org.searlelab.msrawjava.io.thermo.rpc.OpenReply;
import org.searlelab.msrawjava.io.thermo.rpc.RangesReply;
import org.searlelab.msrawjava.io.thermo.rpc.Session;
import org.searlelab.msrawjava.io.thermo.rpc.Spectrum;
import org.searlelab.msrawjava.io.thermo.rpc.SpectrumSummary;
import org.searlelab.msrawjava.io.thermo.rpc.ThermoRawServiceGrpc;
import org.searlelab.msrawjava.io.thermo.rpc.TicReply;
import org.searlelab.msrawjava.io.thermo.rpc.TicRequest;
import org.searlelab.msrawjava.io.thermo.rpc.WindowRange;
import org.searlelab.msrawjava.io.utils.DataAcquisitionType;
import org.searlelab.msrawjava.io.utils.Pair;
import org.searlelab.msrawjava.io.utils.RawFileStructureTools;
import org.searlelab.msrawjava.logging.Logger;
import org.searlelab.msrawjava.model.AcquiredSpectrum;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.ScanSummary;
import org.searlelab.msrawjava.model.WindowData;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;

import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;

/**
 * ThermoRawFile is a thin, blocking gRPC client over the local Thermo server that normalizes RAW access into the
 * project’s common model. It manages a channel and session, opens a RAW path, retrieves run metadata and summary (TIC,
 * gradient length), enumerates DIA window definitions as {@link java.util.Map}&lt;Range,WindowData&gt;, and streams
 * MS1/MS2 content as PrecursorScan and FragmentScan objects.
 */
public final class ThermoRawFile implements StripeFileInterface, StructuredMetadataProvider, Closeable {
	private static final String INVALID_INSTRUMENT_INDEX_TEXT="instrument index";
	private Path rawPath=null;
	private ManagedChannel channel=null;
	private ThermoRawServiceGrpc.ThermoRawServiceBlockingStub stub=null;
	private String sessionId=null;
	private Map<Range, WindowData> acquisitionRanges=new LinkedHashMap<Range, WindowData>();
	private DataAcquisitionType dataAcquisitionType=DataAcquisitionType.DDA;
	private boolean staggered=false;
	private double precursorMarginSize=0.0;
	private Optional<Date> runStartTime=Optional.empty();
	private final ThermoRawSpectrumReader spectrumReader=new ThermoRawSpectrumReader(this);

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
		return stub!=null&&sessionId!=null&&channel!=null;
	}

	ThermoRawServiceGrpc.ThermoRawServiceBlockingStub stub() {
		return stub;
	}

	String sessionId() {
		return sessionId;
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
			applyOpenReply(rawFile, rep);
		} catch (StatusRuntimeException e) {
			String detail=e.getStatus()!=null?e.getStatus().getDescription():e.getMessage();
			if (detail!=null&&detail.toLowerCase(Locale.ROOT).contains(INVALID_INSTRUMENT_INDEX_TEXT)) {
				String name=rawFile.getFileName()!=null?rawFile.getFileName().toString():"RAW file";
				throw new IOException("Unable to open Thermo RAW '"+name+"': file did not have a valid instrument index.", e);
			}
			throw e;
		}
	}

	private void applyOpenReply(Path rawFile, OpenReply rep) {
		this.rawPath=rawFile;
		this.sessionId=rep.getSessionId();
		this.runStartTime=parseDate(rep.getRunStartTimeIso8601());
		this.acquisitionRanges=new LinkedHashMap<Range, WindowData>();
		this.dataAcquisitionType=DataAcquisitionType.DDA;
		this.staggered=false;
		this.precursorMarginSize=0.0;
	}

	public Map<String, String> getMetadata() throws IOException, SQLException {
		ensureStructureDetermined();
		Map<String, String> metadata=fetchMetadata();
		runStartTime=extractRunStartTime(metadata);
		metadata.putAll(RawFileStructureTools.structureMetadata(dataAcquisitionType, staggered, precursorMarginSize));
		return metadata;
	}

	private Map<String, String> fetchMetadata() {
		Session req=Session.newBuilder().setSessionId(sessionId).build();
		MetadataReply reply=stub.getMetadata(req);
		return new LinkedHashMap<>(reply.getKvMap());
	}

	@Override
	public Optional<Date> getRunStartTime() throws IOException, SQLException {
		if (runStartTime.isPresent()||stub==null||sessionId==null) return runStartTime;
		try {
			runStartTime=extractRunStartTime(fetchMetadata());
		} catch (RuntimeException e) {
			throw e;
		}
		return runStartTime;
	}

	public Optional<Date> getRunStartTimeIfKnown() {
		return runStartTime;
	}

	@Override
	public Multimap<String, String> getSoftwareAccessionIdToVersion() throws IOException, SQLException {
		Map<String, String> metadata=getMetadata();
		LinkedHashMultimap<String, String> out=LinkedHashMultimap.create();
		String version=metadata.get("instrument.software_version");
		if (version!=null&&!version.isBlank()) {
			out.put("thermo.instrument.software", version);
		}
		return out;
	}

	@Override
	public ImmutableMultimap<InstrumentId, InstrumentComponent> getInstrumentConfigurations() throws IOException, SQLException {
		Map<String, String> metadata=getMetadata();
		String model=firstNonBlank(metadata.get("instrument.model"), metadata.get("instrument.name"), "Thermo RAW");
		InstrumentId id=InstrumentId.builder().setInstrumentConfigurationId("IC1").setAccession("").setName(model).build();
		ImmutableMultimap.Builder<InstrumentId, InstrumentComponent> builder=ImmutableMultimap.builder();
		String analyzers=metadata.get("acq.mass_analyzers");
		if (analyzers!=null&&!analyzers.isBlank()) {
			int order=1;
			for (String analyzer : analyzers.split(",")) {
				String trimmed=analyzer.trim();
				if (!trimmed.isEmpty()) {
					builder.put(id, InstrumentComponent.builder().setType(InstrumentComponent.Type.ANALYZER).setOrder(order++).setCvRef("")
							.setAccessionId("").setName(trimmed).build());
				}
			}
		}
		if (builder.build().isEmpty()) {
			builder.put(id, InstrumentComponent.builder().setType(InstrumentComponent.Type.ANALYZER).setOrder(1).setCvRef("").setAccessionId("")
					.setName(model).build());
		}
		return builder.build();
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
		ensureStructureDetermined();
		return RawFileStructureTools.trimRanges(acquisitionRanges, precursorMarginSize);
	}

	void ensureStructureDetermined() {
		if (acquisitionRanges==null||acquisitionRanges.isEmpty()) {
			acquisitionRanges=fetchRanges();
			determineStructure();
		}
	}

	private Map<Range, WindowData> fetchRanges() {
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
	public double getPrecursorMarginSize() {
		return precursorMarginSize;
	}

	@Override
	public void setPrecursorMarginSize(double precursorMarginSize) {
		this.precursorMarginSize=Math.max(0.0, precursorMarginSize);
	}

	private void determineStructure() {
		dataAcquisitionType=RawFileStructureTools.getDataType(acquisitionRanges);
		if (dataAcquisitionType==DataAcquisitionType.DIA) {
			staggered=RawFileStructureTools.isStaggered(acquisitionRanges);
			precursorMarginSize=RawFileStructureTools.getPrecursorMarginSize(acquisitionRanges).orElse(0.0);
		} else {
			staggered=false;
			precursorMarginSize=0.0;
		}
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
		return spectrumReader.getPrecursors(rtStart, rtEnd);
	}

	@Override
	public ArrayList<FragmentScan> getStripes(Range targetMzRange, float minRT, float maxRT, boolean sqrt) throws IOException {
		return spectrumReader.getStripes(targetMzRange, minRT, maxRT, sqrt);
	}

	@Override
	public ArrayList<FragmentScan> getStripes(double targetMz, float minRT, float maxRT, boolean sqrt) throws IOException {
		return spectrumReader.getStripes(targetMz, minRT, maxRT, sqrt);
	}

	@Override
	public ArrayList<ScanSummary> getScanSummaries(float minRT, float maxRT) throws IOException {
		return spectrumReader.getScanSummaries(minRT, maxRT);
	}

	@Override
	public Pair<String[], String[]> getScanMetadata(ScanSummary summary) {
		return spectrumReader.getScanMetadata(summary);
	}

	@Override
	public AcquiredSpectrum getSpectrum(ScanSummary summary) throws IOException {
		return spectrumReader.getSpectrum(summary);
	}

	@Override
	public void close() {
		ManagedChannel channelToClose=channel;
		ThermoRawServiceGrpc.ThermoRawServiceBlockingStub stubToClose=stub;
		String sessionIdToClose=sessionId;
		String file=(rawPath!=null)?rawPath.toString():"<unknown>";
		boolean interruptedAtEntry=Thread.currentThread().isInterrupted();
		stub=null;
		sessionId=null;
		channel=null;
		try {
			if (stubToClose!=null&&sessionIdToClose!=null&&channelToClose!=null) {
				if (interruptedAtEntry) {
					Logger.logLine("Previous request cancelled by user for "+file);
				} else {
					sendCloseBestEffort(channelToClose, sessionIdToClose);
				}
			} else if (stubToClose!=null&&sessionIdToClose!=null&&interruptedAtEntry) {
				Logger.logLine("Previous request cancelled by user for "+file);
			}
		} catch (RuntimeException e) {
			Logger.errorLine("Unexpected Thermo close setup failure for "+file+": "+String.valueOf(e));
		} finally {
			shutdownChannel(channelToClose);
		}
	}

	private static double getIsolationWindowTarget(Spectrum s) {
		return ThermoRawSpectrumReader.getIsolationWindowTarget(s);
	}

	private static double getIsolationWindowTarget(SpectrumSummary s) {
		return ThermoRawSpectrumReader.getIsolationWindowTarget(s);
	}

	private static void sendCloseBestEffort(ManagedChannel channel, String sessionId) {
		CloseRequest request=CloseRequest.newBuilder().setSessionId(sessionId).build();
		ThermoRawServiceGrpc.newStub(channel).withDeadlineAfter(3, TimeUnit.SECONDS).close(request, new StreamObserver<CloseReply>() {
			@Override
			public void onNext(CloseReply value) {
			}

			@Override
			public void onError(Throwable t) {
				// Close is best-effort cleanup; suppress transport noise during teardown races and deadlines.
			}

			@Override
			public void onCompleted() {
			}
		});
	}

	private static void shutdownChannel(ManagedChannel channel) {
		if (channel==null) return;
		try {
			channel.shutdown();
			if (!channel.awaitTermination(2, TimeUnit.SECONDS)) {
				channel.shutdownNow();
				channel.awaitTermination(2, TimeUnit.SECONDS);
			}
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
		} catch (Exception ignored) {
			Logger.errorException(ignored);
		}
	}

	private static void consumePendingThermoSpectrumFields(double rawOvFtT) {
		ThermoRawSpectrumReader.consumePendingThermoSpectrumFields(rawOvFtT);
	}

	private static Pair<String[], String[]> emptyScanMetadata() {
		return ThermoRawSpectrumReader.emptyScanMetadata();
	}

	private static String buildDefaultSpectrumName(int scanNumber) {
		return ThermoRawSpectrumReader.buildDefaultSpectrumName(scanNumber);
	}

	private static String firstNonBlank(String... values) {
		for (String value : values) {
			if (value!=null&&!value.isBlank()) return value;
		}
		return "";
	}

	static Optional<Date> extractRunStartTime(Map<String, String> metadata) {
		if (metadata==null) return Optional.empty();
		return parseDate(firstNonBlank(metadata.get("run.start_time_iso8601"), metadata.get("run.start_time_utc")));
	}

	private static Optional<Date> parseDate(String raw) {
		if (raw==null||raw.isBlank()) return Optional.empty();
		try {
			return Optional.of(Date.from(OffsetDateTime.parse(raw).toInstant()));
		} catch (DateTimeParseException ignored) {
			return Optional.empty();
		}
	}
}
