package org.searlelab.msrawjava.logging;

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
	public static boolean PRINT_TO_STDOUT=true;
	public static boolean PRINT_TO_STDERR=true;
	private static final SimpleDateFormat format=new SimpleDateFormat("[HH:mm:ss] ");
	private static final ArrayList<LogRecorder> recorders=new ArrayList<LogRecorder>();
	private static ConsoleStatus consoleStatus=null;

	public static void addRecorder(LogRecorder recorder) {
		recorders.add(recorder);
	}

	public static void setConsoleStatus(ConsoleStatus status) {
		consoleStatus=status;
	}

	public static ConsoleStatus getConsoleStatus() {
		return consoleStatus;
	}

	public static PrintStream getStdout() {
		return System.out;
	}

	public static PrintStream getStderr() {
		return System.err;
	}

	public static void log(String s) {
		if (PRINT_TO_SCREEN&&PRINT_TO_STDOUT) {
			if (consoleStatus!=null&&consoleStatus.isEnabled()) {
				consoleStatus.printStdout(s, false);
			} else {
				System.out.print(s);
			}
		}
		for (LogRecorder recorder : recorders) {
			recorder.log(s);
		}
	}

	public static void logLine(String s) {
		if (PRINT_TO_SCREEN&&PRINT_TO_STDOUT) {
			String line=format.format(new Date())+s;
			if (consoleStatus!=null&&consoleStatus.isEnabled()) {
				consoleStatus.printStdout(line, true);
			} else {
				System.out.println(line);
			}
		}
		for (LogRecorder recorder : recorders) {
			recorder.logLine(s);
		}
	}

	public static void timelessLogLine(String s) {
		if (PRINT_TO_SCREEN&&PRINT_TO_STDOUT) {
			if (consoleStatus!=null&&consoleStatus.isEnabled()) {
				consoleStatus.printStdout(s, true);
			} else {
				System.out.println(s);
			}
		}
		for (LogRecorder recorder : recorders) {
			recorder.timelessLogLine(s);
		}
	}

	public static void errorLine(String s) {
		if (PRINT_TO_SCREEN&&PRINT_TO_STDERR) {
			String line=format.format(new Date())+s;
			if (consoleStatus!=null&&consoleStatus.isEnabled()) {
				consoleStatus.printStderr(line, true);
			} else {
				System.err.println(line);
			}
		}
		for (LogRecorder recorder : recorders) {
			recorder.errorLine(s);
		}
	}

	public static void logException(Throwable e) {
		if (PRINT_TO_SCREEN&&PRINT_TO_STDOUT) {
			if (consoleStatus!=null&&consoleStatus.isEnabled()) {
				consoleStatus.printStdout(stacktraceToString(e), false);
			} else {
				writeStacktraceLines(e, System.out);
			}
		}
		for (LogRecorder recorder : recorders) {
			recorder.logException(e);
		}
	}

	public static void errorException(Throwable e) {
		if (PRINT_TO_SCREEN&&PRINT_TO_STDERR) {
			if (consoleStatus!=null&&consoleStatus.isEnabled()) {
				consoleStatus.printStderr(stacktraceToString(e), false);
			} else {
				writeStacktraceLines(e, System.err);
			}
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

	private static String stacktraceToString(Throwable throwable) {
		java.io.StringWriter sw=new java.io.StringWriter();
		java.io.PrintWriter pw=new java.io.PrintWriter(sw);
		pw.print(format.format(new Date()));
		throwable.printStackTrace(pw);
		pw.flush();
		return sw.toString();
	}

	public static void close() {
		for (LogRecorder recorder : recorders) {
			recorder.close();
		}
		if (consoleStatus!=null) {
			consoleStatus.close();
		}
	}
}
