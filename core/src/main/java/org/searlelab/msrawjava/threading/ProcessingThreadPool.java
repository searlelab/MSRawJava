package org.searlelab.msrawjava.threading;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ProcessingThreadPool implements AutoCloseable {
	private final ThreadPoolExecutor computePool;

	public ProcessingThreadPool(int threads, int queueCapacity) {
		this.computePool=new ThreadPoolExecutor(threads, threads, 365L, TimeUnit.DAYS, new ArrayBlockingQueue<>(queueCapacity),
				namedFactory("msrawjava-worker"), new BlockOnRejectPolicy());
		this.computePool.prestartAllCoreThreads();
	}

	public ExecutorService computePool() {
		return computePool;
	}

	@Override
	public void close() throws InterruptedException {
		computePool.shutdown();
		if (!computePool.awaitTermination(60, TimeUnit.SECONDS)) {
			computePool.shutdownNow();
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
		int cores=Runtime.getRuntime().availableProcessors();
		int threads=Math.max(1, cores-1);
		int queueCapacity=threads*4; // small-ish bounded queue; tune if needed
		return new ProcessingThreadPool(threads, queueCapacity);
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