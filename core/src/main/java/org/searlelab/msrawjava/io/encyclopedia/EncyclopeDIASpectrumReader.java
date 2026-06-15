package org.searlelab.msrawjava.io.encyclopedia;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.DataFormatException;

import org.searlelab.msrawjava.algorithms.MatrixMath;
import org.searlelab.msrawjava.io.utils.RawFileStructureTools;
import org.searlelab.msrawjava.logging.Logger;
import org.searlelab.msrawjava.model.AcquiredSpectrum;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.ScanSummary;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

class EncyclopeDIASpectrumReader {
	private static final int MAX_SQLITE_CORRUPT_READ_ATTEMPTS=2;
	private static volatile ReadFailureHook readFailureHook;

	private final EncyclopeDIAFile owner;

	EncyclopeDIASpectrumReader(EncyclopeDIAFile owner) {
		this.owner=owner;
	}

	ArrayList<PrecursorScan> getPrecursors(float minRT, float maxRT) throws IOException, SQLException, DataFormatException {
		ReadContext context=ReadContext.precursors(minRT, maxRT);
		return readWithRetry(context, new SqlReadOperation<ArrayList<PrecursorScan>>() {
			@Override
			public ArrayList<PrecursorScan> run() throws IOException, SQLException, DataFormatException {
				return getPrecursorsOnce(minRT, maxRT);
			}
		});
	}

	private ArrayList<PrecursorScan> getPrecursorsOnce(float minRT, float maxRT) throws IOException, SQLException, DataFormatException {
		Connection c=owner.getConnection();
		try {
			Statement s=c.createStatement();
			try {
				boolean hasIonInjectionTime=owner.hasColumn(c, "precursor", "IonInjectionTime");
				boolean hasIonMobilityArrayEncodedLength=owner.hasColumn(c, "precursor", "IonMobilityArrayEncodedLength");
				boolean hasIonMobilityArray=owner.hasColumn(c, "precursor", "IonMobilityArray");
				boolean hasTic=owner.hasColumn(c, "precursor", "TIC");
				boolean hasFraction=owner.hasColumn(c, "precursor", "Fraction");
				boolean hasIsolationWindowLower=owner.hasColumn(c, "precursor", "IsolationWindowLower");
				boolean hasIsolationWindowUpper=owner.hasColumn(c, "precursor", "IsolationWindowUpper");

				String ionInjectionTimeSelect=hasIonInjectionTime?"IonInjectionTime":"NULL as IonInjectionTime";
				String ionMobilitySelect=hasIonMobilityArrayEncodedLength&&hasIonMobilityArray?"IonMobilityArrayEncodedLength, IonMobilityArray"
						:"NULL as IonMobilityArrayEncodedLength, NULL as IonMobilityArray";
				String ticSelect=hasTic?"TIC":"0.0 as TIC";
				String fractionSelect=hasFraction?"Fraction":"0 as Fraction";
				String isolationWindowLowerSelect=hasIsolationWindowLower?"IsolationWindowLower":"0.0 as IsolationWindowLower";
				String isolationWindowUpperSelect=hasIsolationWindowUpper?"IsolationWindowUpper":"999999999.0 as IsolationWindowUpper";

				String sql="select SpectrumName, SpectrumIndex, ScanStartTime, "+ionInjectionTimeSelect
						+", MassEncodedLength, MassArray, IntensityEncodedLength, IntensityArray, "+ionMobilitySelect+", "+ticSelect+", "
						+fractionSelect+", "+isolationWindowLowerSelect+", "+isolationWindowUpperSelect+" from precursor where ScanStartTime between "
						+minRT+" and "+maxRT;
				ResultSet rs=s.executeQuery(sql);

				ArrayList<PrecursorScan> precursors=new ArrayList<PrecursorScan>();
				while (rs.next()) {
					String spectrumName=rs.getString(1);
					int spectrumIndex=rs.getInt(2);
					float scanStartTime=rs.getFloat(3);
					float ionInjectionTime=rs.getFloat(4);
					if (rs.wasNull()) {
						ionInjectionTime=-1f;
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

	ArrayList<FragmentScan> getStripes(double targetMz, float minRT, float maxRT, boolean sqrt) throws IOException, SQLException {
		ReadContext context=ReadContext.stripesTarget(targetMz, minRT, maxRT);
		try {
			return readWithRetry(context, new SqlReadOperation<ArrayList<FragmentScan>>() {
				@Override
				public ArrayList<FragmentScan> run() throws IOException, SQLException {
					return getStripesOnce(targetMz, minRT, maxRT, sqrt);
				}
			});
		} catch (DataFormatException e) {
			throw new IOException("Unexpected fragment stripe decompression failure", e);
		}
	}

	private ArrayList<FragmentScan> getStripesOnce(double targetMz, float minRT, float maxRT, boolean sqrt) throws IOException, SQLException {
		Connection c=owner.getConnection();
		try {
			Statement s=c.createStatement();
			try {
				boolean hasPrecursorCharge=owner.hasColumn(c, "spectra", "PrecursorCharge");
				boolean hasIonMobilityArrayEncodedLength=owner.hasColumn(c, "spectra", "IonMobilityArrayEncodedLength");
				boolean hasIonMobilityArray=owner.hasColumn(c, "spectra", "IonMobilityArray");
				boolean hasIonInjectionTime=owner.hasColumn(c, "spectra", "IonInjectionTime");
				boolean hasFraction=owner.hasColumn(c, "spectra", "Fraction");

				String precursorChargeSelect=hasPrecursorCharge?"PrecursorCharge":"0 as PrecursorCharge";
				String ionMobilitySelect=hasIonMobilityArrayEncodedLength&&hasIonMobilityArray?"IonMobilityArrayEncodedLength, IonMobilityArray"
						:"NULL as IonMobilityArrayEncodedLength, NULL as IonMobilityArray";
				String ionInjectionTimeSelect=hasIonInjectionTime?"IonInjectionTime":"NULL as IonInjectionTime";
				String fractionSelect=hasFraction?"Fraction":"0 as Fraction";
				String isolationTargetSelect=getIsolationWindowTargetSelect(c);

				String sql="select SpectrumName, PrecursorName, SpectrumIndex, ScanStartTime, IsolationWindowLower, "+isolationTargetSelect
						+", IsolationWindowUpper, "+precursorChargeSelect+", MassEncodedLength, MassArray, IntensityEncodedLength, IntensityArray, "+ionMobilitySelect+", "
						+ionInjectionTimeSelect+", "+fractionSelect+" from spectra where IsolationWindowLower <= "+targetMz
						+" and IsolationWindowUpper >= "+targetMz+" and ScanStartTime between "+minRT+" and "+maxRT+" order by ScanStartTime asc";
				ResultSet rs=s.executeQuery(sql);

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
					final double isolationWindowTarget=rs.getDouble(6);
					final double isolationWindowUpper=rs.getDouble(7);
					final int precursorCharge=rs.getInt(8);
					final int massEncodedLength=rs.getInt(9);
					final byte[] massBytes=rs.getBytes(10);
					final int intensityEncodedLength=rs.getInt(11);
					final byte[] intensityBytes=rs.getBytes(12);
					Integer ionMobilityEncodedLength=rs.getInt(13);
					final byte[] ionMobilityBytes;
					if (rs.wasNull()) {
						ionMobilityBytes=null;
					} else {
						ionMobilityBytes=rs.getBytes(14);
					}
					float ionInjectionTime=rs.getFloat(15);
					if (rs.wasNull()) {
						ionInjectionTime=-1f;
					}
					final float finalIonInjectionTime=ionInjectionTime;
					final int fraction=rs.getInt(16);
					executor.submit(new Runnable() {
											public void run() {
							try {
								stripes.add(getStripe(sqrt, spectrumName, precursorName, spectrumIndex, scanStartTime, fraction, finalIonInjectionTime,
										isolationWindowLower, isolationWindowTarget, isolationWindowUpper, precursorCharge, massEncodedLength, massBytes,
										intensityEncodedLength, intensityBytes, ionMobilityEncodedLength, ionMobilityBytes));
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

	String getIsolationWindowTargetSelect(Connection c) throws SQLException, IOException {
		if (owner.hasColumn(c, "spectra", "IsolationWindowTarget")) return "IsolationWindowTarget";
		return "(IsolationWindowLower+IsolationWindowUpper)/2.0 as IsolationWindowTarget";
	}

	private FragmentScan getStripe(boolean sqrt, String spectrumName, String precursorName, int spectrumIndex, Float scanStartTime, int fraction,
			float ionInjectionTime, double isolationWindowLower, double isolationWindowTarget, double isolationWindowUpper, int precursorCharge,
			int massEncodedLength, byte[] massBytes, int intensityEncodedLength, byte[] intensityBytes, Integer nullableIonMobilityEncodedLength,
			byte[] ionMobilityArrayBytes)
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
		Range trimmed=RawFileStructureTools.trimRange(new Range(isolationWindowLower, isolationWindowUpper), owner.getPrecursorMarginSize());
		return new FragmentScan(spectrumName, precursorName, spectrumIndex, isolationWindowTarget, scanStartTime, fraction, ionInjectionTime,
				trimmed.getStart(), isolationWindowTarget, trimmed.getStop(), massArray, intensityArray, ionMobilityArray, (byte)precursorCharge, 0.0,
				MatrixMath.max(massArray));
	}

	ArrayList<FragmentScan> getStripes(Range targetMzRange, float minRT, float maxRT, boolean sqrt) throws IOException, SQLException {
		ReadContext context=ReadContext.stripesRange(targetMzRange, minRT, maxRT);
		try {
			return readWithRetry(context, new SqlReadOperation<ArrayList<FragmentScan>>() {
				@Override
				public ArrayList<FragmentScan> run() throws IOException, SQLException {
					return getStripesOnce(targetMzRange, minRT, maxRT, sqrt);
				}
			});
		} catch (DataFormatException e) {
			throw new IOException("Unexpected fragment stripe decompression failure", e);
		}
	}

	private ArrayList<FragmentScan> getStripesOnce(Range targetMzRange, float minRT, float maxRT, boolean sqrt) throws IOException, SQLException {
		Connection c=owner.getConnection();
		try {
			Statement s=c.createStatement();
			try {
				boolean hasPrecursorCharge=owner.hasColumn(c, "spectra", "PrecursorCharge");
				boolean hasIonMobilityArrayEncodedLength=owner.hasColumn(c, "spectra", "IonMobilityArrayEncodedLength");
				boolean hasIonMobilityArray=owner.hasColumn(c, "spectra", "IonMobilityArray");
				boolean hasIonInjectionTime=owner.hasColumn(c, "spectra", "IonInjectionTime");
				boolean hasFraction=owner.hasColumn(c, "spectra", "Fraction");

				String precursorChargeSelect=hasPrecursorCharge?"PrecursorCharge":"0 as PrecursorCharge";
				String ionMobilitySelect=hasIonMobilityArrayEncodedLength&&hasIonMobilityArray?"IonMobilityArrayEncodedLength, IonMobilityArray"
						:"NULL as IonMobilityArrayEncodedLength, NULL as IonMobilityArray";
				String ionInjectionTimeSelect=hasIonInjectionTime?"IonInjectionTime":"NULL as IonInjectionTime";
				String fractionSelect=hasFraction?"Fraction":"0 as Fraction";
				String isolationTargetSelect=getIsolationWindowTargetSelect(c);

				String sql="select SpectrumName, PrecursorName, SpectrumIndex, ScanStartTime, IsolationWindowLower, "+isolationTargetSelect
						+", IsolationWindowUpper, "+precursorChargeSelect+", MassEncodedLength, MassArray, IntensityEncodedLength, IntensityArray, "+ionMobilitySelect+", "
						+ionInjectionTimeSelect+", "+fractionSelect+" from spectra where IsolationWindowLower <= "+targetMzRange.getStop()
						+" and IsolationWindowUpper >= "+targetMzRange.getStart()+" and ScanStartTime between "+minRT+" and "+maxRT
						+" order by ScanStartTime asc";
				ResultSet rs=s.executeQuery(sql);

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
					final float isolationWindowTarget=rs.getFloat(6);
					final float isolationWindowUpper=rs.getFloat(7);
					final int precursorCharge=rs.getInt(8);
					final int massEncodedLength=rs.getInt(9);
					final byte[] massBytes=rs.getBytes(10);
					final int intensityEncodedLength=rs.getInt(11);
					final byte[] intensityBytes=rs.getBytes(12);
					Integer ionMobilityEncodedLength=rs.getInt(13);
					final byte[] ionMobilityBytes;
					if (rs.wasNull()) {
						ionMobilityBytes=null;
					} else {
						ionMobilityBytes=rs.getBytes(14);
					}
					float ionInjectionTime=rs.getFloat(15);
					if (rs.wasNull()) {
						ionInjectionTime=-1f;
					}
					final float finalIonInjectionTime=ionInjectionTime;
					final int fraction=rs.getInt(16);

					executor.submit(new Runnable() {
											public void run() {
							try {
								stripes.add(getStripe(sqrt, spectrumName, precursorName, spectrumIndex, scanStartTime, fraction, finalIonInjectionTime,
										isolationWindowLower, isolationWindowTarget, isolationWindowUpper, precursorCharge, massEncodedLength, massBytes,
										intensityEncodedLength, intensityBytes, ionMobilityEncodedLength, ionMobilityBytes));
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

	private <T> T readWithRetry(ReadContext context, SqlReadOperation<T> operation) throws IOException, SQLException, DataFormatException {
		SQLException firstFailure=null;
		for (int attempt=1; attempt<=MAX_SQLITE_CORRUPT_READ_ATTEMPTS; attempt++) {
			try {
				ReadFailureHook hook=readFailureHook;
				if (hook!=null) {
					hook.beforeReadAttempt(context, attempt);
				}
				T result=operation.run();
				if (attempt>1) {
					Logger.errorLine("Recovered from transient SQLite read failure for "+context.describe(owner.getFile())+" after attempt "+attempt
							+" of "+MAX_SQLITE_CORRUPT_READ_ATTEMPTS+". Initial failure: "+summarize(firstFailure));
				}
				return result;
			} catch (SQLException e) {
				if (!isRetryableSqliteCorruption(e)) {
					throw e;
				}
				if (firstFailure==null) {
					firstFailure=e;
				}
				if (attempt>=MAX_SQLITE_CORRUPT_READ_ATTEMPTS) {
					throw actionableReadFailure(context, attempt, e, firstFailure);
				}
				Logger.errorLine("Retrying transient SQLite read failure for "+context.describe(owner.getFile())+" after attempt "+attempt+" of "
						+MAX_SQLITE_CORRUPT_READ_ATTEMPTS+": "+summarize(e));
			}
		}
		throw actionableReadFailure(context, MAX_SQLITE_CORRUPT_READ_ATTEMPTS, firstFailure, firstFailure);
	}

	private SQLException actionableReadFailure(ReadContext context, int attempts, SQLException failure, SQLException firstFailure) {
		String message="Error reading DIA data from "+context.describe(owner.getFile())+" after "+attempts+" attempts. SQLite reported possible database "
				+"corruption ("+summarize(failure)+"). The .dia file may need SQLite integrity checking or regeneration.";
		SQLException actionable=new SQLException(message, failure);
		if (firstFailure!=null&&firstFailure!=failure) {
			actionable.addSuppressed(firstFailure);
		}
		return actionable;
	}

	private static boolean isRetryableSqliteCorruption(Throwable throwable) {
		for (Throwable t=throwable; t!=null; t=t.getCause()) {
			if (t instanceof SQLiteException) {
				SQLiteException sqlite=(SQLiteException)t;
				if (sqlite.getResultCode()==SQLiteErrorCode.SQLITE_CORRUPT) return true;
			}
			String message=t.getMessage();
			if (message!=null) {
				String lower=message.toLowerCase(Locale.ROOT);
				if (lower.contains("sqlite_corrupt")||lower.contains("database disk image is malformed")) return true;
			}
		}
		return false;
	}

	private static String summarize(Throwable throwable) {
		if (throwable==null) return "unknown";
		String message=throwable.getMessage();
		return throwable.getClass().getSimpleName()+(message==null||message.isBlank()?"":": "+message);
	}

	static void setReadFailureHookForTesting(ReadFailureHook hook) {
		readFailureHook=hook;
	}

	@FunctionalInterface
	interface ReadFailureHook {
		void beforeReadAttempt(ReadContext context, int attempt) throws SQLException;
	}

	@FunctionalInterface
	private interface SqlReadOperation<T> {
		T run() throws IOException, SQLException, DataFormatException;
	}

	static final class ReadContext {
		private final String queryType;
		private final String precursorMzContext;
		private final Double targetMz;
		private final Range targetMzRange;
		private final float minRT;
		private final float maxRT;

		private ReadContext(String queryType, String precursorMzContext, Double targetMz, Range targetMzRange, float minRT, float maxRT) {
			this.queryType=queryType;
			this.precursorMzContext=precursorMzContext;
			this.targetMz=targetMz;
			this.targetMzRange=targetMzRange;
			this.minRT=minRT;
			this.maxRT=maxRT;
		}

		static ReadContext precursors(float minRT, float maxRT) {
			return new ReadContext("precursor read", "m/z: n/a", null, null, minRT, maxRT);
		}

		static ReadContext stripesTarget(double targetMz, float minRT, float maxRT) {
			return new ReadContext("MS2 stripe target read", null, targetMz, null, minRT, maxRT);
		}

		static ReadContext stripesRange(Range targetMzRange, float minRT, float maxRT) {
			return new ReadContext("MS2 stripe range read", null, null, targetMzRange, minRT, maxRT);
		}

		String describe(java.io.File file) {
			return String.valueOf(file)+" ("+queryType+", "+mzContext()+", retention times "+minRT+" to "+maxRT+" sec)";
		}

		private String mzContext() {
			if (precursorMzContext!=null) return precursorMzContext;
			if (targetMz!=null) return "target m/z "+targetMz;
			return "m/z range "+targetMzRange.getStart()+" to "+targetMzRange.getStop();
		}
	}

	ArrayList<ScanSummary> getScanSummaries(float minRT, float maxRT) throws IOException, SQLException {
		ArrayList<ScanSummary> out=new ArrayList<>();
		Connection c=owner.getConnection();
		try {
			Statement s=c.createStatement();
			try {
				boolean hasPrecursorIonInjectionTime=owner.hasColumn(c, "precursor", "IonInjectionTime");
				boolean hasPrecursorIsolationWindowLower=owner.hasColumn(c, "precursor", "IsolationWindowLower");
				boolean hasPrecursorIsolationWindowUpper=owner.hasColumn(c, "precursor", "IsolationWindowUpper");
				boolean hasPrecursorTic=owner.hasColumn(c, "precursor", "TIC");

				String precursorIonInjectionTimeSelect=hasPrecursorIonInjectionTime?"IonInjectionTime":"NULL as IonInjectionTime";
				String precursorIsolationWindowLowerSelect=hasPrecursorIsolationWindowLower?"IsolationWindowLower":"0.0 as IsolationWindowLower";
				String precursorIsolationWindowUpperSelect=hasPrecursorIsolationWindowUpper?"IsolationWindowUpper":"999999999.0 as IsolationWindowUpper";
				String precursorTicSelect=hasPrecursorTic?"TIC":"NULL as TIC";

				ResultSet rs=s.executeQuery("select SpectrumName, SpectrumIndex, ScanStartTime, "+precursorIonInjectionTimeSelect+", "
						+precursorIsolationWindowLowerSelect+", "+precursorIsolationWindowUpperSelect+", "+precursorTicSelect+" from precursor where ScanStartTime>="
						+minRT+" and ScanStartTime<="+maxRT+" order by ScanStartTime");
				while (rs.next()) {
					String name=rs.getString(1);
					int index=rs.getInt(2);
					float rt=rs.getFloat(3);
					Float iit=rs.getFloat(4);
					if (rs.wasNull()) iit=null;
					double isoLo=rs.getDouble(5);
					double isoHi=rs.getDouble(6);
					float tic=rs.getFloat(7);
					if (rs.wasNull()) tic=Float.NaN;
					out.add(new ScanSummary(name, index, rt, 0, tic, -1.0, true, iit, isoLo, isoHi, isoLo, isoHi, (byte)0));
				}
				rs.close();

				boolean hasScanWindowLower=owner.hasColumn(c, "spectra", "ScanWindowLower");
				boolean hasScanWindowUpper=owner.hasColumn(c, "spectra", "ScanWindowUpper");
				boolean hasPrecursorCharge=owner.hasColumn(c, "spectra", "PrecursorCharge");
				boolean hasSpectraIonInjectionTime=owner.hasColumn(c, "spectra", "IonInjectionTime");
				boolean hasSpectraTic=owner.hasColumn(c, "spectra", "TIC");

				String scanWindowSelect;
				if (hasScanWindowLower&&hasScanWindowUpper) {
					scanWindowSelect=", ScanWindowLower, ScanWindowUpper";
				} else {
					scanWindowSelect=", IsolationWindowLower as ScanWindowLower, IsolationWindowUpper as ScanWindowUpper";
				}
				String isolationTargetSelect=", "+getIsolationWindowTargetSelect(c);
				String precursorChargeSelect=hasPrecursorCharge?", PrecursorCharge":", 0 as PrecursorCharge";
				String spectraIonInjectionTimeSelect=hasSpectraIonInjectionTime?", IonInjectionTime":", NULL as IonInjectionTime";
				String spectraSql;
				if (hasSpectraTic) {
					spectraSql="select SpectrumName, SpectrumIndex, ScanStartTime"+spectraIonInjectionTimeSelect
							+", IsolationWindowLower, IsolationWindowUpper"+isolationTargetSelect+precursorChargeSelect+scanWindowSelect+", TIC from spectra "
							+"where ScanStartTime>="+minRT+" and ScanStartTime<="+maxRT+" order by ScanStartTime";
				} else {
					spectraSql="select SpectrumName, SpectrumIndex, ScanStartTime"+spectraIonInjectionTimeSelect
							+", IsolationWindowLower, IsolationWindowUpper"+isolationTargetSelect+precursorChargeSelect+scanWindowSelect
							+", IntensityEncodedLength, IntensityArray from spectra "
							+"where ScanStartTime>="+minRT+" and ScanStartTime<="+maxRT+" order by ScanStartTime";
				}

				rs=s.executeQuery(spectraSql);
				while (rs.next()) {
					String name=rs.getString(1);
					int index=rs.getInt(2);
					float rt=rs.getFloat(3);
					Float iit=rs.getFloat(4);
					if (rs.wasNull()) iit=null;
					double isoLo=rs.getDouble(5);
					double isoHi=rs.getDouble(6);
					double target=rs.getDouble(7);
					Range trimmed=RawFileStructureTools.trimRange(new Range(isoLo, isoHi), owner.getPrecursorMarginSize());
					byte charge=(byte)rs.getInt(8);
					double scanLo=rs.getDouble(9);
					double scanHi=rs.getDouble(10);
					float tic;
					if (hasSpectraTic) {
						tic=rs.getFloat(11);
						if (rs.wasNull()) tic=Float.NaN;
					} else {
						int encodedLength=rs.getInt(11);
						byte[] intensityBytes=rs.getBytes(12);
						tic=Float.NaN;
						if (intensityBytes!=null&&encodedLength>0) {
							try {
								float[] intensities=ByteConverter.toFloatArray(CompressionUtils.decompress(intensityBytes, encodedLength));
								tic=MatrixMath.sum(intensities);
							} catch (DataFormatException e) {
								throw new IOException("Failed to decode fragment intensity array for legacy DIA scan summary TIC", e);
							}
						}
					}
					out.add(new ScanSummary(name, index, rt, 0, tic, target, false, iit, trimmed.getStart(), trimmed.getStop(), scanLo, scanHi, charge));
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

	AcquiredSpectrum getSpectrum(ScanSummary summary) throws IOException, SQLException, DataFormatException {
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

	void addPrecursor(ArrayList<PrecursorScan> precursors) throws IOException, SQLException {
		Connection c=owner.getConnection();
		try {
			PreparedStatement prep=c.prepareStatement(
					"insert into precursor (SpectrumName, SpectrumIndex, ScanStartTime, IonInjectionTime, MassEncodedLength, MassArray, IntensityEncodedLength, IntensityArray, IonMobilityArrayEncodedLength, IonMobilityArray, TIC, Fraction, IsolationWindowLower, IsolationWindowUpper) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
			try {
				for (AcquiredSpectrum precursor : precursors) {
					prep.setString(1, precursor.getSpectrumName());
					prep.setInt(2, precursor.getSpectrumIndex());
					prep.setFloat(3, precursor.getScanStartTime());

					if (precursor.getIonInjectionTime()>0) {
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


	void addStripe(ArrayList<FragmentScan> stripes) throws IOException, SQLException {
		try (Connection c=owner.getConnection()) {
			try (PreparedStatement prep=c.prepareStatement(
					"insert into spectra (SpectrumName, PrecursorName, SpectrumIndex, ScanStartTime, Fraction, IonInjectionTime, IsolationWindowLower, IsolationWindowTarget, IsolationWindowUpper, PrecursorCharge, MassEncodedLength, MassArray, IntensityEncodedLength, IntensityArray, IonMobilityArrayEncodedLength, IonMobilityArray, TIC)"
							+" VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
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

			if (stripe.getIonInjectionTime()>0) {
				prep.setFloat(index++, stripe.getIonInjectionTime());
			} else {
				prep.setNull(index++, Types.FLOAT);
			}

			prep.setDouble(index++, stripe.getIsolationWindowLower());
			prep.setDouble(index++, stripe.getIsolationWindowTarget());
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
			prep.setFloat(index++, stripe.getTIC());
			prep.addBatch();
		}
		prep.executeBatch();
	}

}
