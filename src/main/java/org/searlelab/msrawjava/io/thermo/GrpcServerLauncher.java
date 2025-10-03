package org.searlelab.msrawjava.io.thermo;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.searlelab.msrawjava.io.utils.ResourceTreeExtractor;

final class GrpcServerLauncher implements AutoCloseable {
    private static final String OS_LINUX = "linux";
	private static final String OS_OSX = "osx";
	private static final String OS_WIN = "win";
	private final int port;
    private final Process proc;
    private final Path workDir; // temp dir holding extracted publish tree

    static int pickFreePort() throws IOException {
        try (ServerSocket ss = new ServerSocket(0)) {
            ss.setReuseAddress(true);
            return ss.getLocalPort();
        }
    }

    static boolean isMac()  { return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac"); }
    static boolean isWin()  { return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains(OS_WIN); }
    static boolean isArm64(){ String a = System.getProperty("os.arch").toLowerCase(Locale.ROOT); return a.contains("aarch64") || a.contains("arm64"); }

    static String rid() {
        String osPart = getOS();
        //String archPart = isArm64() ? "arm64" : "x64";
        
        if (osPart.equals(OS_WIN)) return "win-x64";
        
        // Force x64 server (Rosetta) on Apple Silicon until Thermo makes a compatible library
        //if (osPart.equals("osx")) return "osx-" + archPart;
        if (osPart.equals(OS_OSX)) return "osx-x64";
        
        return "linux-x64";
    }


	private static String getOS() {
		String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        return os.contains(OS_WIN) ? OS_WIN : (os.contains("mac") || os.contains("darwin")) ? OS_OSX : OS_LINUX;
	}

    GrpcServerLauncher() throws IOException, InterruptedException {
        this(pickFreePort());
    }

    GrpcServerLauncher(int port) throws IOException, InterruptedException {
        this.port = port;

        // 1) Extract entire published folder from resources into a fresh temp dir
        String rid = rid();
        this.workDir = Files.createTempDirectory("msraw-thermo-");

        // Extract everything including runtimes/
	     // 1.1) extract common managed DLLs
	     ResourceTreeExtractor.extractDirectory(GrpcServerLauncher.class, "/msraw/thermo/bin/common", workDir);
	
	     // 1.2) extract RID-specific payload (apphost, runtimes/, deps/runtimeconfig, etc.)
	     ResourceTreeExtractor.extractDirectory(GrpcServerLauncher.class, "/msraw/thermo/bin/" + rid, workDir);

        // 2) Build command
        String exeName = isWin() ? "MSRaw.Thermo.Server.exe" : "MSRaw.Thermo.Server";
        Path exe = workDir.resolve(exeName);
        if (!Files.isRegularFile(exe)) throw new FileNotFoundException("Server exe not found: " + exe);

        List<String> cmd = new ArrayList<>();
        if (isMac() && isArm64()) cmd.addAll(Arrays.asList("arch", "-x86_64")); // Rosetta
        cmd.add(exe.toAbsolutePath().toString());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().put("MSRAW_THERMO_URL", "http://127.0.0.1:" + port);
        pb.directory(workDir.toFile()); // critical: loader probes base dir for ThermoFisher.*.dll + runtimes/
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);

        this.proc = pb.start();

        // 3) Wait for port readiness
        long start = System.nanoTime();
        final long timeoutNs = 20_000_000_000L;
        while (true) {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress("127.0.0.1", port), 250);
                break;
            } catch (IOException e) {
                if (!proc.isAlive()) {
                    throw new IOException("Thermo server exited early with code " + proc.exitValue(), e);
                }
                if (System.nanoTime() - start > timeoutNs) {
                    throw new IOException("Thermo server did not open port " + port + " within 20s", e);
                }
                try { Thread.sleep(150); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for server", ie);
                }
            }
        }
    }

    int port() { return port; }

    @Override public void close() {
        try { if (proc != null) proc.destroy(); } catch (Exception ignored) {}
        // Clean up temp directory tree
        if (workDir != null) {
            try (var walk = Files.walk(workDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
            } catch (Exception ignored) {}
        }
    }
}