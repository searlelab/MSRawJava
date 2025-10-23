package org.searlelab.msrawjava.logging;

/**
 * LogRecorder collects recent log messages in memory for inspection, diagnostics, or UI display. It pairs with Logger
 * to timestamp and retain a bounded sequence of events so higher-level components (CLI, tests, or GUIs) can surface
 * concise run summaries without scraping stdout/stderr.
 */
public interface LogRecorder {
	public void log(String s);

	public void logLine(String s);

	public void timelessLogLine(String s);

	public void errorLine(String s);

	public void logException(Throwable e);

	public void errorException(Throwable e);

	public void close();
}
