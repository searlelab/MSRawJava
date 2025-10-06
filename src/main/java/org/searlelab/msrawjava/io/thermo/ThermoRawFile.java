package org.searlelab.msrawjava.io.thermo;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.searlelab.msrawjava.io.thermo.rpc.CloseRequest;
import org.searlelab.msrawjava.io.thermo.rpc.OpenRequest;
import org.searlelab.msrawjava.io.thermo.rpc.PrecursorsRequest;
import org.searlelab.msrawjava.io.thermo.rpc.Spectrum;
import org.searlelab.msrawjava.io.thermo.rpc.StripesRequest;
import org.searlelab.msrawjava.io.thermo.rpc.ThermoRawServiceGrpc;
import org.searlelab.msrawjava.io.thermo.rpc.Session;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.StripeFileInterface;
import org.searlelab.msrawjava.model.WindowData;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;

public final class ThermoRawFile implements StripeFileInterface, Closeable {
    private final Path rawPath;
    private final ManagedChannel channel;
    private final ThermoRawServiceGrpc.ThermoRawServiceBlockingStub stub;
    private final String sessionId;

    public ThermoRawFile(Path rawFile) throws Exception {
        this.rawPath = rawFile;
        int port = ThermoServerPool.port();

        this.channel = NettyChannelBuilder.forAddress("127.0.0.1", port)
                .usePlaintext()
                .maxInboundMessageSize(64 * 1024 * 1024)
                .build();
        this.stub = ThermoRawServiceGrpc.newBlockingStub(channel);

        var rep = stub.open(OpenRequest.newBuilder()
                .setPath(rawFile.toAbsolutePath().toString())
                .build());
        this.sessionId = rep.getSessionId();
    }
    
    @Override
    public String getOriginalFileName() {
        return rawPath.toString();
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
    	// FIXME Auto-generated method stub
    	
    }
    public Map<String,String> getMetadata() throws IOException, SQLException {
        var req = Session.newBuilder().setSessionId(sessionId).build();
        var reply = stub.getMetadata(req);
        return reply.getKvMap();
    }
    
    public static final class RunSummary {
        public final double gradientLengthSeconds;
        public final double totalIonCurrent;
        public RunSummary(double gls, double tic) { this.gradientLengthSeconds = gls; this.totalIonCurrent = tic; }
    }

    public RunSummary getRunSummary() {
        var req = Session.newBuilder().setSessionId(sessionId).build();
        var resp = stub.getRunSummary(req);
        return new RunSummary(resp.getGradientLengthSeconds(), resp.getTotalIonCurrent());
    }

	@Override
	public float getTIC() {
		return (float) getRunSummary().totalIonCurrent;
	}

	@Override
	public float getGradientLength() {
		return (float) getRunSummary().gradientLengthSeconds;
	}

	@Override
	public Map<Range, WindowData> getRanges() {
	    var req = Session.newBuilder().setSessionId(sessionId).build();
	    var resp = stub.getRanges(req);
	
	    var out = new LinkedHashMap<Range, WindowData>(resp.getWindowsCount());
	    for (var w : resp.getWindowsList()) {
	        Range key = new Range(w.getLo(), w.getHi());
	        WindowData val = new WindowData((float) w.getAverageDutyCycleSeconds(), w.getNumberOfMsms());
	        out.put(key, val);
	    }
	    return out;
	}

    @Override
    public ArrayList<PrecursorScan> getPrecursors(float rtStart, float rtEnd) throws IOException {
        var req = PrecursorsRequest.newBuilder()
                .setSessionId(sessionId)
                .setRtMin(rtStart/60f)
                .setRtMax(rtEnd/60f)
                .setProfile(false)
                .build();

        ArrayList<PrecursorScan> out = new ArrayList<>();
        var it = stub.getPrecursors(req);

        while (it.hasNext()) {
            Spectrum s = it.next();
            double[] mz = s.getMzList().stream().mapToDouble(d -> d).toArray();
            float[] intensity = new float[s.getIntensityCount()];
            for (int i = 0; i < intensity.length; i++) intensity[i] = s.getIntensity(i);

            out.add(new PrecursorScan(
                    s.getSpectrumName(), s.getScanNumber(), (float) s.getRtSeconds(),
                    0, s.getIsoLower(), s.getIsoUpper(),
                    (float) s.getIonInjectionTimeS(),
                    mz, intensity, null
            ));
        }
        out.sort(Comparator.comparingDouble(PrecursorScan::getScanStartTime));
        return out;
    }

    @Override
    public ArrayList<FragmentScan> getStripes(Range targetMzRange, float minRT, float maxRT, boolean sqrt) throws IOException {
        var req = StripesRequest.newBuilder()
                .setSessionId(sessionId)
                .setRtMin(minRT/60f)
                .setRtMax(maxRT/60f)
                .setMzLo(targetMzRange.getStart())
                .setMzHi(targetMzRange.getStop())
                .setProfile(false)
                .build();

        ArrayList<FragmentScan> out = new ArrayList<>();
        var it = stub.getStripes(req);

        while (it.hasNext()) {
            Spectrum s = it.next();
            double[] mz = s.getMzList().stream().mapToDouble(d -> d).toArray();
            float[] intensity = new float[s.getIntensityCount()];
            for (int i = 0; i < intensity.length; i++) {
                float v = s.getIntensity(i);
                intensity[i] = sqrt ? (float) Math.sqrt(Math.max(0f, v)) : v;
            }
            out.add(new FragmentScan(
                    s.getSpectrumName(), s.getPrecursorName(),
                    s.getScanNumber(), (float) s.getRtSeconds(),
                    0, (float) s.getIonInjectionTimeS(),
                    s.getIsoLower(), s.getIsoUpper(),
                    mz, intensity, null, (byte) s.getCharge()
            ));
        }
        out.sort(Comparator.comparingDouble(FragmentScan::getScanStartTime));
        return out;
    }

    @Override
    public ArrayList<FragmentScan> getStripes(double targetMz, float minRT, float maxRT, boolean sqrt) throws IOException {
        double half = 1e-4;
        return getStripes(new Range(targetMz - half, targetMz + half), minRT, maxRT, sqrt);
    }

    @Override
    public void close() {
        try {
            this.stub.close(CloseRequest.newBuilder().setSessionId(sessionId).build());
        } catch (Exception ignored) {
            // swallow on close
        } finally {
            try {
                channel.shutdownNow();
                channel.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Exception ignored) {
            }
        }
    }
}