package org.searlelab.msrawjava;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * Logger is a lightweight, dependency-free logging facade for the project that formats and emits messages in a
 * consistent style and severity order. It is designed to be thread-friendly and predictable, and can optionally feed
 * LogRecorder so tests and UIs can capture recent output without depending on external logging frameworks.
 */
public class Logger {
	public static boolean PRINT_TO_SCREEN=true;
	private static final SimpleDateFormat format=new SimpleDateFormat("[HH:mm:ss] ");
	private static final ArrayList<LogRecorder> recorders=new ArrayList<LogRecorder>();

	public static void addRecorder(LogRecorder recorder) {
		recorders.add(recorder);
	}

	public static void log(String s) {
		if (PRINT_TO_SCREEN) System.out.print(s);
		for (LogRecorder recorder : recorders) {
			recorder.log(s);
		}
	}

	public static void logLine(String s) {
		if (PRINT_TO_SCREEN) System.out.println(format.format(new Date())+s);
		for (LogRecorder recorder : recorders) {
			recorder.logLine(s);
		}
	}

	public static void timelessLogLine(String s) {
		if (PRINT_TO_SCREEN) System.out.println(s);
		for (LogRecorder recorder : recorders) {
			recorder.timelessLogLine(s);
		}
	}

	public static void errorLine(String s) {
		if (PRINT_TO_SCREEN) System.err.println(format.format(new Date())+s);
		for (LogRecorder recorder : recorders) {
			recorder.errorLine(s);
		}
	}

	public static void logException(Throwable e) {
		if (PRINT_TO_SCREEN) {
			writeStacktraceLines(e, System.out);
		}
		for (LogRecorder recorder : recorders) {
			recorder.logException(e);
		}
	}

	public static void errorException(Throwable e) {
		if (PRINT_TO_SCREEN) {
			writeStacktraceLines(e, System.err);
		}
		for (LogRecorder recorder : recorders) {
			recorder.errorException(e);
		}
	}

	static void writeStacktraceLines(Throwable throwable, PrintStream stream) {
		// Log the timestamp without a linebreak
		stream.print(format.format(new Date()));

		// Log the full stacktrace
		throwable.printStackTrace(stream);
	}

	public static void close() {
		for (LogRecorder recorder : recorders) {
			recorder.close();
		}
	}
}
