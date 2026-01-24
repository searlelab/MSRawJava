package org.searlelab.msrawjava.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class ConsoleStatusTest {

	@Test
	void disabledConsoleWritesDirectlyWithoutAnsi() {
		ByteArrayOutputStream outBytes=new ByteArrayOutputStream();
		ByteArrayOutputStream errBytes=new ByteArrayOutputStream();
		PrintStream out=new PrintStream(outBytes, true, StandardCharsets.UTF_8);
		PrintStream err=new PrintStream(errBytes, true, StandardCharsets.UTF_8);
		ConsoleStatus status=new ConsoleStatus(false, out, err);

		status.printStdout("hello", true);
		status.printStdout("partial", false);
		status.printStderr("oops", true);
		status.close();

		String outText=outBytes.toString(StandardCharsets.UTF_8);
		String errText=errBytes.toString(StandardCharsets.UTF_8);
		assertEquals("hello"+System.lineSeparator()+"partial", outText);
		assertEquals("oops"+System.lineSeparator(), errText);
		assertFalse(outText.contains("\u001b["));
		assertFalse(errText.contains("\u001b["));
	}

	@Test
	void enabledConsoleRendersStatusAndRestoresCursor() {
		ByteArrayOutputStream outBytes=new ByteArrayOutputStream();
		ByteArrayOutputStream errBytes=new ByteArrayOutputStream();
		PrintStream out=new PrintStream(outBytes, true, StandardCharsets.UTF_8);
		PrintStream err=new PrintStream(errBytes, true, StandardCharsets.UTF_8);
		ConsoleStatus status=new ConsoleStatus(true, out, err);

		status.setMessage("Working");
		status.setProgress(0.3f);
		status.tick();
		status.tick();
		status.printStdout("line", true);
		status.printStdout("inline", false);
		status.printStderr("err", true);
		status.printStderr("inline-err", false);
		status.close();

		String outText=outBytes.toString(StandardCharsets.UTF_8);
		String errText=errBytes.toString(StandardCharsets.UTF_8);
		assertTrue(outText.contains("\u001b[?25l"));
		assertTrue(outText.contains("\u001b[?25h"));
		assertTrue(outText.contains("Working"));
		assertTrue(outText.contains("% ["));
		assertTrue(outText.contains("line"));
		assertTrue(outText.contains("inline"));
		assertTrue(outText.contains("err"));
		assertEquals("", errText);
	}

	@Test
	void progressClampsAtBounds() {
		ByteArrayOutputStream outBytes=new ByteArrayOutputStream();
		PrintStream out=new PrintStream(outBytes, true, StandardCharsets.UTF_8);
		ConsoleStatus status=new ConsoleStatus(true, out, out);

		status.setProgress(1.5f);
		status.setProgress(-0.2f);
		status.close();

		String outText=outBytes.toString(StandardCharsets.UTF_8);
		assertTrue(outText.contains("100% ["));
		assertTrue(outText.contains("  0% ["));
	}
}
