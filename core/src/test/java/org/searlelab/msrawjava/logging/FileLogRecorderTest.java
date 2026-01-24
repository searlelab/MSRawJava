package org.searlelab.msrawjava.logging;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileLogRecorderTest {

	@Test
	void writesAllLogVariantsToFile(@TempDir Path tempDir) throws Exception {
		Path logFile=tempDir.resolve("msraw.log");
		FileLogRecorder recorder=new FileLogRecorder(logFile, true);

		recorder.log("raw");
		recorder.logLine("line");
		recorder.timelessLogLine("timeless");
		recorder.errorLine("bad");
		RuntimeException ex=new RuntimeException("boom");
		recorder.logException(ex);
		recorder.errorException(ex);
		recorder.close();

		String content=Files.readString(logFile, StandardCharsets.UTF_8);
		assertTrue(content.startsWith("raw"));
		assertTrue(content.contains("line"));
		assertTrue(content.contains("timeless"));
		assertTrue(content.contains("ERROR: bad"));
		assertTrue(content.contains("RuntimeException: boom"));

		String[] lines=content.split("\\R");
		boolean hasStackLine=Arrays.stream(lines).anyMatch(line -> line.startsWith("  at "));
		assertTrue(hasStackLine, "Expected stacktrace lines to be written");
	}

	@Test
	void appendModeDoesNotTruncate(@TempDir Path tempDir) throws Exception {
		Path logFile=tempDir.resolve("append.log");
		FileLogRecorder first=new FileLogRecorder(logFile, true);
		first.logLine("first");
		first.close();

		FileLogRecorder second=new FileLogRecorder(logFile, false);
		second.logLine("second");
		second.close();

		String content=Files.readString(logFile, StandardCharsets.UTF_8);
		assertTrue(content.contains("first"));
		assertTrue(content.contains("second"));
	}
}
