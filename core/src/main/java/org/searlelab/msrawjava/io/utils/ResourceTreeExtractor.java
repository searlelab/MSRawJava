package org.searlelab.msrawjava.io.utils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import org.searlelab.msrawjava.logging.Logger;

/**
 * ResourceTreeExtractor copies a packaged resource directory (on the classpath) to a writable filesystem location,
 * preserving the tree structure and basic metadata. It exists to stage bundled assets—such as native libraries or the
 * Thermo server—without external installers, handling path resolution and safe overwrites so callers can launch tools
 * directly from extracted resources. It supports both "file:" resources (exploded) and "jar:" resources (packaged in a
 * JAR).
 */
public final class ResourceTreeExtractor {

	private ResourceTreeExtractor() {
	}

	/**
	 * Extract a classpath directory (e.g. "/msraw/thermo/bin/osx-x64") to destDir.
	 * The destDir will be created if missing. Existing files are overwritten.
	 */
	public static void extractDirectory(Class<?> anchor, String resourceDir, Path destDir) throws IOException {
		Objects.requireNonNull(anchor, "anchor");
		Objects.requireNonNull(resourceDir, "resourceDir");
		Objects.requireNonNull(destDir, "destDir");

		if (!resourceDir.startsWith("/")) resourceDir="/"+resourceDir;
		URL url=anchor.getResource(resourceDir);
		if (url==null) {
			throw new FileNotFoundException("Classpath directory not found: "+resourceDir);
		}
		Files.createDirectories(destDir);

		String protocol=url.getProtocol();
		if ("file".equalsIgnoreCase(protocol)) {
			Path srcRoot=Paths.get(urlToUri(url)).normalize();
			copyTree(srcRoot, destDir);
		} else if ("jar".equalsIgnoreCase(protocol)) {
			String spec=url.toString();
			int bang=spec.indexOf("!/");
			if (bang<0) throw new MalformedURLException("Invalid jar url: "+spec);
			URI jarUri=URI.create(spec.substring(0, bang));
			String entryPath=spec.substring(bang+1);

			// Open or reuse a FileSystem for the jar
			try (FileSystem fs=newFileSystemIfNeeded(jarUri)) {
				Path jarRoot=fs.getPath("/"+entryPath).normalize();
				copyTree(jarRoot, destDir);
			}
		} else {
			throw new IOException("Unsupported resource protocol: "+protocol+" for "+resourceDir);
		}
	}

	private static URI urlToUri(URL url) throws IOException {
		try {
			return url.toURI();
		} catch (URISyntaxException e) {
			throw new IOException("Bad URI for "+url, e);
		}
	}

	private static FileSystem newFileSystemIfNeeded(URI jarUri) throws IOException {
		// If a FS for this JAR is already mounted, return it; else create a new one.
		// The default provider returns an existing FS for identical URIs.
		try {
			return FileSystems.newFileSystem(jarUri, Map.of());
		} catch (FileSystemAlreadyExistsException e) {
			return FileSystems.getFileSystem(jarUri);
		}
	}

	private static void copyTree(Path srcRoot, Path destRoot) throws IOException {
		try (Stream<Path> walk=Files.walk(srcRoot)) {
			for (Path p : (Iterable<Path>)walk::iterator) {
				Path rel=srcRoot.relativize(p);
				if (rel.toString().isEmpty()) continue;
				Path dst=destRoot.resolve(rel.toString());
				if (Files.isDirectory(p)) {
					Files.createDirectories(dst);
				} else {
					// regular file (including native libs)
					try (InputStream in=Files.newInputStream(p)) {
						Files.createDirectories(dst.getParent());
						Files.copy(in, dst, StandardCopyOption.REPLACE_EXISTING);
					}
					// best effort: mark executables on *nix
					maybeSetExecutable(dst);
				}
			}
		}
	}

	private static void maybeSetExecutable(Path file) {
		try {
			String name=file.getFileName().toString();
			boolean looksExe=name.equals("MSRaw.Thermo.Server")||name.endsWith(".so")||name.endsWith(".dylib");
			if (looksExe&&Files.getFileStore(file).supportsFileAttributeView("posix")) {
				Set<PosixFilePermission> perms=Files.getPosixFilePermissions(file);
				perms.add(PosixFilePermission.OWNER_EXECUTE);
				Files.setPosixFilePermissions(file, perms);
			}
		} catch (Exception ignored) {
			Logger.errorException(ignored);
		}
	}
}
