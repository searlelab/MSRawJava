package org.searlelab.msrawjava.benchmarks;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;

/** Sampled process-wide heap/GC counters for explicit replays, outside kernel timing. No forced GC. */
public final class ReplayMemory {
	private final long collectionsBefore=collections(), gcBefore=gcMillis();
	private long usedMax, committedMax;

	public void sample() {
		MemoryUsage heap=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
		usedMax=Math.max(usedMax, heap.getUsed());
		committedMax=Math.max(committedMax, heap.getCommitted());
	}

	public String jsonFields() {
		return ",\"sampled_heap_used_max\":"+usedMax+",\"sampled_heap_committed_max\":"+committedMax
				+",\"collections\":"+(collections()-collectionsBefore)+",\"gc_ms\":"+(gcMillis()-gcBefore);
	}

	private static long collections() {
		return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b -> Math.max(0, b.getCollectionCount())).sum();
	}

	private static long gcMillis() {
		return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b -> Math.max(0, b.getCollectionTime())).sum();
	}
}
