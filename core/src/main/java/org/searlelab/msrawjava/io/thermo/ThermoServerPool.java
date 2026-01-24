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
import java.util.concurrent.atomic.AtomicReference;

/**
 * ThermoServerPool provides a synchronized way to access a single GrpcServerLauncher instance, lazily starting
 * the local Thermo server on first use and exposing its listening port to clients.
 */
public final class ThermoServerPool {
	// Single daemon thread so it won't keep the JVM alive
	private static final ExecutorService EXEC=Executors.newSingleThreadExecutor(r -> {
		Thread t=new Thread(r, "thermo-server-launcher");
		t.setDaemon(true);
		return t;
	});

	// One shared future for the singleton launcher
	private static volatile CompletableFuture<GrpcServerLauncher> launcherFuture;
	private static final AtomicReference<String> lastStatusNote=new AtomicReference<>();
	private static final AtomicReference<Throwable> lastStartError=new AtomicReference<>();

	private ThermoServerPool() {
	}

	/** Lazily start the Thermo server in the background (idempotent). */
	public static synchronized CompletableFuture<Integer> startAsync() {
		if (launcherFuture==null||launcherFuture.isCompletedExceptionally()||launcherFuture.isCancelled()) {
			lastStatusNote.set(null);
			lastStartError.set(null);
			launcherFuture=CompletableFuture.supplyAsync(() -> {
				try {
					return new GrpcServerLauncher(ThermoServerPool::noteStatusLine); // <- your slow constructor
				} catch (Exception e) {
					throw new CompletionException(e);
				}
			}, EXEC);
			launcherFuture.whenComplete((launcher, ex) -> {
				if (ex!=null) {
					Throwable cause=(ex instanceof CompletionException&&ex.getCause()!=null)?ex.getCause():ex;
					lastStartError.set(cause);
				} else {
					lastStartError.set(null);
				}
			});
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

	/** Whether startup finished with an error. */
	public static boolean isFailed() {
		CompletableFuture<GrpcServerLauncher> f=launcherFuture;
		return f!=null&&f.isCompletedExceptionally();
	}

	/** Last status line reported by the Thermo server process (may be null). */
	public static String lastStatusNote() {
		return lastStatusNote.get();
	}

	/** Last startup error, if the server failed to start. */
	public static Throwable lastStartError() {
		return lastStartError.get();
	}

	private static void noteStatusLine(String line) {
		if (line==null) return;
		String trimmed=line.trim();
		if (!trimmed.isEmpty()) lastStatusNote.set(trimmed);
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
					}
				}
			});
			f.cancel(true);
		}
	}
}
