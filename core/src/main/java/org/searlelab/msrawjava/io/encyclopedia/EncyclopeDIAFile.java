package org.searlelab.msrawjava.io.encyclopedia;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TimeZone;
import java.util.zip.DataFormatException;

import org.searlelab.msrawjava.Version;
import org.searlelab.msrawjava.algorithms.MatrixMath;
import org.searlelab.msrawjava.io.OutputSpectrumFile;
import org.searlelab.msrawjava.io.StripeFileInterface;
import org.searlelab.msrawjava.io.mzml.InstrumentComponent;
import org.searlelab.msrawjava.io.mzml.InstrumentId;
import org.searlelab.msrawjava.io.mzml.InstrumentMapTranscoder;
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
import org.sqlite.SQLiteException;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

import gnu.trove.list.array.TFloatArrayList;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.procedure.TIntObjectProcedure;

/**
 * EncyclopeDIAFile implements a streamed writer for EncyclopeDIA .DIA outputs, organizing run metadata and spectra into
 * the expected on-disk layout. It coordinates schema creation and batched inserts via SQLFile, encodes binary fields
 * with ByteConverter, optionally applies CompressionUtils, and enforces deterministic ordering so downstream tools
 * consume stable archives.
 */
public class EncyclopeDIAFile extends SQLFile implements OutputSpectrumFile, StripeFileInterface {
	public static final DateFormat m_ISO8601Local=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
	private static final Version MOST_RECENT_VERSION=new Version(0, 8, 0, false);
	private static final java.util.Set<String> RANGES_WARNING_LOGGED=java.util.concurrent.ConcurrentHashMap.newKeySet();
	private static final java.util.Set<String> FRACTIONS_WARNING_LOGGED=java.util.concurrent.ConcurrentHashMap.newKeySet();
	private static final java.util.Set<String> METADATA_WARNING_LOGGED=java.util.concurrent.ConcurrentHashMap.newKeySet();

	private static final String UNKNOWN_VALUE="unknown";
	public static final String FILELOCATION_ATTRIBUTE="filelocation";
	public static final String SOURCENAME_ATTRIBUTE="sourcename";
	public static final String FILENAME_ATTRIBUTE="filename";
	public static final String TOTAL_PRECURSOR_TIC_ATTRIBUTE="totalPrecursorTIC";
	public static final String GRADIENT_LENGTH_ATTRIBUTE="gradientLength";
	public static final String SOFTWARE_VERSION_PREFIX="SoftwareVersion_";
	public static final String RUN_START_TIME="runStartTime";
	public static final String SOFTWARE_VERSIONS_DELIMITER=";";
	public static final String INSTRUMENT_CONFIGURATIONS="InstrumentConfigurations";

	public static final String DIA_EXTENSION=".dia";
	private File tempFile;
	private File userFile;
	private boolean readOnly=false;

	private final HashMap<Range, WindowData> ranges=new HashMap<Range, WindowData>();
	private DataAcquisitionType dataAcquisitionType=DataAcquisitionType.DDA;
	private boolean staggered=false;
	private double precursorMarginSize=0.0;

	private final TIntObjectHashMap<String> fractionNames=new TIntObjectHashMap<String>();
	private final EncyclopeDIASpectrumReader spectrumReader=new EncyclopeDIASpectrumReader(this);

	static {
		m_ISO8601Local.setTimeZone(TimeZone.getTimeZone("UTC"));
	}

	public EncyclopeDIAFile() throws IOException {
	}

	@Override
	public String getFileExtension() {
		return EncyclopeDIAFile.DIA_EXTENSION;
	}

	@Override
	public void setRanges(HashMap<Range, WindowData> ranges) {
		this.ranges.clear();
		this.ranges.putAll(ranges);
		determineStructure();
	}

	public void setFractionNames(TIntObjectHashMap<String> fractionNames) {
		this.fractionNames.clear();
		this.fractionNames.putAll(fractionNames);
	}

	public TIntObjectHashMap<String> getFractionNames() {
		return new TIntObjectHashMap<String>(fractionNames);
	}

	public void writeRanges() throws IOException, SQLException {
		Connection c=getConnection();
		try {
			PreparedStatement prep=c.prepareStatement(
					"insert into ranges (Start, Stop, DutyCycle, NumWindows, IonMobilityStart, IonMobilityStop, RtStart, RtStop) VALUES (?,?,?,?,?,?,?,?)");
			try {
				int rangeCount=0;
				for (Entry<Range, WindowData> entry : ranges.entrySet()) {
					Range range=entry.getKey();
					WindowData data=entry.getValue();
					if (data!=null) {
						float dutyCycle=data.getAverageDutyCycle();
						int numWindows=data.getNumberOfMSMS();
						prep.setFloat(1, range.getStart());
						prep.setFloat(2, range.getStop());
						prep.setFloat(3, dutyCycle);
						prep.setInt(4, numWindows);

						if (data.getIonMobilityRange().isPresent()) {
							prep.setFloat(5, data.getIonMobilityRange().get().getStart());
							prep.setFloat(6, data.getIonMobilityRange().get().getStop());
						} else {
							prep.setNull(5, Types.DOUBLE);
							prep.setNull(6, Types.DOUBLE);
						}
						if (data.getRtRange().isPresent()) {
							prep.setFloat(7, data.getRtRange().get().getStart());
							prep.setFloat(8, data.getRtRange().get().getStop());
						} else {
							prep.setNull(7, Types.DOUBLE);
							prep.setNull(8, Types.DOUBLE);
						}

						prep.addBatch();
						rangeCount++;
					}
				}
				if (rangeCount>0) {
					prep.executeBatch();
				}
				prep.close();
				c.commit();
			} finally {
				prep.close();
			}
		} finally {
			c.close();
		}
		determineStructure();
	}

	private void determineStructure() {
		dataAcquisitionType=RawFileStructureTools.getDataType(ranges);
		if (dataAcquisitionType==DataAcquisitionType.DIA) {
			staggered=RawFileStructureTools.isStaggered(ranges);
			precursorMarginSize=RawFileStructureTools.getPrecursorMarginSize(ranges).orElse(0.0);
		} else {
			staggered=false;
			precursorMarginSize=0.0;
		}
	}

	public void writeFractionNames() throws IOException, SQLException {
		Connection c=getConnection();
		try {
			PreparedStatement prep=c.prepareStatement("insert into fractions (Fraction, Name) VALUES (?,?)");
			try {
				if (fractionNames.size()>0) {
					fractionNames.forEachEntry(new TIntObjectProcedure<String>() {
						@Override
						public boolean execute(int a, String b) {
							try {
								prep.setInt(1, a);
								prep.setString(2, b);

								prep.addBatch();

								return true;
							} catch (SQLException e) {
								Logger.logException(e);
								return false;
							}
						}
					});
					prep.executeBatch();
					prep.close();
					c.commit();
				}
			} finally {
				prep.close();
			}
		} finally {
			c.close();
		}
	}

	public void openFile() throws IOException, SQLException {
		userFile=null;
		readOnly=false;
		if (tempFile==null) {
			tempFile=File.createTempFile("encyclopedia_", DIA_EXTENSION);
			tempFile.deleteOnExit();
		}
		createNewTables();
	}

	@Override
	public void openFile(File userFile) throws IOException, SQLException {
		this.userFile=userFile;
		this.tempFile=null;
		this.readOnly=true;

		loadRanges();
		loadFractionNames();
	}

	public void loadRanges() throws IOException, SQLException {

		ranges.clear();
		Connection c=getConnection();
		try {
			Statement s=c.createStatement();
			try {
				boolean hasIonMobilityStart=doesColumnExist(c, "ranges", "IonMobilityStart");
				boolean hasIonMobilityStop=doesColumnExist(c, "ranges", "IonMobilityStop");
				boolean hasRtStart=doesColumnExist(c, "ranges", "RtStart");
				boolean hasRtStop=doesColumnExist(c, "ranges", "RtStop");
				String ionMobilitySelect=hasIonMobilityStart&&hasIonMobilityStop?"IonMobilityStart, IonMobilityStop"
						:"NULL as IonMobilityStart, NULL as IonMobilityStop";
				String rtSelect=hasRtStart&&hasRtStop?"RtStart, RtStop":"NULL as RtStart, NULL as RtStop";
				String sql="select Start, Stop, DutyCycle, NumWindows, "+ionMobilitySelect+", "+rtSelect+" from Ranges";
				ResultSet rs=s.executeQuery(sql);

				while (rs.next()) {
					float start=rs.getFloat(1);
					float stop=rs.getFloat(2);
					float dutyCycle=rs.getFloat(3);
					int numWindows=rs.getInt(4);
					Float ionMobilityStart=rs.getFloat(5);
					if (rs.wasNull()) ionMobilityStart=null;
					Float ionMobilityStop=rs.getFloat(6);
					if (rs.wasNull()) ionMobilityStop=null;

					Optional<Range> range=(ionMobilityStart==null||ionMobilityStop==null)?Optional.empty()
							:Optional.of(new Range(ionMobilityStart, ionMobilityStop));

					Float rtStart=rs.getFloat(7);
					if (rs.wasNull()) rtStart=null;
					Float rtStop=rs.getFloat(8);
					if (rs.wasNull()) rtStop=null;
					Optional<Range> rtRange=(rtStart==null||rtStop==null)?Optional.empty():Optional.of(new Range(rtStart, rtStop));

					ranges.put(new Range(start, stop), new WindowData(dutyCycle, numWindows, range, rtRange));
				}
				} catch (SQLException sqle) {
					String key=String.valueOf(getFile());
					if (RANGES_WARNING_LOGGED.add(key)) {
						Logger.errorLine("Unexpected error reading ranges from "+getFile()+", suggests potential file corruption!");
					}
				} finally {
					s.close();
				}
		} finally {
			c.close();
		}
		determineStructure();
	}

	public void loadFractionNames() throws IOException, SQLException {
		fractionNames.clear();
		Connection c=getConnection();
		try {
			Statement s=c.createStatement();
			try {
				ResultSet rs=s.executeQuery("select fraction, name from fractions");

				while (rs.next()) {
					int fraction=rs.getInt(1);
					String name=rs.getString(2);

					fractionNames.put(fraction, name);
				}
					} catch (SQLiteException e) {
						String key=String.valueOf(getFile());
						if (FRACTIONS_WARNING_LOGGED.add(key)) {
							Logger.errorLine("Error getting fractions from "+getFile()+", likely caused from reading an older file: "+e.getMessage());
						}
				} finally {
					s.close();
				}
		} finally {
			c.close();
		}
	}

	@Override
	public Map<Range, WindowData> getRanges() {
		return RawFileStructureTools.trimRanges(ranges, precursorMarginSize);
	}

	@Override
	public double getPrecursorMarginSize() {
		return precursorMarginSize;
	}

	@Override
	public void setPrecursorMarginSize(double precursorMarginSize) {
		this.precursorMarginSize=Math.max(0.0, precursorMarginSize);
	}

	@Override
	public ArrayList<PrecursorScan> getPrecursors(float minRT, float maxRT) throws IOException, SQLException, DataFormatException {
		return spectrumReader.getPrecursors(minRT, maxRT);
	}

	@Override
	public ArrayList<FragmentScan> getStripes(double targetMz, float minRT, float maxRT, boolean sqrt) throws IOException, SQLException {
		return spectrumReader.getStripes(targetMz, minRT, maxRT, sqrt);
	}

	@Override
	public ArrayList<FragmentScan> getStripes(Range targetMzRange, float minRT, float maxRT, boolean sqrt) throws IOException, SQLException {
		return spectrumReader.getStripes(targetMzRange, minRT, maxRT, sqrt);
	}
	@Override
	public float getTIC() throws IOException, SQLException {
		Connection c=getConnection();
		try {
			Statement s=c.createStatement();
			try {
				ResultSet rs=s.executeQuery("select coalesce(sum(TIC), 0.0) from precursor");
				if (rs.next()) {
					return (float)rs.getDouble(1);
				}
				return 0.0f;
			} finally {
				s.close();
			}
		} finally {
			c.close();
		}
	}

	@Override
	public float getGradientLength() throws IOException, SQLException {
		String value=getMetadata().get(GRADIENT_LENGTH_ATTRIBUTE);
		if (value==null) {
			float rt=0.0f;
			Connection c=getConnection();
			try {
				Statement s=c.createStatement();
				try {
					ResultSet rs=s.executeQuery("select max(scanstarttime) from spectra");

					while (rs.next()) {
						rt=rs.getFloat(1);
					}
				} finally {
					s.close();
				}
			} finally {
				c.close();
			}

			if (rt>0.0f) {
				addMetadata(GRADIENT_LENGTH_ATTRIBUTE, Float.toString(rt));
			}
			return rt;
		}
		return Float.parseFloat(value);
	}

	@Override
	public Pair<float[], float[]> getTICTrace() throws IOException, SQLException {
		TFloatArrayList rts=new TFloatArrayList();
		TFloatArrayList tics=new TFloatArrayList();

		Connection c=getConnection();
		try {
			Statement s=c.createStatement();
			try {
				ResultSet rs=s.executeQuery("SELECT ScanStartTime, TIC FROM precursor ORDER BY ScanStartTime");

				while (rs.next()) {
					rts.add(rs.getFloat(1));
					tics.add(rs.getFloat(2));
				}

			} finally {
				s.close();
			}
		} finally {
			c.close();
		}
		return new Pair<float[], float[]>(rts.toArray(), tics.toArray());
	}

	@Override
	public boolean isOpen() {
		File f=(tempFile!=null)?tempFile:userFile;
		return f!=null&&f.exists();
	}

	@Override
	public File getFile() {
		if (userFile!=null) return userFile;
		return tempFile;
	}

	@Override
	public String getOriginalFileName() {
		try {
			return getMetadata().get(SOURCENAME_ATTRIBUTE);
		} catch (Exception e) {
			throw new RuntimeException("Error getting metadata", e);
		}
	}

	@Override
	public void saveAsFile(File saveFile) throws IOException, SQLException {
		ensureWritableTempFile();
		applySchemaPatchesToWritableFile();
		HashMap<String, String> map=new HashMap<String, String>();
		map.put(FILENAME_ATTRIBUTE, saveFile.getName()==null?UNKNOWN_VALUE:saveFile.getName());
		addMetadata(map);

		writeRanges();
		writeFractionNames();
		createIndices();

		if (saveFile!=null) {
			setFileVersion();
			File source=(tempFile!=null)?tempFile:userFile;
			if (source!=null&&source.toPath().equals(saveFile.toPath())) {
				Logger.errorLine("Refusing to overwrite source DIA file: "+saveFile.getAbsolutePath());
				return;
			}
			Files.move(tempFile.toPath(), saveFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
	}

	public void setFileVersion() throws IOException, SQLException {
		HashMap<String, String> map=new HashMap<String, String>();
		map.put(VERSION_STRING, MOST_RECENT_VERSION.toString());
		addMetadata(map);
	}

	private void setFileVersion(Connection c) throws SQLException {
		try (PreparedStatement prep=c.prepareStatement("insert or replace into metadata (Key, Value) VALUES (?,?)")) {
			prep.setString(1, VERSION_STRING);
			prep.setString(2, MOST_RECENT_VERSION.toString());
			prep.executeUpdate();
		}
	}

	@Override
	public void setFileName(String fileName, String fileLocation) throws IOException, SQLException {
		setFileName(fileName, fileName, fileLocation);
	}

	public void setFileName(String fileName, String sourceName, String fileLocation) throws IOException, SQLException {
		HashMap<String, String> map=new HashMap<String, String>();
		map.put(FILENAME_ATTRIBUTE, fileName==null?UNKNOWN_VALUE:fileName);
		map.put(SOURCENAME_ATTRIBUTE, sourceName==null?UNKNOWN_VALUE:sourceName);
		map.put(FILELOCATION_ATTRIBUTE, fileLocation==null?UNKNOWN_VALUE:fileLocation);
		addMetadata(map);
	}

	public void setStartTime(Date startTime) throws IOException, SQLException {
		addMetadata(RUN_START_TIME, startTime==null?null:m_ISO8601Local.format(startTime));
	}

	public void setSoftwareVersions(final Multimap<String, String> softwareAccessionIdToVersion) throws IOException, SQLException {
		if (softwareAccessionIdToVersion==null||softwareAccessionIdToVersion.isEmpty()) return;
		HashMap<String, String> data=new HashMap<String, String>();
		softwareAccessionIdToVersion.asMap().forEach((key, value) -> {
			data.put(SOFTWARE_VERSION_PREFIX+key, Joiner.on(SOFTWARE_VERSIONS_DELIMITER).join(value));
		});
		addMetadata(data);
	}

	public void setInstrumentConfiguration(ImmutableMultimap<InstrumentId, InstrumentComponent> instrumentConfigurations) throws IOException, SQLException {
		if (instrumentConfigurations==null||instrumentConfigurations.isEmpty()) return;
		addMetadata(INSTRUMENT_CONFIGURATIONS, InstrumentMapTranscoder.encode(instrumentConfigurations));
	}

	public void addMetadata(String key, String value) throws IOException, SQLException {
		HashMap<String, String> map=new HashMap<String, String>();
		map.put(key, value==null?UNKNOWN_VALUE:value);
		addMetadata(map);
	}

	public HashMap<String, String> getMetadata() throws IOException, SQLException {
		Connection c=getConnection();
		try {
			Statement s=c.createStatement();
			HashMap<String, String> map=new HashMap<String, String>();
			try {
				ResultSet rs=s.executeQuery("select Key, Value from metadata");

				while (rs.next()) {
					String key=rs.getString(1);
					String value=rs.getString(2);
					map.put(key, value);
				}

				map.putAll(RawFileStructureTools.structureMetadata(dataAcquisitionType, staggered, precursorMarginSize));
				return map;
				} catch (SQLException sqle) {
					String key=String.valueOf(getFile());
					if (METADATA_WARNING_LOGGED.add(key)) {
						Logger.errorLine("Unexpected error reading metadata from "+getFile()+", suggests potential file corruption!");
					}
					map.putAll(RawFileStructureTools.structureMetadata(dataAcquisitionType, staggered, precursorMarginSize));
					return map;
				} finally {
					s.close();
			}
		} finally {
			c.close();
		}
	}

	@Override
	public void addMetadata(Map<String, String> data) throws IOException, SQLException {
		if (readOnly) {
			return;
		}
		Connection c=getConnection();
		try {
			PreparedStatement prep=c.prepareStatement("insert or replace into metadata (Key, Value) VALUES (?,?)");
			try {
				for (Entry<String, String> entry : data.entrySet()) {
					prep.setString(1, entry.getKey());
					prep.setString(2, entry.getValue());
					prep.addBatch();
				}
				prep.executeBatch();
				prep.close();
				c.commit();
			} finally {
				prep.close();
			}
		} finally {
			c.close();
		}
	}

	@Override
	public void addSpectra(ArrayList<PrecursorScan> precursors, ArrayList<FragmentScan> stripes) throws Exception {
		addPrecursor(precursors);
		addStripe(stripes);
	}

	public void saveFile() throws IOException, SQLException {
		ensureWritableTempFile();
		applySchemaPatchesToWritableFile();
		writeRanges();
		writeFractionNames();
		createIndices();
		if (userFile!=null) {
			setFileVersion();
			if (tempFile!=null&&!tempFile.toPath().equals(userFile.toPath())) {
				Files.move(tempFile.toPath(), userFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}

	/**
	 * Add the given block of precursor scans to the file using a single prepared statement and commit.
	 */
	/**
	 * Add the given block of precursor scans to the file using a single prepared statement and commit.
	 */
	public void addPrecursor(ArrayList<PrecursorScan> precursors) throws IOException, SQLException {
		spectrumReader.addPrecursor(precursors);
	}

	public Connection getConnection() throws IOException, SQLException {
		if (tempFile!=null) return getConnection(tempFile);
		return getConnection(userFile, true);
	}

	boolean hasColumn(Connection c, String table, String column) throws SQLException {
		return doesColumnExist(c, table, column);
	}

	/**
	 * Add the given block of fragment scans to the file using a single prepared statement and commit.
	 */
	public void addStripe(ArrayList<FragmentScan> stripes) throws IOException, SQLException {
		spectrumReader.addStripe(stripes);
	}

	private void createNewTables() throws IOException, SQLException {
		Connection c=getConnection();
		try {
			Statement s=c.createStatement();
			try {
				s.execute("create table if not exists metadata ( Key string not null, Value string not null, primary key (Key) )");
				s.execute(
						"create table if not exists ranges ( Start float not null, Stop float not null, DutyCycle float not null, NumWindows int, IonMobilityStart float, IonMobilityStop float, RtStart float, RtStop float )");
				s.execute(
						"create table if not exists spectra ( Fraction int not null, SpectrumName string not null, PrecursorName string, SpectrumIndex int not null, ScanStartTime float not null, IonInjectionTime float, IsolationWindowLower float not null, IsolationWindowTarget float not null, IsolationWindowUpper float not null, PrecursorCharge int not null, MassEncodedLength int not null, MassArray blob not null, IntensityEncodedLength int not null, IntensityArray blob not null, IonMobilityArrayEncodedLength int, IonMobilityArray blob, TIC float, primary key (SpectrumIndex) )");
				s.execute(
						"create table if not exists precursor ( Fraction int not null, SpectrumName string not null, SpectrumIndex int not null, ScanStartTime float not null, IonInjectionTime float, IsolationWindowLower float not null, IsolationWindowUpper float not null, MassEncodedLength int not null, MassArray blob not null, IntensityEncodedLength int not null, IntensityArray blob not null, IonMobilityArrayEncodedLength int, IonMobilityArray blob, TIC float, primary key (SpectrumIndex) )");
				s.execute("create table if not exists fractions ( Fraction int not null, Name string not null, primary key (Fraction) )");

				c.commit();
			} finally {
				s.close();
			}
		} finally {
			c.close();
		}
		setFileVersion();
	}

	public void createIndices() throws IOException, SQLException {
		Connection c=getConnection();
		try {
			Statement s=c.createStatement();
			try {
				s.execute("create index if not exists \"spectra_index_isolation_window_lower\" on \"spectra\" (\"IsolationWindowLower\" ASC)");
				s.execute("create index if not exists \"spectra_index_isolation_window_upper\" on \"spectra\" (\"IsolationWindowUpper\" ASC)");
				s.execute(
						"create index if not exists \"spectra_index_scan_start_time_and_windows\" on \"spectra\" (\"ScanStartTime\",\"IsolationWindowLower\",\"IsolationWindowUpper\" ASC)");

				s.execute("create index if not exists \"precursor_index_isolation_window_lower\" on \"precursor\" (\"IsolationWindowLower\" ASC)");
				s.execute("create index if not exists \"precursor_index_isolation_window_upper\" on \"precursor\" (\"IsolationWindowUpper\" ASC)");
				s.execute("create index if not exists \"precursor_index_scan_start_time\" on \"precursor\" (\"ScanStartTime\" ASC)");
				c.commit();
			} finally {
				s.close();
			}
		} finally {
			c.close();
		}
	}

	public boolean needsSpectraTicUpgrade() throws IOException, SQLException {
		return needsSchemaUpgrade();
	}

	public boolean needsSchemaUpgrade() throws IOException, SQLException {
		if (userFile==null||!userFile.exists()) return false;
		Connection c=getConnection();
		try {
			Version currentVersion=readFileVersion(c);
			if (currentVersion!=null&&currentVersion.amIAbove(MOST_RECENT_VERSION)) return false;
			if (currentVersion==null||!MOST_RECENT_VERSION.equals(currentVersion)) return true;
			return isCurrentSchemaMissing(c);
		} finally {
			c.close();
		}
	}

	public void upgradeSchemaToV080() throws Exception {
		if (userFile==null||!userFile.exists()) return;
		Logger.logLine("Starting DIA schema upgrade to 0.8.0 for file: "+userFile.getAbsolutePath());

		Connection c=getConnection(userFile, false);
		try {
			Version currentVersion=getFileVersion(c);
			int spectraCount=applySchemaPatches(c, currentVersion);
			c.commit();
			Logger.logLine("Completed DIA schema upgrade to 0.8.0 for file: "+userFile.getAbsolutePath()+" (backfilled TIC for "+spectraCount+" spectra).");
		} finally {
			c.close();
		}
	}

	private void applySchemaPatchesToWritableFile() throws IOException, SQLException {
		if (tempFile==null) return;
		Connection c=getConnection(tempFile, false);
		try {
			Version currentVersion=getFileVersion(c);
			applySchemaPatches(c, currentVersion);
			c.commit();
		} finally {
			c.close();
		}
	}

	private Version getFileVersion(Connection c) throws SQLException, IOException {
		try (Statement s=c.createStatement()) {
			s.execute("create table if not exists metadata ( Key string not null, Value string not null, primary key (Key) )");
		}
		return readFileVersion(c);
	}

	private Version readFileVersion(Connection c) throws SQLException, IOException {
		if (!doesTableExist(c, "metadata")) return null;
		try (PreparedStatement prep=c.prepareStatement("select Value from metadata where Key=?")) {
			prep.setString(1, VERSION_STRING);
			try (ResultSet rs=prep.executeQuery()) {
				if (rs.next()) {
					try {
						return new Version(rs.getString(1));
					} catch (RuntimeException e) {
						Logger.errorLine("Unexpected DIA file version "+rs.getString(1)+", applying schema patches by inspection.");
					}
				}
			}
		}
		return null;
	}

	private boolean isCurrentSchemaMissing(Connection c) throws SQLException, IOException {
		if (doesTableExist(c, "ranges")) {
			if (!doesColumnExist(c, "ranges", "NumWindows")) return true;
			if (!doesColumnExist(c, "ranges", "IonMobilityStart")) return true;
			if (!doesColumnExist(c, "ranges", "IonMobilityStop")) return true;
			if (!doesColumnExist(c, "ranges", "RtStart")) return true;
			if (!doesColumnExist(c, "ranges", "RtStop")) return true;
		}
		if (doesTableExist(c, "spectra")) {
			if (!doesColumnExist(c, "spectra", "Fraction")) return true;
			if (!doesColumnExist(c, "spectra", "IsolationWindowTarget")) return true;
			if (!doesColumnExist(c, "spectra", "PrecursorCharge")) return true;
			if (!doesColumnExist(c, "spectra", "IonInjectionTime")) return true;
			if (!doesColumnExist(c, "spectra", "IonMobilityArrayEncodedLength")) return true;
			if (!doesColumnExist(c, "spectra", "IonMobilityArray")) return true;
			if (!doesColumnExist(c, "spectra", "TIC")) return true;
		}
		if (doesTableExist(c, "precursor")) {
			if (!doesColumnExist(c, "precursor", "Fraction")) return true;
			if (!doesColumnExist(c, "precursor", "IsolationWindowLower")) return true;
			if (!doesColumnExist(c, "precursor", "IsolationWindowUpper")) return true;
			if (!doesColumnExist(c, "precursor", "TIC")) return true;
			if (!doesColumnExist(c, "precursor", "IonInjectionTime")) return true;
			if (!doesColumnExist(c, "precursor", "IonMobilityArrayEncodedLength")) return true;
			if (!doesColumnExist(c, "precursor", "IonMobilityArray")) return true;
		}
		return !doesTableExist(c, "fractions");
	}

	private int applySchemaPatches(Connection c, Version currentVersion) throws SQLException, IOException {
		if (currentVersion!=null&&currentVersion.amIAbove(MOST_RECENT_VERSION)) {
			Logger.errorLine("WARNING: Dia file "+this.getOriginalFileName()+" is from a more recent version of EncyclopeDIA. "
					+"Attempting to open, but this may cause unpredictable results.");
			return 0;
		}

		int spectraTicBackfilled=0;
		try (Statement s=c.createStatement()) {
			s.execute("create table if not exists metadata ( Key string not null, Value string not null, primary key (Key) )");

			if (doesTableExist(c, "ranges")) {
				addColumnIfMissing(c, s, "ranges", "NumWindows", "alter table ranges add column NumWindows int");
				addColumnIfMissing(c, s, "ranges", "IonMobilityStart", "alter table ranges add column IonMobilityStart float");
				addColumnIfMissing(c, s, "ranges", "IonMobilityStop", "alter table ranges add column IonMobilityStop float");
				addColumnIfMissing(c, s, "ranges", "RtStart", "alter table ranges add column RtStart float");
				addColumnIfMissing(c, s, "ranges", "RtStop", "alter table ranges add column RtStop float");
			}

		if (doesTableExist(c, "spectra")) {
			boolean addedFraction=addColumnIfMissing(c, s, "spectra", "Fraction", "alter table spectra add column Fraction int");
			boolean addedIsolationTarget=addColumnIfMissing(c, s, "spectra", "IsolationWindowTarget",
					"alter table spectra add column IsolationWindowTarget float");
			boolean addedCharge=addColumnIfMissing(c, s, "spectra", "PrecursorCharge", "alter table spectra add column PrecursorCharge int");
			addColumnIfMissing(c, s, "spectra", "IonInjectionTime", "alter table spectra add column IonInjectionTime float");
				addColumnIfMissing(c, s, "spectra", "IonMobilityArrayEncodedLength",
						"alter table spectra add column IonMobilityArrayEncodedLength int");
				addColumnIfMissing(c, s, "spectra", "IonMobilityArray", "alter table spectra add column IonMobilityArray blob");
			boolean addedTic=addColumnIfMissing(c, s, "spectra", "TIC", "alter table spectra add column TIC float");
			if (addedFraction) s.execute("update spectra set Fraction=0 where Fraction is null");
			if (addedIsolationTarget) {
				s.execute("update spectra set IsolationWindowTarget=(IsolationWindowLower+IsolationWindowUpper)/2.0 where IsolationWindowTarget is null");
			}
			if (addedCharge) s.execute("update spectra set PrecursorCharge=0 where PrecursorCharge is null");
				if (addedTic) spectraTicBackfilled=backfillSpectraTic(c);
			}

			if (doesTableExist(c, "precursor")) {
				boolean addedFraction=addColumnIfMissing(c, s, "precursor", "Fraction", "alter table precursor add column Fraction int");
				boolean addedIsolationLower=addColumnIfMissing(c, s, "precursor", "IsolationWindowLower",
						"alter table precursor add column IsolationWindowLower float");
				boolean addedIsolationUpper=addColumnIfMissing(c, s, "precursor", "IsolationWindowUpper",
						"alter table precursor add column IsolationWindowUpper float");
				addColumnIfMissing(c, s, "precursor", "TIC", "alter table precursor add column TIC float");
				addColumnIfMissing(c, s, "precursor", "IonInjectionTime", "alter table precursor add column IonInjectionTime float");
				addColumnIfMissing(c, s, "precursor", "IonMobilityArrayEncodedLength",
						"alter table precursor add column IonMobilityArrayEncodedLength int");
				addColumnIfMissing(c, s, "precursor", "IonMobilityArray", "alter table precursor add column IonMobilityArray blob");
				if (addedFraction) s.execute("update precursor set Fraction=0 where Fraction is null");
				if (addedIsolationLower) s.execute("update precursor set IsolationWindowLower=0 where IsolationWindowLower is null");
				if (addedIsolationUpper) s.execute("update precursor set IsolationWindowUpper=999999999 where IsolationWindowUpper is null");
			}

			s.execute("create table if not exists fractions ( Fraction int not null, Name string not null, primary key (Fraction) )");
		}
		setFileVersion(c);
		return spectraTicBackfilled;
	}

	private boolean addColumnIfMissing(Connection c, Statement s, String table, String column, String sql) throws SQLException {
		if (doesColumnExist(c, table, column)) return false;
		s.execute(sql);
		return true;
	}

	private int backfillSpectraTic(Connection c) throws SQLException, IOException {
		int spectraCount=0;
		try (Statement s=c.createStatement();
				ResultSet rs=s.executeQuery("SELECT SpectrumIndex, IntensityEncodedLength, IntensityArray FROM spectra");
				PreparedStatement update=c.prepareStatement("UPDATE spectra SET TIC=? WHERE SpectrumIndex=?")) {
			while (rs.next()) {
				int spectrumIndex=rs.getInt(1);
				int encodedLength=rs.getInt(2);
				byte[] intensityBytes=rs.getBytes(3);

				float tic=Float.NaN;
				if (intensityBytes!=null&&encodedLength>0) {
					float[] intensities;
					try {
						intensities=ByteConverter.toFloatArray(CompressionUtils.decompress(intensityBytes, encodedLength));
					} catch (DataFormatException e) {
						throw new IOException("Error decompressing spectra intensity array while upgrading DIA schema", e);
					}
					tic=MatrixMath.sum(intensities);
				}

				update.setFloat(1, tic);
				update.setInt(2, spectrumIndex);
				update.addBatch();
				spectraCount++;
			}
			update.executeBatch();
		}
		return spectraCount;
	}

	@Override
	public ArrayList<ScanSummary> getScanSummaries(float minRT, float maxRT) throws IOException, SQLException {
		return spectrumReader.getScanSummaries(minRT, maxRT);
	}

	@Override
	public AcquiredSpectrum getSpectrum(ScanSummary summary) throws IOException, SQLException, DataFormatException {
		return spectrumReader.getSpectrum(summary);
	}

	@Override
	public void close() {
		if (tempFile!=null&&tempFile.exists()&&!tempFile.delete()) {
			Logger.errorLine("Error deleting temp DIA file!");
		}
	}

	private void ensureWritableTempFile() throws IOException, SQLException {
		if (tempFile!=null) return;
		if (userFile==null) {
			throw new IOException("No source file available for save.");
		}
		tempFile=File.createTempFile("encyclopedia_", DIA_EXTENSION);
		Files.copy(userFile.toPath(), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		tempFile.deleteOnExit();
		readOnly=false;
	}
}
