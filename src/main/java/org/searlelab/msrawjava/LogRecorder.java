package org.searlelab.msrawjava;

public interface LogRecorder {
	public void log(String s);

	public void logLine(String s);

	public void timelessLogLine(String s);

	public void errorLine(String s);

	public void logException(Throwable e);

	public void errorException(Throwable e);

	public void close();
}
