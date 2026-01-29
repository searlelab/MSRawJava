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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.DataFormatException;

import org.searlelab.msrawjava.Version;
import org.searlelab.msrawjava.algorithms.MatrixMath;
import org.searlelab.msrawjava.io.OutputSpectrumFile;
import org.searlelab.msrawjava.io.StripeFileInterface;
import org.searlelab.msrawjava.io.utils.Pair;
import org.searlelab.msrawjava.logging.Logger;
import org.searlelab.msrawjava.model.AcquiredSpectrum;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.ScanSummary;
import org.searlelab.msrawjava.model.WindowData;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

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
	private static final Version MOST_RECENT_VERSION=new Version(0, 7, 0, false);

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

	private final TIntObjectHashMap<String> fractionNames=new TIntObjectHashMap<String>();

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
	}

	public void setFractionNames(TIntObjectHashMap<String> fractionNames) {
		this.fractionNames.clear();
		this.fractionNames.putAll(fractionNames);
	}

	public void writeRanges() throws IOException, SQLException {
		Connection c=getConnection();
		try {
			PreparedStatement prep=c
					.prepareStatement("insert into ranges (Start, Stop, DutyCycle, NumWindows, IonMobilityStart, IonMobilityStop, RtStart, RtStop) VALUES (?,?,?,?,?,?,?,?)");
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

		getMetadata();
		loadRanges();
		loadFractionNames();
	}

	public void loadRanges() throws IOException, SQLException {

		ranges.clear();
		Connection c=getConnection();
		try {
			Statement s=c.createStatement();
			try {
				boolean hasRtStart=doesColumnExist(c, "ranges", "RtStart");
				boolean hasRtStop=doesColumnExist(c, "ranges", "RtStop");
				String sql=hasRtStart&&hasRtStop
						?"select Start, Stop, DutyCycle, NumWindows, IonMobilityStart, IonMobilityStop, RtStart, RtStop from Ranges"
						:"select Start, Stop, DutyCycle, NumWindows, IonMobilityStart, IonMobilityStop from Ranges";
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

					Float rtStart=null;
					Float rtStop=null;
					if (hasRtStart&&hasRtStop) {
						rtStart=rs.getFloat(7);
						if (rs.wasNull()) rtStart=null;
						rtStop=rs.getFloat(8);
						if (rs.wasNull()) rtStop=null;
					}
					Optional<Range> rtRange=(rtStart==null||rtStop==null)?Optional.empty():Optional.of(new Range(rtStart, rtStop));

					ranges.put(new Range(start, stop), new WindowData(dutyCycle, numWindows, range, rtRange));
				}
			} finally {
				s.close();
			}
		} finally {
			c.close();
		}
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
			} finally {
				s.close();
			}
		} finally {
			c.close();
		}
	}

	@Override
	public Map<Range, WindowData> getRanges() {
		return ranges;
	}

	@Override
	public ArrayList<PrecursorScan> getPrecursors(float minRT, float maxRT) throws IOException, SQLException, DataFormatException {
		Connection c=getConnection();
		try {
			Statement s=c.createStatement();
			try {
				ResultSet rs=s.executeQuery(
						"select SpectrumName, SpectrumIndex, ScanStartTime, IonInjectionTime, MassEncodedLength, MassArray, IntensityEncodedLength, IntensityArray, IonMobilityArrayEncodedLength, IonMobilityArray, TIC, fraction, isolationWindowLower, isolationWindowUpper from precursor "
								+"where ScanStartTime between "+minRT+" and "+maxRT);

				ArrayList<PrecursorScan> precursors=new ArrayList<PrecursorScan>();
				while (rs.next()) {
					String spectrumName=rs.getString(1);
					int spectrumIndex=rs.getInt(2);
					float scanStartTime=rs.getFloat(3);
					Float ionInjectionTime=rs.getFloat(4);
					if (rs.wasNull()) {
						ionInjectionTime=null;
					}
					int massEncodedLength=rs.getInt(5);
					double[] massArray=ByteConverter.toDoubleArray(CompressionUtils.decompress(rs.getBytes(6), massEncodedLength));
					int intensityEncodedLength=rs.getInt(7);
					float[] intensityArray=ByteConverter.toFloatArray(CompressionUtils.decompress(rs.getBytes(8), intensityEncodedLength));
					Integer ionMobilityEncodedLength=rs.getInt(9);
					float[] ionMobilityArray=null;
					if (!rs.wasNull()) {
						ionMobilityArray=ByteConverter.toFloatArray(CompressionUtils.decompress(rs.getBytes(10), ionMobilityEncodedLength));
					}
					int fraction=rs.getInt(12);
					double isolationWindowLower=rs.getDouble(13);
					double isolationWindowUpper=rs.getDouble(14);

					precursors.add(new PrecursorScan(spectrumName, spectrumIndex, scanStartTime, fraction, isolationWindowLower, isolationWindowUpper,
							ionInjectionTime, massArray, intensityArray, ionMobilityArray));
				}

				return precursors;
			} finally {
				s.close();
			}
		} finally {
			c.close();
		}
	}

	@Override
	public ArrayList<FragmentScan> getStripes(double targetMz, float minRT, float maxRT, boolean sqrt) throws IOException, SQLException {
		Connection c=getConnection();
		try {
			Statement s=c.createStatement();
			try {
				ResultSet rs=s.executeQuery(
						"select SpectrumName, PrecursorName, SpectrumIndex, ScanStartTime, IsolationWindowLower, IsolationWindowUpper, PrecursorCharge, MassEncodedLength, MassArray, IntensityEncodedLength, IntensityArray, IonMobilityArrayEncodedLength, IonMobilityArray, IonInjectionTime, Fraction from spectra "
								+"where IsolationWindowLower <= "+targetMz+" and IsolationWindowUpper >= "+targetMz+" and ScanStartTime between "+minRT+" and "
								+maxRT+" order by ScanStartTime asc");

				final Vector<FragmentScan> stripes=new Vector<FragmentScan>();

				int cores=Runtime.getRuntime().availableProcessors();
				ThreadFactory threadFactory=new ThreadFactoryBuilder().setNameFormat("STRIPE_"+targetMz+"-%d").setDaemon(true).build();
				LinkedBlockingQueue<Runnable> workQueue=new LinkedBlockingQueue<Runnable>();
				ExecutorService executor=new ThreadPoolExecutor(cores, cores, Long.MAX_VALUE, TimeUnit.NANOSECONDS, workQueue, threadFactory);

				while (rs.next()) {
					final String spectrumName=rs.getString(1);
					final String precursorName=rs.getString(2);
					final int spectrumIndex=rs.getInt(3);
					final float scanStartTime=rs.getFloat(4);
					final double isolationWindowLower=rs.getDouble(5);
					final double isolationWindowUpper=rs.getDouble(6);
					final int precursorCharge=rs.getInt(7);
					final int massEncodedLength=rs.getInt(8);
					final byte[] massBytes=rs.getBytes(9);
					final int intensityEncodedLength=rs.getInt(10);
					final byte[] intensityBytes=rs.getBytes(11);
					Integer ionMobilityEncodedLength=rs.getInt(12);
					final byte[] ionMobilityBytes;
					if (rs.wasNull()) {
						ionMobilityBytes=null;
					} else {
						ionMobilityBytes=rs.getBytes(13);
					}
					Float nullableIonInjectionTime=rs.getFloat(14);
					if (rs.wasNull()) {
						nullableIonInjectionTime=null;
					}
					final Float ionInjectionTime=nullableIonInjectionTime;
					final int fraction=rs.getInt(15);
					executor.submit(new Runnable() {
						@Override
						public void run() {
							try {
								stripes.add(getStripe(sqrt, spectrumName, precursorName, spectrumIndex, scanStartTime, fraction, ionInjectionTime,
										isolationWindowLower, isolationWindowUpper, precursorCharge, massEncodedLength, massBytes, intensityEncodedLength,
										intensityBytes, ionMobilityEncodedLength, ionMobilityBytes));
							} catch (DataFormatException dfe) {
								throw new RuntimeException(dfe);
							} catch (IOException ioe) {
								throw new RuntimeException(ioe);
							}
						}
					});
				}

				executor.shutdown();
				try {
					executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
				} catch (InterruptedException ie) {
					throw new RuntimeException(ie);
				} finally {
					executor.shutdownNow();
				}

				ArrayList<FragmentScan> arrayList=new ArrayList<FragmentScan>(stripes);
				Collections.sort(arrayList);
				return arrayList;
			} finally {
				s.close();
			}
		} finally {
			c.close();
		}
	}

	private FragmentScan getStripe(boolean sqrt, String spectrumName, String precursorName, int spectrumIndex, Float scanStartTime, int fraction,
			Float ionInjectionTime, double isolationWindowLower, double isolationWindowUpper, int precursorCharge, int massEncodedLength, byte[] massBytes,
			int intensityEncodedLength, byte[] intensityBytes, Integer nullableIonMobilityEncodedLength, byte[] ionMobilityArrayBytes)
			throws IOException, DataFormatException {
		double[] massArray=ByteConverter.toDoubleArray(CompressionUtils.decompress(massBytes, massEncodedLength));
		float[] intensityArray=ByteConverter.toFloatArray(CompressionUtils.decompress(intensityBytes, intensityEncodedLength));
		if (sqrt) {
			for (int i=0; i<intensityArray.length; i++) {
				intensityArray[i]=(float)Math.sqrt(intensityArray[i]);
			}
		}
		float[] ionMobilityArray=null;
		if (nullableIonMobilityEncodedLength!=null&&nullableIonMobilityEncodedLength>0) {
			ionMobilityArray=ByteConverter.toFloatArray(CompressionUtils.decompress(ionMobilityArrayBytes, nullableIonMobilityEncodedLength));
		}
		return new FragmentScan(spectrumName, precursorName, spectrumIndex, (isolationWindowUpper+isolationWindowLower)/2.0, scanStartTime, fraction,
				ionInjectionTime, isolationWindowLower, isolationWindowUpper, massArray, intensityArray, ionMobilityArray, (byte)precursorCharge, 0.0,
				MatrixMath.max(massArray));
	}

	@Override
	public ArrayList<FragmentScan> getStripes(Range targetMzRange, float minRT, float maxRT, boolean sqrt) throws IOException, SQLException {
		Connection c=getConnection();
		try {
			Statement s=c.createStatement();
			try {
				ResultSet rs=s.executeQuery(
						"select SpectrumName, PrecursorName, SpectrumIndex, ScanStartTime, IsolationWindowLower, IsolationWindowUpper, PrecursorCharge, MassEncodedLength, MassArray, IntensityEncodedLength, IntensityArray, IonMobilityArrayEncodedLength, IonMobilityArray, IonInjectionTime, Fraction from spectra "
								+"where  IsolationWindowLower <= "+targetMzRange.getStop()+" and IsolationWindowUpper >= "+targetMzRange.getStart()
								+" and ScanStartTime between "+minRT+" and "+maxRT+" order by ScanStartTime asc");

				final Vector<FragmentScan> stripes=new Vector<FragmentScan>();

				int cores=Runtime.getRuntime().availableProcessors();
				ThreadFactory threadFactory=new ThreadFactoryBuilder().setNameFormat("STRIPE_"+targetMzRange.getStart()+"_"+targetMzRange.getStop()+"-%d")
						.setDaemon(true).build();
				LinkedBlockingQueue<Runnable> workQueue=new LinkedBlockingQueue<Runnable>();
				ExecutorService executor=new ThreadPoolExecutor(cores, cores, Long.MAX_VALUE, TimeUnit.NANOSECONDS, workQueue, threadFactory);

				while (rs.next()) {
					final String spectrumName=rs.getString(1);
					final String precursorName=rs.getString(2);
					final int spectrumIndex=rs.getInt(3);
					final float scanStartTime=rs.getFloat(4);
					final float isolationWindowLower=rs.getFloat(5);
					final float isolationWindowUpper=rs.getFloat(6);
					final int precursorCharge=rs.getInt(7);
					final int massEncodedLength=rs.getInt(8);
					final byte[] massBytes=rs.getBytes(9);
					final int intensityEncodedLength=rs.getInt(10);
					final byte[] intensityBytes=rs.getBytes(11);
					Integer ionMobilityEncodedLength=rs.getInt(12);
					final byte[] ionMobilityBytes;
					if (rs.wasNull()) {
						ionMobilityBytes=null;
					} else {
						ionMobilityBytes=rs.getBytes(13);
					}
					Float nullableIonInjectionTime=rs.getFloat(14);
					if (rs.wasNull()) {
						nullableIonInjectionTime=null;
					}
					final Float ionInjectionTime=nullableIonInjectionTime;
					final int fraction=rs.getInt(15);

					executor.submit(new Runnable() {
						@Override
						public void run() {
							try {
								stripes.add(getStripe(sqrt, spectrumName, precursorName, spectrumIndex, scanStartTime, fraction, ionInjectionTime,
										isolationWindowLower, isolationWindowUpper, precursorCharge, massEncodedLength, massBytes, intensityEncodedLength,
										intensityBytes, ionMobilityEncodedLength, ionMobilityBytes));
							} catch (DataFormatException dfe) {
								throw new RuntimeException(dfe);
							} catch (IOException ioe) {
								throw new RuntimeException(ioe);
							}
						}
					});
				}

				executor.shutdown();
				try {
					executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
				} catch (InterruptedException ie) {
					throw new RuntimeException(ie);
				} finally {
					executor.shutdownNow();
				}
				ArrayList<FragmentScan> arrayList=new ArrayList<FragmentScan>(stripes);
				Collections.sort(arrayList);
				return arrayList;
			} finally {
				s.close();
			}
		} finally {
			c.close();
		}
	}

	@Override
	public float getTIC() throws IOException, SQLException {
		String value=getMetadata().get(TOTAL_PRECURSOR_TIC_ATTRIBUTE);
		if (value==null) {
			return MatrixMath.sum(getTICTrace().y);
		}
		return Float.parseFloat(value);
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

	@Override
	public void setFileName(String sourceName, String fileLocation) throws IOException, SQLException {
		HashMap<String, String> map=new HashMap<String, String>();
		map.put(SOURCENAME_ATTRIBUTE, sourceName==null?UNKNOWN_VALUE:sourceName);
		map.put(FILELOCATION_ATTRIBUTE, fileLocation==null?UNKNOWN_VALUE:fileLocation);
		addMetadata(map);
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

	@Override
	public void addMetadata(Map<String, String> data) throws IOException, SQLException {
		if (readOnly) {
			Logger.errorLine("Skipping metadata write in read-only mode.");
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

	/**
	 * Add the given block of precursor scans to the file using a single prepared statement and commit.
	 */
	public void addPrecursor(ArrayList<PrecursorScan> precursors) throws IOException, SQLException {
		Connection c=getConnection();
		try {
			PreparedStatement prep=c.prepareStatement(
					"insert into precursor (SpectrumName, SpectrumIndex, ScanStartTime, IonInjectionTime, MassEncodedLength, MassArray, IntensityEncodedLength, IntensityArray, IonMobilityArrayEncodedLength, IonMobilityArray, TIC, Fraction, IsolationWindowLower, IsolationWindowUpper) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
			try {
				for (AcquiredSpectrum precursor : precursors) {
					prep.setString(1, precursor.getSpectrumName());
					prep.setInt(2, precursor.getSpectrumIndex());
					prep.setFloat(3, precursor.getScanStartTime());

					if (precursor.getIonInjectionTime()!=null) {
						prep.setFloat(4, precursor.getIonInjectionTime());
					} else {
						prep.setNull(4, Types.FLOAT);
					}

					byte[] massByteArray=ByteConverter.toByteArray(precursor.getMassArray());
					prep.setInt(5, massByteArray.length);
					prep.setBytes(6, CompressionUtils.compress(massByteArray));
					byte[] intensityByteArray=ByteConverter.toByteArray(precursor.getIntensityArray());
					prep.setInt(7, intensityByteArray.length);
					prep.setBytes(8, CompressionUtils.compress(intensityByteArray));

					if (!precursor.getIonMobilityArray().isPresent()) {
						prep.setNull(9, Types.INTEGER);
						prep.setNull(10, Types.BLOB);
					} else {
						byte[] ionMobilityByteArray=ByteConverter.toByteArray(precursor.getIonMobilityArray().get());
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
		if (tempFile!=null) return getConnection(tempFile);
		return getConnection(userFile, true);
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

			if (stripe.getIonInjectionTime()!=null) {
				prep.setFloat(index++, stripe.getIonInjectionTime());
			} else {
				prep.setNull(index++, Types.FLOAT);
			}

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
			if (!stripe.getIonMobilityArray().isPresent()) {
				prep.setNull(index++, Types.INTEGER);
				prep.setNull(index++, Types.BLOB);
			} else {
				byte[] ionMobilityByteArray=ByteConverter.toByteArray(stripe.getIonMobilityArray().get());
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
						"create table if not exists ranges ( Start float not null, Stop float not null, DutyCycle float not null, NumWindows int, IonMobilityStart float, IonMobilityStop float, RtStart float, RtStop float )");
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

	@Override
	public ArrayList<ScanSummary> getScanSummaries(float minRT, float maxRT) throws IOException, SQLException {
		ArrayList<ScanSummary> out=new ArrayList<>();
		Connection c=getConnection();
		try {
			Statement s=c.createStatement();
			try {
				ResultSet rs=s.executeQuery("select SpectrumName, SpectrumIndex, ScanStartTime, IonInjectionTime, IsolationWindowLower, IsolationWindowUpper "
						+"from precursor where ScanStartTime>="+minRT+" and ScanStartTime<="+maxRT+" order by ScanStartTime");
				while (rs.next()) {
					String name=rs.getString(1);
					int index=rs.getInt(2);
					float rt=rs.getFloat(3);
					Float iit=rs.getFloat(4);
					if (rs.wasNull()) iit=null;
					double isoLo=rs.getDouble(5);
					double isoHi=rs.getDouble(6);
					out.add(new ScanSummary(name, index, rt, 0, -1.0, true, iit, isoLo, isoHi, isoLo, isoHi, (byte)0));
				}
				rs.close();

				boolean hasScanWindowLower=doesColumnExist(c, "spectra", "ScanWindowLower");
				boolean hasScanWindowUpper=doesColumnExist(c, "spectra", "ScanWindowUpper");
				boolean hasIsolationCenter=doesColumnExist(c, "spectra", "IsolationWindowCenter");

				String scanWindowSelect;
				if (hasScanWindowLower&&hasScanWindowUpper) {
					scanWindowSelect=", ScanWindowLower, ScanWindowUpper";
				} else {
					scanWindowSelect=", IsolationWindowLower as ScanWindowLower, IsolationWindowUpper as ScanWindowUpper";
				}
				String isolationCenterSelect=hasIsolationCenter?", IsolationWindowCenter":", (IsolationWindowLower+IsolationWindowUpper)/2.0 as IsolationWindowCenter";

				rs=s.executeQuery("select SpectrumName, SpectrumIndex, ScanStartTime, IonInjectionTime, IsolationWindowLower, IsolationWindowUpper"
						+isolationCenterSelect+", PrecursorCharge"+scanWindowSelect+" from spectra "+"where ScanStartTime>="+minRT
						+" and ScanStartTime<="+maxRT+" order by ScanStartTime");
				while (rs.next()) {
					String name=rs.getString(1);
					int index=rs.getInt(2);
					float rt=rs.getFloat(3);
					Float iit=rs.getFloat(4);
					if (rs.wasNull()) iit=null;
					double isoLo=rs.getDouble(5);
					double isoHi=rs.getDouble(6);
					double center=rs.getDouble(7);
					byte charge=(byte)rs.getInt(8);
					double scanLo=rs.getDouble(9);
					double scanHi=rs.getDouble(10);
					out.add(new ScanSummary(name, index, rt, 0, center, false, iit, isoLo, isoHi, scanLo, scanHi, charge));
				}
				rs.close();
			} finally {
				s.close();
			}
		} finally {
			c.close();
		}
		out.sort((a, b) -> Float.compare(a.getScanStartTime(), b.getScanStartTime()));
		return out;
	}

	@Override
	public AcquiredSpectrum getSpectrum(ScanSummary summary) throws IOException, SQLException, DataFormatException {
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
