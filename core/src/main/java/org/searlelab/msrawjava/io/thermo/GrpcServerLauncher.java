package org.searlelab.msrawjava.io.thermo;

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
import java.util.stream.Stream;

import org.searlelab.msrawjava.io.utils.ResourceTreeExtractor;
import org.searlelab.msrawjava.logging.Logger;

/**
 * GrpcServerLauncher prepares and supervises the self-contained Thermo gRPC server used by the Java client: it extracts
 * the published server bundle from resources into a temporary work directory, selects a free TCP port, starts the child
 * process, blocks until the port is reachable, exposes the chosen port to callers, and on close tears down the process
 * and deletes the extracted files. The class infers a runtime identifier from the host OS, keeps launch state per-JVM
 * instance, and serves as the package-level mechanism behind ThermoServerPool.
 */
final class GrpcServerLauncher implements AutoCloseable {
	private static final String OS_LINUX="linux";
	private static final String OS_OSX="osx";
	private static final String OS_WIN="win";
	private final int port;
	private final Process proc;
	private final Path workDir; // temp dir holding extracted publish tree

	static int pickFreePort() throws IOException {
		try (ServerSocket ss=new ServerSocket(0)) {
			ss.setReuseAddress(true);
			return ss.getLocalPort();
		}
	}

	static boolean isMac() {
		return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac");
	}

	static boolean isWin() {
		return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains(OS_WIN);
	}

	static boolean isArm64() {
		String a=System.getProperty("os.arch").toLowerCase(Locale.ROOT);
		return a.contains("aarch64")||a.contains("arm64");
	}

	static String rid() {
		String osPart=getOS();
		//String archPart = isArm64() ? "arm64" : "x64";

		if (osPart.equals(OS_WIN)) return "win-x64";

		// Force x64 server (Rosetta) on Apple Silicon until Thermo makes a compatible library
		//if (osPart.equals("osx")) return "osx-" + archPart;
		if (osPart.equals(OS_OSX)) return "osx-x64";

		return "linux-x64";
	}

	private static String getOS() {
		String os=System.getProperty("os.name").toLowerCase(Locale.ROOT);
		return os.contains(OS_WIN)?OS_WIN:(os.contains("mac")||os.contains("darwin"))?OS_OSX:OS_LINUX;
	}

	GrpcServerLauncher() throws IOException, InterruptedException {
		this(pickFreePort());
	}

	GrpcServerLauncher(int port) throws IOException, InterruptedException {
		this.port=port;

		// 1) Extract entire published folder from resources into a fresh temp dir
		String rid=rid();
		this.workDir=Files.createTempDirectory("msraw-thermo-");

		// Extract everything including runtimes/
		// 1.1) extract common managed DLLs
		Logger.logLine("Thermo server: extracting common runtime files...");
		long t0=System.nanoTime();
		ResourceTreeExtractor.extractDirectory(GrpcServerLauncher.class, "/msraw/thermo/bin/common", workDir);
		Logger.logLine(String.format(Locale.ROOT, "Thermo server: common files extracted in %.2f s", (System.nanoTime()-t0)/1_000_000_000.0));

		// 1.2) extract RID-specific payload (apphost, runtimes/, deps/runtimeconfig, etc.)
		Logger.logLine("Thermo server: extracting "+rid+" runtime files...");
		long t1=System.nanoTime();
		ResourceTreeExtractor.extractDirectory(GrpcServerLauncher.class, "/msraw/thermo/bin/"+rid, workDir);
		Logger.logLine(String.format(Locale.ROOT, "Thermo server: "+rid+" files extracted in %.2f s", (System.nanoTime()-t1)/1_000_000_000.0));

		// 2) Build command
		String exeName=isWin()?"MSRaw.Thermo.Server.exe":"MSRaw.Thermo.Server";
		Path exe=workDir.resolve(exeName);
		if (!Files.isRegularFile(exe)) throw new FileNotFoundException("Server exe not found: "+exe);

		List<String> cmd=new ArrayList<>();
		if (isMac()&&isArm64()) cmd.addAll(Arrays.asList("arch", "-x86_64")); // Rosetta
		cmd.add(exe.toAbsolutePath().toString());

		ProcessBuilder pb=new ProcessBuilder(cmd);
		pb.environment().put("MSRAW_THERMO_URL", "http://127.0.0.1:"+port);
		pb.directory(workDir.toFile()); // critical: loader probes base dir for ThermoFisher.*.dll + runtimes/
		pb.redirectErrorStream(true);
		if (Logger.getConsoleStatus()!=null&&Logger.getConsoleStatus().isEnabled()) {
			pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
		} else {
			pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
		}

		Logger.logLine("Thermo server: starting process...");
		long t2=System.nanoTime();
		this.proc=pb.start();
		Logger.logLine(String.format(Locale.ROOT, "Thermo server: process started (pid %d) in %.2f s", proc.pid(), (System.nanoTime()-t2)/1_000_000_000.0));

		// 3) Wait for port readiness
		Logger.logLine("Thermo server: waiting for gRPC port "+port+" to accept connections...");
		long start=System.nanoTime();
		final long timeoutNs=60_000_000_000L;
		while (true) {
			try (Socket s=new Socket()) {
				s.connect(new InetSocketAddress("127.0.0.1", port), 250);
				break;
			} catch (IOException e) {
				if (!proc.isAlive()) {
					throw new IOException("Thermo server exited early with code "+proc.exitValue(), e);
				}
				if (System.nanoTime()-start>timeoutNs) {
					throw new IOException("Thermo server did not open port "+port+" within 60s", e);
				}
				try {
					Thread.sleep(150);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					throw new IOException("Interrupted while waiting for server", ie);
				}
			}
		}
		Logger.logLine(String.format(Locale.ROOT, "Thermo server: port "+port+" ready in %.2f s", (System.nanoTime()-start)/1_000_000_000.0));
	}

	// Test-only constructor helper to avoid spinning up native processes.
	static GrpcServerLauncher forTest(int port, Process proc, Path workDir) {
		return new GrpcServerLauncher(port, proc, workDir);
	}

	private GrpcServerLauncher(int port, Process proc, Path workDir) {
		this.port=port;
		this.proc=proc;
		this.workDir=workDir;
	}

	int port() {
		return port;
	}

	@Override
	public void close() {
		try {
			if (proc!=null) proc.destroy();
		} catch (Exception ignored) {
		}
		// Clean up temp directory tree
		if (workDir!=null) {
			try (Stream<Path> walk=Files.walk(workDir)) {
				walk.sorted(Comparator.reverseOrder()).forEach(p -> {
					try {
						Files.deleteIfExists(p);
					} catch (Exception ignored) {
					}
				});
			} catch (Exception ignored) {
			}
		}
	}
}
