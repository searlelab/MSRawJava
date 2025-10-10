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

public interface OutputSpectrumFile {
	String getFileExtension();

	void setRanges(HashMap<Range, WindowData> ranges);

	void saveAsFile(File userFile) throws IOException, SQLException;

	void setFileName(String fileName, String fileLocation) throws IOException, SQLException;

	void addMetadata(Map<String, String> data) throws IOException, SQLException;

	void addSpectra(ArrayList<PrecursorScan> precursors, ArrayList<FragmentScan> stripes) throws Exception;

	void close();

}