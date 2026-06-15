package org.searlelab.msrawjava.io.tims;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.DataFormatException;

import org.searlelab.msrawjava.io.utils.Pair;
import org.searlelab.msrawjava.io.utils.RawFileStructureTools;
import org.searlelab.msrawjava.io.utils.Triplet;
import org.searlelab.msrawjava.logging.Logger;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.ScanSummary;

class BrukerTimsSpectrumReader {
	private final BrukerTIMSFile owner;

	BrukerTimsSpectrumReader(BrukerTIMSFile owner) {
		this.owner=owner;
	}

	/** Read MS1 precursor scans within an RT window. */
	ArrayList<PrecursorScan> getPrecursors(float rtStart, float rtEnd) throws SQLException, IOException, DataFormatException {
		owner.ensureOpen();

		Map<String, String> meta=owner.getMetadata();
		double scanWindowLower;
		double scanWindowUpper;
		try {
			scanWindowLower=Double.parseDouble(meta.get("meta.MzAcqRangeLower"));
			scanWindowUpper=Double.parseDouble(meta.get("meta.MzAcqRangeUpper"));
		} catch (Exception e) {
			Logger.errorException(e);
			scanWindowLower=0.0;
			scanWindowUpper=2000.0;
		}

		String sql="SELECT Id, Time, AccumulationTime, t1, NumScans FROM Frames WHERE MsMsType = ? AND Time BETWEEN ? AND ? ORDER BY Time ASC";
		try (PreparedStatement ps=owner.connection().prepareStatement(sql)) {
			ps.setInt(1, owner.ms1Key());
			ps.setDouble(2, rtStart);
			ps.setDouble(3, rtEnd);
			try (ResultSet rs=ps.executeQuery()) {

				final ArrayList<PrecursorScan> out=new ArrayList<>();
				while (rs.next()) {
					int frameId=rs.getInt(1);
					float rt=rs.getFloat(2);
					float injTime=accumulationTimeSeconds(rs.getFloat(3));
					double t1=rs.getDouble(4);
					int numScans=rs.getInt(5);

					Triplet<double[], float[], int[]> triplet=owner.reader().readRawFrameAndCalibrate(frameId-1, 0, 99999, t1); // ms1 reads all scans

					final String name=buildBrukerFrameScanName(frameId, 1, numScans);
					if (triplet==null||triplet.x.length==0) {
						out.add(new PrecursorScan(name, frameId, rt, 0, scanWindowLower, scanWindowUpper, injTime, new double[0], new float[0], new float[0]));
					} else {
						float[] ims=new float[triplet.z.length];
						for (int j=0; j<ims.length; j++) {
							ims[j]=getIMSFromScanNumber(triplet.z[j], numScans);
						}
						out.add(new PrecursorScan(name, frameId, rt, 0, scanWindowLower, scanWindowUpper, injTime, triplet.x, triplet.y, ims));
					}
				}
				Collections.sort(out);
				return out;
			}
		}
	}

	ArrayList<FragmentScan> getStripes(double targetMz, float minRT, float maxRT, boolean sqrt) throws IOException, SQLException {
		owner.ensureOpen();

		//targetMz=owner.reader().uncalibrateMz(targetMz); // do we trust the precursor m/zs?

		Map<String, String> meta=owner.getMetadata();
		double scanWindowLower;
		double scanWindowUpper;
		try {
			scanWindowLower=Double.parseDouble(meta.get("meta.MzAcqRangeLower"));
			scanWindowUpper=Double.parseDouble(meta.get("meta.MzAcqRangeUpper"));
		} catch (Exception e) {
			Logger.errorException(e);
			scanWindowLower=0.0;
			scanWindowUpper=2000.0;
		}

		if (owner.ms2Key()==9) {
			// DIA: select the single window per frame that contains targetMz

			final String sql="SELECT F.Id, W.WindowGroup, F.Time,  W.IsolationMz, W.IsolationWidth, F.AccumulationTime, W.ScanNumBegin, W.ScanNumEnd, F.t1, F.NumScans "
					+"FROM Frames F "+"JOIN DiaFrameMsMsInfo I ON I.Frame = F.Id "+"JOIN DiaFrameMsMsWindows W ON W.WindowGroup = I.WindowGroup "
					+"WHERE F.MsMsType = "+owner.ms2Key()+" AND F.Time BETWEEN ? AND ? "
					+"AND (? BETWEEN (W.IsolationMz - 0.5*W.IsolationWidth) AND (W.IsolationMz + 0.5*W.IsolationWidth)) "
					+"ORDER BY F.Time ASC, W.IsolationMz ASC";

			LinkedHashMap<Integer, Meta> map=new LinkedHashMap<>();

			try (PreparedStatement ps=owner.connection().prepareStatement(sql)) {
				ps.setDouble(1, minRT);
				ps.setDouble(2, maxRT);
				ps.setDouble(3, targetMz);
				try (ResultSet rs=ps.executeQuery()) {
					while (rs.next()) {
						int fid=rs.getInt(1);
						Meta m=map.get(fid);
						if (m==null) {
							m=new Meta(fid, rs.getDouble(3), rs.getDouble(6), rs.getDouble(9), rs.getInt(10));
							map.put(fid, m);
						}
						Win w=new Win();
						w.center=rs.getDouble(4);
						w.width=rs.getDouble(5);
						w.windowGroup=rs.getInt(2);
						w.scanLo=rs.getInt(7);
						w.scanHi=rs.getInt(8);
						m.wins.add(w);
					}
				}
			}
			if (map.isEmpty()) return new ArrayList<>();

			// Build time-ordered list of frames
			ArrayList<Meta> metas=new ArrayList<>(map.values());
			metas.sort(Comparator.comparingDouble(m -> m.rt));

			ArrayList<FragmentScan> out=extractDIASpectra(metas, sqrt, scanWindowLower, scanWindowUpper);

			return out;
		} else if (owner.ms2Key()==8) {
			// DDA: select frames whose isolation contains targetMz, pick closest per frame, include charge and parent
			String sql="SELECT I.frame, F.Time, I.ScanNumBegin, I.ScanNumEnd, I.IsolationMz, I.IsolationWidth, F.AccumulationTime, "
					+"COALESCE(P.Charge, 0) AS Charge, P.Parent, I.Precursor, F.t1, F.NumScans, COALESCE(P.MonoisotopicMz, P.largestPeakMz) AS targetMz "
					+"FROM PasefFrameMsMsInfo I, Frames F,  Precursors P "+"WHERE I.frame = F.Id "+"AND I.Precursor = P.Id "+"AND F.MsMsType = 8 "
					+"AND F.Time BETWEEN ? AND ? "+"AND ? BETWEEN I.IsolationMz-I.IsolationWidth/2 AND I.IsolationMz+I.IsolationWidth/2 "
					+"ORDER BY F.Time ASC, I.IsolationMz ASC";

			final ArrayList<FragmentScan> out=new ArrayList<>();

			try (PreparedStatement ps=owner.connection().prepareStatement(sql)) {
				ps.setDouble(1, minRT);
				ps.setDouble(2, maxRT);
				ps.setDouble(3, targetMz);

				try (ResultSet rs=ps.executeQuery()) {
					while (rs.next()) {
						int frameId=rs.getInt(1);
						float rt=(float)rs.getDouble(2);
						int scanLo=rs.getInt(3);
						int scanHi=rs.getInt(4);
						double isoMz=rs.getDouble(5);
						double isoW=rs.getDouble(6);
						float acc=(float)rs.getDouble(7);
						byte charge=(byte)Math.max(0, rs.getInt(8));
						String parent=Integer.toString(rs.getInt(9));
						int precursorID=rs.getInt(10); // use precursorID as the spectrumIndex
						float t1=(float)rs.getDouble(11);
						int numScans=rs.getInt(12);
						double precursorTargetMz=rs.getDouble(13);

						//isoMz=owner.reader().calibrateMz(isoMz, t1); // do we trust the precursor mz?

						final double isoLo=isoMz-0.5*isoW;
						final double isoHi=isoMz+0.5*isoW;

						try {
							Triplet<double[], float[], int[]> triplet=owner.reader().readRawFrameAndCalibrate(frameId-1, scanLo, scanHi, t1);
							if (triplet==null||triplet.x.length==0) continue;

							// Optionally sqrt intensities
							float[] intens=triplet.y;
							if (sqrt) {
								intens=intens.clone();
								for (int i=0; i<intens.length; i++) {
									intens[i]=(float)Math.sqrt(intens[i]);
								}
							}
							float[] ims=new float[triplet.z.length];
							for (int i=0; i<ims.length; i++) {
								ims[i]=getIMSFromScanNumber(triplet.z[i], numScans);
							}

							final String name=buildBrukerFrameScanName(frameId, scanLo, scanHi);
							Range trimmed=RawFileStructureTools.trimRange(new Range(isoLo, isoHi), owner.getPrecursorMarginSize());
							out.add(new FragmentScan(name, // spectrumName
									parent, // precursorName from Precursors.Parent
									precursorID, // spectrumIndex
									precursorTargetMz, // precursor
									rt, // scanStartTime
									0, // fraction
									accumulationTimeSeconds(acc), trimmed.getStart(), trimmed.getStop(), // isolation window bounds
									triplet.x, intens, ims, charge, // precursor charge
									scanWindowLower, scanWindowUpper));
						} catch (Exception ex) {
							// propagate after closing iterator
							throw new RuntimeException("Unexpected error in Rust", ex);
						}
					}
				}
			}
			Collections.sort(out);
			return out;
		} else {
			// Unknown MS2 key, return empty
			return new ArrayList<>();
		}
	}

	private static class Win {
		double center, width;
		int windowGroup, scanLo, scanHi;
	}

	private static class Meta {
		private final int frameId, scanMax;
		private final double rt, acc, t1;
		ArrayList<Win> wins=new ArrayList<>();

		public Meta(int frameId, double rt, double acc, double t1, int scanMax) {
			super();
			this.frameId=frameId;
			this.rt=rt;
			this.acc=acc;
			this.t1=t1;
			this.scanMax=scanMax;
		}

	}

	ArrayList<FragmentScan> getStripes(Range targetMzRange, float minRT, float maxRT, final boolean sqrt) throws IOException, SQLException {
		owner.ensureOpen();

		// do we trust the precursor m/zs?
		//double start=targetMzRange.getStart()<=0.0f?0.0f:owner.reader().uncalibrateMz(targetMzRange.getStart());
		//double stop=targetMzRange.getStop()>=Float.MAX_VALUE?Float.MAX_VALUE:owner.reader().uncalibrateMz(targetMzRange.getStop());
		//targetMzRange=new Range(start, stop);

		Map<String, String> meta=owner.getMetadata();
		double scanWindowLower;
		double scanWindowUpper;
		try {
			scanWindowLower=Double.parseDouble(meta.get("meta.MzAcqRangeLower"));
			scanWindowUpper=Double.parseDouble(meta.get("meta.MzAcqRangeUpper"));
		} catch (Exception e) {
			Logger.errorException(e);
			scanWindowLower=0.0;
			scanWindowUpper=2000.0;
		}

		final double rangeLo=targetMzRange.getStart();
		final double rangeHi=targetMzRange.getStop();

		if (owner.ms2Key()==9) {
			// DIA: gather all windows overlapping the target range per frame

			final String sql="SELECT F.Id, W.WindowGroup, F.Time, W.IsolationMz, W.IsolationWidth, F.AccumulationTime, W.ScanNumBegin, W.ScanNumEnd, F.t1, F.NumScans "
					+"FROM Frames F "+"JOIN DiaFrameMsMsInfo I ON I.Frame = F.Id "+"JOIN DiaFrameMsMsWindows W ON W.WindowGroup = I.WindowGroup "
					+"WHERE F.MsMsType = "+owner.ms2Key()+" AND F.Time BETWEEN ? AND ? "+"AND ( W.IsolationMz <= ? AND W.IsolationMz >= ? ) "
					+"ORDER BY F.Time ASC, W.IsolationMz ASC";

			LinkedHashMap<Integer, Meta> map=new LinkedHashMap<>();

			try (PreparedStatement ps=owner.connection().prepareStatement(sql)) {
				ps.setDouble(1, minRT);
				ps.setDouble(2, maxRT);
				ps.setDouble(3, rangeHi);
				ps.setDouble(4, rangeLo);
				try (ResultSet rs=ps.executeQuery()) {
					while (rs.next()) {
						int fid=rs.getInt(1);
						Meta m=map.get(fid);
						if (m==null) {
							m=new Meta(fid, rs.getDouble(3), rs.getDouble(6), rs.getDouble(9), rs.getInt(10));
							map.put(fid, m);
						}
						Win w=new Win();
						w.center=rs.getDouble(4);
						w.width=rs.getDouble(5);
						w.windowGroup=rs.getInt(2);
						w.scanLo=rs.getInt(7);
						w.scanHi=rs.getInt(8);
						m.wins.add(w);
					}
				}
			}
			if (map.isEmpty()) return new ArrayList<>();

			// Build time-ordered list of frames
			ArrayList<Meta> metas=new ArrayList<>(map.values());
			metas.sort(Comparator.comparingDouble(m -> m.rt));

			ArrayList<FragmentScan> out=extractDIASpectra(metas, sqrt, scanWindowLower, scanWindowUpper);

			return out;
		} else if (owner.ms2Key()==8) {
			// DDA: pick targets whose isolation window overlaps the target range.
			String sql="SELECT I.frame, F.Time, I.ScanNumBegin, I.ScanNumEnd, I.IsolationMz, I.IsolationWidth, F.AccumulationTime, "
					+"COALESCE(P.Charge, 0) AS Charge, P.Parent, I.Precursor, F.t1, F.NumScans, COALESCE(P.MonoisotopicMz, P.largestPeakMz) AS targetMz "
					+"FROM PasefFrameMsMsInfo I, Frames F,  Precursors P "+"WHERE I.frame = F.Id "+"AND I.Precursor = P.Id "+"AND F.MsMsType = 8 "
					+"AND F.Time BETWEEN ? AND ? "+"AND COALESCE(P.MonoisotopicMz, P.largestPeakMz) BETWEEN ? AND ? "+"ORDER BY F.Time ASC, I.IsolationMz ASC";

			final ArrayList<FragmentScan> out=new ArrayList<>();

			try (PreparedStatement ps=owner.connection().prepareStatement(sql)) {
				ps.setDouble(1, minRT);
				ps.setDouble(2, maxRT);
				ps.setDouble(3, targetMzRange.getStart());
				ps.setDouble(4, targetMzRange.getStop());

				try (ResultSet rs=ps.executeQuery()) {
					while (rs.next()) {
						int frameId=rs.getInt(1);
						float rt=(float)rs.getDouble(2);
						int scanLo=rs.getInt(3);
						int scanHi=rs.getInt(4);
						double isoMz=rs.getDouble(5);
						double isoW=rs.getDouble(6);
						float acc=(float)rs.getDouble(7);
						byte charge=(byte)Math.max(0, rs.getInt(8));
						String parent=Integer.toString(rs.getInt(9));
						int precursorID=rs.getInt(10); // use precursorID as the spectrumIndex
						float t1=(float)rs.getDouble(11);
						int numScans=rs.getInt(12);
						double precursorTargetMz=rs.getDouble(13);

						//isoMz=owner.reader().calibrateMz(isoMz, t1); // do we trust the precursor mz?

						double isoLo=isoMz-0.5*isoW;
						double isoHi=isoMz+0.5*isoW;

						try {
							Triplet<double[], float[], int[]> triplet=owner.reader().readRawFrameAndCalibrate(frameId-1, scanLo, scanHi, t1);
							if (triplet==null||triplet.x.length==0) continue;

							// Optionally sqrt intensities
							float[] intens=triplet.y;
							if (sqrt) {
								intens=intens.clone();
								for (int i=0; i<intens.length; i++) {
									intens[i]=(float)Math.sqrt(intens[i]);
								}
							}
							float[] ims=new float[triplet.z.length];
							for (int i=0; i<ims.length; i++) {
								ims[i]=getIMSFromScanNumber(triplet.z[i], numScans);
							}

							final String name=buildBrukerFrameScanName(frameId, scanLo, scanHi);
							Range trimmed=RawFileStructureTools.trimRange(new Range(isoLo, isoHi), owner.getPrecursorMarginSize());

							out.add(new FragmentScan(name, // spectrumName
									parent, // precursorName from Precursors.Parent
									precursorID, // spectrumIndex
									precursorTargetMz, // precursor
									rt, // scanStartTime
									0, // fraction
									1000f*acc, // IonInjectionTime (sec) = 1000 * AccumulationTime
									trimmed.getStart(), trimmed.getStop(), // isolation window bounds
									triplet.x, intens, ims, charge, // precursor charge
									scanWindowLower, scanWindowUpper));
						} catch (Exception ex) {
							// propagate after closing iterator
							throw new RuntimeException("Unexpected error in Rust", ex);
						}
					}
				}
			}
			Collections.sort(out);
			return out;
		} else {
			return new ArrayList<>();
		}
	}

	ArrayList<ScanSummary> getScanSummaries(float rtStart, float rtEnd) throws IOException, SQLException {
		owner.ensureOpen();
		Map<String, String> meta=owner.getMetadata();
		double scanWindowLower;
		double scanWindowUpper;
		try {
			scanWindowLower=Double.parseDouble(meta.get("meta.MzAcqRangeLower"));
			scanWindowUpper=Double.parseDouble(meta.get("meta.MzAcqRangeUpper"));
		} catch (Exception e) {
			Logger.errorException(e);
			scanWindowLower=0.0;
			scanWindowUpper=2000.0;
		}

		ArrayList<ScanSummary> out=new ArrayList<>();

		String ms1Sql="SELECT Id, Time, AccumulationTime, SummedIntensities, NumScans FROM Frames WHERE MsMsType = ? AND Time BETWEEN ? AND ? ORDER BY Time ASC";
		try (PreparedStatement ps=owner.connection().prepareStatement(ms1Sql)) {
			ps.setInt(1, owner.ms1Key());
			ps.setDouble(2, rtStart);
			ps.setDouble(3, rtEnd);
			try (ResultSet rs=ps.executeQuery()) {
				while (rs.next()) {
					int frameId=rs.getInt(1);
					float rt=rs.getFloat(2);
					float injTime=accumulationTimeSeconds(rs.getFloat(3));
					float tic=(float)rs.getDouble(4);
					int numScans=rs.getInt(5);
					String name=buildBrukerFrameScanName(frameId, 1, numScans);
					out.add(new ScanSummary(name, frameId, rt, 0, tic, -1.0, true, injTime, scanWindowLower, scanWindowUpper, scanWindowLower,
							scanWindowUpper, (byte)0));
				}
			}
		}

		if (owner.tableExists("DiaFrameMsMsWindows")&&owner.tableExists("DiaFrameMsMsInfo")) {
			String ms2Sql="SELECT F.Id, F.Time, F.AccumulationTime, F.SummedIntensities, W.IsolationMz, W.IsolationWidth, W.ScanNumBegin, W.ScanNumEnd "
					+"FROM Frames F "
					+"JOIN DiaFrameMsMsInfo I ON I.Frame = F.Id "+"JOIN DiaFrameMsMsWindows W ON W.WindowGroup = I.WindowGroup "
					+"WHERE F.MsMsType = ? AND F.Time BETWEEN ? AND ? "+"ORDER BY F.Time ASC, W.IsolationMz ASC";
			try (PreparedStatement ps=owner.connection().prepareStatement(ms2Sql)) {
				ps.setInt(1, owner.ms2Key());
				ps.setDouble(2, rtStart);
				ps.setDouble(3, rtEnd);
				try (ResultSet rs=ps.executeQuery()) {
					while (rs.next()) {
						int frameId=rs.getInt(1);
						float rt=rs.getFloat(2);
						float injTime=accumulationTimeSeconds(rs.getFloat(3));
						float tic=(float)rs.getDouble(4);
						double center=rs.getDouble(5);
						double width=rs.getDouble(6);
						int scanBegin=rs.getInt(7);
						int scanEnd=rs.getInt(8);
						double lo=center-0.5*width;
						double hi=center+0.5*width;
						Range trimmed=RawFileStructureTools.trimRange(new Range(lo, hi), owner.getPrecursorMarginSize());
						String name=buildBrukerFrameScanName(frameId, scanBegin, scanEnd);
						out.add(new ScanSummary(name, frameId, rt, 0, tic, center, false, injTime, trimmed.getStart(), trimmed.getStop(), scanWindowLower,
								scanWindowUpper, (byte)0));
					}
				}
			}
		}
		if (owner.ms2Key()==8&&owner.tableExists("PasefFrameMsMsInfo")&&owner.tableExists("Precursors")) {
			String ms2Sql="SELECT I.frame, F.Time, F.AccumulationTime, F.SummedIntensities, I.IsolationMz, I.IsolationWidth, "
					+"COALESCE(P.MonoisotopicMz, P.largestPeakMz, I.IsolationMz) AS targetMz, COALESCE(P.Charge, 0) AS Charge, P.Parent, I.Precursor, "
					+"I.ScanNumBegin, I.ScanNumEnd "
					+"FROM PasefFrameMsMsInfo I, Frames F, Precursors P "
					+"WHERE I.frame = F.Id AND I.Precursor = P.Id AND F.MsMsType = 8 AND F.Time BETWEEN ? AND ? "+"ORDER BY F.Time ASC, I.IsolationMz ASC";
			try (PreparedStatement ps=owner.connection().prepareStatement(ms2Sql)) {
				ps.setDouble(1, rtStart);
				ps.setDouble(2, rtEnd);
				try (ResultSet rs=ps.executeQuery()) {
					while (rs.next()) {
						int frameId=rs.getInt(1);
						float rt=rs.getFloat(2);
						float injTime=accumulationTimeSeconds(rs.getFloat(3));
						float tic=(float)rs.getDouble(4);
						double center=rs.getDouble(5);
						double width=rs.getDouble(6);
						double targetMz=rs.getDouble(7);
						byte charge=(byte)Math.max(0, rs.getInt(8));
						int precursorId=rs.getInt(10);
						int scanBegin=rs.getInt(11);
						int scanEnd=rs.getInt(12);

						double lo=center-0.5*width;
						double hi=center+0.5*width;
						Range trimmed=RawFileStructureTools.trimRange(new Range(lo, hi), owner.getPrecursorMarginSize());
						String name=buildBrukerFrameScanName(frameId, scanBegin, scanEnd);
						double summaryMz=targetMz>0.0?targetMz:(center>0.0?center:-1.0);
						out.add(new ScanSummary(name, precursorId, rt, 0, tic, summaryMz, false, injTime, trimmed.getStart(), trimmed.getStop(), scanWindowLower,
								scanWindowUpper, charge));
					}
				}
			}
		}

		out.sort((a, b) -> Float.compare(a.getScanStartTime(), b.getScanStartTime()));
		return out;
	}

	org.searlelab.msrawjava.model.AcquiredSpectrum getSpectrum(ScanSummary summary) throws IOException, SQLException, DataFormatException {
		if (summary==null) return null;
		float rt=summary.getScanStartTime();
		float delta=1.0f; // seconds
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

	Pair<String[], String[]> getScanMetadata(ScanSummary summary) {
		if (summary==null) return emptyScanMetadata();
		try {
			owner.ensureOpen();
			if (!owner.tableOrViewExists("Properties")||!owner.tableOrViewExists("PropertyDefinitions")) return emptyScanMetadata();
			int frameId=resolveFrameIdForMetadata(summary);
			if (frameId<=0) return emptyScanMetadata();

			ArrayList<String> properties=new ArrayList<>();
			ArrayList<String> values=new ArrayList<>();
			String sql="SELECT pd.DisplayGroupName, pd.DisplayName, pd.DisplayDimension, CAST(p.Value AS TEXT) AS ValueText "
					+"FROM Properties p JOIN PropertyDefinitions pd ON pd.Id = p.Property "
					+"WHERE p.Frame = ? AND p.Value IS NOT NULL AND TRIM(CAST(p.Value AS TEXT)) <> '' "+"ORDER BY p.Property ASC";
			try (PreparedStatement ps=owner.connection().prepareStatement(sql)) {
				ps.setInt(1, frameId);
				try (ResultSet rs=ps.executeQuery()) {
					while (rs.next()) {
						String property=formatPropertyName(rs.getString(1), rs.getString(2));
						String value=rs.getString(4);
						String dimension=rs.getString(3);
						if (property.isBlank()||value==null||value.isBlank()) continue;
						if (dimension!=null&&!dimension.isBlank()) value=value+" "+dimension.trim();
						properties.add(property);
						values.add(value);
					}
				}
			}
			return new Pair<>(properties.toArray(new String[0]), values.toArray(new String[0]));
		} catch (Exception e) {
			return emptyScanMetadata();
		}
	}

	private int resolveFrameIdForMetadata(ScanSummary summary) throws SQLException {
		String sql="SELECT Id FROM Frames ORDER BY ABS(Time - ?) ASC, Id ASC LIMIT 1";
		try (PreparedStatement ps=owner.connection().prepareStatement(sql)) {
			ps.setFloat(1, summary.getScanStartTime());
			try (ResultSet rs=ps.executeQuery()) {
				if (rs.next()) return rs.getInt(1);
			}
		}
		return -1;
	}

	private static String formatPropertyName(String group, String displayName) {
		String cleanGroup=group==null?"":group.trim();
		String cleanName=displayName==null?"":displayName.trim();
		if (cleanGroup.isEmpty()) return cleanName;
		if (cleanName.isEmpty()) return cleanGroup;
		return cleanGroup+": "+cleanName;
	}

	private static Pair<String[], String[]> emptyScanMetadata() {
		return new Pair<>(new String[0], new String[0]);
	}

	private float getIMSFromScanNumber(int scanNumber, int scanMax) {
		if (owner.imsUpper()-owner.imsLower()>0) {
			return owner.imsUpper()+(owner.imsLower()-owner.imsUpper())*((scanNumber-1.0f)/scanMax);
		} else {
			return scanNumber;
		}
	}

	static float accumulationTimeSeconds(double accumulationMs) {
		return (float)(accumulationMs/1000.0);
	}

	private static String buildBrukerFrameScanName(int frameId, int scanStart, int scanEnd) {
		return "frame="+frameId+" scanStart="+scanStart+" scanEnd="+scanEnd;
	}

	private ArrayList<FragmentScan> extractDIASpectra(ArrayList<Meta> metas, final boolean sqrt, double scanWindowLower, double scanWindowUpper) {
		ArrayList<FragmentScan> out=new ArrayList<>();
		// For each frame, emit one FragmentScan per window using IM scan bounds if present
		for (Meta m : metas) {
			for (Win w : m.wins) {
				// intersect m/z based on the window’s center/width and the user’s target range

				double realCenter=w.center;// owner.reader().calibrateMz(w.center); // do we trust the precursor m/z?

				double isoL=realCenter-0.5*w.width;
				double isoH=realCenter+0.5*w.width;
				Range trimmed=RawFileStructureTools.trimRange(new Range(isoL, isoH), owner.getPrecursorMarginSize());

				try {
					Triplet<double[], float[], int[]> triplet=owner.reader().readRawFrameAndCalibrate(m.frameId-1, w.scanLo, w.scanHi, m.t1);

					// Build a stable id and names
					final int scanID=m.frameId*100+w.windowGroup; // simple monotone id
					final String name=buildBrukerFrameScanName(m.frameId, w.scanLo, w.scanHi);

					final int n=triplet.x==null?0:triplet.x.length;
					if (n==0) {
						out.add(new FragmentScan(name, name, scanID, realCenter, (float)m.rt, 0, accumulationTimeSeconds(m.acc), trimmed.getStart(),
								trimmed.getStop(), new double[0], new float[0], new float[0], (byte)0, scanWindowLower, scanWindowUpper));
					} else {
						// Optionally sqrt intensities
						float[] intens=triplet.y;
						if (sqrt) {
							intens=intens.clone();
							for (int i=0; i<intens.length; i++) {
								intens[i]=(float)Math.sqrt(intens[i]);
							}
						}
						float[] ims=new float[triplet.z.length];
						for (int i=0; i<ims.length; i++) {
							ims[i]=getIMSFromScanNumber(triplet.z[i], m.scanMax);
						}

						out.add(new FragmentScan(name, name, scanID, realCenter, (float)m.rt, 0, accumulationTimeSeconds(m.acc), trimmed.getStart(),
								trimmed.getStop(), triplet.x, intens, ims, (byte)0, scanWindowLower, scanWindowUpper));
					}
				} catch (Exception ex) {
					// propagate after closing iterator
					throw new RuntimeException("Unexpected error in Rust", ex);
				}
			}
		}
		Collections.sort(out);
		return out;
	}

}
