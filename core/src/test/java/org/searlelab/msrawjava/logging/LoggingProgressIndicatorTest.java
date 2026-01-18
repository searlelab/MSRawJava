package org.searlelab.msrawjava.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class LoggingProgressIndicatorTest {

	@Test
	void defaultConstructorStartsAtZeroProgress() {
		LoggingProgressIndicator indicator = new LoggingProgressIndicator();
		assertEquals(0.0f, indicator.getTotalProgress(), 1e-6);
	}

	@Test
	void defaultConstructorIsNotCanceled() {
		LoggingProgressIndicator indicator = new LoggingProgressIndicator();
		assertFalse(indicator.isCanceled());
	}

	@Test
	void constructorWithPrintFlagWorks() {
		LoggingProgressIndicator withPrint = new LoggingProgressIndicator(true);
		LoggingProgressIndicator withoutPrint = new LoggingProgressIndicator(false);

		// Both should start at 0
		assertEquals(0.0f, withPrint.getTotalProgress(), 1e-6);
		assertEquals(0.0f, withoutPrint.getTotalProgress(), 1e-6);
	}

	@Test
	void updateWithProgressSetsProgress() {
		LoggingProgressIndicator indicator = new LoggingProgressIndicator();

		indicator.update("Starting", 0.25f);
		assertEquals(0.25f, indicator.getTotalProgress(), 1e-6);

		indicator.update("Halfway", 0.5f);
		assertEquals(0.5f, indicator.getTotalProgress(), 1e-6);

		indicator.update("Done", 1.0f);
		assertEquals(1.0f, indicator.getTotalProgress(), 1e-6);
	}

	@Test
	void updateWithoutProgressKeepsCurrentProgress() {
		LoggingProgressIndicator indicator = new LoggingProgressIndicator();

		indicator.update("Starting", 0.5f);
		assertEquals(0.5f, indicator.getTotalProgress(), 1e-6);

		indicator.update("Still working");
		assertEquals(0.5f, indicator.getTotalProgress(), 1e-6);
	}

	@Test
	void setCanceledWorks() {
		LoggingProgressIndicator indicator = new LoggingProgressIndicator();

		assertFalse(indicator.isCanceled());

		indicator.setCanceled(true);
		assertTrue(indicator.isCanceled());

		indicator.setCanceled(false);
		assertFalse(indicator.isCanceled());
	}

	@Test
	void progressCanGoBackwards() {
		// Not necessarily desired behavior, but documenting current behavior
		LoggingProgressIndicator indicator = new LoggingProgressIndicator();

		indicator.update("Forward", 0.8f);
		assertEquals(0.8f, indicator.getTotalProgress(), 1e-6);

		indicator.update("Backward", 0.3f);
		assertEquals(0.3f, indicator.getTotalProgress(), 1e-6);
	}

	@Test
	void progressCanExceedOne() {
		// Documenting current behavior - no bounds checking
		LoggingProgressIndicator indicator = new LoggingProgressIndicator();

		indicator.update("Over", 1.5f);
		assertEquals(1.5f, indicator.getTotalProgress(), 1e-6);
	}

	@Test
	void progressCanBeNegative() {
		// Documenting current behavior - no bounds checking
		LoggingProgressIndicator indicator = new LoggingProgressIndicator();

		indicator.update("Negative", -0.5f);
		assertEquals(-0.5f, indicator.getTotalProgress(), 1e-6);
	}

	@Test
	void implementsProgressIndicatorInterface() {
		ProgressIndicator indicator = new LoggingProgressIndicator();

		indicator.update("Test", 0.5f);
		assertEquals(0.5f, indicator.getTotalProgress(), 1e-6);
		assertFalse(indicator.isCanceled());
	}

	@Test
	void threadSafeProgressUpdates() throws InterruptedException {
		LoggingProgressIndicator indicator = new LoggingProgressIndicator();
		int threadCount = 10;
		int updatesPerThread = 100;
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CountDownLatch latch = new CountDownLatch(threadCount);
		AtomicInteger updateCount = new AtomicInteger(0);

		for (int t = 0; t < threadCount; t++) {
			final int threadId = t;
			executor.submit(() -> {
				try {
					for (int i = 0; i < updatesPerThread; i++) {
						float progress = (threadId * updatesPerThread + i) / 1000.0f;
						indicator.update("Thread " + threadId, progress);
						updateCount.incrementAndGet();
					}
				} finally {
					latch.countDown();
				}
			});
		}

		assertTrue(latch.await(10, TimeUnit.SECONDS));
		executor.shutdown();

		// All updates should have completed
		assertEquals(threadCount * updatesPerThread, updateCount.get());

		// Progress should be some valid float (last write wins due to volatile)
		float finalProgress = indicator.getTotalProgress();
		assertTrue(finalProgress >= 0.0f && finalProgress <= 1.0f,
			"Final progress should be valid: " + finalProgress);
	}

	@Test
	void threadSafeCancellation() throws InterruptedException {
		LoggingProgressIndicator indicator = new LoggingProgressIndicator();
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch canCheck = new CountDownLatch(1);

		Thread worker = new Thread(() -> {
			started.countDown();
			try {
				canCheck.await();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			// Check cancellation after main thread sets it
			assertTrue(indicator.isCanceled());
		});

		worker.start();
		started.await();

		indicator.setCanceled(true);
		canCheck.countDown();

		worker.join(5000);
		assertFalse(worker.isAlive());
	}

	@Test
	void multipleIndicatorsAreIndependent() {
		LoggingProgressIndicator indicator1 = new LoggingProgressIndicator();
		LoggingProgressIndicator indicator2 = new LoggingProgressIndicator();

		indicator1.update("One", 0.3f);
		indicator2.update("Two", 0.7f);

		assertEquals(0.3f, indicator1.getTotalProgress(), 1e-6);
		assertEquals(0.7f, indicator2.getTotalProgress(), 1e-6);

		indicator1.setCanceled(true);
		assertTrue(indicator1.isCanceled());
		assertFalse(indicator2.isCanceled());
	}
}
