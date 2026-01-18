package org.searlelab.msrawjava.io.thermo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.OptionalInt;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for ThermoServerPool state management.
 *
 * Note: These tests only verify initial state and methods that don't trigger
 * server startup. Tests that call startAsync() are avoided because they trigger
 * background resource extraction that can hang the test suite.
 * Full integration tests are in ThermoRawFileSmokeIT.
 */
class ThermoServerPoolTest {

	@BeforeEach
	void setUp() {
		// Ensure clean state before each test
		ThermoServerPool.shutdown();
	}

	@AfterEach
	void tearDown() {
		// Clean up after each test
		ThermoServerPool.shutdown();
	}

	@Test
	void initialStateIsNotReady() {
		assertFalse(ThermoServerPool.isReady());
	}

	@Test
	void initialStateIsNotStarting() {
		assertFalse(ThermoServerPool.isStarting());
	}

	@Test
	void portIfReadyReturnsEmptyInitially() {
		OptionalInt port = ThermoServerPool.portIfReady();
		assertFalse(port.isPresent());
	}

	@Test
	void shutdownIsIdempotent() {
		// Multiple shutdowns should not throw
		ThermoServerPool.shutdown();
		ThermoServerPool.shutdown();
		ThermoServerPool.shutdown();

		assertFalse(ThermoServerPool.isReady());
	}

	@Test
	void shutdownBeforeStartIsHarmless() {
		// Shutdown without ever starting should be fine
		ThermoServerPool.shutdown();
		assertFalse(ThermoServerPool.isReady());
		assertFalse(ThermoServerPool.isStarting());
	}

	@Test
	void isReadyReturnsFalseWhenNotStarted() {
		assertFalse(ThermoServerPool.isReady());
	}

	@Test
	void isReadyDelegatesToPortIfReady() {
		// isReady() is implemented as portIfReady().isPresent()
		assertEquals(ThermoServerPool.portIfReady().isPresent(), ThermoServerPool.isReady());
	}

	@Test
	void portIfReadyIsEmptyAfterShutdown() {
		ThermoServerPool.shutdown();
		OptionalInt port = ThermoServerPool.portIfReady();
		assertFalse(port.isPresent());
	}

	@Test
	void isStartingIsFalseAfterShutdown() {
		ThermoServerPool.shutdown();
		assertFalse(ThermoServerPool.isStarting());
	}
}
