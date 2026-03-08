package org.searlelab.msrawjava.io;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * VendorFile defines the supported vendor input types and provides consistent naming
 * and extension helpers across the core and GUI.
 */
public enum VendorFile {
	THERMO("Thermo", ".raw", false), BRUKER("Bruker", ".d", true), ENCYCLOPEDIA("EncyclopeDIA", ".dia", false), MZML("mzML", ".mzml", false);

	private final String vendorName;
	private final String extension;
	private final boolean directoryBundle;

	VendorFile(String vendorName, String extension, boolean directoryBundle) {
		this.vendorName=vendorName;
		this.extension=extension;
		this.directoryBundle=directoryBundle;
	}

	public String getVendorName() {
		return vendorName;
	}

	public String getExtension() {
		return extension;
	}

	public String getDisplayName() {
		return vendorName+" "+extension;
	}

	public boolean isDirectoryBundle() {
		return directoryBundle;
	}

	public boolean matchesName(String name) {
		if (name==null) return false;
		String lower=name.toLowerCase(Locale.ROOT);
		return lower.endsWith(extension);
	}

	public boolean matchesPath(Path path) {
		if (path==null) return false;
		if (directoryBundle) {
			return Files.isDirectory(path)&&matchesName(path.getFileName().toString());
		}
		return Files.isRegularFile(path)&&matchesName(path.getFileName().toString());
	}

	public static Optional<VendorFile> fromPath(Path path) {
		if (path==null) return Optional.empty();
		for (VendorFile vendor : values()) {
			if (vendor.matchesPath(path)) return Optional.of(vendor);
		}
		return Optional.empty();
	}

	public static Optional<VendorFile> fromName(String name) {
		if (name==null) return Optional.empty();
		for (VendorFile vendor : values()) {
			if (vendor.matchesName(name)) return Optional.of(vendor);
		}
		return Optional.empty();
	}

	public static List<VendorFile> list() {
		return List.of(values());
	}

	public static boolean isThermoFile(Path root) {
		return VendorFile.THERMO.matchesPath(root);
	}

	public static boolean isDotDFile(Path root) {
		return VendorFile.BRUKER.matchesPath(root);
	}

	public static boolean isDiaFile(Path root) {
		return VendorFile.ENCYCLOPEDIA.matchesPath(root);
	}

	public static boolean isMzMLFile(Path root) {
		return VendorFile.MZML.matchesPath(root);
	}
}
