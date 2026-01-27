package org.searlelab.msrawjava.logging;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoggerBehaviorTest {

	private final ByteArrayOutputStream outBytes=new ByteArrayOutputStream();
	private final ByteArrayOutputStream errBytes=new ByteArrayOutputStream();
	private PrintStream originalOut;
	private PrintStream originalErr;

	@BeforeEach
	void setUp() {
		LoggerTestSupport.resetLogger();
		originalOut=System.out;
		originalErr=System.err;
		System.setOut(new PrintStream(outBytes, true, StandardCharsets.UTF_8));
		System.setErr(new PrintStream(errBytes, true, StandardCharsets.UTF_8));
	}

	@AfterEach
	void tearDown() {
		System.setOut(originalOut);
		System.setErr(originalErr);
		LoggerTestSupport.resetLogger();
	}

	@Test
	void logLineWritesTimestampedMessageToStdout() {
		Logger.logLine("hello");
		String out=outBytes.toString(StandardCharsets.UTF_8);
		assertTrue(out.contains("hello"));
		assertTrue(out.contains("["));
		assertTrue(out.contains("]"));
	}

	@Test
	void errorLineWritesToStderr() {
		Logger.errorLine("oops");
		String err=errBytes.toString(StandardCharsets.UTF_8);
		assertTrue(err.contains("oops"));
		assertTrue(err.contains("["));
	}

	@Test
	void logExceptionWritesToStdout() {
		Logger.logException(new RuntimeException("boom"));
		String out=outBytes.toString(StandardCharsets.UTF_8);
		assertTrue(out.contains("RuntimeException: boom"));
	}

	@Test
	void errorExceptionWritesToStderr() {
		Logger.errorException(new IllegalStateException("bad"));
		String err=errBytes.toString(StandardCharsets.UTF_8);
		assertTrue(err.contains("IllegalStateException: bad"));
	}

	@Test
	void recorderReceivesEventsWhenPrintingDisabled() {
		Logger.PRINT_TO_SCREEN=false;
		TestRecorder recorder=new TestRecorder();
		Logger.addRecorder(recorder);

		Logger.logLine("quiet");
		Logger.errorLine("silent");

		String out=outBytes.toString(StandardCharsets.UTF_8);
		String err=errBytes.toString(StandardCharsets.UTF_8);
		assertTrue(recorder.lines.contains("quiet"));
		assertTrue(recorder.errors.contains("silent"));
		assertTrue(out.isEmpty());
		assertTrue(err.isEmpty());
	}

	@Test
	void consoleStatusReceivesOutputWhenEnabled() {
		ByteArrayOutputStream statusOutBytes=new ByteArrayOutputStream();
		ByteArrayOutputStream statusErrBytes=new ByteArrayOutputStream();
		ConsoleStatus status=new ConsoleStatus(true, new PrintStream(statusOutBytes, true, StandardCharsets.UTF_8),
				new PrintStream(statusErrBytes, true, StandardCharsets.UTF_8));
		Logger.setConsoleStatus(status);

		Logger.logLine("console");
		Logger.errorLine("problem");
		Logger.logException(new RuntimeException("boom"));
		Logger.errorException(new RuntimeException("kaboom"));
		Logger.close();

		String outText=statusOutBytes.toString(StandardCharsets.UTF_8);
		assertTrue(outText.contains("console"));
		assertTrue(outText.contains("problem"));
		assertTrue(outText.contains("RuntimeException: boom"));
		assertTrue(outText.contains("RuntimeException: kaboom"));
	}

	@Test
	void stdoutAndStderrTogglesSuppressPrints() {
		Logger.PRINT_TO_STDOUT=false;
		Logger.PRINT_TO_STDERR=false;
		TestRecorder recorder=new TestRecorder();
		Logger.addRecorder(recorder);

		Logger.logLine("quiet");
		Logger.errorLine("quiet-err");

		String out=outBytes.toString(StandardCharsets.UTF_8);
		String err=errBytes.toString(StandardCharsets.UTF_8);
		assertTrue(out.isEmpty());
		assertTrue(err.isEmpty());
		assertTrue(recorder.lines.contains("quiet"));
		assertTrue(recorder.errors.contains("quiet-err"));
	}

	private static class TestRecorder implements LogRecorder {
		final List<String> lines=new ArrayList<>();
		final List<String> errors=new ArrayList<>();

		@Override
		public void log(String s) {
		}

		@Override
		public void logLine(String s) {
			lines.add(s);
		}

		@Override
		public void timelessLogLine(String s) {
		}

		@Override
		public void errorLine(String s) {
			errors.add(s);
		}

		@Override
		public void logException(Throwable e) {
		}

		@Override
		public void errorException(Throwable e) {
		}

		@Override
		public void close() {
		}
	}
}
