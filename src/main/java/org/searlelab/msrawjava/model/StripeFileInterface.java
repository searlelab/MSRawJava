package org.searlelab.msrawjava.model;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.zip.DataFormatException;

public interface StripeFileInterface {

	/**
	 * ranges for dia stripe boundaries
	 * @return Range: low/high boundaries for stripes, Float value is average time in seconds between cycles 
	 */
	Map<Range, WindowData> getRanges();
	
	/**
	 * metadata map for experiment
	 * @return
	 */
	Map<String, String> getMetadata() throws IOException, SQLException;

	/**
	 * opens specific file on disk
	 * @param userFile
	 * @throws IOException
	 * @throws SQLException
	 */
	void openFile(File userFile) throws IOException, SQLException;

	/**
	 * returns precursor scans between RT ranges
	 * @param minRT
	 * @param maxRT
	 * @return
	 * @throws IOException
	 * @throws SQLException
	 * @throws DataFormatException
	 */
	ArrayList<PrecursorScan> getPrecursors(float minRT, float maxRT) throws IOException, SQLException, DataFormatException;

	/**
	 * returns DIA scans between RT ranges at a specific target MZ
	 * @param targetMz
	 * @param minRT
	 * @param maxRT
	 * @param sqrt if intensities should be sqrted
	 * @return
	 * @throws IOException
	 * @throws SQLException
	 */
	ArrayList<FragmentScan> getStripes(double targetMz, float minRT, float maxRT, boolean sqrt) throws IOException, SQLException;

	/**
	 * returns DIA scans between RT ranges within target MZ range
	 * @param targetMzRange
	 * @param minRT
	 * @param maxRT
	 * @param sqrt
	 * @return
	 * @throws IOException
	 * @throws SQLException
	 */
	ArrayList<FragmentScan> getStripes(Range targetMzRange, float minRT, float maxRT, final boolean sqrt) throws IOException, SQLException;
	
	/**
	 * returns total precursor ion current across entire file
	 * @return
	 * @throws IOException
	 * @throws SQLException
	 */
	float getTIC() throws IOException, SQLException;
	
	/**
	 * returns the time in seconds between the first scan and the last scan
	 * @return
	 * @throws IOException
	 * @throws SQLException
	 */
	float getGradientLength() throws IOException, SQLException;
	
	/**
	 * closes file
	 */
	void close() throws IOException;
	
	boolean isOpen();

	/**
	 * returns the file object for the user file (or the temp file if no user file exists) 
	 * @return
	 */
	File getFile();
	
	/**
	 * returns the original file name (for the equivalent of a .RAW file)
	 * @return
	 */
	String getOriginalFileName();
}