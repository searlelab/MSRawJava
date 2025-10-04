package org.searlelab.msrawjava;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;

public final class VendorFileFinder {

    /**
     * Collects vendor data roots under the given path.
     * - If the path is a .raw file, it's added to rawFiles.
     * - If the path is a .d directory, it's added to dDirs and not descended into.
     * - Otherwise, recursively walks and collects both.
     */
    public static VendorFiles collectRawAndD(Path start) throws IOException {
        Objects.requireNonNull(start, "start");

        final ArrayList<Path> raw = new ArrayList<>();
        final ArrayList<Path> ddirs = new ArrayList<>();

        if (!Files.exists(start)) {
            throw new NoSuchFileException("Path does not exist: " + start);
        }

        // Normalize start
        final Path root = start.toAbsolutePath().normalize();

        // Handle single-file or single-dir cases quickly
        if (Files.isRegularFile(root)) {
            if (hasExt(root, ".raw")) raw.add(root);
            return new VendorFiles(raw, ddirs);
        }
        if (Files.isDirectory(root)) {
            if (hasExt(root.getFileName(), ".d")) {
                ddirs.add(root);
                return new VendorFiles(raw, ddirs);
            }
        }

        // Walk recursively. We do NOT follow symlinks to avoid loops.
        Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE, new SimpleFileVisitor<>() {

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                // If this directory itself is a .d bundle, collect and skip its subtree
                if (hasExt(dir.getFileName(), ".d")) {
                    ddirs.add(dir.toAbsolutePath().normalize());
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile() && hasExt(file, ".raw")) {
                    raw.add(file.toAbsolutePath().normalize());
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                // Keep going on permission issues or transient errors
                return FileVisitResult.CONTINUE;
            }
        });

        return new VendorFiles(raw, ddirs);
    }

    private static boolean hasExt(Path p, String extLowerDot) {
        if (p == null) return false;
        return hasExt(p.toString(), extLowerDot);
    }

    private static boolean hasExt(String pathStr, String extLowerDot) {
        if (pathStr == null) return false;
        String s = pathStr.toLowerCase(Locale.ROOT);
        return s.endsWith(extLowerDot);
    }
}
