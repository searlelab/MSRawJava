package org.searlelab.msrawjava;

import java.nio.file.Path;
import java.util.ArrayList;

public class VendorFiles {
	private final ArrayList<Path> rawFiles;
	private final ArrayList<Path> dDirs;
	
	public VendorFiles(ArrayList<Path> rawFiles, ArrayList<Path> dDirs) {
		this.rawFiles = rawFiles;
		this.dDirs = dDirs;
	}
	public VendorFiles() {
		this.rawFiles = new ArrayList<Path>();
		this.dDirs = new ArrayList<Path>();
	}
	
	public void addRaw(Path p) {
		rawFiles.add(p);
	}
	public void addD(Path p) {
		dDirs.add(p);
	}
	
	public ArrayList<Path> getBrukerDirs() {
		return dDirs;
	}
	public ArrayList<Path> getThermoFiles() {
		return rawFiles;
	}
}