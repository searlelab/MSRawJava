package org.searlelab.msrawjava.io.tims;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;

import org.searlelab.msrawjava.API;
import org.searlelab.msrawjava.io.StructuredMetadataProvider;
import org.searlelab.msrawjava.io.StripeFileInterface;
import org.searlelab.msrawjava.io.mzml.InstrumentComponent;
import org.searlelab.msrawjava.io.mzml.InstrumentId;
import org.searlelab.msrawjava.io.utils.DataAcquisitionType;
import org.searlelab.msrawjava.io.utils.Pair;
import org.searlelab.msrawjava.io.utils.RawFileStructureTools;
import org.searlelab.msrawjava.io.utils.Triplet;
import org.searlelab.msrawjava.logging.Logger;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.ScanSummary;
import org.searlelab.msrawjava.model.WindowData;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;

import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TFloatArrayList;

/**
 * BrukerTIMSFile coordinates access to Bruker timsTOF runs and presents them through the project’s common data model.
 * It orchestrates reading run metadata and frame/scan content, delegates low-level extraction to TimsReader and native
 * calls via TimsNative, applies calibration objects, and materializes MS1/MS2 spectra (e.g., PrecursorScan and
 * FragmentScan) along with DIA window summaries (Range/WindowData). The class isolates vendor specifics so call-sites
 * can treat Bruker data uniformly alongside other vendors.
 */
@API(status = API.Status.STABLE, since = "v26.7.31")
public class BrukerTIMSFile implements StripeFileInterface, StructuredMetadataProvider, AutoCloseable {
	private static final Pattern SQLITE_MISSING_COLUMN=Pattern.compile("no such column: ([^)\\s]+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern SQLITE_MISSING_TABLE=Pattern.compile("no such table: ([^)\\s]+)", Pattern.CASE_INSENSITIVE);

	/** Raised when a PASEF-off TSF run is supplied to the TDF-only reader. */
	public static class UnsupportedTsfException extends IOException {
		public UnsupportedTsfException(Path dPath) {
			super("Unsupported Bruker timsTOF TSF input: "+dPath
					+" contains analysis.tsf but no analysis.tdf. PASEF-off / TSF files are not supported by this build; use a TDF/PASEF-on file.");
		}
	}

	private Path dPath=null;
	private File fileObj=null;
	private String originalFileName=null;
	private Connection conn=null;
	private TimsReader reader=null;
	private volatile boolean open=false;
	private int ms1Key=0;
	private int ms2Key=-1; // unknown
	private int prmSpectrumIndexStride=1;
	private DataAcquisitionType dataAcquisitionType=DataAcquisitionType.DDA;
	private boolean staggered=false;
	private double precursorMarginSize=0.0;

	private float OneOverK0AcqRangeLower=0;
	private float OneOverK0AcqRangeUpper=0;
	private final BrukerTimsSpectrumReader spectrumReader=new BrukerTimsSpectrumReader(this);

	@API(status = API.Status.STABLE, since = "v26.7.31")
	public BrukerTIMSFile() {
	}

	public boolean isPASEFDIA() {
		return ms2Key==9;
	}

	public boolean isPASEFDDA() {
		return ms2Key==8;
	}

	public boolean isPASEFPRM() {
		return ms2Key==10;
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
		if (conn!=null||reader!=null||open) {
			close();
		}
		open=false;

		Objects.requireNonNull(dPath, "dPath");
		Path tdfPath=dPath.resolve("analysis.tdf");
		if (!Files.isRegularFile(tdfPath)) {
			if (Files.isRegularFile(dPath.resolve("analysis.tsf"))) {
				throw new UnsupportedTsfException(dPath);
			}
			throw new IOException("Bruker timsTOF input is missing analysis.tdf: "+dPath);
		}
		this.dPath=dPath;
		this.fileObj=dPath.toFile();
		this.originalFileName=dPath.getFileName().toString();
		String url="jdbc:sqlite:"+tdfPath.toAbsolutePath();
		this.conn=DriverManager.getConnection(url);
		this.conn.setAutoCommit(false);
		Optional<MzCalibrationParams> params=readCalibrationParams();

		int digitizerNumSamples=-1;
		double mzAcqRangeLower=-1;
		double mzAcqRangeUpper=-1;
		boolean isOtofControl=true;
		boolean failedGettingCalibrationParams=params.isEmpty();
		if (!failedGettingCalibrationParams) {
			try (PreparedStatement ps=conn.prepareStatement("SELECT value FROM GlobalMetadata where key=\"DigitizerNumSamples\"");
					ResultSet rs=ps.executeQuery()) {
				rs.next();
				digitizerNumSamples=rs.getInt(1);
			} catch (Exception e) {
				Logger.errorException(e);
				failedGettingCalibrationParams=true;
			}
		}
		if (!failedGettingCalibrationParams) {
			try (PreparedStatement ps=conn.prepareStatement("SELECT value FROM GlobalMetadata where key=\"MzAcqRangeLower\""); ResultSet rs=ps.executeQuery()) {
				rs.next();
				mzAcqRangeLower=rs.getDouble(1);
			} catch (Exception e) {
				Logger.errorException(e);
				failedGettingCalibrationParams=true;
			}
		}
		if (!failedGettingCalibrationParams) {
			try (PreparedStatement ps=conn.prepareStatement("SELECT value FROM GlobalMetadata where key=\"MzAcqRangeUpper\""); ResultSet rs=ps.executeQuery()) {
				rs.next();
				mzAcqRangeUpper=rs.getDouble(1);
			} catch (Exception e) {
				Logger.errorException(e);
				failedGettingCalibrationParams=true;
			}
		}

		if (!failedGettingCalibrationParams) {
			try (PreparedStatement ps=conn.prepareStatement("SELECT value FROM GlobalMetadata where key=\"AcquisitionSoftware\"");
					ResultSet rs=ps.executeQuery()) {
				rs.next();
				isOtofControl="Bruker otofControl".equalsIgnoreCase(rs.getString(1));
			} catch (Exception e) {
				Logger.errorException(e);
				failedGettingCalibrationParams=true;
			}
		}

		Optional<MzCalibrator> calibrator;
		if (failedGettingCalibrationParams) {
			calibrator=Optional.empty();
		} else {
			if (isOtofControl) {
				mzAcqRangeLower=mzAcqRangeLower-5.0;
				mzAcqRangeUpper=mzAcqRangeUpper+5.0;
			}
			//calibrator=Optional.of(new MzCalibrationWithEvenPowers(digitizerNumSamples, mzAcqRangeLower, mzAcqRangeUpper, params.get()));
			calibrator=Optional.of(new MzCalibrationPoly(digitizerNumSamples, mzAcqRangeLower, mzAcqRangeUpper, params.get()));
		}

		this.reader=TimsReader.open(dPath, calibrator);

		Map<Integer, Integer> hist=new LinkedHashMap<>();
		try (PreparedStatement ps=conn.prepareStatement("SELECT MsMsType, COUNT(*) FROM Frames GROUP BY MsMsType ORDER BY MsMsType");
				ResultSet rs=ps.executeQuery()) {
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
		} else if (hist.containsKey(Integer.valueOf(10))) {
			expectedMS2Key=10;
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
		if (expectedMS1==0) Logger.errorLine("No MS1s found!");
		if (expectedMS2==0) Logger.errorLine("No MS2s found!");
		ms1Key=expectedMS1Key;
		ms2Key=expectedMS2Key;
		configurePrmSpectrumIndexing();

		try (PreparedStatement ps=conn.prepareStatement("SELECT value FROM GlobalMetadata where key=\"OneOverK0AcqRangeLower\"");
				ResultSet rs=ps.executeQuery()) {
			rs.next();
			OneOverK0AcqRangeLower=rs.getFloat(1);
		}

		try (PreparedStatement ps=conn.prepareStatement("SELECT value FROM GlobalMetadata where key=\"OneOverK0AcqRangeUpper\"");
				ResultSet rs=ps.executeQuery()) {
			rs.next();
			OneOverK0AcqRangeUpper=rs.getFloat(1);
		}

		open=true;
		determineStructure();
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

	/** Return acquired isolation-window boundaries and stats; empty for datasets without MS2 windows. */
	public Map<Range, WindowData> getRanges() {
		return RawFileStructureTools.trimRanges(fetchRanges(), precursorMarginSize);
	}

	private Map<Range, WindowData> fetchRanges() {
		try {
			if (ms2Key==10) return fetchPrmRanges();
			if (!tableExists("DiaFrameMsMsWindows")||!tableExists("DiaFrameMsMsInfo")) {
				return Collections.emptyMap();
			}
			// Aggregate per window to get RT span and counts
			String sql="SELECT W.IsolationMz, W.IsolationWidth, MIN(F.Time) AS RtStart, MAX(F.Time) AS RtStop, COUNT(*) AS NumFrames, "
					+"MIN(W.ScanNumBegin) AS ScanNumBegin, MAX(W.ScanNumEnd) AS ScanNumEnd "+"FROM Frames F JOIN DiaFrameMsMsInfo I ON I.Frame = F.Id "
					+"JOIN DiaFrameMsMsWindows W ON W.WindowGroup = I.WindowGroup "+"WHERE F.MsMsType = "+ms2Key
					+" GROUP BY W.IsolationMz, W.IsolationWidth ORDER BY W.IsolationMz ASC";

			Map<Range, WindowData> out=new LinkedHashMap<>();
			try (PreparedStatement ps=conn.prepareStatement(sql); ResultSet rs=ps.executeQuery()) {
				while (rs.next()) {
					double isoMz=rs.getDouble("IsolationMz");
					double realCenter=isoMz;//reader.calibrateMz(isoMz); // do we trust the precursor m/z?

					double width=rs.getDouble("IsolationWidth");
					double rtStart=rs.getDouble("RtStart");
					double rtStop=rs.getDouble("RtStop");
					int count=rs.getInt("NumFrames");
					int sLo=rs.getInt("ScanNumBegin");
					int sHi=rs.getInt("ScanNumEnd");

					double lo=realCenter-0.5*width;
					double hi=realCenter+0.5*width;
					Range r=new Range((float)lo, (float)hi);

					float avgCycle=0f;
					if (count>=2) {
						avgCycle=(float)((rtStop-rtStart)/(count-1));
					}

					Optional<Range> imRange=Optional.empty();
					if (sLo>0||sHi>0) {
						imRange=Optional.of(new Range((float)sLo, (float)sHi));
					}

					Optional<Range> rtRange=Optional.empty();
					if (count>0) {
						rtRange=Optional.of(new Range((float)rtStart, (float)rtStop));
					}

					out.put(r, new WindowData(avgCycle, count, imRange, rtRange));
				}
			}
			return out;
		} catch (SQLException e) {
			Logger.errorLine("Error getting ranges:");
			Logger.errorException(e);
			throw new RuntimeException(e);
		}
	}

	private Map<Range, WindowData> fetchPrmRanges() throws SQLException {
		if (!tableExists("PrmFrameMsMsInfo")) return Collections.emptyMap();
		String sql="SELECT I.IsolationMz, I.IsolationWidth, MIN(F.Time) AS RtStart, MAX(F.Time) AS RtStop, COUNT(*) AS NumFrames, "
				+"MIN(I.ScanNumBegin) AS ScanNumBegin, MAX(I.ScanNumEnd) AS ScanNumEnd "
				+"FROM Frames F JOIN PrmFrameMsMsInfo I ON I.Frame = F.Id WHERE F.MsMsType = 10 "
				+"GROUP BY I.IsolationMz, I.IsolationWidth ORDER BY I.IsolationMz ASC";
		Map<Range, WindowData> out=new LinkedHashMap<>();
		try (PreparedStatement ps=conn.prepareStatement(sql); ResultSet rs=ps.executeQuery()) {
			while (rs.next()) {
				double center=rs.getDouble("IsolationMz");
				double width=rs.getDouble("IsolationWidth");
				int count=rs.getInt("NumFrames");
				float avgCycle=count>=2?(float)((rs.getDouble("RtStop")-rs.getDouble("RtStart"))/(count-1)):0f;
				Range range=new Range(center-0.5*width, center+0.5*width);
				Optional<Range> imRange=Optional.of(new Range(rs.getFloat("ScanNumBegin"), rs.getFloat("ScanNumEnd")));
				Optional<Range> rtRange=Optional.of(new Range(rs.getFloat("RtStart"), rs.getFloat("RtStop")));
				out.put(range, new WindowData(avgCycle, count, imRange, rtRange));
			}
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
		metadata=null;
	}

	private void determineStructure() {
		Map<Range, WindowData> acquisitionRanges=fetchRanges();
		dataAcquisitionType=ms2Key==10?DataAcquisitionType.PRM:RawFileStructureTools.getDataType(acquisitionRanges);
		if (dataAcquisitionType==DataAcquisitionType.DIA) {
			staggered=RawFileStructureTools.isStaggered(acquisitionRanges);
			precursorMarginSize=RawFileStructureTools.getPrecursorMarginSize(acquisitionRanges).orElse(0.0);
		} else {
			staggered=false;
			precursorMarginSize=0.0;
		}
	}

	/** for testing */
	double[] getPrecursorMzs() {
		try {
			if (!tableExists("Precursors")) return new double[0];
			try (PreparedStatement ps=conn.prepareStatement("SELECT MonoisotopicMz FROM Precursors WHERE MonoisotopicMz IS NOT NULL");
					ResultSet rs=ps.executeQuery()) {
				TDoubleArrayList precursors=new TDoubleArrayList();
				while (rs.next()) {
					double monoMz=rs.getDouble(1);
					precursors.add(monoMz);
				}
				return precursors.toArray();
			}

		} catch (SQLException e) {
			Logger.errorLine("Error getting precursors:");
			Logger.errorException(e);
			throw new RuntimeException(e);
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

	@Override
	public Pair<float[], float[]> getTICTrace() throws IOException, SQLException {
		ensureOpen();
		final String sql="SELECT time, SummedIntensities FROM Frames WHERE MsMsType = "+ms1Key;
		TFloatArrayList time=new TFloatArrayList();
		TFloatArrayList TIC=new TFloatArrayList();
		try (PreparedStatement ps=conn.prepareStatement(sql); ResultSet rs=ps.executeQuery()) {
			while (rs.next()) {
				time.add((float)rs.getDouble(1));
				TIC.add((float)rs.getDouble(2));
			}
			return new Pair<float[], float[]>(time.toArray(), TIC.toArray());
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
		try {
			if (reader!=null) {
				reader.close();
			}
		} catch (Exception ignore) {
			Logger.errorException(ignore);
		} finally {
			reader=null;
		}
		try {
			if (conn!=null) {
				conn.close();
			}
		} catch (Exception ignore) {
			Logger.errorException(ignore);
		} finally {
			conn=null;
		}
		open=false;
	}

	void ensureOpen() throws IOException {
		if (!open) throw new IOException("TIMSStripeFile is closed");
	}

	boolean tableExists(String name) throws SQLException {
		try (PreparedStatement ps=conn.prepareStatement("SELECT 1 FROM sqlite_master WHERE type='table' AND name=\""+name+"\"")) {
			try (ResultSet rs=ps.executeQuery()) {
				return rs.next();
			}
		}
	}

	boolean tableOrViewExists(String name) throws SQLException {
		try (PreparedStatement ps=conn.prepareStatement("SELECT 1 FROM sqlite_master WHERE type IN ('table', 'view') AND name = ?")) {
			ps.setString(1, name);
			try (ResultSet rs=ps.executeQuery()) {
				return rs.next();
			}
		}
	}

	Connection connection() {
		return conn;
	}

	TimsReader reader() {
		return reader;
	}

	int ms1Key() {
		return ms1Key;
	}

	int ms2Key() {
		return ms2Key;
	}

	int prmSpectrumIndex(int frameId, int scanNumBegin) {
		return frameId*prmSpectrumIndexStride+scanNumBegin;
	}

	float imsLower() {
		return OneOverK0AcqRangeLower;
	}

	float imsUpper() {
		return OneOverK0AcqRangeUpper;
	}

	private boolean metadataTableExists(String name, String section) {
		try {
			return tableExists(name);
		} catch (SQLException sqle) {
			logMetadataReadFailure(section, sqle);
			return false;
		}
	}

	private void logMetadataReadFailure(String section, SQLException failure) {
		Logger.errorLine("Unable to read Bruker metadata for "+metadataFileDescription()+" ("+section+"): "+metadataFailureSummary(failure));
	}

	private String metadataFileDescription() {
		if (dPath!=null) return dPath.toAbsolutePath().toString();
		if (originalFileName!=null&&!originalFileName.isEmpty()) return originalFileName;
		return "<unknown>";
	}

	static String metadataFailureSummary(SQLException failure) {
		if (failure==null) return "metadata query failed";
		String message=failure.getMessage();
		if (message==null||message.trim().isEmpty()) return failure.getClass().getSimpleName();
		Matcher columnMatcher=SQLITE_MISSING_COLUMN.matcher(message);
		if (columnMatcher.find()) return "missing column "+columnMatcher.group(1);
		Matcher tableMatcher=SQLITE_MISSING_TABLE.matcher(message);
		if (tableMatcher.find()) return "missing table "+tableMatcher.group(1);
		return message;
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

		out.put("file.path", dPath.toAbsolutePath().toString());
		out.put("file.name", originalFileName);

		try (PreparedStatement ps=conn.prepareStatement("SELECT COUNT(*), MIN(Time), MAX(Time), SUM(CASE WHEN MsMsType=0 THEN 1 ELSE 0 END), "
				+"SUM(CASE WHEN MsMsType=8 THEN 1 ELSE 0 END), SUM(CASE WHEN MsMsType=9 THEN 1 ELSE 0 END), "
				+"SUM(CASE WHEN MsMsType=10 THEN 1 ELSE 0 END) FROM Frames")) {
			try (ResultSet rs=ps.executeQuery()) {
				if (rs.next()) {
					out.put("frames.total", Integer.toString(rs.getInt(1)));
					out.put("rt.start.s", Double.toString(rs.getDouble(2)));
					out.put("rt.end.s", Double.toString(rs.getDouble(3)));
					out.put("frames.ms1", Integer.toString(rs.getInt(4)));
					out.put("frames.ms2.dda", Integer.toString(rs.getInt(5)));
					out.put("frames.ms2.dia", Integer.toString(rs.getInt(6)));
					out.put("frames.ms2.prm", Integer.toString(rs.getInt(7)));
				}
			}
		} catch (SQLException sqle) {
			logMetadataReadFailure("frame summary", sqle);
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
		} catch (SQLException sqle) {
			logMetadataReadFailure("frame temperature summary", sqle);
		}

		try (PreparedStatement ps=conn.prepareStatement("SELECT AVG(CASE WHEN MsMsType=0 THEN AccumulationTime END), "
				+"AVG(CASE WHEN MsMsType=8 THEN AccumulationTime END), AVG(CASE WHEN MsMsType=9 THEN AccumulationTime END) "+"FROM Frames")) {
			try (ResultSet rs=ps.executeQuery()) {
				if (rs.next()) {
					out.put("accTime.avg.ms1.s", Double.toString(1000.0*rs.getDouble(1)));
					out.put("accTime.avg.ms2.dda.s", Double.toString(1000.0*rs.getDouble(2)));
					out.put("accTime.avg.ms2.dia.s", Double.toString(1000.0*rs.getDouble(3)));
				}
			}
		} catch (SQLException sqle) {
			logMetadataReadFailure("accumulation time summary", sqle);
		}

		if (metadataTableExists("DiaFrameMsMsWindows", "DIA window table check")) {
			String cntSql="SELECT COUNT(*) FROM DiaFrameMsMsWindows";
			String wgSql="SELECT COUNT(DISTINCT WindowGroup) FROM DiaFrameMsMsWindows";
			String spanSql="SELECT MIN(IsolationMz - 0.5*IsolationWidth), MAX(IsolationMz + 0.5*IsolationWidth), AVG(IsolationWidth) "
					+"FROM DiaFrameMsMsWindows";
			try (PreparedStatement ps1=conn.prepareStatement(cntSql); ResultSet r1=ps1.executeQuery()) {
				if (r1.next()) out.put("dia.windows.count", Integer.toString(r1.getInt(1)));
			} catch (SQLException sqle) {
				logMetadataReadFailure("DIA window count", sqle);
			}
			try (PreparedStatement ps2=conn.prepareStatement(wgSql); ResultSet r2=ps2.executeQuery()) {
				if (r2.next()) out.put("dia.windowGroups.count", Integer.toString(r2.getInt(1)));
			} catch (SQLException sqle) {
				logMetadataReadFailure("DIA window group count", sqle);
			}
			try (PreparedStatement ps3=conn.prepareStatement(spanSql); ResultSet r3=ps3.executeQuery()) {
				if (r3.next()) {
					out.put("dia.mz.min", Double.toString(r3.getDouble(1)));
					out.put("dia.mz.max", Double.toString(r3.getDouble(2)));
					out.put("dia.window.avgWidth", Double.toString(r3.getDouble(3)));
				}
			} catch (SQLException sqle) {
				logMetadataReadFailure("DIA window span", sqle);
			}
		}

		if (metadataTableExists("PasefFrameMsMsInfo", "DDA target table check")) {
			try (PreparedStatement ps=conn.prepareStatement("SELECT COUNT(*), MIN(IsolationMz - 0.5*IsolationWidth), "
					+"MAX(IsolationMz + 0.5*IsolationWidth), AVG(IsolationWidth) "+"FROM PasefFrameMsMsInfo"); ResultSet rs=ps.executeQuery()) {
				if (rs.next()) {
					out.put("dda.targets.count", Integer.toString(rs.getInt(1)));
					out.put("dda.mz.min", Double.toString(rs.getDouble(2)));
					out.put("dda.mz.max", Double.toString(rs.getDouble(3)));
					out.put("dda.window.avgWidth", Double.toString(rs.getDouble(4)));
				}
			} catch (SQLException sqle) {
				logMetadataReadFailure("DDA target summary", sqle);
			}
		}

		if (ms2Key==10&&metadataTableExists("PrmFrameMsMsInfo", "PRM target table check")&&metadataTableExists("PrmTargets", "PRM target table check")) {
			try (PreparedStatement ps=conn.prepareStatement("SELECT COUNT(*), COUNT(DISTINCT I.Target), MIN(I.IsolationMz - 0.5*I.IsolationWidth), "
					+"MAX(I.IsolationMz + 0.5*I.IsolationWidth), AVG(I.IsolationWidth) FROM PrmFrameMsMsInfo I "
					+"JOIN Frames F ON F.Id = I.Frame WHERE F.MsMsType = 10"); ResultSet rs=ps.executeQuery()) {
				if (rs.next()) {
					out.put("prm.windows.count", Integer.toString(rs.getInt(1)));
					out.put("prm.targets.count", Integer.toString(rs.getInt(2)));
					out.put("prm.mz.min", Double.toString(rs.getDouble(3)));
					out.put("prm.mz.max", Double.toString(rs.getDouble(4)));
					out.put("prm.window.avgWidth", Double.toString(rs.getDouble(5)));
				}
			} catch (SQLException sqle) {
				logMetadataReadFailure("PRM target summary", sqle);
			}
		}

		if (metadataTableExists("GlobalMetadata", "global metadata table check")) {
			String sql="SELECT Key, Value FROM GlobalMetadata";
			try (PreparedStatement ps=conn.prepareStatement(sql); ResultSet rs=ps.executeQuery()) {
				while (rs.next()) {
					String k=rs.getString(1);
					String v=rs.getString(2);
					if (k!=null&&v!=null) {
						out.put("meta."+k, v);
					}
				}
			} catch (SQLException sqle) {
				logMetadataReadFailure("global metadata", sqle);
			}
		}
		out.putAll(RawFileStructureTools.structureMetadata(dataAcquisitionType, staggered, precursorMarginSize));
		metadata=out;

		return out;
	}

	@Override
	public Optional<Date> getRunStartTime() throws IOException, SQLException {
		return extractRunStartTime(getMetadata());
	}

	static Optional<Date> extractRunStartTime(Map<String, String> meta) {
		if (meta==null) return Optional.empty();
		String value=firstNonBlank(meta.get("meta.AcquisitionDateTime"), meta.get("meta.AcquisitionDate"), meta.get("meta.Date"), meta.get("AcquisitionDateTime"),
				meta.get("AcquisitionDate"), meta.get("Date"));
		if (value==null) return Optional.empty();
		return parseDate(value);
	}

	@Override
	public Multimap<String, String> getSoftwareAccessionIdToVersion() throws IOException, SQLException {
		Map<String, String> meta=getMetadata();
		LinkedHashMultimap<String, String> out=LinkedHashMultimap.create();
		String software=firstNonBlank(meta.get("meta.AcquisitionSoftware"), meta.get("meta.AcquisitionProgram"));
		String version=firstNonBlank(meta.get("meta.AcquisitionSoftwareVersion"), meta.get("meta.AcquisitionProgramVersion"));
		if (software!=null||version!=null) {
			out.put(software==null?"bruker.acquisition.software":software, version==null?"":version);
		}
		return out;
	}

	@Override
	public ImmutableMultimap<InstrumentId, InstrumentComponent> getInstrumentConfigurations() throws IOException, SQLException {
		Map<String, String> meta=getMetadata();
		String instrumentName=firstNonBlank(meta.get("meta.InstrumentName"), meta.get("meta.InstrumentFamily"), meta.get("meta.MaldiApplicationType"),
				meta.get("meta.SchemaType"), "Bruker timsTOF");
		InstrumentId id=InstrumentId.builder().setInstrumentConfigurationId("IC1").setAccession("").setName(instrumentName).build();
		return ImmutableMultimap.of(id, InstrumentComponent.builder().setType(InstrumentComponent.Type.ANALYZER).setOrder(1).setCvRef("")
				.setAccessionId("").setName(instrumentName).build());
	}

	private static double getNullableDouble(ResultSet rs, int col, double def) throws SQLException {
		double v=rs.getDouble(col);
		return rs.wasNull()?def:v;
	}

	private static String firstNonBlank(String... values) {
		for (String value : values) {
			if (value!=null&&!value.isBlank()) return value;
		}
		return null;
	}

	private static Optional<Date> parseDate(String raw) {
		try {
			return Optional.of(Date.from(OffsetDateTime.parse(raw).toInstant()));
		} catch (DateTimeParseException ignored) {
		}
		try {
			return Optional.of(Date.from(LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atZone(java.time.ZoneId.systemDefault())
					.toInstant()));
		} catch (DateTimeParseException ignored) {
		}
		return Optional.empty();
	}

	/** Read MS1 precursor scans within an RT window. */
	@Override
	public ArrayList<PrecursorScan> getPrecursors(float rtStart, float rtEnd) throws SQLException, IOException, DataFormatException {
		return spectrumReader.getPrecursors(rtStart, rtEnd);
	}

	@Override
	public ArrayList<FragmentScan> getStripes(double targetMz, float minRT, float maxRT, boolean sqrt) throws IOException, SQLException {
		return spectrumReader.getStripes(targetMz, minRT, maxRT, sqrt);
	}

	@Override
	public ArrayList<FragmentScan> getStripes(Range targetMzRange, float minRT, float maxRT, final boolean sqrt) throws IOException, SQLException {
		return spectrumReader.getStripes(targetMzRange, minRT, maxRT, sqrt);
	}

	@Override
	public ArrayList<ScanSummary> getScanSummaries(float rtStart, float rtEnd) throws IOException, SQLException {
		return spectrumReader.getScanSummaries(rtStart, rtEnd);
	}

	@Override
	public org.searlelab.msrawjava.model.AcquiredSpectrum getSpectrum(ScanSummary summary) throws IOException, SQLException, DataFormatException {
		return spectrumReader.getSpectrum(summary);
	}

	@Override
	public Pair<String[], String[]> getScanMetadata(ScanSummary summary) {
		return spectrumReader.getScanMetadata(summary);
	}

	static float accumulationTimeSeconds(double accumulationMs) {
		return BrukerTimsSpectrumReader.accumulationTimeSeconds(accumulationMs);
	}

	private void configurePrmSpectrumIndexing() throws SQLException {
		prmSpectrumIndexStride=1;
		if (ms2Key!=10) return;
		try (PreparedStatement ps=conn.prepareStatement("SELECT COALESCE(MAX(Id), 0), COALESCE(MAX(NumScans), 0) FROM Frames WHERE MsMsType = 10");
				ResultSet rs=ps.executeQuery()) {
			if (!rs.next()) return;
			long maxFrameId=rs.getLong(1);
			long maxNumScans=rs.getLong(2);
			long stride=maxNumScans+1L;
			if (stride>Integer.MAX_VALUE||maxFrameId*stride+maxNumScans>Integer.MAX_VALUE) {
				throw new SQLException("PRM frame and scan identifiers cannot be represented as distinct spectrum indices");
			}
			prmSpectrumIndexStride=(int)stride;
		}
	}

	/**
	 * for testing only!
	 * 
	 * @return
	 */
	public TimsReader getReader() {
		return reader;
	}
}
