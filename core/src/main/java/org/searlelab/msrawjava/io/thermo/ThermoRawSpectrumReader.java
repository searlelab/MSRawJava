package org.searlelab.msrawjava.io.thermo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;

import org.searlelab.msrawjava.io.thermo.rpc.PrecursorsRequest;
import org.searlelab.msrawjava.io.thermo.rpc.ScanMetadataReply;
import org.searlelab.msrawjava.io.thermo.rpc.ScanMetadataRequest;
import org.searlelab.msrawjava.io.thermo.rpc.Session;
import org.searlelab.msrawjava.io.thermo.rpc.Spectrum;
import org.searlelab.msrawjava.io.thermo.rpc.SpectrumSummary;
import org.searlelab.msrawjava.io.thermo.rpc.SummariesReply;
import org.searlelab.msrawjava.io.thermo.rpc.StripesRequest;
import org.searlelab.msrawjava.io.utils.Pair;
import org.searlelab.msrawjava.io.utils.RawFileStructureTools;
import org.searlelab.msrawjava.model.AcquiredSpectrum;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.ScanSummary;

class ThermoRawSpectrumReader {
	private final ThermoRawFile owner;

	ThermoRawSpectrumReader(ThermoRawFile owner) {
		this.owner=owner;
	}

	ArrayList<PrecursorScan> getPrecursors(float rtStart, float rtEnd) throws IOException {
		PrecursorsRequest req=PrecursorsRequest.newBuilder().setSessionId(owner.sessionId()).setRtMin(rtStart/60f).setRtMax(rtEnd/60f).setProfile(false)
				.build();

		ArrayList<PrecursorScan> out=new ArrayList<>();
		java.util.Iterator<Spectrum> it=owner.stub().getPrecursors(req);

		while (it.hasNext()) {
			Spectrum s=it.next();
			double rawOvFtT=s.getRawOvFtt();
			double[] mz=s.getMzList().stream().mapToDouble(d -> d).toArray();
			float[] intensity=new float[s.getIntensityCount()];
			for (int i=0; i<intensity.length; i++) {
				intensity[i]=s.getIntensity(i);
			}

			String spectrumName=buildDefaultSpectrumName(s.getScanNumber());
			out.add(new PrecursorScan(spectrumName, s.getScanNumber(), (float)s.getRtSeconds(), 0, s.getIsoLower(), s.getIsoUpper(),
					(float)s.getIonInjectionTimeS(), mz, intensity, null));
			consumePendingThermoSpectrumFields(rawOvFtT);
		}
		out.sort(Comparator.comparingDouble(PrecursorScan::getScanStartTime));
		return out;
	}

	ArrayList<FragmentScan> getStripes(Range targetMzRange, float minRT, float maxRT, boolean sqrt) throws IOException {
		owner.ensureStructureDetermined();
		StripesRequest req=StripesRequest.newBuilder().setSessionId(owner.sessionId()).setRtMin(minRT/60f).setRtMax(maxRT/60f)
				.setMzLo(targetMzRange.getStart()).setMzHi(targetMzRange.getStop()).setProfile(false).build();

		ArrayList<FragmentScan> out=new ArrayList<>();
		java.util.Iterator<Spectrum> it=owner.stub().getStripes(req);

		while (it.hasNext()) {
			Spectrum s=it.next();
			double rawOvFtT=s.getRawOvFtt();
			double[] mz=s.getMzList().stream().mapToDouble(d -> d).toArray();
			float[] intensity=new float[s.getIntensityCount()];
			for (int i=0; i<intensity.length; i++) {
				float v=s.getIntensity(i);
				intensity[i]=sqrt?(float)Math.sqrt(Math.max(0f, v)):v;
			}
			double isolationWindowTarget=getIsolationWindowTarget(s);
			double precursorMz=isolationWindowTarget;
			String spectrumName=buildDefaultSpectrumName(s.getScanNumber());
			Range trimmed=RawFileStructureTools.trimRange(new Range(s.getIsoLower(), s.getIsoUpper()), owner.getPrecursorMarginSize());
			out.add(new FragmentScan(spectrumName, s.getPrecursorName(), s.getScanNumber(), precursorMz, (float)s.getRtSeconds(), 0,
					(float)s.getIonInjectionTimeS(), trimmed.getStart(), isolationWindowTarget, trimmed.getStop(), mz, intensity, null, (byte)s.getCharge(),
					s.getScanWindowLower(), s.getScanWindowUpper()));
			consumePendingThermoSpectrumFields(rawOvFtT);
		}
		out.sort(Comparator.comparingDouble(FragmentScan::getScanStartTime));
		return out;
	}

	ArrayList<FragmentScan> getStripes(double targetMz, float minRT, float maxRT, boolean sqrt) throws IOException {
		double half=1e-4;
		return getStripes(new Range(targetMz-half, targetMz+half), minRT, maxRT, sqrt);
	}

	ArrayList<ScanSummary> getScanSummaries(float minRT, float maxRT) throws IOException {
		owner.ensureStructureDetermined();
		Session req=Session.newBuilder().setSessionId(owner.sessionId()).build();
		SummariesReply reply=owner.stub().getScanSummaries(req);
		ArrayList<ScanSummary> out=new ArrayList<>(reply.getSummariesCount());
		for (SpectrumSummary s : reply.getSummariesList()) {
			double rawOvFtT=s.getRawOvFtt();
			boolean precursor=s.getMsLevel()==1;
			String spectrumName=buildDefaultSpectrumName(s.getScanNumber());
			Range window=precursor?new Range(s.getIsoLower(), s.getIsoUpper())
					:RawFileStructureTools.trimRange(new Range(s.getIsoLower(), s.getIsoUpper()), owner.getPrecursorMarginSize());
			out.add(new ScanSummary(spectrumName, s.getScanNumber(), (float)s.getRtSeconds(), 0, (float)s.getTic(),
					precursor?-1.0:getIsolationWindowTarget(s), precursor, (float)s.getIonInjectionTimeS(), window.getStart(), window.getStop(),
					s.getScanWindowLower(), s.getScanWindowUpper(), (byte)s.getCharge()));
			consumePendingThermoSpectrumFields(rawOvFtT);
		}
		out.sort(Comparator.comparingDouble(ScanSummary::getScanStartTime));
		return out;
	}

	Pair<String[], String[]> getScanMetadata(ScanSummary summary) {
		if (summary==null||owner.stub()==null||owner.sessionId()==null) return emptyScanMetadata();
		try {
			ScanMetadataRequest req=ScanMetadataRequest.newBuilder().setSessionId(owner.sessionId()).setScanNumber(summary.getSpectrumIndex()).build();
			ScanMetadataReply reply=owner.stub().getScanMetadata(req);
			int n=Math.min(reply.getPropertiesCount(), reply.getValuesCount());
			String[] properties=new String[n];
			String[] values=new String[n];
			for (int i=0; i<n; i++) {
				properties[i]=reply.getProperties(i);
				values[i]=reply.getValues(i);
			}
			return new Pair<>(properties, values);
		} catch (Exception e) {
			return emptyScanMetadata();
		}
	}

	AcquiredSpectrum getSpectrum(ScanSummary summary) throws IOException {
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

	static double getIsolationWindowTarget(Spectrum s) {
		double target=s.getIsoTarget();
		if (target>0.0&&Double.isFinite(target)) return target;
		return (s.getIsoLower()+s.getIsoUpper())/2.0;
	}

	static double getIsolationWindowTarget(SpectrumSummary s) {
		double target=s.getIsoTarget();
		if (target>0.0&&Double.isFinite(target)) return target;
		return (s.getIsoLower()+s.getIsoUpper())/2.0;
	}

	static void consumePendingThermoSpectrumFields(double rawOvFtT) {
		// Transport-only for now. This verifies the Java client can read RawOvFtT
		// without changing the shared spectrum model until the fields have a defined downstream use.
		if (rawOvFtT==Double.NEGATIVE_INFINITY) {
			throw new IllegalStateException("Unreachable Thermo metadata sentinel");
		}
	}

	static Pair<String[], String[]> emptyScanMetadata() {
		return new Pair<>(new String[0], new String[0]);
	}

	static String buildDefaultSpectrumName(int scanNumber) {
		return "scan="+scanNumber;
	}
}
