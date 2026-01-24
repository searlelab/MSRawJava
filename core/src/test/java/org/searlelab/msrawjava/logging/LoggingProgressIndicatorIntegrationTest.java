package org.searlelab.msrawjava.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoggingProgressIndicatorIntegrationTest {

	@BeforeEach
	void setUp() {
		LoggerTestSupport.resetLogger();
	}

	@AfterEach
	void tearDown() {
		LoggerTestSupport.resetLogger();
	}

	@Test
	void updateNormalizesNaNInfinityAndPercentInputs() {
		LoggingProgressIndicator indicator=new LoggingProgressIndicator(LoggingProgressIndicator.Mode.SILENT, false);

		indicator.update("nan", Float.NaN);
		assertEquals(0.0f, indicator.getTotalProgress(), 1e-6);

		indicator.update("inf", Float.POSITIVE_INFINITY);
		assertEquals(0.0f, indicator.getTotalProgress(), 1e-6);

		indicator.update("percent", 50.0f);
		assertEquals(0.5f, indicator.getTotalProgress(), 1e-6);
	}

	@Test
	void updateLogsMessageWhenAnsiDisabled() {
		Logger.PRINT_TO_SCREEN=false;
		TestRecorder recorder=new TestRecorder();
		Logger.addRecorder(recorder);

		LoggingProgressIndicator indicator=new LoggingProgressIndicator(LoggingProgressIndicator.Mode.DEFAULT, false);
		indicator.update("Hello", 0.25f);

		assertTrue(recorder.lines.contains("Hello"));
	}

	@Test
	void updateRendersToConsoleStatusWhenAnsiEnabled() {
		ByteArrayOutputStream outBytes=new ByteArrayOutputStream();
		ByteArrayOutputStream errBytes=new ByteArrayOutputStream();
		PrintStream out=new PrintStream(outBytes, true, StandardCharsets.UTF_8);
		PrintStream err=new PrintStream(errBytes, true, StandardCharsets.UTF_8);
		ConsoleStatus status=new ConsoleStatus(true, out, err);
		Logger.setConsoleStatus(status);

		LoggingProgressIndicator indicator=new LoggingProgressIndicator(LoggingProgressIndicator.Mode.DEFAULT, true);
		indicator.update("Running", 0.2f);
		indicator.close();
		Logger.close();

		String output=outBytes.toString(StandardCharsets.UTF_8);
		assertTrue(output.contains("Running"));
		assertTrue(output.contains("% ["));
		assertTrue(output.contains("\u001b[?25h"));
	}

	@Test
	void updateWithAnsiEnabledHandlesMissingConsoleStatus() {
		Logger.setConsoleStatus(null);
		LoggingProgressIndicator indicator=new LoggingProgressIndicator(LoggingProgressIndicator.Mode.DEFAULT, true);
		indicator.update("No status", 0.4f);
		indicator.close();
		assertEquals(0.4f, indicator.getTotalProgress(), 1e-6);
	}

	private static class TestRecorder implements LogRecorder {
		final List<String> lines=new ArrayList<>();

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
