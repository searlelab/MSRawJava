package org.searlelab.msrawjava;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.io.ConversionOptions;
import org.searlelab.msrawjava.io.OutputType;
import org.searlelab.msrawjava.logging.LoggingProgressIndicator;

class MainHelperTest {
	@Test
	void conversionOptionsExposeLibraryDefaults() {
		ConversionOptions options=ConversionOptions.builder().outputType(OutputType.mgf).build();
		assertEquals(OutputType.mgf, options.getOutputType());
		assertEquals(3.0f, options.getMinimumMS1Intensity());
		assertEquals(1.0f, options.getMinimumMS2Intensity());
	}

	@Test
	void createIndicator_respectsSilentAndBatchModes() throws Exception {
		Method method=Main.class.getDeclaredMethod("createIndicator", org.searlelab.msrawjava.io.ConversionParameters.class);
		method.setAccessible(true);
		org.searlelab.msrawjava.io.ConversionParameters silent=org.searlelab.msrawjava.io.ConversionParameters.builder().silent(true).build();
		LoggingProgressIndicator indicator=(LoggingProgressIndicator)method.invoke(null, silent);
		assertEquals(LoggingProgressIndicator.Mode.SILENT, getMode(indicator));
		indicator.close();

		org.searlelab.msrawjava.io.ConversionParameters normal=org.searlelab.msrawjava.io.ConversionParameters.builder().build();
		indicator=(LoggingProgressIndicator)method.invoke(null, normal);
		assertEquals(LoggingProgressIndicator.Mode.DEFAULT, getMode(indicator));
		indicator.close();

		org.searlelab.msrawjava.io.ConversionParameters batch=org.searlelab.msrawjava.io.ConversionParameters.builder().batch(true).build();
		indicator=(LoggingProgressIndicator)method.invoke(null, batch);
		assertEquals(LoggingProgressIndicator.Mode.BATCH, getMode(indicator));
		indicator.close();
	}

	private static LoggingProgressIndicator.Mode getMode(LoggingProgressIndicator indicator) throws Exception {
		Field field=LoggingProgressIndicator.class.getDeclaredField("mode");
		field.setAccessible(true);
		return (LoggingProgressIndicator.Mode)field.get(indicator);
	}
}
