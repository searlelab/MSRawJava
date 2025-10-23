package org.searlelab.msrawjava.logging;

public class LoggingProgressIndicator implements ProgressIndicator {
	volatile private float totalProgress=0.0f;
	private final boolean print;
	private boolean canceled;

	public LoggingProgressIndicator() {
		this.print=false;
	}
	
	public LoggingProgressIndicator(boolean print) {
		this.print=print;
	}

	@Override
	public void update(String message, float totalProgress) {
		if (print) {
			Logger.logLine(((int)(totalProgress*100f))+"%\t"+message);
		}
		this.totalProgress=totalProgress;
	}
	
	@Override
	public void update(String message) {
		if (print) {
			Logger.logLine(((int)(totalProgress*100f))+"%\t"+message);
		}
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
}
