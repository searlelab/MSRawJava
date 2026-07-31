package org.searlelab.msrawjava.io;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.zip.DataFormatException;

import org.searlelab.msrawjava.API;
import org.searlelab.msrawjava.io.utils.Pair;
import org.searlelab.msrawjava.model.AcquiredSpectrum;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.ScanSummary;
import org.searlelab.msrawjava.model.WindowData;

/**
 * StripeFileInterface describes the reader-facing contract expected by writers that consume DIA-style windowed data. It
 * exposes access to run metadata and to window/range organization in a vendor-neutral way so output code can pull
 * MS1/MS2 content uniformly from Bruker and Thermo sources.
 */
@API(status = API.Status.STABLE, since = "v26.7.31")
public interface StripeFileInterface {

	/**
	 * ranges for dia stripe boundaries
	 * 
	 * @return Range: low/high boundaries for stripes, Float value is average time in seconds between cycles
	 */
	@API(status = API.Status.STABLE, since = "v26.7.31")
	Map<Range, WindowData> getRanges();

	@API(status = API.Status.STABLE, since = "v26.7.31")
	default double getPrecursorMarginSize() {
		return 0.0;
	}

	@API(status = API.Status.STABLE, since = "v26.7.31")
	default void setPrecursorMarginSize(double precursorMarginSize) {
	}

	/**
	 * metadata map for experiment
	 * 
	 * @return
	 */
	@API(status = API.Status.STABLE, since = "v26.7.31")
	Map<String, String> getMetadata() throws IOException, SQLException;

	/**
	 * opens specific file on disk
	 * 
	 * @param userFile
	 * @throws IOException
	 * @throws SQLException
	 */
	@API(status = API.Status.STABLE, since = "v26.7.31")
	void openFile(File userFile) throws IOException, SQLException;

	/**
	 * returns precursor scans between RT ranges
	 * 
	 * @param minRT
	 * @param maxRT
	 * @return
	 * @throws IOException
	 * @throws SQLException
	 * @throws DataFormatException
	 */
	@API(status = API.Status.STABLE, since = "v26.7.31")
	ArrayList<PrecursorScan> getPrecursors(float minRT, float maxRT) throws IOException, SQLException, DataFormatException;

	/**
	 * returns DIA scans between RT ranges at a specific target MZ
	 * 
	 * @param targetMz
	 * @param minRT
	 * @param maxRT
	 * @param sqrt
	 *            if intensities should be sqrted
	 * @return
	 * @throws IOException
	 * @throws SQLException
	 */
	@API(status = API.Status.STABLE, since = "v26.7.31")
	ArrayList<FragmentScan> getStripes(double targetMz, float minRT, float maxRT, boolean sqrt) throws IOException, SQLException;

	/**
	 * returns DIA scans between RT ranges within target MZ range
	 * 
	 * @param targetMzRange
	 * @param minRT
	 * @param maxRT
	 * @param sqrt
	 * @return
	 * @throws IOException
	 * @throws SQLException
	 */
	@API(status = API.Status.STABLE, since = "v26.7.31")
	ArrayList<FragmentScan> getStripes(Range targetMzRange, float minRT, float maxRT, final boolean sqrt) throws IOException, SQLException;

	/**
	 * Fast metadata-only scan summaries for UI listing without loading spectra arrays.
	 */
	@API(status = API.Status.STABLE, since = "v26.7.31")
	ArrayList<ScanSummary> getScanSummaries(float minRT, float maxRT) throws IOException, SQLException;

	/**
	 * On-demand spectrum fetch based on summary (avoids full-file parsing).
	 */
	@API(status = API.Status.STABLE, since = "v26.7.31")
	AcquiredSpectrum getSpectrum(ScanSummary summary) throws IOException, SQLException, DataFormatException;

	/**
	 * On-demand per-scan vendor metadata for GUI inspection. Unsupported readers or missing vendor data should return
	 * empty arrays rather than fail the spectrum view.
	 */
	@API(status = API.Status.STABLE, since = "v26.7.31")
	default Pair<String[], String[]> getScanMetadata(ScanSummary summary) throws IOException, SQLException, DataFormatException {
		return new Pair<>(new String[0], new String[0]);
	}

	/**
	 * returns total precursor ion current across entire file
	 * 
	 * @return
	 * @throws IOException
	 * @throws SQLException
	 */
	@API(status = API.Status.STABLE, since = "v26.7.31")
	float getTIC() throws IOException, SQLException;

	/**
	 * returns total precursor ion current and time (in seconds) arrays across entire file
	 * 
	 * @return
	 * @throws IOException
	 * @throws SQLException
	 */
	@API(status = API.Status.STABLE, since = "v26.7.31")
	public Pair<float[], float[]> getTICTrace() throws IOException, SQLException;

	/**
	 * returns the time in seconds between the first scan and the last scan
	 * 
	 * @return
	 * @throws IOException
	 * @throws SQLException
	 */
	@API(status = API.Status.STABLE, since = "v26.7.31")
	float getGradientLength() throws IOException, SQLException;

	/**
	 * closes file
	 */
	@API(status = API.Status.STABLE, since = "v26.7.31")
	void close();

	@API(status = API.Status.STABLE, since = "v26.7.31")
	boolean isOpen();

	/**
	 * returns the file object for the user file (or the temp file if no user file exists)
	 * 
	 * @return
	 */
	@API(status = API.Status.STABLE, since = "v26.7.31")
	File getFile();

	/**
	 * returns the original file name (for the equivalent of a .RAW file)
	 * 
	 * @return
	 */
	@API(status = API.Status.STABLE, since = "v26.7.31")
	String getOriginalFileName();
}
