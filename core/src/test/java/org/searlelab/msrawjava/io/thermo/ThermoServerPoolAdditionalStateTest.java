package org.searlelab.msrawjava.io.thermo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ThermoServerPoolAdditionalStateTest {

	@AfterEach
	void tearDown() throws Exception {
		ThermoServerPool.shutdown();
		setLauncherFuture(null);
	}

	@Test
	void portIfReadyReturnsInjectedCompletedLauncherPort() throws Exception {
		setLauncherFuture(CompletableFuture.completedFuture(GrpcServerLauncher.forTest(4567, null, null)));

		OptionalInt port=ThermoServerPool.portIfReady();
		assertTrue(port.isPresent());
		assertEquals(4567, port.getAsInt());
		assertTrue(ThermoServerPool.isReady());
		assertFalse(ThermoServerPool.isStarting());
	}

	@Test
	void portIfReadyIsEmptyForCancelledExceptionalAndNullCompletedFutures() throws Exception {
		CompletableFuture<GrpcServerLauncher> cancelled=new CompletableFuture<>();
		cancelled.cancel(true);
		setLauncherFuture(cancelled);
		assertFalse(ThermoServerPool.portIfReady().isPresent());

		CompletableFuture<GrpcServerLauncher> failed=new CompletableFuture<>();
		failed.completeExceptionally(new IOException("boom"));
		setLauncherFuture(failed);
		assertFalse(ThermoServerPool.portIfReady().isPresent());

		setLauncherFuture(CompletableFuture.completedFuture(null));
		assertFalse(ThermoServerPool.portIfReady().isPresent());
	}

	@Test
	void isStartingReflectsIncompleteFuture() throws Exception {
		setLauncherFuture(new CompletableFuture<>());
		assertTrue(ThermoServerPool.isStarting());
		assertFalse(ThermoServerPool.isReady());
	}

	@Test
	void portIfReadyTreatsExceptionalFutureAsNotReady() throws Exception {
		CompletableFuture<GrpcServerLauncher> ioFailure=new CompletableFuture<>();
		ioFailure.completeExceptionally(new IllegalStateException("cannot start"));
		setLauncherFuture(ioFailure);
		assertFalse(ThermoServerPool.portIfReady().isPresent());
		assertFalse(ThermoServerPool.isReady());
	}

	@Test
	void portPropagatesTimeoutForIncompleteFuture() throws Exception {
		setLauncherFuture(new CompletableFuture<>());
		assertThrows(TimeoutException.class, () -> ThermoServerPool.port(Duration.ofMillis(1)));
	}

	private static void setLauncherFuture(CompletableFuture<GrpcServerLauncher> future) throws Exception {
		Field field=ThermoServerPool.class.getDeclaredField("launcherFuture");
		field.setAccessible(true);
		field.set(null, future);
	}
}
