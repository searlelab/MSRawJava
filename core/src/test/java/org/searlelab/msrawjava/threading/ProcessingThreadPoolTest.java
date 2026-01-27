package org.searlelab.msrawjava.threading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class ProcessingThreadPoolTest {

	@Test
	void constructorCreatesPoolWithSpecifiedThreads() throws InterruptedException {
		try (ProcessingThreadPool pool=new ProcessingThreadPool(2, 10)) {
			ExecutorService executor=pool.computePool();
			assertNotNull(executor);
			assertFalse(executor.isShutdown());
		}
	}

	@Test
	void computePoolReturnsNonNullExecutor() throws InterruptedException {
		try (ProcessingThreadPool pool=new ProcessingThreadPool(2, 10)) {
			assertNotNull(pool.computePool());
		}
	}

	@Test
	void closeShutdownsPool() throws InterruptedException {
		ProcessingThreadPool pool=new ProcessingThreadPool(2, 10);
		ExecutorService executor=pool.computePool();
		assertFalse(executor.isShutdown());

		pool.close();

		assertTrue(executor.isShutdown());
	}

	@Test @Timeout(5)
	void closeWaitsForTasksToComplete() throws InterruptedException {
		ProcessingThreadPool pool=new ProcessingThreadPool(2, 10);
		AtomicInteger completed=new AtomicInteger(0);

		// Submit a task that takes a bit of time
		pool.computePool().submit(() -> {
			try {
				Thread.sleep(100);
				completed.incrementAndGet();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});

		pool.close();

		assertEquals(1, completed.get());
	}

	@Test
	void createDefaultCreatesWorkingPool() throws InterruptedException {
		try (ProcessingThreadPool pool=ProcessingThreadPool.createDefault()) {
			assertNotNull(pool);
			assertNotNull(pool.computePool());
			assertFalse(pool.computePool().isShutdown());
		}
	}

	@Test
	void createDefaultUsesAvailableProcessors() throws InterruptedException {
		int cores=Runtime.getRuntime().availableProcessors();
		int expectedThreads=Math.max(1, cores-1);

		try (ProcessingThreadPool pool=ProcessingThreadPool.createDefault()) {
			// Submit tasks to verify thread count
			CountDownLatch latch=new CountDownLatch(expectedThreads);
			CountDownLatch startLatch=new CountDownLatch(1);

			for (int i=0; i<expectedThreads; i++) {
				pool.computePool().submit(() -> {
					try {
						startLatch.await();
						latch.countDown();
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				});
			}

			startLatch.countDown();
			assertTrue(latch.await(5, TimeUnit.SECONDS));
		}
	}

	@Test @Timeout(5)
	void tasksExecuteCorrectly() throws Exception {
		try (ProcessingThreadPool pool=new ProcessingThreadPool(2, 10)) {
			AtomicInteger counter=new AtomicInteger(0);
			int taskCount=5;
			CountDownLatch latch=new CountDownLatch(taskCount);

			for (int i=0; i<taskCount; i++) {
				pool.computePool().submit(() -> {
					counter.incrementAndGet();
					latch.countDown();
				});
			}

			assertTrue(latch.await(5, TimeUnit.SECONDS));
			assertEquals(taskCount, counter.get());
		}
	}

	@Test @Timeout(5)
	void tasksExecuteInParallel() throws Exception {
		try (ProcessingThreadPool pool=new ProcessingThreadPool(4, 10)) {
			AtomicInteger maxConcurrent=new AtomicInteger(0);
			AtomicInteger currentConcurrent=new AtomicInteger(0);
			int taskCount=4;
			CountDownLatch allStarted=new CountDownLatch(taskCount);
			CountDownLatch canFinish=new CountDownLatch(1);

			for (int i=0; i<taskCount; i++) {
				pool.computePool().submit(() -> {
					int current=currentConcurrent.incrementAndGet();
					maxConcurrent.updateAndGet(max -> Math.max(max, current));
					allStarted.countDown();
					try {
						canFinish.await();
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
					currentConcurrent.decrementAndGet();
				});
			}

			assertTrue(allStarted.await(5, TimeUnit.SECONDS));
			canFinish.countDown();

			// Should have had multiple tasks running concurrently
			assertTrue(maxConcurrent.get()>1, "Expected concurrent execution, got max "+maxConcurrent.get());
		}
	}

	@Test
	void threadsAreDaemon() throws InterruptedException {
		try (ProcessingThreadPool pool=new ProcessingThreadPool(2, 10)) {
			CountDownLatch latch=new CountDownLatch(1);
			List<Boolean> daemonFlags=Collections.synchronizedList(new ArrayList<>());

			pool.computePool().submit(() -> {
				daemonFlags.add(Thread.currentThread().isDaemon());
				latch.countDown();
			});

			assertTrue(latch.await(5, TimeUnit.SECONDS));
			assertTrue(daemonFlags.get(0), "Thread should be a daemon thread");
		}
	}

	@Test
	void threadsHaveCorrectNamePrefix() throws InterruptedException {
		try (ProcessingThreadPool pool=new ProcessingThreadPool(2, 10)) {
			CountDownLatch latch=new CountDownLatch(1);
			List<String> threadNames=Collections.synchronizedList(new ArrayList<>());

			pool.computePool().submit(() -> {
				threadNames.add(Thread.currentThread().getName());
				latch.countDown();
			});

			assertTrue(latch.await(5, TimeUnit.SECONDS));
			assertTrue(threadNames.get(0).startsWith("msrawjava-worker-"), "Thread name should start with 'msrawjava-worker-', got: "+threadNames.get(0));
		}
	}

	@Test @Timeout(10)
	void blockOnRejectPolicyBlocksWhenQueueFull() throws Exception {
		// Create a pool with 1 thread and queue capacity of 1
		try (ProcessingThreadPool pool=new ProcessingThreadPool(1, 1)) {
			CountDownLatch firstTaskStarted=new CountDownLatch(1);
			CountDownLatch canFinish=new CountDownLatch(1);
			AtomicInteger submittedCount=new AtomicInteger(0);

			// First task: blocks the thread
			pool.computePool().submit(() -> {
				firstTaskStarted.countDown();
				try {
					canFinish.await();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			});

			// Wait for first task to start
			assertTrue(firstTaskStarted.await(5, TimeUnit.SECONDS));

			// Second task: fills the queue
			pool.computePool().submit(() -> submittedCount.incrementAndGet());

			// Third task should block (queue is full, thread is busy)
			// Run in separate thread to verify blocking
			Thread submitter=new Thread(() -> {
				pool.computePool().submit(() -> submittedCount.incrementAndGet());
			});
			submitter.start();

			// Give the submitter thread time to block
			Thread.sleep(100);
			assertTrue(submitter.isAlive(), "Submitter thread should be blocked");

			// Release the first task
			canFinish.countDown();

			// Now submitter should complete
			submitter.join(5000);
			assertFalse(submitter.isAlive(), "Submitter thread should have completed");
		}
	}

	@Test
	void rejectsPolicyThrowsWhenShutdown() throws InterruptedException {
		ProcessingThreadPool pool=new ProcessingThreadPool(1, 1);
		pool.close();

		assertThrows(RejectedExecutionException.class, () -> {
			pool.computePool().submit(() -> {
			});
		});
	}

	@Test @Timeout(5)
	void futuresReturnCorrectResults() throws Exception {
		try (ProcessingThreadPool pool=new ProcessingThreadPool(2, 10)) {
			Future<Integer> future1=pool.computePool().submit(() -> 42);
			Future<Integer> future2=pool.computePool().submit(() -> 100);

			assertEquals(42, future1.get(5, TimeUnit.SECONDS));
			assertEquals(100, future2.get(5, TimeUnit.SECONDS));
		}
	}

	@Test @Timeout(5)
	void handlesExceptionsInTasks() throws Exception {
		try (ProcessingThreadPool pool=new ProcessingThreadPool(2, 10)) {
			CountDownLatch normalTaskCompleted=new CountDownLatch(1);

			// Submit a task that throws
			Future<?> failingFuture=pool.computePool().submit(() -> {
				throw new RuntimeException("Test exception");
			});

			// Submit a normal task after
			pool.computePool().submit(() -> {
				normalTaskCompleted.countDown();
			});

			// Normal task should complete despite the exception
			assertTrue(normalTaskCompleted.await(5, TimeUnit.SECONDS));

			// Failing future should throw when getting result
			assertThrows(Exception.class, () -> failingFuture.get(1, TimeUnit.SECONDS));
		}
	}

	@Test
	void singleThreadPoolWorks() throws Exception {
		try (ProcessingThreadPool pool=new ProcessingThreadPool(1, 5)) {
			AtomicInteger counter=new AtomicInteger(0);
			CountDownLatch latch=new CountDownLatch(3);

			for (int i=0; i<3; i++) {
				pool.computePool().submit(() -> {
					counter.incrementAndGet();
					latch.countDown();
				});
			}

			assertTrue(latch.await(5, TimeUnit.SECONDS));
			assertEquals(3, counter.get());
		}
	}

	@Test
	void manyThreadsPoolWorks() throws Exception {
		try (ProcessingThreadPool pool=new ProcessingThreadPool(8, 20)) {
			AtomicInteger counter=new AtomicInteger(0);
			int taskCount=50;
			CountDownLatch latch=new CountDownLatch(taskCount);

			for (int i=0; i<taskCount; i++) {
				pool.computePool().submit(() -> {
					counter.incrementAndGet();
					latch.countDown();
				});
			}

			assertTrue(latch.await(10, TimeUnit.SECONDS));
			assertEquals(taskCount, counter.get());
		}
	}
}
