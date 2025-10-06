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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.searlelab.msrawjava.Logger;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.WindowData;

import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.procedure.TIntObjectProcedure;

public class EncyclopeDIAFile extends SQLFile {
	public static final DateFormat m_ISO8601Local=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
	private static final Version MOST_RECENT_VERSION=new Version(0, 7, 0);

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

	private volatile String originalFileName=null;
	private File tempFile;

	private final HashMap<Range, WindowData> ranges=new HashMap<Range, WindowData>();

	private final TIntObjectHashMap<String> fractionNames=new TIntObjectHashMap<String>();

	public EncyclopeDIAFile(String originalFileName) throws IOException {
		this.originalFileName=originalFileName;
	}

	public String getOriginalFileName() {
		return originalFileName;
	}

	public void setRanges(HashMap<Range, WindowData> ranges) {
		this.ranges.clear();
		this.ranges.putAll(ranges);
	}

	public void setFractionNames(TIntObjectHashMap<String> fractionNames) {
		this.fractionNames.clear();
		this.fractionNames.putAll(fractionNames);
	}

	public void writeRanges() throws IOException, SQLException {
		Connection c=getConnection();
		try {
			PreparedStatement prep=c
					.prepareStatement("insert into ranges (Start, Stop, DutyCycle, NumWindows, IonMobilityStart, IonMobilityStop) VALUES (?,?,?,?,?,?)");
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
		tempFile=File.createTempFile("encyclopedia_", DIA_EXTENSION);
		tempFile.deleteOnExit();
		createNewTables();
	}

	public void saveAsFile(File userFile) throws IOException, SQLException {
		writeRanges();
		writeFractionNames();
		createIndices();

		if (userFile!=null) {
			setFileVersion();

			Files.copy(tempFile.toPath(), userFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
	}

	public void setFileVersion() throws IOException, SQLException {
		HashMap<String, String> map=new HashMap<String, String>();
		map.put(VERSION_STRING, MOST_RECENT_VERSION.toString());
		addMetadata(map);
	}

	public void setFileName(String fileName, String sourceName, String fileLocation) throws IOException, SQLException {
		HashMap<String, String> map=new HashMap<String, String>();
		map.put(FILENAME_ATTRIBUTE, fileName==null?UNKNOWN_VALUE:fileName);
		map.put(SOURCENAME_ATTRIBUTE, sourceName==null?UNKNOWN_VALUE:sourceName);
		map.put(FILELOCATION_ATTRIBUTE, fileLocation==null?UNKNOWN_VALUE:fileLocation);
		addMetadata(map);
	}

	public void setStartTime(Date startTime) throws IOException, SQLException {
		addMetadata(RUN_START_TIME, m_ISO8601Local.format(startTime));
	}

	public void setSoftwareVersions(final Multimap<String, String> softwareAccessionIdToVersion) throws IOException, SQLException {
		if (!softwareAccessionIdToVersion.isEmpty()) {
			Map<String, String> toAdd=Maps.newHashMap();
			softwareAccessionIdToVersion.asMap().forEach((key, value) -> {
				toAdd.put(SOFTWARE_VERSION_PREFIX+key, Joiner.on(SOFTWARE_VERSIONS_DELIMITER).join(value));
			});
			addMetadata(toAdd);
		}
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
			try {
				ResultSet rs=s.executeQuery("select Key, Value from metadata");

				HashMap<String, String> map=new HashMap<String, String>();
				while (rs.next()) {
					String key=rs.getString(1);
					String value=rs.getString(2);
					map.put(key, value);
				}

				return map;
			} finally {
				s.close();
			}
		} finally {
			c.close();
		}
	}

	public void addMetadata(Map<String, String> data) throws IOException, SQLException {
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

	/**
	 * Add the given block of precursor scans to the file using a single prepared statement and commit.
	 */
	public void addPrecursor(ArrayList<PrecursorScan> precursors) throws IOException, SQLException {
		Connection c=getConnection();
		try {
			PreparedStatement prep=c.prepareStatement(
					"insert into precursor (SpectrumName, SpectrumIndex, ScanStartTime, IonInjectionTime, MassEncodedLength, MassArray, IntensityEncodedLength, IntensityArray, IonMobilityArrayEncodedLength, IonMobilityArray, TIC, Fraction, IsolationWindowLower, IsolationWindowUpper) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
			try {
				for (PrecursorScan precursor : precursors) {
					prep.setString(1, precursor.getSpectrumName());
					prep.setInt(2, precursor.getSpectrumIndex());
					prep.setFloat(3, precursor.getScanStartTime());
					prep.setFloat(4, precursor.getIonInjectionTime());
					byte[] massByteArray=ByteConverter.toByteArray(precursor.getMassArray());
					prep.setInt(5, massByteArray.length);
					prep.setBytes(6, CompressionUtils.compress(massByteArray));
					byte[] intensityByteArray=ByteConverter.toByteArray(precursor.getIntensityArray());
					prep.setInt(7, intensityByteArray.length);
					prep.setBytes(8, CompressionUtils.compress(intensityByteArray));

					if (precursor.getIonMobilityArray()==null) {
						prep.setNull(9, Types.INTEGER);
						prep.setNull(10, Types.BLOB);
					} else {
						byte[] ionMobilityByteArray=ByteConverter.toByteArray(precursor.getIonMobilityArray());
						prep.setInt(9, ionMobilityByteArray.length);
						prep.setBytes(10, CompressionUtils.compress(ionMobilityByteArray));
					}
					prep.setFloat(11, precursor.getTIC());
					prep.setInt(12, precursor.getFraction());
					prep.setDouble(13, precursor.getIsolationWindowLower());
					prep.setDouble(14, precursor.getIsolationWindowUpper());
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

	public Connection getConnection() throws IOException, SQLException {
		return getConnection(tempFile);
	}

	/**
	 * Add the given block of fragment scans to the file using a single prepared statement and commit.
	 */
	public void addStripe(ArrayList<FragmentScan> stripes) throws IOException, SQLException {
		try (Connection c=getConnection()) {
			try (PreparedStatement prep=c.prepareStatement(
					"insert into spectra (SpectrumName, PrecursorName, SpectrumIndex, ScanStartTime, Fraction, IonInjectionTime, IsolationWindowLower, IsolationWindowCenter, IsolationWindowUpper, PrecursorCharge, MassEncodedLength, MassArray, IntensityEncodedLength, IntensityArray, IonMobilityArrayEncodedLength, IonMobilityArray)"
							+" VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
				// handle commits manually
				c.setAutoCommit(false);

				internalAddStripeToStatement(stripes, prep);

				c.commit();
			}
		}
	}

	private void internalAddStripeToStatement(List<FragmentScan> stripes, PreparedStatement prep) throws SQLException, IOException {
		for (FragmentScan stripe : stripes) {
			int index=1;
			prep.setString(index++, stripe.getSpectrumName());
			prep.setString(index++, stripe.getPrecursorName());
			prep.setInt(index++, stripe.getSpectrumIndex());
			prep.setFloat(index++, stripe.getScanStartTime());
			prep.setInt(index++, stripe.getFraction());
			prep.setFloat(index++, stripe.getIonInjectionTime());
			prep.setDouble(index++, stripe.getIsolationWindowLower());
			prep.setDouble(index++, (stripe.getIsolationWindowLower()+stripe.getIsolationWindowUpper())/2.0);
			prep.setDouble(index++, stripe.getIsolationWindowUpper());
			prep.setInt(index++, stripe.getCharge());
			byte[] massByteArray=ByteConverter.toByteArray(stripe.getMassArray());
			prep.setInt(index++, massByteArray.length);
			prep.setBytes(index++, CompressionUtils.compress(massByteArray));
			byte[] intensityByteArray=ByteConverter.toByteArray(stripe.getIntensityArray());
			prep.setInt(index++, intensityByteArray.length);
			prep.setBytes(index++, CompressionUtils.compress(intensityByteArray));
			if (stripe.getIonMobilityArray()==null) {
				prep.setNull(index++, Types.INTEGER);
				prep.setNull(index++, Types.BLOB);
			} else {
				byte[] ionMobilityByteArray=ByteConverter.toByteArray(stripe.getIonMobilityArray());
				prep.setInt(index++, ionMobilityByteArray.length);
				prep.setBytes(index++, CompressionUtils.compress(ionMobilityByteArray));
			}
			prep.addBatch();
		}
		prep.executeBatch();
	}

	private void createNewTables() throws IOException, SQLException {
		Connection c=getConnection();
		try {
			Statement s=c.createStatement();
			try {
				s.execute("create table if not exists metadata ( Key string not null, Value string not null, primary key (Key) )");
				s.execute(
						"create table if not exists ranges ( Start float not null, Stop float not null, DutyCycle float not null, NumWindows int, IonMobilityStart float, IonMobilityStop float )");
				s.execute(
						"create table if not exists spectra ( Fraction int not null, SpectrumName string not null, PrecursorName string, SpectrumIndex int not null, ScanStartTime float not null, IonInjectionTime float, IsolationWindowLower float not null, IsolationWindowCenter float not null, IsolationWindowUpper float not null, PrecursorCharge int not null, MassEncodedLength int not null, MassArray blob not null, IntensityEncodedLength int not null, IntensityArray blob not null, IonMobilityArrayEncodedLength int, IonMobilityArray blob, primary key (SpectrumIndex) )");
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

	public void dropIndices() throws IOException, SQLException {
		Connection c=getConnection();
		try {
			Statement s=c.createStatement();
			try {
				s.execute("drop index if exists 'spectra_index_isolation_window_lower'");
				s.execute("drop index if exists 'spectra_index_isolation_window_upper'");
				s.execute("drop index if exists 'spectra_index_scan_start_time_and_windows'");

				s.execute("drop index if exists 'precursor_index_isolation_window_lower'");
				s.execute("drop index if exists 'precursor_index_isolation_window_upper'");
				s.execute("drop index if exists 'precursor_index_scan_start_time'");

				c.commit();
			} finally {
				s.close();
			}
		} finally {
			c.close();
		}
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

	public void close() {
		if (tempFile.exists()&&!tempFile.delete()) {
			Logger.errorLine("Error deleting temp DIA file!");
		}
	}
}