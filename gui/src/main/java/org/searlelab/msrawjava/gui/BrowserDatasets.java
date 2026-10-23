package org.searlelab.msrawjava.gui;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Locale;

import org.searlelab.msrawjava.io.VendorFile;
import org.searlelab.msrawjava.io.VendorFileFinder;
import org.searlelab.msrawjava.io.VendorFiles;

/** GUI discovery requires TDF datasets; core discovery retains its TSF diagnostics. */
final class BrowserDatasets {
	private BrowserDatasets() {}

	static boolean isBrukerBundle(Path path) {
		return VendorFile.BRUKER.matchesPath(path)&&Files.isRegularFile(path.resolve("analysis.tdf"));
	}

	static boolean isDataset(Path path) {
		return VendorFile.fromPath(path).filter(vendor -> vendor!=VendorFile.BRUKER||isBrukerBundle(path)).isPresent();
	}

	static Path datasetPath(Path path) {
		if (isDataset(path)) return path;
		Path parent=path.getParent();
		if (parent!=null&&path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".tdf")&&Files.isRegularFile(path)
				&&isBrukerBundle(parent)) return parent;
		return null;
	}

	static VendorFiles find(Path start) throws IOException {
		VendorFiles result=new VendorFiles();
		ArrayDeque<Path> pending=new ArrayDeque<>();
		pending.add(start);
		while (!pending.isEmpty()) {
			if (Thread.currentThread().isInterrupted()) break;
			VendorFiles found=VendorFileFinder.findAndAddRawAndD(pending.remove(), true, true);
			result.addRaw(found.getThermoFiles());
			result.addDia(found.getDiaFiles());
			result.addMzml(found.getMzmlFiles());
			for (Path directory : found.getBrukerDirs()) {
				if (isBrukerBundle(directory)) {
					result.addD(directory);
				} else {
					// Core treats .d as terminal. Continue into ordinary .d folders in the browser only.
					try (DirectoryStream<Path> children=Files.newDirectoryStream(directory)) {
						for (Path child : children) {
							if (!Files.isSymbolicLink(child)) pending.add(child);
						}
					} catch (IOException ignored) {
						// Match core discovery's tolerance of inaccessible descendants.
					}
				}
			}
		}
		return result;
	}
}
