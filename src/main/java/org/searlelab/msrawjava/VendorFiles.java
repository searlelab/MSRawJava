package org.searlelab.msrawjava;

import java.nio.file.Path;
import java.util.ArrayList;

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