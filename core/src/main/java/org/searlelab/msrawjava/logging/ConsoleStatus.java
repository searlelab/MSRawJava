package org.searlelab.msrawjava.logging;

/**
 * Renders a console progress bar with optional ANSI color output.
 */
public class ConsoleStatus {
	private static final String ESC="\u001b[";
	private static final String COLOR_RESET=ESC+"0m";
	private static final String COLOR_CYAN=ESC+"96m";
	private static final String COLOR_WHITE=ESC+"37m";
	private static final String COLOR_RED=ESC+"91m";
	//private static final char[] SPINNER=new char[] {'|', '/', '-', '\\'};
	private static final char[] SPINNER=new char[] {'▉', '▊', '▋', '▌', '▍', '▎', '▏', '▎', '▍', '▌', '▋', '▊', '▉'};

	private static final int BAR_WIDTH=60;
	private static final String BAR_PREFIX=String.format("%3d%% [%s] ", 100, " ".repeat(BAR_WIDTH));
	private static final int SPINNER_COLUMN=BAR_PREFIX.length()+1;

	private final Object lock=new Object();
	private final boolean enabled;
	private final java.io.PrintStream out;
	private final java.io.PrintStream err;
	private volatile String message="";
	private volatile float progress=0.0f;
	private int tickIndex=0;
	private volatile boolean dirty=true;
	private char lastSpinnerChar=SPINNER[0];

	public ConsoleStatus(boolean enabled, java.io.PrintStream out, java.io.PrintStream err) {
		this.enabled=enabled;
		this.out=out;
		this.err=err;
		if (enabled) {
			out.print(ESC+"?25l");
			out.flush();
			render();
		}
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setMessage(String message) {
		if (!enabled) return;
		this.message=message==null?"":message;
		dirty=true;
		render();
	}

	public void setProgress(float progress) {
		if (!enabled) return;
		this.progress=progress;
		dirty=true;
		render();
	}

	public void tick() {
		if (!enabled) return;
		tickIndex++;
		if (dirty) {
			render();
		} else {
			renderSpinnerOnly();
		}
	}

	public void printStdout(String text, boolean newline) {
		if (!enabled) {
			if (newline) {
				out.println(text);
			} else {
				out.print(text);
			}
			return;
		}
		synchronized (lock) {
			if (newline) {
				out.print(COLOR_WHITE+text+COLOR_RESET);
				out.println();
			} else {
				out.print(COLOR_WHITE+text+COLOR_RESET);
			}
			dirty=true;
			renderLocked();
		}
	}

	public void printStderr(String text, boolean newline) {
		if (!enabled) {
			if (newline) {
				err.println(text);
			} else {
				err.print(text);
			}
			return;
		}
		synchronized (lock) {
			if (newline) {
				out.print(COLOR_RED+text+COLOR_RESET);
				out.println();
			} else {
				out.print(COLOR_RED+text+COLOR_RESET);
			}
			dirty=true;
			renderLocked();
		}
	}

	public void close() {
		if (!enabled) return;
		synchronized (lock) {
			renderLocked();
			out.print(ESC+"?25h");
			out.flush();
		}
	}

	private void render() {
		synchronized (lock) {
			renderLocked();
		}
	}

	private void renderLocked() {
		if (!enabled) return;
		String msgLine=message;
		if (msgLine==null) msgLine="";
		char spinnerChar=SPINNER[Math.abs(tickIndex)%SPINNER.length];
		String barLine=buildBarLine(spinnerChar);
		StringBuilder out=new StringBuilder();
		out.append(ESC).append("s");
		out.append(ESC).append("999;1H");
		out.append('\r').append(ESC).append("2K");
		out.append(COLOR_CYAN).append(barLine).append(COLOR_RESET);
		out.append(ESC).append("1A");
		out.append('\r').append(ESC).append("2K");
		out.append(COLOR_CYAN).append(msgLine).append(COLOR_RESET);
		out.append(ESC).append("u");
		this.out.print(out.toString());
		this.out.flush();
		lastSpinnerChar=spinnerChar;
		dirty=false;
	}

	private void renderSpinnerOnly() {
		if (!enabled) return;
		char spinnerChar=SPINNER[Math.abs(tickIndex)%SPINNER.length];
		if (spinnerChar==lastSpinnerChar) return;
		StringBuilder out=new StringBuilder();
		out.append(ESC).append("s");
		out.append(ESC).append("999;1H");
		out.append('\r');
		out.append(ESC).append(SPINNER_COLUMN-1).append('C');
		out.append(COLOR_CYAN).append(spinnerChar).append(COLOR_RESET);
		out.append(ESC).append("u");
		this.out.print(out.toString());
		this.out.flush();
		lastSpinnerChar=spinnerChar;
	}

	private String buildBarLine(char spinnerChar) {
		int percent=Math.round(progress*100f);
		if (percent<0) percent=0;
		if (percent>100) percent=100;
		int filled=Math.round(progress*BAR_WIDTH);
		if (filled<0) filled=0;
		if (filled>BAR_WIDTH) filled=BAR_WIDTH;
		StringBuilder bar=new StringBuilder();
		for (int i=0; i<BAR_WIDTH; i++) {
			char c=' ';
			if (i<filled) {
				c='=';
			}
			bar.append(c);
		}
		return String.format("%3d%% [%s] %c", percent, bar.toString(), spinnerChar);
	}

	public static void main(String[] args) throws Exception {
		ConsoleStatus console=new ConsoleStatus(true, Logger.getStdout(), Logger.getStderr());
		Logger.setConsoleStatus(console);
		LoggingProgressIndicator indicator=new LoggingProgressIndicator(LoggingProgressIndicator.Mode.DEFAULT, true);
		for (int i=0; i<10; i++) {
			indicator.update("Status tick "+i, i/10f);
			Thread.sleep(500);
		}
		indicator.update("Done", 1.0f);
		indicator.close();
		Logger.close();
	}
}
