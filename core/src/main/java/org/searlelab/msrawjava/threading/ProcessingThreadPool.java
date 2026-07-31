package org.searlelab.msrawjava.threading;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.searlelab.msrawjava.API;

/**
 * Bounded worker pool with backpressure for CPU-intensive processing stages.
 */
public class ProcessingThreadPool implements AutoCloseable {
	private final ThreadPoolExecutor computePool;

	@API(status = API.Status.STABLE, since = "v26.7.31")
	public ProcessingThreadPool(int threads, int queueCapacity) {
		this.computePool=new ThreadPoolExecutor(threads, threads, 365L, TimeUnit.DAYS, new ArrayBlockingQueue<>(queueCapacity),
				namedFactory("msrawjava-worker"), new BlockOnRejectPolicy());
		this.computePool.prestartAllCoreThreads();
	}

	public ExecutorService computePool() {
		return computePool;
	}

	@Override
	@API(status = API.Status.STABLE, since = "v26.7.31")
	public void close() throws InterruptedException {
		computePool.shutdown();
		try {
			if (!computePool.awaitTermination(60, TimeUnit.SECONDS)) {
				computePool.shutdownNow();
			}
		} catch (InterruptedException e) {
			computePool.shutdownNow();
			Thread.currentThread().interrupt();
			throw e;
		}
	}

	private static ThreadFactory namedFactory(String base) {
		AtomicInteger n=new AtomicInteger(1);
		return r -> {
			Thread t=new Thread(r, base+"-"+n.getAndIncrement());
			t.setDaemon(true);
			return t;
		};
	}

	public static ProcessingThreadPool createDefault() {
		return createWithThreadLimit(defaultThreadCount());
	}

	public static ProcessingThreadPool createWithThreadLimit(Integer threadLimit) {
		int threads=(threadLimit==null)?defaultThreadCount():Math.max(1, threadLimit);
		int queueCapacity=threads*4; // small-ish bounded queue; tune if needed
		return new ProcessingThreadPool(threads, queueCapacity);
	}

	public static int defaultThreadCount() {
		int cores=Runtime.getRuntime().availableProcessors();
		return Math.max(1, cores-1);
	}

	private static final class BlockOnRejectPolicy implements RejectedExecutionHandler {
		@Override
		public void rejectedExecution(Runnable r, ThreadPoolExecutor ex) {
			if (ex.isShutdown()) throw new RejectedExecutionException("Executor is shut down");
			try {
				ex.getQueue().put(r); // block until space
			} catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
				throw new RejectedExecutionException("Interrupted while enqueueing", ie);
			}
		}
	}
}
