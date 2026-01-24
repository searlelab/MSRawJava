package org.searlelab.msrawjava.logging;

import java.lang.reflect.Field;
import java.util.List;

final class LoggerTestSupport {
	private LoggerTestSupport() {
	}

	static void resetLogger() {
		clearRecorders();
		Logger.setConsoleStatus(null);
		Logger.PRINT_TO_SCREEN=true;
		Logger.PRINT_TO_STDOUT=true;
		Logger.PRINT_TO_STDERR=true;
	}

	@SuppressWarnings("unchecked")
	private static void clearRecorders() {
		try {
			Field recordersField=Logger.class.getDeclaredField("recorders");
			recordersField.setAccessible(true);
			List<LogRecorder> recorders=(List<LogRecorder>) recordersField.get(null);
			recorders.clear();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Failed to reset Logger recorders", e);
		}
	}
}
