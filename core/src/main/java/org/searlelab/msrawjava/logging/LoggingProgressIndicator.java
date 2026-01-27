package org.searlelab.msrawjava.logging;

/**
 * Progress indicator that logs updates via Logger and optional ANSI output.
 */
public class LoggingProgressIndicator implements ProgressIndicator {
	private static final long HEARTBEAT_MS=100L;

	public enum Mode {
		DEFAULT, BATCH, SILENT
	}

	volatile private float totalProgress=0.0f;
	private final Mode mode;
	private final boolean useAnsi;
	private volatile boolean canceled;
	private volatile boolean done;
	private Thread heartbeatThread;

	public LoggingProgressIndicator() {
		this(Mode.DEFAULT, true);
	}

	public LoggingProgressIndicator(boolean showProgress) {
		this(showProgress?Mode.DEFAULT:Mode.SILENT, true);
	}

	public LoggingProgressIndicator(Mode mode) {
		this(mode, true);
	}

	public LoggingProgressIndicator(Mode mode, boolean useAnsi) {
		this.mode=mode;
		this.useAnsi=useAnsi;
		if (mode==Mode.DEFAULT&&useAnsi) {
			startHeartbeat();
		}
	}

	@Override
	public void update(String message, float totalProgress) {
		float p=totalProgress;
		if (Float.isNaN(p)||Float.isInfinite(p)) p=0f;
		if (p>2.0f&&p<=100.0f) p=p/100f;
		p=Math.max(0f, Math.min(1f, p));
		this.totalProgress=p;
		if (message!=null) {
			updateMessage(message);
		}
		render();
		if (p>=1.0f) {
			done=true;
		}
	}

	@Override
	public void update(String message) {
		if (message!=null) {
			updateMessage(message);
		}
		render();
	}

	@Override
	public float getTotalProgress() {
		return totalProgress;
	}

	@Override
	public boolean isCanceled() {
		return canceled;
	}

	public void setCanceled(boolean canceled) {
		this.canceled=canceled;
	}

	public void close() {
		done=true;
		if (mode==Mode.DEFAULT&&useAnsi) {
			render();
			Logger.timelessLogLine("");
		}
	}

	private void startHeartbeat() {
		heartbeatThread=new Thread(() -> {
			while (!done) {
				try {
					Thread.sleep(HEARTBEAT_MS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				if (!done) {
					ConsoleStatus status=Logger.getConsoleStatus();
					if (status!=null) {
						status.tick();
					}
				}
			}
		}, "cli-progress-heartbeat");
		heartbeatThread.setDaemon(true);
		heartbeatThread.start();
	}

	private void updateMessage(String message) {
		if (mode!=Mode.DEFAULT) return;
		if (useAnsi) {
			ConsoleStatus status=Logger.getConsoleStatus();
			if (status!=null) {
				status.setMessage(message);
			}
		} else {
			Logger.logLine(message);
		}
	}

	private void render() {
		if (mode!=Mode.DEFAULT) return;
		if (useAnsi) {
			ConsoleStatus status=Logger.getConsoleStatus();
			if (status!=null) {
				status.setProgress(totalProgress);
			}
		}
	}
}
