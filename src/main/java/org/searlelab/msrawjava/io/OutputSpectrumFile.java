package org.searlelab.msrawjava.io;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.WindowData;

/**
 * OutputSpectrumFile defines a small, uniform lifecycle for spectrum file writers (initialization, metadata/range
 * configuration, spectra emission, and closing). It exists so concrete writers such as MZMLOutputFile, MGFOutputFile,
 * and EncyclopeDIAFile can share consistent semantics and be orchestrated interchangeably by higher-level code.
 */
public interface OutputSpectrumFile {

	/** Returns the file extension produced by this writer (e.g., ".mzML", ".mgf"). */
	String getFileExtension();

	/** Sets the DIA m/z windows and associated statistics used to annotate the save file. */
	void setRanges(HashMap<Range, WindowData> ranges);

	/** Saves all staged content to the given file path. */
	void saveAsFile(File userFile) throws IOException, SQLException;

	/** Records the source file name and location for embedding in headers/metadata. */
	void setFileName(String fileName, String fileLocation) throws IOException, SQLException;

	/** Adds key–value metadata entries to be written with the save file. */
	void addMetadata(Map<String, String> data) throws IOException, SQLException;

	/** Appends MS1 precursors and MS2 fragment spectra to the save file. */
	void addSpectra(ArrayList<PrecursorScan> precursors, ArrayList<FragmentScan> stripes) throws Exception;

	/** Finalizes the writer and releases any held resources. */
	void close();

}