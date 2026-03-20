package org.searlelab.msrawjava.gui.loadingpanels;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public abstract class LoadingPanel extends JPanel {
	private static final long serialVersionUID=1L;

	protected static MinPriorityAnimationLoop createMinPriorityAnimationLoop(int frameMs, String threadName, Runnable tickOnEdt) {
		return new MinPriorityAnimationLoop(frameMs, threadName, tickOnEdt);
	}

	protected static final class MinPriorityAnimationLoop {
		private final long frameNanos;
		private final String threadName;
		private final Runnable tickOnEdt;
		private final AtomicBoolean running=new AtomicBoolean(false);
		private final AtomicBoolean tickQueued=new AtomicBoolean(false);
		private volatile Thread thread;

		private MinPriorityAnimationLoop(int frameMs, String threadName, Runnable tickOnEdt) {
			this.frameNanos=Math.max(1, frameMs)*1_000_000L;
			this.threadName=(threadName==null||threadName.isBlank())?"loading-panel-animation":threadName;
			this.tickOnEdt=tickOnEdt;
		}

		void start() {
			if (!running.compareAndSet(false, true)) return;
			Thread t=new Thread(this::runLoop, threadName);
			t.setDaemon(true);
			t.setPriority(Thread.MIN_PRIORITY);
			thread=t;
			t.start();
		}

		void stop() {
			running.set(false);
			Thread t=thread;
			thread=null;
			if (t!=null) t.interrupt();
			tickQueued.set(false);
		}

		boolean isRunning() {
			return running.get();
		}

		private void runLoop() {
			long nextTick=System.nanoTime();
			while (running.get()) {
				queueTick();
				nextTick+=frameNanos;
				long sleepNanos=nextTick-System.nanoTime();
				if (sleepNanos>0L) {
					LockSupport.parkNanos(this, sleepNanos);
				} else {
					nextTick=System.nanoTime();
				}
				if (Thread.interrupted()&&!running.get()) {
					break;
				}
			}
			tickQueued.set(false);
		}

		private void queueTick() {
			if (!tickQueued.compareAndSet(false, true)) return;
			SwingUtilities.invokeLater(() -> {
				tickQueued.set(false);
				if (running.get()) {
					tickOnEdt.run();
				}
			});
		}
	}

	public abstract void start();

	public abstract void stop();
}
