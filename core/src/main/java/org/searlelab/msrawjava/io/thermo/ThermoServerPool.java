package org.searlelab.msrawjava.io.thermo;

import java.io.IOException;
import java.time.Duration;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.searlelab.msrawjava.logging.Logger;

/**
 * ThermoServerPool provides a synchronized way to access a single GrpcServerLauncher instance, lazily starting
 * the local Thermo server on first use and exposing its listening port to clients.
 */
public final class ThermoServerPool {
	private static final int MAX_START_ATTEMPTS=3;
	private static final long RETRY_SLEEP_MS=250L;
	// Single daemon thread so it won't keep the JVM alive
	private static final ExecutorService EXEC=Executors.newSingleThreadExecutor(r -> {
		Thread t=new Thread(r, "thermo-server-launcher");
		t.setDaemon(true);
		return t;
	});

	// One shared future for the singleton launcher
	private static volatile CompletableFuture<GrpcServerLauncher> launcherFuture;

	private ThermoServerPool() {
	}

	/** Lazily start the Thermo server in the background (idempotent). */
	public static synchronized CompletableFuture<Integer> startAsync() {
		if (launcherFuture==null||launcherFuture.isCompletedExceptionally()||launcherFuture.isCancelled()) {
			launcherFuture=CompletableFuture.supplyAsync(() -> {
				int attempt=0;
				while (true) {
					attempt++;
					try {
						return new GrpcServerLauncher(); // <- your slow constructor
					} catch (Exception e) {
						Logger.logLine("Thermo server: startup attempt "+attempt+" failed: "+e.getClass().getSimpleName()+" - "+e.getMessage());
						if (attempt>=MAX_START_ATTEMPTS) {
							throw new CompletionException(e);
						}
						try {
							Thread.sleep(RETRY_SLEEP_MS);
						} catch (InterruptedException ie) {
							Thread.currentThread().interrupt();
							throw new CompletionException(ie);
						}
					}
				}
			}, EXEC);
		}
		// Map to port when ready
		return launcherFuture.thenApply(launcher -> {
			return launcher.port();
		});
	}

	/** blocking call with a sensible timeout. */
	public static int port() throws IOException, InterruptedException {
		try {
			return port(Duration.ofSeconds(60));
		} catch (TimeoutException e) {
			throw new IOException("Timed out waiting for Thermo server to start.", e);
		}
	}

	/** Block for readiness with an explicit timeout. */
	public static int port(Duration timeout) throws IOException, InterruptedException, TimeoutException {
		CompletableFuture<Integer> f=startAsync();
		try {
			return f.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
		} catch (ExecutionException ex) {
			Throwable cause=ex.getCause();
			if (cause instanceof IOException) throw (IOException)cause;
			if (cause instanceof InterruptedException) {
				Thread.currentThread().interrupt();
				throw (InterruptedException)cause;
			}
			// Wrap any other startup failure
			throw new IOException("Failed to start Thermo server.", cause);
		}
	}

	/** Non-blocking peek: returns a port if ready, otherwise empty. */
	public static OptionalInt portIfReady() {
		CompletableFuture<GrpcServerLauncher> f=launcherFuture;
		if (f==null||!f.isDone()||f.isCompletedExceptionally()||f.isCancelled()) {
			return OptionalInt.empty();
		}

		GrpcServerLauncher l=f.getNow(null);
		if (l==null) return OptionalInt.empty(); // defensive
		return OptionalInt.of(l.port());
	}

	/** Whether startup has begun but not yet finished. */
	public static boolean isStarting() {
		CompletableFuture<GrpcServerLauncher> f=launcherFuture;
		return f!=null&&!f.isDone();
	}

	/** Whether the server is up and the port is available. */
	public static boolean isReady() {
		return portIfReady().isPresent();
	}

	/** Shut down the server (safe to call any time). */
	public static synchronized void shutdown() {
		CompletableFuture<GrpcServerLauncher> f=launcherFuture;
		launcherFuture=null;
		if (f!=null) {
			// Close when available (even if still starting)
			f.whenComplete((launcher, ex) -> {
				if (launcher!=null) {
					try {
						launcher.close();
					} catch (Exception ignored) {
						Logger.errorException(ignored);
					}
				}
			});
			f.cancel(true);
		}
	}
}
