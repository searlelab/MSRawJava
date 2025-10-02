package org.searlelab.msrawjava.io.tims;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Locale;

final class NativeLibraryLoader {
    private NativeLibraryLoader() {}

    static void load() {
        // First try the default lookup (java.library.path or already loaded)
        try {
            System.loadLibrary("timsreader_jni");
            return;
        } catch (UnsatisfiedLinkError ignore) {
            // Fall through to classpath-extraction path
        }

        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);

        final String subdir;
        final String libName;
        if (os.contains("mac")) {
            subdir = "osx-aarch64";           // Apple Silicon. If you also ship x86_64, add a branch.
            libName = "libtimsrust_jni.dylib";
        } else if (os.contains("linux")) {
            subdir = "linux-x86_64";
            libName = "libtimsrust_jni.so";
        } else if (os.contains("win")) {
            subdir = "windows-x86_64";
            libName = "timsrust_jni.dll";
        } else {
            throw new UnsatisfiedLinkError("Unsupported OS: " + os + ", arch: " + arch);
        }

        String resourcePath = "/META-INF/lib/" + subdir + "/" + libName;
        try (InputStream in = NativeLibraryLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new UnsatisfiedLinkError("Native library not found on classpath at " + resourcePath);
            }
            Path tmp = Files.createTempFile("timsreader_jni_", "_" + libName);
            tmp.toFile().deleteOnExit();
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            System.load(tmp.toAbsolutePath().toString());
        } catch (IOException e) {
            throw new UnsatisfiedLinkError("Failed to extract native library: " + e.getMessage());
        }
    }
}
