package org.searlelab.msrawjava.gui;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks foreground GUI file processing so opportunistic background work can back off.
 */
public final class GuiProcessingActivity {
	private static final AtomicInteger activeForegroundWork=new AtomicInteger(0);
	private static final CopyOnWriteArrayList<Runnable> listeners=new CopyOnWriteArrayList<>();

	private GuiProcessingActivity() {
	}

	public static AutoCloseable beginForegroundWork() {
		int count=activeForegroundWork.incrementAndGet();
		if (count==1) notifyListeners();
		return new Token();
	}

	public static boolean isForegroundWorkActive() {
		return activeForegroundWork.get()>0;
	}

	public static void addListener(Runnable listener) {
		listeners.add(Objects.requireNonNull(listener, "listener"));
	}

	public static void removeListener(Runnable listener) {
		listeners.remove(listener);
	}

	static int activeForegroundWorkCount() {
		return activeForegroundWork.get();
	}

	static void resetForTests() {
		activeForegroundWork.set(0);
		listeners.clear();
	}

	private static void notifyListeners() {
		for (Runnable listener : listeners) {
			listener.run();
		}
	}

	private static final class Token implements AutoCloseable {
		private final AtomicBoolean closed=new AtomicBoolean(false);

		@Override
		public void close() {
			if (!closed.compareAndSet(false, true)) return;
			int count=activeForegroundWork.decrementAndGet();
			if (count==0) {
				notifyListeners();
			} else if (count<0) {
				activeForegroundWork.set(0);
				notifyListeners();
			}
		}
	}
}
