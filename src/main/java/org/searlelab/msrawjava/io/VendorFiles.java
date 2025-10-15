package org.searlelab.msrawjava.io;

import java.nio.file.Path;
import java.util.ArrayList;

/**
 * VendorFiles is a simple aggregate that holds the results of vendor file discovery, grouping recognized inputs by type
 * and exposing stable iteration for batch processing. It acts as the handoff between discovery (VendorFileFinder) and
 * the reading/serialization stages, keeping CLI and orchestration code concise. Currently it only works for Thermo and
 * Bruker raw files.
 */
public class VendorFiles {
	private final ArrayList<Path> rawFiles;
	private final ArrayList<Path> dDirs;

	public VendorFiles() {
		this.rawFiles=new ArrayList<Path>();
		this.dDirs=new ArrayList<Path>();
	}

	public void addRaw(Path p) {
		rawFiles.add(p);
	}

	public void addD(Path p) {
		dDirs.add(p);
	}
	
	public void add(ArrayList<Path> rawFiles, ArrayList<Path> dDirs) {
		addRaw(rawFiles);
		addD(dDirs);
	}

	public void addRaw(ArrayList<Path> p) {
		rawFiles.addAll(p);
	}

	public void addD(ArrayList<Path> p) {
		dDirs.addAll(p);
	}

	public ArrayList<Path> getBrukerDirs() {
		return dDirs;
	}

	public ArrayList<Path> getThermoFiles() {
		return rawFiles;
	}
}