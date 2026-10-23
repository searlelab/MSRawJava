package org.searlelab.msrawjava.gui;

import java.awt.Component;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

import org.searlelab.msrawjava.logging.Logger;

/** Shared native file-manager actions for directory-tree nodes and dataset rows. */
final class FileManagerActions {
	interface DesktopAccess {
		boolean supports(Desktop.Action action);
		void reveal(File file) throws IOException;
		void open(File directory) throws IOException;
	}

	@FunctionalInterface
	interface CommandLauncher {
		void launch(List<String> command) throws IOException;
	}

	private static final DesktopAccess SYSTEM_DESKTOP=new DesktopAccess() {
		@Override
		public boolean supports(Desktop.Action action) {
			return Desktop.isDesktopSupported()&&Desktop.getDesktop().isSupported(action);
		}

		@Override
		public void reveal(File file) {
			Desktop.getDesktop().browseFileDirectory(file);
		}

		@Override
		public void open(File directory) throws IOException {
			Desktop.getDesktop().open(directory);
		}
	};

	private FileManagerActions() {
	}

	static String label(String osName) {
		String os=osName.toLowerCase(Locale.ROOT);
		if (os.contains("mac")||os.contains("darwin")) return "Show in Finder";
		if (os.contains("win")) return "Show in File Explorer";
		return "Show in Files";
	}

	static JMenuItem menuItem(Component owner, Path path) {
		return menuItem(System.getProperty("os.name", ""), path, target -> show(owner, target));
	}

	static JMenuItem menuItem(String osName, Path path, Consumer<Path> reveal) {
		JMenuItem item=new JMenuItem(label(osName));
		item.setToolTipText("Open the enclosing folder in the system file manager.");
		item.addActionListener(event -> reveal.accept(path));
		return item;
	}

	private static void show(Component owner, Path path) {
		new SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() throws IOException {
				reveal(path, SYSTEM_DESKTOP, command -> new ProcessBuilder(command).redirectErrorStream(true)
						.redirectOutput(ProcessBuilder.Redirect.DISCARD).start(), System.getProperty("os.name", ""));
				return null;
			}

			@Override
			protected void done() {
				try {
					get();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (ExecutionException e) {
					Logger.errorException(e.getCause());
					JOptionPane.showMessageDialog(owner, "Could not open the enclosing folder for:\n"+path+"\n"+e.getCause().getMessage(),
							ProductBranding.PRODUCT_NAME, JOptionPane.ERROR_MESSAGE);
				}
			}
		}.execute();
	}

	static void reveal(Path path, DesktopAccess desktop, CommandLauncher launcher, String osName) throws IOException {
		Path target=path.toAbsolutePath().normalize();
		if (!Files.exists(target)) throw new IOException("File or directory no longer exists: "+target);
		Path parent=target.getParent();
		Path folder=parent==null?target:parent;
		if (parent!=null&&desktop.supports(Desktop.Action.BROWSE_FILE_DIR)) {
			try {
				desktop.reveal(target.toFile());
				return;
			} catch (UnsupportedOperationException ignored) {
				// Some desktop integrations advertise the action but do not implement it.
			}
		}
		if (desktop.supports(Desktop.Action.OPEN)) {
			desktop.open(folder.toFile());
		} else {
			launcher.launch(folderCommand(osName, folder));
		}
	}

	static List<String> folderCommand(String osName, Path folder) {
		String os=osName.toLowerCase(Locale.ROOT);
		String executable=(os.contains("mac")||os.contains("darwin"))?"open":os.contains("win")?"explorer.exe":"xdg-open";
		return List.of(executable, folder.toString());
	}
}
