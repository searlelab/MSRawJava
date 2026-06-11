package org.searlelab.msrawjava.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.model.Peak;
import org.searlelab.msrawjava.model.PeakInterface;
import org.searlelab.msrawjava.model.PeakWithIMS;

class RawFileConvertersHelperReflectionTest {

	@Test
	void addBrukerMergedPrefixHandlesNullBlankAndAlreadyMergedNames() {
		assertEquals("merged=7", RawFileConverters.addBrukerMergedPrefix(null, 7));
		assertEquals("merged=7", RawFileConverters.addBrukerMergedPrefix("  ", 7));
		assertEquals("merged=7 scan=3", RawFileConverters.addBrukerMergedPrefix(" scan=3 ", 7));
		assertEquals("merged=1 scan=3", RawFileConverters.addBrukerMergedPrefix("merged=1 scan=3", 7));
	}

	@Test
	void privateFutureAndPeakHelpersCoverFailureBranches() throws Exception {
		CompletableFuture<String> failed=new CompletableFuture<>();
		failed.completeExceptionally(new IllegalStateException("boom"));
		assertNull(invokeGetOrNull(failed));

		CompletableFuture<ArrayList<?>> failedDemux=new CompletableFuture<>();
		failedDemux.completeExceptionally(new IllegalStateException("boom"));
		assertTrue(invokeGetDemuxResult(failedDemux).isEmpty());

		ArrayList<PeakInterface> nonImsPeaks=new ArrayList<>();
		nonImsPeaks.add(new Peak(100.0, 10.0f));
		assertNull(invokeAsImsPeaks(nonImsPeaks));

		ArrayList<PeakInterface> imsPeaks=new ArrayList<>();
		imsPeaks.add(new PeakWithIMS(100.0, 10.0f, 1.2f));
		assertEquals(1, invokeAsImsPeaks(imsPeaks).size());
	}

	@Test
	void privateWorkerCountUsesThreadPoolCoreSize() throws Exception {
		ExecutorService pool=Executors.newFixedThreadPool(2);
		try {
			assertEquals(2, invokeGetWorkerCount(pool));
		} finally {
			pool.shutdownNow();
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> T invokeGetOrNull(CompletableFuture<T> future) throws Exception {
		Method method=RawFileConverters.class.getDeclaredMethod("getOrNull", java.util.concurrent.Future.class);
		method.setAccessible(true);
		return (T)method.invoke(null, future);
	}

	@SuppressWarnings("unchecked")
	private static ArrayList<?> invokeGetDemuxResult(CompletableFuture<ArrayList<?>> future) throws Exception {
		Method method=RawFileConverters.class.getDeclaredMethod("getDemuxResult", java.util.concurrent.Future.class);
		method.setAccessible(true);
		return (ArrayList<?>)method.invoke(null, future);
	}

	@SuppressWarnings("unchecked")
	private static ArrayList<PeakWithIMS> invokeAsImsPeaks(ArrayList<PeakInterface> peaks) throws Exception {
		Method method=RawFileConverters.class.getDeclaredMethod("asImsPeaks", ArrayList.class);
		method.setAccessible(true);
		return (ArrayList<PeakWithIMS>)method.invoke(null, peaks);
	}

	private static int invokeGetWorkerCount(ExecutorService pool) throws Exception {
		Method method=RawFileConverters.class.getDeclaredMethod("getWorkerCount", ExecutorService.class);
		method.setAccessible(true);
		return (Integer)method.invoke(null, pool);
	}
}
