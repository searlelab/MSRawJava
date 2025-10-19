package org.searlelab.msrawjava;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class LoggerTest {

	static class TestRecorder implements LogRecorder {
		final List<String> logs=new ArrayList<>();
		final List<String> lines=new ArrayList<>();
		final List<String> timeless=new ArrayList<>();
		final List<String> errors=new ArrayList<>();
		final List<Throwable> logEx=new ArrayList<>();
		final List<Throwable> errEx=new ArrayList<>();
		boolean closed=false;

		@Override
		public void log(String s) {
			logs.add(s);
		}

		@Override
		public void logLine(String s) {
			lines.add(s);
		}

		@Override
		public void timelessLogLine(String s) {
			timeless.add(s);
		}

		@Override
		public void errorLine(String s) {
			errors.add(s);
		}

		@Override
		public void logException(Throwable e) {
			logEx.add(e);
		}

		@Override
		public void errorException(Throwable e) {
			errEx.add(e);
		}

		@Override
		public void close() {
			closed=true;
		}
	}

	@Test
	void emitsToRecorder_forAllPublicAPIs_andClosePropagates() {
		TestRecorder r=new TestRecorder();
		Logger.addRecorder(r);

		String msg="hello logging";
		String emsg="bad news";
		Throwable ex=new RuntimeException("boom");

		Logger.log(msg);
		Logger.logLine(msg);
		Logger.timelessLogLine(msg);
		Logger.errorLine(emsg);
		Logger.logException(ex);
		Logger.errorException(ex);
		Logger.close(); // propagates to all recorders

		assertTrue(r.logs.contains(msg));
		assertTrue(r.lines.contains(msg));
		assertTrue(r.timeless.contains(msg));
		assertTrue(r.errors.contains(emsg));
		assertTrue(r.logEx.contains(ex));
		assertTrue(r.errEx.contains(ex));
		assertTrue(r.closed, "close() should propagate to recorders");
	}
}
