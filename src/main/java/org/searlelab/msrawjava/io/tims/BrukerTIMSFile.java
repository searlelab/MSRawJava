package org.searlelab.msrawjava.io.tims;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.DataFormatException;

import org.searlelab.msrawjava.io.utils.Triplet;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.StripeFileInterface;
import org.searlelab.msrawjava.model.WindowData;

/**
 * TIMS-backed implementation that reads frame metadata via sqlite-jdbc and
 * pulls peak arrays via the Rust JNI iterator. Java controls what to read.
 *
 */
public class BrukerTIMSFile implements StripeFileInterface, AutoCloseable {

	private Path dPath=null;
	private File fileObj=null;
	private String originalFileName=null;
	private Connection conn=null;
	private TimsReader reader=null;
	private volatile boolean open=true;
	private int ms1Key=0;
	private int ms2Key=-1; // unknown
	
	private float OneOverK0AcqRangeLower=0;
	private float OneOverK0AcqRangeUpper=0;


	public BrukerTIMSFile() {
	}

	public boolean isPASEFDIA() {
		return ms2Key==9;
	}

	public boolean isPASEFDDA() {
		return ms2Key==8;
	}

	/** Histogram of MsMsType values present. */
	public Map<Integer, Integer> msmsTypeHistogram() throws SQLException {
		String sql="SELECT MsMsType, COUNT(*) FROM Frames GROUP BY MsMsType ORDER BY MsMsType";
		Map<Integer, Integer> out=new LinkedHashMap<>();
		try (PreparedStatement ps=conn.prepareStatement(sql); ResultSet rs=ps.executeQuery()) {
			while (rs.next())
				out.put(rs.getInt(1), rs.getInt(2));
		}
		return out;
	}

	public Range getRtRange() throws SQLException {
		try (PreparedStatement ps=conn.prepareStatement("SELECT MIN(Time), MAX(Time) FROM Frames"); ResultSet rs=ps.executeQuery()) {
			if (!rs.next()) return new Range(0, Float.MAX_VALUE);
			return new Range(rs.getDouble(1), rs.getDouble(2));
		}
	}

	@Override
	public void openFile(File userFile) throws IOException, SQLException {
		if (conn!=null) {
			close();
		}
		openFile(userFile.toPath());
	}

	public void openFile(Path dPath) throws IOException, SQLException {
		if (conn!=null) {
			close();
		}

		Objects.requireNonNull(dPath, "dPath");
		this.dPath=dPath;
		this.fileObj=dPath.toFile();
		this.originalFileName=dPath.getFileName().toString();
		String url="jdbc:sqlite:"+dPath.resolve("analysis.tdf").toAbsolutePath();
		this.conn=DriverManager.getConnection(url);
		this.conn.setAutoCommit(false);
		Optional<MzCalibrationParams> params=readCalibrationParams();
		
		this.reader=TimsReader.open(dPath, params);

		String sql="SELECT MsMsType, COUNT(*) FROM Frames GROUP BY MsMsType ORDER BY MsMsType";
		Map<Integer, Integer> hist=new LinkedHashMap<>();
		try (PreparedStatement ps=conn.prepareStatement(sql); ResultSet rs=ps.executeQuery()) {
			while (rs.next())
				hist.put(rs.getInt(1), rs.getInt(2));
		}
		int expectedMS1=hist.getOrDefault(0, 0); // MsMsType=0 → MS1
		int expectedMS2=0;
		int expectedMS1Key=0;
		int expectedMS2Key=-1;

		if (hist.containsKey(Integer.valueOf(9))) {
			expectedMS2Key=9;
			expectedMS2=hist.getOrDefault(expectedMS2Key, 0);
		} else if (hist.containsKey(Integer.valueOf(8))) {
			expectedMS2Key=8;
			expectedMS2=hist.getOrDefault(expectedMS2Key, 0);
		} else {
			for (Entry<Integer, Integer> tally : hist.entrySet()) {
				if (!Integer.valueOf(0).equals(tally.getKey())) {
					Integer value=tally.getValue();

					if (value!=null&&expectedMS2>value) {
						expectedMS2=value;
						expectedMS2Key=tally.getKey();
					}
				}
			}
		}
		if (expectedMS1==0) System.err.println("No MS1s found!");
		if (expectedMS2==0) System.err.println("No MS2s found!");
		ms1Key=expectedMS1Key;
		ms2Key=expectedMS2Key;
		

		sql="SELECT value FROM GlobalMetadata where key=\"OneOverK0AcqRangeLower\"";
		try (PreparedStatement ps=conn.prepareStatement(sql); ResultSet rs=ps.executeQuery()) {
			rs.next();
			OneOverK0AcqRangeLower=rs.getFloat(1);
		}
		
		sql="SELECT value FROM GlobalMetadata where key=\"OneOverK0AcqRangeUpper\"";
		try (PreparedStatement ps=conn.prepareStatement(sql); ResultSet rs=ps.executeQuery()) {
			rs.next();
			OneOverK0AcqRangeUpper=rs.getFloat(1);
		}
		
	}
	
	public Optional<MzCalibrationParams> readCalibrationParams() {

		try {
			if (tableExists("MzCalibration")) {
				final String sql="SELECT DigitizerTimebase, DigitizerDelay, T1, T2, dC1, dC2, C0, C1, C2, C3, C4 FROM MzCalibration ORDER BY Id LIMIT 1";

				try (PreparedStatement ps=conn.prepareStatement(sql)) {
					try (ResultSet rs=ps.executeQuery()) {
						if (!rs.next()) throw new SQLException("MzCalibration table is empty");
			
						// Required fields
						double tbNs=rs.getDouble(1);
						double delayNs=rs.getDouble(2);
						double T1=rs.getDouble(3);
						double T2=rs.getDouble(4);
						double dC1=rs.getDouble(5);
						double dC2=rs.getDouble(6);
			
						// Nullable C0..C4 → default to 0.0 if null
						double C0=getNullableDouble(rs, 7, 0.0);
						double C1=getNullableDouble(rs, 8, 0.0);
						double C2=getNullableDouble(rs, 9, 0.0);
						double C3=getNullableDouble(rs, 10, 0.0);
						double C4=getNullableDouble(rs, 11, 0.0);
			
						return Optional.of(new MzCalibrationParams(tbNs, delayNs, T1, T2, dC1, dC2, C0, C1, C2, C3, C4));
					}
				}
			}
			return Optional.empty();
		} catch (SQLException e) {
			return Optional.empty();
		}
	}

	/** Return DIA stripe boundaries and stats; empty for datasets without DIA. */
	public Map<Range, WindowData> getRanges() {
		try {
			if (!tableExists("DiaFrameMsMsWindows")||!tableExists("DiaFrameMsMsInfo")) {
				return Collections.emptyMap();
			}
			// Gather all windows with their frame times
			String sql="SELECT F.Id AS FrameId, F.Time AS RT, W.IsolationMz, W.IsolationWidth, W.ScanNumBegin, W.ScanNumEnd "
					+"FROM Frames F "
					+"JOIN DiaFrameMsMsInfo I ON I.Frame = F.Id "
					+"JOIN DiaFrameMsMsWindows W ON W.WindowGroup = I.WindowGroup "
					+"WHERE F.MsMsType = "+ms2Key+" "
					+"ORDER BY W.IsolationMz ASC, F.Time ASC";

			Map<Range, List<Double>> rtByRange=new LinkedHashMap<>();
			Map<Range, int[]> scanRangeByRange=new HashMap<>();

			try (PreparedStatement ps=conn.prepareStatement(sql); ResultSet rs=ps.executeQuery()) {
				while (rs.next()) {
					double isoMz=rs.getDouble("IsolationMz");
					double width=rs.getDouble("IsolationWidth");
					int sLo=rs.getInt("ScanNumBegin");
					int sHi=rs.getInt("ScanNumEnd");
					double lo=isoMz-0.5*width;
					double hi=isoMz+0.5*width;
					Range r=new Range((float)lo, (float)hi);
					rtByRange.computeIfAbsent(r, k -> new ArrayList<>()).add(rs.getDouble("RT"));
					scanRangeByRange.putIfAbsent(r, new int[] {sLo, sHi});
				}
			}

			Map<Range, WindowData> out=new LinkedHashMap<>();
			for (Map.Entry<Range, List<Double>> e : rtByRange.entrySet()) {
				Range r=e.getKey();
				List<Double> rts=e.getValue();
				// average duty cycle: mean delta between consecutive RTs for this window
				float avgCycle=0f;
				if (rts.size()>=2) {
					double sum=0.0;
					for (int i=1; i<rts.size(); i++)
						sum+=(rts.get(i)-rts.get(i-1));
					avgCycle=(float)(sum/(rts.size()-1));
				}
				int count=rts.size();
				// Represent IM range as scan index range if present
				Optional<Range> imRange=Optional.empty();
				int[] scans=scanRangeByRange.get(e.getKey());
				if (scans!=null) {
					imRange=Optional.of(new Range((float)scans[0], (float)scans[1]));
				}
				out.put(r, new WindowData(avgCycle, count, imRange));
			}
			return out;
		} catch (SQLException e) {
			throw new RuntimeException(e); //FIXME
		}
	}

	/**
	 * returns total precursor ion current across entire file
	 */
	@Override
	public float getTIC() throws IOException, SQLException {
		ensureOpen();
		final String sql="SELECT COALESCE(SUM(SummedIntensities), 0.0) FROM Frames WHERE MsMsType = "+ms1Key;
		try (PreparedStatement ps=conn.prepareStatement(sql); ResultSet rs=ps.executeQuery()) {
			if (rs.next()) {
				return (float)rs.getDouble(1);
			}
			return 0f;
		}
	}

	/**
	 * returns the time in seconds between the first scan and the last scan
	 */
	@Override
	public float getGradientLength() throws IOException, SQLException {
		ensureOpen();
		final String sql="SELECT MIN(Time), MAX(Time) FROM Frames";
		try (PreparedStatement ps=conn.prepareStatement(sql); ResultSet rs=ps.executeQuery()) {
			if (rs.next()) {
				double tMin=rs.getDouble(1);
				double tMax=rs.getDouble(2);
				// Guard against nulls or weird ordering
				if (rs.wasNull()) return 0f; // only checks last read column, but both MIN/MAX on empty give NULL
				return (float)Math.max(0.0, tMax-tMin);
			}
			return 0f;
		}
	}

	public boolean isOpen() {
		return open;
	}

	public File getFile() {
		return fileObj;
	}

	public String getOriginalFileName() {
		return originalFileName;
	}

	@Override
	public void close() {
		if (!open) return;
		try {
			reader.close();
		} catch (Exception ignore) {
		}
		try {
			conn.close();
		} catch (Exception ignore) {
		}
		open=false;
	}

	private void ensureOpen() throws IOException {
		if (!open) throw new IOException("TIMSStripeFile is closed");
	}

	private boolean tableExists(String name) throws SQLException {
		try (PreparedStatement ps=conn.prepareStatement("SELECT 1 FROM sqlite_master WHERE type='table' AND name=\""+name+"\"")) {
			try (ResultSet rs=ps.executeQuery()) {
				return rs.next();
			}
		}
	}
	
	private LinkedHashMap<String, String> metadata=null;

	/**
	 * metadata map for experiment
	 */
	@Override
	public Map<String, String> getMetadata() throws IOException, SQLException {
		ensureOpen();
		if (metadata!=null) return metadata;
		LinkedHashMap<String, String> out=new LinkedHashMap<>();

		// File basics
		out.put("file.path", dPath.toAbsolutePath().toString());
		out.put("file.name", originalFileName);

		try (PreparedStatement ps=conn.prepareStatement("SELECT COUNT(*), MIN(Time), MAX(Time), SUM(CASE WHEN MsMsType=0 THEN 1 ELSE 0 END), "
				+"SUM(CASE WHEN MsMsType=8 THEN 1 ELSE 0 END), SUM(CASE WHEN MsMsType=9 THEN 1 ELSE 0 END) " 
				+"FROM Frames")) {
			try (ResultSet rs=ps.executeQuery()) {
				if (rs.next()) {
					out.put("frames.total", Integer.toString(rs.getInt(1)));
					out.put("rt.start.s", Double.toString(rs.getDouble(2)));
					out.put("rt.end.s", Double.toString(rs.getDouble(3)));
					out.put("frames.ms1", Integer.toString(rs.getInt(4)));
					out.put("frames.ms2.dda", Integer.toString(rs.getInt(5)));
					out.put("frames.ms2.dia", Integer.toString(rs.getInt(6)));
				}
			}
		}

		try (PreparedStatement ps=conn.prepareStatement("SELECT MIN(t1), AVG(t1), MAX(t1), MIN(t2), AVG(t2), MAX(t2) FROM Frames")) {
			try (ResultSet rs=ps.executeQuery()) {
				if (rs.next()) {
					out.put("temp.min.t1", Double.toString(rs.getDouble(1)));
					out.put("temp.avg.t1", Double.toString(rs.getDouble(2)));
					out.put("temp.max.t1", Double.toString(rs.getDouble(3)));
					out.put("temp.min.t2", Double.toString(rs.getDouble(4)));
					out.put("temp.avg.t2", Double.toString(rs.getDouble(5)));
					out.put("temp.max.t2", Double.toString(rs.getDouble(6)));
				}
			}
		}

		try (PreparedStatement ps=conn.prepareStatement("SELECT AVG(CASE WHEN MsMsType=0 THEN AccumulationTime END), "
				+"AVG(CASE WHEN MsMsType=8 THEN AccumulationTime END), AVG(CASE WHEN MsMsType=9 THEN AccumulationTime END) "
				+"FROM Frames")) {
			try (ResultSet rs=ps.executeQuery()) {
				if (rs.next()) {
					out.put("accTime.avg.ms1.s", Double.toString(1000.0*rs.getDouble(1)));
					out.put("accTime.avg.ms2.dda.s", Double.toString(1000.0*rs.getDouble(2)));
					out.put("accTime.avg.ms2.dia.s", Double.toString(1000.0*rs.getDouble(3)));
				}
			}
		}

		if (tableExists("DiaFrameMsMsWindows")) {
			String cntSql="SELECT COUNT(*) FROM DiaFrameMsMsWindows";
			String wgSql="SELECT COUNT(DISTINCT WindowGroup) FROM DiaFrameMsMsWindows";
			String spanSql="SELECT MIN(IsolationMz - 0.5*IsolationWidth), MAX(IsolationMz + 0.5*IsolationWidth), AVG(IsolationWidth) "
					+"FROM DiaFrameMsMsWindows";
			try (PreparedStatement ps1=conn.prepareStatement(cntSql); ResultSet r1=ps1.executeQuery()) {
				if (r1.next()) out.put("dia.windows.count", Integer.toString(r1.getInt(1)));
			}
			try (PreparedStatement ps2=conn.prepareStatement(wgSql); ResultSet r2=ps2.executeQuery()) {
				if (r2.next()) out.put("dia.windowGroups.count", Integer.toString(r2.getInt(1)));
			}
			try (PreparedStatement ps3=conn.prepareStatement(spanSql); ResultSet r3=ps3.executeQuery()) {
				if (r3.next()) {
					out.put("dia.mz.min", Double.toString(r3.getDouble(1)));
					out.put("dia.mz.max", Double.toString(r3.getDouble(2)));
					out.put("dia.window.avgWidth", Double.toString(r3.getDouble(3)));
				}
			}
		}

		if (tableExists("PasefFrameMsMsInfo")) {
			try (PreparedStatement ps=conn.prepareStatement("SELECT COUNT(*), MIN(IsolationMz - 0.5*IsolationWidth), "
					+"MAX(IsolationMz + 0.5*IsolationWidth), AVG(IsolationWidth) "
					+"FROM PasefFrameMsMsInfo"); ResultSet rs=ps.executeQuery()) {
				if (rs.next()) {
					out.put("dda.targets.count", Integer.toString(rs.getInt(1)));
					out.put("dda.mz.min", Double.toString(rs.getDouble(2)));
					out.put("dda.mz.max", Double.toString(rs.getDouble(3)));
					out.put("dda.window.avgWidth", Double.toString(rs.getDouble(4)));
				}
			}
		}

		if (tableExists("GlobalMetadata")) {
			String sql="SELECT Key, Value FROM GlobalMetadata";
			try (PreparedStatement ps=conn.prepareStatement(sql); ResultSet rs=ps.executeQuery()) {
				while (rs.next()) {
					String k=rs.getString(1);
					String v=rs.getString(2);
					if (k!=null&&v!=null) {
						out.put("meta."+k, v);
					}
				}
			}
		}
		metadata=out;

		return out;
	}

    private static double getNullableDouble(ResultSet rs, int col, double def) throws SQLException {
        double v = rs.getDouble(col);
        return rs.wasNull() ? def : v;
    }

	/** Read MS1 precursor scans within an RT window. */
	@Override
	public ArrayList<PrecursorScan> getPrecursors(float rtStart, float rtEnd) throws SQLException, IOException, DataFormatException {
		ensureOpen();

		double isolationWindowLower;
		double isolationWindowUpper;
		try {
			isolationWindowLower=Double.parseDouble(getMetadata().get("meta.MzAcqRangeLower"));
			isolationWindowUpper=Double.parseDouble(getMetadata().get("meta.MzAcqRangeUpper"));
		} catch (Exception e) {
			isolationWindowLower=0;
			isolationWindowUpper=2000;
		}

		String sql="SELECT Id, Time, AccumulationTime, t1, NumScans FROM Frames WHERE MsMsType = ? AND Time BETWEEN ? AND ? ORDER BY Time ASC";
		try (PreparedStatement ps=conn.prepareStatement(sql)) {
			ps.setInt(1, ms1Key);
			ps.setDouble(2, rtStart);
			ps.setDouble(3, rtEnd);
			try (ResultSet rs=ps.executeQuery()) {
				
				final ArrayList<PrecursorScan> out=new ArrayList<>();
				while (rs.next()) {
					int frameId=rs.getInt(1);
					float rt=rs.getFloat(2);
					float injTime=rs.getFloat(3)/1000f; //msec to sec
					double t1=rs.getDouble(4);
					int numScans=rs.getInt(5);
					
					Triplet<double[], float[], int[]> triplet=reader.readRawFrameAndCalibrate(frameId-1, 0, 99999, t1); // ms1 reads all scans

					final String name=Integer.toString(frameId);
					if (triplet==null||triplet.x.length==0) {
						out.add(new PrecursorScan(name, frameId, rt, 0, isolationWindowLower, isolationWindowUpper, injTime, new double[0], new float[0], new float[0]));
					} else {
						float[] ims=new float[triplet.z.length];
						for (int j=0; j<ims.length; j++) {
							ims[j]=getIMSFromScanNumber(triplet.z[j], numScans);
						}
						out.add(new PrecursorScan(name, frameId, rt, 0, isolationWindowLower, isolationWindowUpper, injTime, triplet.x, triplet.y, ims));
					}
				}
				Collections.sort(out);
				return out;
			}
		}
	}

	@Override
	public ArrayList<FragmentScan> getStripes(double targetMz, float minRT, float maxRT, boolean sqrt) throws IOException, SQLException {
		ensureOpen();
		if (ms2Key==9) {
			// DIA: select the single window per frame that contains targetMz

			final String sql="SELECT F.Id, W.WindowGroup, F.Time,  W.IsolationMz, W.IsolationWidth, F.AccumulationTime, W.ScanNumBegin, W.ScanNumEnd, F.t1, F.NumScans "
					+"FROM Frames F "
					+"JOIN DiaFrameMsMsInfo I ON I.Frame = F.Id "
					+"JOIN DiaFrameMsMsWindows W ON W.WindowGroup = I.WindowGroup "
					+"WHERE F.MsMsType = "+ms2Key+" AND F.Time BETWEEN ? AND ? "
					+"AND (? BETWEEN (W.IsolationMz - 0.5*W.IsolationWidth) AND (W.IsolationMz + 0.5*W.IsolationWidth)) "
					+"ORDER BY F.Time ASC, W.IsolationMz ASC";

			LinkedHashMap<Integer, Meta> map=new LinkedHashMap<>();

			try (PreparedStatement ps=conn.prepareStatement(sql)) {
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

			ArrayList<FragmentScan> out=extractDIASpectra(metas, sqrt);

			return out;
		} else if (ms2Key==8) {
			// DDA: select frames whose isolation contains targetMz, pick closest per frame, include charge and parent
			final String sql="SELECT F.Id, F.Time, I.ScanNumBegin, I.ScanNumEnd, I.IsolationMz, I.IsolationWidth, F.AccumulationTime, F.t1, F.NumScans "
					+"COALESCE(P.Charge, 0) AS Charge, COALESCE(P.Parent, F.Id) AS Parent, P.Id "
					+"FROM Frames F "
					+"JOIN PasefFrameMsMsInfo I ON I.Frame = F.Id "
					+"LEFT JOIN Precursors P ON P.Id = I.Precursor "
					+"WHERE F.MsMsType = 8 AND F.Time BETWEEN ? AND ? "
					+"ORDER BY F.Time ASC, I.ScanNumBegin ASC";

			final ArrayList<FragmentScan> out=new ArrayList<>();

			try (PreparedStatement ps=conn.prepareStatement(sql)) {
				ps.setDouble(1, minRT);
				ps.setDouble(2, maxRT);
				try (ResultSet rs=ps.executeQuery()) {
					while (rs.next()) {
						final int frameId=rs.getInt(1);
						final float rt=(float)rs.getDouble(2);
						final int scanLo=rs.getInt(3);
						final int scanHi=rs.getInt(4);
						final double isoMz=rs.getDouble(5);
						final double isoW=rs.getDouble(6);
						final float acc=(float)rs.getDouble(7);
						final byte charge=(byte)Math.max(0, rs.getInt(8));
						final String parent=Integer.toString(rs.getInt(9));
						final int precursorID=rs.getInt(10); // use precursorID as the spectrumIndex
						float t1=(float)rs.getDouble(11);
						int numScans=rs.getInt(12);

						final double isoLo=isoMz-0.5*isoW;
						final double isoHi=isoMz+0.5*isoW;

						try {
							Triplet<double[], float[], int[]> triplet=reader.readRawFrameAndCalibrate(frameId-1, scanLo, scanHi, t1);
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

							final String name=Integer.toString(frameId)+"_"+Integer.toString(precursorID); // spectrumName
							out.add(new FragmentScan(name, // spectrumName
									parent, // precursorName from Precursors.Parent
									precursorID, // spectrumIndex
									rt, // scanStartTime
									0, // fraction
									1000f*acc, // IonInjectionTime (sec) = 1000 * AccumulationTime
									isoLo, isoHi, // isolation window bounds
									triplet.x, intens, ims, charge // precursor charge
							));
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

	private class Win {
		double center, width;
		int windowGroup, scanLo, scanHi;
	}

	private class Meta {
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

	@Override
	public ArrayList<FragmentScan> getStripes(Range targetMzRange, float minRT, float maxRT, final boolean sqrt) throws IOException, SQLException {
		ensureOpen();
		final double rangeLo=targetMzRange.getStart();
		final double rangeHi=targetMzRange.getStop();

		if (ms2Key==9) {
			// DIA: gather all windows overlapping the target range per frame

			final String sql="SELECT F.Id, W.WindowGroup, F.Time, W.IsolationMz, W.IsolationWidth, F.AccumulationTime, W.ScanNumBegin, W.ScanNumEnd, F.t1, F.NumScans "
					+"FROM Frames F "
					+"JOIN DiaFrameMsMsInfo I ON I.Frame = F.Id "
					+"JOIN DiaFrameMsMsWindows W ON W.WindowGroup = I.WindowGroup "
					+"WHERE F.MsMsType = "+ms2Key+" AND F.Time BETWEEN ? AND ? "+"AND ( W.IsolationMz <= ? AND W.IsolationMz >= ? ) "
					+"ORDER BY F.Time ASC, W.IsolationMz ASC";

			LinkedHashMap<Integer, Meta> map=new LinkedHashMap<>();

			try (PreparedStatement ps=conn.prepareStatement(sql)) {
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

			ArrayList<FragmentScan> out=extractDIASpectra(metas, sqrt);

			return out;
		} else if (ms2Key==8) {
			// DDA: pick targets whose isolation window overlaps the target range.
			String sql="SELECT I.frame, F.Time, I.ScanNumBegin, I.ScanNumEnd, I.IsolationMz, I.IsolationWidth, "
					+ "F.AccumulationTime, COALESCE(P.Charge, 0) AS Charge, P.Parent, I.Precursor, F.t1, F.NumScans "
					+ "FROM PasefFrameMsMsInfo I, Frames F,  Precursors P "
					+ "WHERE I.frame = F.Id "
					+ "AND I.Precursor = P.Id "
					+ "AND F.MsMsType = 8 "
					+ "AND F.Time BETWEEN ? AND ? "
					+ "AND I.IsolationMz BETWEEN ? AND ? "
					+ "ORDER BY F.Time ASC, I.IsolationMz ASC";

			final ArrayList<FragmentScan> out=new ArrayList<>();

			try (PreparedStatement ps=conn.prepareStatement(sql)) {
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

						double isoLo=isoMz-0.5*isoW;
						double isoHi=isoMz+0.5*isoW;

						try {
							Triplet<double[], float[], int[]> triplet=reader.readRawFrameAndCalibrate(frameId-1, scanLo, scanHi, t1);
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

							final String name="frame="+Integer.toString(frameId)+" start="+scanLo+" stop="+scanHi;
							
							out.add(new FragmentScan(name, // spectrumName
									parent, // precursorName from Precursors.Parent
									precursorID, // spectrumIndex
									rt, // scanStartTime
									0, // fraction
									1000f*acc, // IonInjectionTime (sec) = 1000 * AccumulationTime
									isoLo, isoHi, // isolation window bounds
									triplet.x, intens, ims, charge // precursor charge
							));
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
	
	private float getIMSFromScanNumber(int scanNumber, int scanMax) {
		if (OneOverK0AcqRangeUpper-OneOverK0AcqRangeLower>0) {
			return OneOverK0AcqRangeUpper+(OneOverK0AcqRangeLower-OneOverK0AcqRangeUpper)*((scanNumber-1.0f)/scanMax);
		} else {
			return scanNumber;
		}
	}

	private ArrayList<FragmentScan> extractDIASpectra(ArrayList<Meta> metas, final boolean sqrt) {
		ArrayList<FragmentScan> out=new ArrayList<>();
		// For each frame, emit one FragmentScan per window using IM scan bounds if present
		for (Meta m : metas) {
			for (Win w : m.wins) {
				// intersect m/z based on the window’s center/width and the user’s target range
				final double isoL=w.center-0.5*w.width;
				final double isoH=w.center+0.5*w.width;

				try {
					Triplet<double[], float[], int[]> triplet=reader.readRawFrameAndCalibrate(m.frameId-1, w.scanLo, w.scanHi, m.t1);

					// Build a stable id and names
					final int scanID=m.frameId*100+w.windowGroup; // simple monotone id
					final String name=Integer.toString(m.frameId)+"_"+w.windowGroup;

					final int n=triplet.x==null?0:triplet.x.length;
					if (n==0) {
						out.add(new FragmentScan(name, name, scanID, (float)m.rt, 0, 1000f*(float)m.acc, isoL, isoH, new double[0], new float[0], new float[0], (byte)0));
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

						out.add(new FragmentScan(name, name, scanID, (float)m.rt, 0, 1000f*(float)m.acc, isoL, isoH, triplet.x, intens, ims, (byte)0));
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

	/**
	 * for testing only!
	 * @return
	 */
	public TimsReader getReader() {
		return reader;
	}
}
