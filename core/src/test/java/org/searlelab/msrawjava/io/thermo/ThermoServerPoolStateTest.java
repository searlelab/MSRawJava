package org.searlelab.msrawjava.io.thermo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ThermoServerPoolStateTest {

	@TempDir
	Path tmp;

	@BeforeEach
	void setUp() {
		resetLauncherFuture();
	}

	@AfterEach
	void tearDown() {
		ThermoServerPool.shutdown();
		resetLauncherFuture();
	}

	@Test
	void portIfReadyIsEmptyWhenNotStarted() {
		OptionalInt port=ThermoServerPool.portIfReady();
		assertTrue(port.isEmpty());
		assertFalse(ThermoServerPool.isStarting());
		assertFalse(ThermoServerPool.isReady());
	}

	@Test
	void reportsReadyWhenLauncherFutureCompleted() throws Exception {
		GrpcServerLauncher launcher=allocateLauncher(43123, null, null);
		setLauncherFuture(CompletableFuture.completedFuture(launcher));

		OptionalInt port=ThermoServerPool.portIfReady();
		assertTrue(port.isPresent());
		assertEquals(43123, port.getAsInt());
		assertFalse(ThermoServerPool.isStarting());
		assertTrue(ThermoServerPool.isReady());
	}

	@Test
	void isStartingTrueWhenFuturePending() {
		CompletableFuture<GrpcServerLauncher> future=new CompletableFuture<>();
		setLauncherFuture(future);
		assertTrue(ThermoServerPool.isStarting());
		assertFalse(ThermoServerPool.isReady());
	}

	@Test
	void portReturnsFromPrecompletedFuture() throws Exception {
		GrpcServerLauncher launcher=allocateLauncher(45219, null, null);
		setLauncherFuture(CompletableFuture.completedFuture(launcher));

		int port=ThermoServerPool.port(Duration.ofMillis(50));
		assertEquals(45219, port);
	}

	@Test
	void shutdownClosesLauncherAndDeletesWorkDir() throws Exception {
		Path workDir=tmp.resolve("thermo-work");
		Files.createDirectories(workDir.resolve("nested"));
		Files.writeString(workDir.resolve("nested/file.txt"), "data");

		DummyProcess proc=new DummyProcess();
		GrpcServerLauncher launcher=allocateLauncher(50123, proc, workDir);
		setLauncherFuture(CompletableFuture.completedFuture(launcher));

		ThermoServerPool.shutdown();

		assertTrue(proc.destroyed);
		assertFalse(Files.exists(workDir));
	}

	private static void resetLauncherFuture() {
		try {
			Field field=ThermoServerPool.class.getDeclaredField("launcherFuture");
			field.setAccessible(true);
			field.set(null, null);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Failed to reset launcherFuture", e);
		}
	}

	private static void setLauncherFuture(CompletableFuture<GrpcServerLauncher> future) {
		try {
			Field field=ThermoServerPool.class.getDeclaredField("launcherFuture");
			field.setAccessible(true);
			field.set(null, future);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Failed to set launcherFuture", e);
		}
	}

	private static GrpcServerLauncher allocateLauncher(int port, Process proc, Path workDir) throws Exception {
		return GrpcServerLauncher.forTest(port, proc, workDir);
	}

	private static void setField(Object target, String name, Object value) throws Exception {
		Field field=target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}

	static class DummyProcess extends Process {
		boolean destroyed=false;

		@Override
		public void destroy() {
			destroyed=true;
		}

		@Override
		public Process destroyForcibly() {
			destroyed=true;
			return this;
		}

		@Override
		public boolean isAlive() {
			return !destroyed;
		}

		@Override
		public int waitFor() throws InterruptedException {
			TimeUnit.MILLISECONDS.sleep(1);
			return 0;
		}

		@Override
		public int exitValue() {
			if (isAlive()) throw new IllegalThreadStateException("Process still running");
			return 0;
		}

		@Override
		public java.io.OutputStream getOutputStream() {
			return java.io.OutputStream.nullOutputStream();
		}

		@Override
		public java.io.InputStream getInputStream() {
			return java.io.InputStream.nullInputStream();
		}

		@Override
		public java.io.InputStream getErrorStream() {
			return java.io.InputStream.nullInputStream();
		}
	}
}
