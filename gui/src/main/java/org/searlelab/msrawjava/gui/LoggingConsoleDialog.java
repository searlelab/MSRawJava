package org.searlelab.msrawjava.gui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * Non-modal dialog that captures and renders System.out/System.err.
 */
public final class LoggingConsoleDialog {
	private static final Object LOCK=new Object();
	private static final List<ConsoleChunk> pendingChunks=new ArrayList<>();

	private static volatile boolean installed=false;
	private static ConsoleWindow window=null;

	private LoggingConsoleDialog() {
	}

	public static void installSystemCapture() {
		synchronized (LOCK) {
			if (installed) return;
			PrintStream originalOut=System.out;
			PrintStream originalErr=System.err;
			System.setOut(new PrintStream(new TeeConsoleOutputStream(originalOut, false), true, StandardCharsets.UTF_8));
			System.setErr(new PrintStream(new TeeConsoleOutputStream(originalErr, true), true, StandardCharsets.UTF_8));
			installed=true;
		}
	}

	public static void showDialog(Frame owner) {
		SwingUtilities.invokeLater(() -> {
			ConsoleWindow dlg;
			List<ConsoleChunk> backlog;
			synchronized (LOCK) {
				if (window==null) {
					window=new ConsoleWindow(owner);
				}
				dlg=window;
				backlog=new ArrayList<>(pendingChunks);
				pendingChunks.clear();
			}
			dlg.applyThemeColors();
			for (ConsoleChunk chunk : backlog) {
				dlg.append(chunk.text, chunk.error);
			}
			dlg.setLocationRelativeTo(owner);
			dlg.setVisible(true);
			dlg.toFront();
		});
	}

	private static void publish(String text, boolean error) {
		if (text==null||text.isEmpty()) return;
		ConsoleWindow dlg;
		synchronized (LOCK) {
			dlg=window;
			if (dlg==null) {
				pendingChunks.add(new ConsoleChunk(text, error));
				return;
			}
		}
		dlg.append(text, error);
	}

	private static boolean isDarkTheme() {
		Color bg=UIManager.getColor("Panel.background");
		if (bg==null) return false;
		double brightness=0.2126*bg.getRed()+0.7152*bg.getGreen()+0.0722*bg.getBlue();
		return brightness<128.0;
	}

	private static SimpleAttributeSet attributesFor(boolean error) {
		Color color;
		if (!error) {
			color=getConsoleForeground();
		} else if (isDarkTheme()) {
			color=new Color(0xFF9E9E);
		} else {
			color=new Color(0x8B0000);
		}
		SimpleAttributeSet attrs=new SimpleAttributeSet();
		StyleConstants.setForeground(attrs, color);
		StyleConstants.setFontFamily(attrs, Font.SANS_SERIF);
		StyleConstants.setFontSize(attrs, 10);
		return attrs;
	}

	private static Color getConsoleBackground() {
		Color bg=UIManager.getColor("TextPane.background");
		if (bg==null) bg=UIManager.getColor("Panel.background");
		if (bg!=null) return bg;
		return isDarkTheme()?new Color(0x1e1e1e):Color.WHITE;
	}

	private static Color getConsoleForeground() {
		Color fg=UIManager.getColor("TextPane.foreground");
		if (fg!=null) return fg;
		return isDarkTheme()?new Color(0xe6e6e6):Color.BLACK;
	}

	private static final class ConsoleWindow extends JDialog {
		private static final long serialVersionUID=1L;

		private final JTextPane textPane=new JTextPane();

		private ConsoleWindow(Frame owner) {
			super(owner, "Logging Console", false);
			setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);

			textPane.setEditable(false);
			textPane.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
			textPane.setToolTipText("Captured standard output and error streams.");
			applyThemeColors();

			installActionsAndPopup();

			JScrollPane scroll=new JScrollPane(textPane);
			setContentPane(scroll);
			setSize(980, 320);
		}

		private void applyThemeColors() {
			Color bg=getConsoleBackground();
			Color fg=getConsoleForeground();
			textPane.setBackground(bg);
			textPane.setForeground(fg);
			textPane.setCaretColor(fg);
			if (textPane.getParent()!=null) {
				textPane.getParent().setBackground(bg);
			}
		}

		private void installActionsAndPopup() {
			KeyStroke copyKey=KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK);
			KeyStroke selectAllKey=KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK);

			textPane.getInputMap(JComponent.WHEN_FOCUSED).put(copyKey, "console.copy");
			textPane.getActionMap().put("console.copy", new AbstractAction() {
				private static final long serialVersionUID=1L;

				@Override
				public void actionPerformed(java.awt.event.ActionEvent e) {
					textPane.copy();
				}
			});
			textPane.getInputMap(JComponent.WHEN_FOCUSED).put(selectAllKey, "console.selectAll");
			textPane.getActionMap().put("console.selectAll", new AbstractAction() {
				private static final long serialVersionUID=1L;

				@Override
				public void actionPerformed(java.awt.event.ActionEvent e) {
					textPane.selectAll();
				}
			});

			JPopupMenu popup=new JPopupMenu();
			JMenuItem selectAllItem=new JMenuItem("Select All");
			selectAllItem.setAccelerator(selectAllKey);
			selectAllItem.addActionListener(e -> textPane.selectAll());

			JMenuItem copyItem=new JMenuItem("Copy");
			copyItem.setAccelerator(copyKey);
			copyItem.addActionListener(e -> textPane.copy());

			JMenuItem clearItem=new JMenuItem("Clear");
			clearItem.addActionListener(e -> clearText());

			popup.add(selectAllItem);
			popup.add(copyItem);
			popup.add(clearItem);
			textPane.setComponentPopupMenu(popup);
		}

		private void clearText() {
			StyledDocument doc=textPane.getStyledDocument();
			try {
				doc.remove(0, doc.getLength());
			} catch (BadLocationException ignore) {
			}
		}

		private void append(String text, boolean error) {
			Runnable writer=() -> {
				applyThemeColors();
				StyledDocument doc=textPane.getStyledDocument();
				try {
					doc.insertString(doc.getLength(), text, attributesFor(error));
				} catch (BadLocationException ignore) {
				}
				textPane.setCaretPosition(doc.getLength());
			};
			if (SwingUtilities.isEventDispatchThread()) {
				writer.run();
			} else {
				SwingUtilities.invokeLater(writer);
			}
		}
	}

	private static final class ConsoleChunk {
		private final String text;
		private final boolean error;

		private ConsoleChunk(String text, boolean error) {
			this.text=text;
			this.error=error;
		}
	}

	private static final class TeeConsoleOutputStream extends OutputStream {
		private final PrintStream mirror;
		private final boolean error;
		private final ByteArrayOutputStream buffer=new ByteArrayOutputStream(1024);
		private final Object lock=new Object();

		private TeeConsoleOutputStream(PrintStream mirror, boolean error) {
			this.mirror=mirror;
			this.error=error;
		}

		@Override
		public void write(int b) throws IOException {
			synchronized (lock) {
				mirror.write(b);
				buffer.write(b);
				if (b=='\n') {
					flushBufferLocked();
				}
			}
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			synchronized (lock) {
				mirror.write(b, off, len);
				for (int i=off; i<off+len; i++) {
					byte value=b[i];
					buffer.write(value);
					if (value=='\n') {
						flushBufferLocked();
					}
				}
			}
		}

		@Override
		public void flush() throws IOException {
			synchronized (lock) {
				mirror.flush();
				flushBufferLocked();
			}
		}

		@Override
		public void close() throws IOException {
			flush();
		}

		private void flushBufferLocked() {
			if (buffer.size()==0) return;
			String text=buffer.toString(StandardCharsets.UTF_8);
			buffer.reset();
			publish(text, error);
		}
	}
}
