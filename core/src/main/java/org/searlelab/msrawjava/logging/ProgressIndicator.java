package org.searlelab.msrawjava.logging;

public interface ProgressIndicator {
	public void update(String message);

	public void update(String message, float totalProgress);

	public float getTotalProgress();

	public boolean isCanceled();
}
