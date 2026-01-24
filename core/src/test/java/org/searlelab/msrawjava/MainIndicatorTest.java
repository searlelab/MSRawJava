package org.searlelab.msrawjava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.msrawjava.io.ConversionParameters;
import org.searlelab.msrawjava.logging.LogRecorder;
import org.searlelab.msrawjava.logging.Logger;
import org.searlelab.msrawjava.logging.LoggingProgressIndicator;

class MainIndicatorTest {

	private PrintStream originalOut;
	private PrintStream originalErr;

	@TempDir
	Path tmp;

	@BeforeEach
	void setUp() {
		resetLogger();
		originalOut=System.out;
		originalErr=System.err;
	}

	@AfterEach
	void tearDown() {
		System.setOut(originalOut);
		System.setErr(originalErr);
		resetLogger();
	}

	@Test
	void createIndicatorSelectsModeBasedOnFlags() throws Exception {
		Method method=Main.class.getDeclaredMethod("createIndicator", ConversionParameters.class);
		method.setAccessible(true);

		ConversionParameters silent=ConversionParameters.builder().silent(true).build();
		LoggingProgressIndicator silentIndicator=(LoggingProgressIndicator)method.invoke(null, silent);
		assertEquals(LoggingProgressIndicator.Mode.SILENT, readMode(silentIndicator));

		ConversionParameters batch=ConversionParameters.builder().batch(true).build();
		LoggingProgressIndicator batchIndicator=(LoggingProgressIndicator)method.invoke(null, batch);
		assertEquals(LoggingProgressIndicator.Mode.BATCH, readMode(batchIndicator));

		ConversionParameters normal=ConversionParameters.builder().build();
		LoggingProgressIndicator normalIndicator=(LoggingProgressIndicator)method.invoke(null, normal);
		assertEquals(LoggingProgressIndicator.Mode.DEFAULT, readMode(normalIndicator));
	}

	@Test
	void configureLoggingSetsSilentFlagsAndAddsRecorder() throws Exception {
		Main.CliArguments args=new Main.CliArguments();
		Method toParams=Main.CliArguments.class.getDeclaredMethod("toParameters");
		toParams.setAccessible(true);

		setField(args, "silent", true);
		setField(args, "logFilePath", tmp.resolve("run.log"));

		ConversionParameters params=(ConversionParameters)toParams.invoke(args);

		Method configure=Main.CliArguments.class.getDeclaredMethod("configureLogging", ConversionParameters.class);
		configure.setAccessible(true);
		configure.invoke(args, params);

		assertFalse(Logger.PRINT_TO_STDOUT);
		assertTrue(Logger.PRINT_TO_STDERR);
		assertEquals(1, recorderCount());
	}

	@Test
	void convertKnownFilesWithNoInputsCompletes() throws Exception {
		ConversionParameters params=ConversionParameters.builder().build();
		Main.convertKnownFiles(params);
		assertTrue(params.getFileList().isEmpty());
	}

	private static LoggingProgressIndicator.Mode readMode(LoggingProgressIndicator indicator) throws Exception {
		Field field=LoggingProgressIndicator.class.getDeclaredField("mode");
		field.setAccessible(true);
		return (LoggingProgressIndicator.Mode)field.get(indicator);
	}

	private static void setField(Object target, String fieldName, Object value) throws Exception {
		Field field=target.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(target, value);
	}

	private static int recorderCount() {
		try {
			Field recordersField=Logger.class.getDeclaredField("recorders");
			recordersField.setAccessible(true);
			@SuppressWarnings("unchecked")
			List<LogRecorder> recorders=(List<LogRecorder>) recordersField.get(null);
			return recorders.size();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Failed to read Logger recorders", e);
		}
	}

	private static void resetLogger() {
		Logger.PRINT_TO_SCREEN=true;
		Logger.PRINT_TO_STDOUT=true;
		Logger.PRINT_TO_STDERR=true;
		Logger.setConsoleStatus(null);
		try {
			Field recordersField=Logger.class.getDeclaredField("recorders");
			recordersField.setAccessible(true);
			@SuppressWarnings("unchecked")
			List<LogRecorder> recorders=(List<LogRecorder>) recordersField.get(null);
			recorders.clear();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Failed to reset Logger recorders", e);
		}
	}
}
