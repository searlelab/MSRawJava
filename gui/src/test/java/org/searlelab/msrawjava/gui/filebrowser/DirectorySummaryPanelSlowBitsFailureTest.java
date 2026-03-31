package org.searlelab.msrawjava.gui.filebrowser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

class DirectorySummaryPanelSlowBitsFailureTest {

	@Test
	void expectedSlowBitsFailureSummary_handlesInterruptedCancellationChain() {
		InterruptedException interrupted=new InterruptedException();
		RuntimeException cancelled=new RuntimeException("CANCELLED: Thread interrupted", interrupted);
		RuntimeException failure=new RuntimeException(cancelled);

		assertEquals("previous request was cancelled by user", DirectorySummaryPanel.expectedSlowBitsFailureSummary(failure));
	}

	@Test
	void expectedSlowBitsFailureSummary_handlesCancelledWithoutSpaceAfterColon() {
		RuntimeException failure=new RuntimeException("io.grpc.StatusRuntimeException:CANCELLED");

		assertEquals("previous request was cancelled by user", DirectorySummaryPanel.expectedSlowBitsFailureSummary(failure));
	}

	@Test
	void expectedSlowBitsFailureSummary_handlesCancelledGrpcStatusCode() {
		StatusRuntimeException failure=new StatusRuntimeException(Status.CANCELLED);

		assertEquals("previous request was cancelled by user", DirectorySummaryPanel.expectedSlowBitsFailureSummary(failure));
	}

	@Test
	void expectedSlowBitsFailureSummary_handlesInstrumentIndexError() {
		RuntimeException failure=new RuntimeException("No valid instrument index was found");

		assertEquals("Thermo RAW has no usable MS instrument index", DirectorySummaryPanel.expectedSlowBitsFailureSummary(failure));
	}

	@Test
	void expectedSlowBitsFailureSummary_returnsNullForUnavailableReaderFailure() {
		StatusRuntimeException failure=new StatusRuntimeException(Status.UNAVAILABLE);

		assertNull(DirectorySummaryPanel.expectedSlowBitsFailureSummary(failure));
	}

	@Test
	void expectedSlowBitsFailureSummary_returnsNullForUnexpectedFailure() {
		RuntimeException failure=new RuntimeException("completely unrelated failure");

		assertNull(DirectorySummaryPanel.expectedSlowBitsFailureSummary(failure));
	}

	@Test
	void thermoReaderUnavailable_detectsGrpcUnavailableStatus() {
		StatusRuntimeException failure=new StatusRuntimeException(Status.UNAVAILABLE);

		assertEquals(true, DirectorySummaryPanel.isThermoReaderUnavailable(failure));
	}

	@Test
	void thermoReaderUnavailable_detectsConnectionRefusedMessage() {
		RuntimeException failure=new RuntimeException(new java.net.ConnectException("Connection refused"));

		assertEquals(true, DirectorySummaryPanel.isThermoReaderUnavailable(failure));
	}

	@Test
	void safeConvertRowIndexToView_returnsHiddenForOutOfRangeIndex() {
		assertEquals(-1, DirectorySummaryPanel.safeConvertRowIndexToView(null, 0));
	}

	@Test
	void removeNotify_shutsDownSlowBitsPoolWithoutInterruptingRunningWork() throws Exception {
		ExecutorService pool=Executors.newSingleThreadExecutor();
		CountDownLatch started=new CountDownLatch(1);
		CountDownLatch release=new CountDownLatch(1);
		AtomicBoolean interrupted=new AtomicBoolean(false);

		pool.submit(() -> {
			started.countDown();
			try {
				release.await(2, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				interrupted.set(true);
				Thread.currentThread().interrupt();
			}
		});

		assertTrue(started.await(2, TimeUnit.SECONDS));
		DirectorySummaryPanel.shutdownSlowBitsPool(pool);
		release.countDown();
		assertTrue(pool.awaitTermination(2, TimeUnit.SECONDS));
		assertFalse(interrupted.get());
	}

	@Test
	void shouldSkipThermoRetryOnClose_isTrueOnlyWhenPanelIsClosed() {
		assertTrue(DirectorySummaryPanel.shouldSkipThermoRetryOnClose(true));
		assertFalse(DirectorySummaryPanel.shouldSkipThermoRetryOnClose(false));
	}
}
