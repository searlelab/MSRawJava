package org.searlelab.msrawjava.io;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Objects;

/**
 * VendorFileFinder walks files and directories to discover supported vendor inputs (e.g., Bruker .d directories and
 * Thermo .raw files), applying simple filters and normalizing paths. It returns a VendorFiles aggregate that groups
 * discovered items by vendor so downstream readers and the CLI can orchestrate processing consistently.
 */
public final class VendorFileFinder {
	public static VendorFiles findAndAddRawAndD(Path start) throws IOException {
		VendorFiles files=new VendorFiles();
		findAndAddRawAndD(start, files, false);
		return files;
	}

	public static VendorFiles findAndAddRawAndD(Path start, boolean includeDia) throws IOException {
		VendorFiles files=new VendorFiles();
		findAndAddRawAndD(start, files, includeDia);
		return files;
	}

	/**
	 * Collects vendor data roots under the given path.
	 * - If the path is a .raw file, it's added to rawFiles.
	 * - If the path is a .d directory, it's added to dDirs and not descended into.
	 * - Otherwise, recursively walks and collects both.
	 */
	public static void findAndAddRawAndD(Path start, VendorFiles files) throws IOException {
		findAndAddRawAndD(start, files, false);
	}

	/**
	 * Collects vendor data roots under the given path.
	 * - If the path is a .raw file, it's added to rawFiles.
	 * - If the path is a .d directory, it's added to dDirs and not descended into.
	 * - If includeDia is true and the path is a .dia file, it's added to diaFiles.
	 * - Otherwise, recursively walks and collects both (and .dia when enabled).
	 */
	public static void findAndAddRawAndD(Path start, VendorFiles files, boolean includeDia) throws IOException {
		Objects.requireNonNull(start, "start");

		final ArrayList<Path> raw=new ArrayList<>();
		final ArrayList<Path> ddirs=new ArrayList<>();
		final ArrayList<Path> dias=new ArrayList<>();

		if (!Files.exists(start)) {
			throw new NoSuchFileException("Path does not exist: "+start);
		}

		// Normalize start
		final Path root=start.toAbsolutePath().normalize();

		// Handle single-file or single-dir cases quickly
		if (Files.isRegularFile(root)) {
			if (VendorFile.isThermoFile(root)) {
				raw.add(root);
			} else if (VendorFile.isDiaFile(root)) {
				dias.add(root);
			}
			files.add(raw, ddirs, dias);
			return;
		}
		if (VendorFile.isDotDFile(root)) {
			ddirs.add(root);
			files.add(raw, ddirs, dias);
			return;
		}

		// Walk recursively. We do NOT follow symlinks to avoid loops.
		Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE, new SimpleFileVisitor<>() {

			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
				// If this directory itself is a .d bundle, collect and skip its subtree
				if (VendorFile.isDotDFile(dir)) {
					ddirs.add(dir.toAbsolutePath().normalize());
					return FileVisitResult.SKIP_SUBTREE;
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				if (attrs.isRegularFile()&&VendorFile.isThermoFile(file)) {
					raw.add(file.toAbsolutePath().normalize());
				} else if (includeDia&&attrs.isRegularFile()&&VendorFile.isDiaFile(file)) {
					dias.add(file.toAbsolutePath().normalize());
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc) {
				// Keep going on permission issues or transient errors
				return FileVisitResult.CONTINUE;
			}
		});

		files.add(raw, ddirs, dias);
		return;
	}
}
