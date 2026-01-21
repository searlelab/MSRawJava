package org.searlelab.msrawjava.logging;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;

public class FileLogRecorder implements LogRecorder {
	private final BufferedWriter writer;
	private final SimpleDateFormat format=new SimpleDateFormat("[HH:mm:ss] ");

	public FileLogRecorder(Path path, boolean truncate) throws IOException {
		if (truncate) {
			writer=Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
		} else {
			writer=Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
		}
	}

	@Override
	public synchronized void log(String s) {
		writeRaw(s);
	}

	@Override
	public synchronized void logLine(String s) {
		writeLine(format.format(new Date())+s);
	}

	@Override
	public synchronized void timelessLogLine(String s) {
		writeLine(s);
	}

	@Override
	public synchronized void errorLine(String s) {
		writeLine(format.format(new Date())+"ERROR: "+s);
	}

	@Override
	public synchronized void logException(Throwable e) {
		writeLine(format.format(new Date())+e.toString());
		for (StackTraceElement element : e.getStackTrace()) {
			writeLine("  at "+element.toString());
		}
	}

	@Override
	public synchronized void errorException(Throwable e) {
		writeLine(format.format(new Date())+"ERROR: "+e.toString());
		for (StackTraceElement element : e.getStackTrace()) {
			writeLine("  at "+element.toString());
		}
	}

	@Override
	public synchronized void close() {
		try {
			writer.flush();
			writer.close();
		} catch (IOException e) {
			/* ignore */
		}
	}

	private void writeLine(String s) {
		try {
			writer.write(s);
			writer.newLine();
			writer.flush();
		} catch (IOException e) {
			/* ignore */
		}
	}

	private void writeRaw(String s) {
		try {
			writer.write(s);
			writer.flush();
		} catch (IOException e) {
			/* ignore */
		}
	}
}
