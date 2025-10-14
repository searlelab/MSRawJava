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
	String getFileExtension();

	void setRanges(HashMap<Range, WindowData> ranges);

	void saveAsFile(File userFile) throws IOException, SQLException;

	void setFileName(String fileName, String fileLocation) throws IOException, SQLException;

	void addMetadata(Map<String, String> data) throws IOException, SQLException;

	void addSpectra(ArrayList<PrecursorScan> precursors, ArrayList<FragmentScan> stripes) throws Exception;

	void close();

}