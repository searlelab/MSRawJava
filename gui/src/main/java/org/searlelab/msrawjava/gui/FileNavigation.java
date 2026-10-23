package org.searlelab.msrawjava.gui;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import org.searlelab.msrawjava.logging.Logger;

/** Resolves filesystem inputs off the EDT and discards superseded navigation requests. */
final class FileNavigation {
	static final class Target {
		final Path directory;
		final Path selection;

		Target(Path directory, Path selection) {
			this.directory=directory;
			this.selection=selection;
		}
	}

	private final Executor background;
	private final Executor ui;
	private long revision;

	FileNavigation() {
		this(task -> new SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() {
				task.run();
				return null;
			}
		}.execute(), SwingUtilities::invokeLater);
	}

	FileNavigation(Executor background, Executor ui) {
		this.background=background;
		this.ui=ui;
	}

	void cancel() {
		revision++;
	}

	void open(List<File> files, boolean datasetsOnly, Consumer<Target> resolved, Runnable invalid) {
		long request=++revision;
		background.execute(() -> {
			Target target=resolve(files, datasetsOnly);
			ui.execute(() -> {
				if (request!=revision) return;
				if (target==null) invalid.run();
				else resolved.accept(target);
			});
		});
	}

	static Target resolve(List<File> files, boolean datasetsOnly) {
		for (File file : files) {
			if (file==null) continue;
			try {
				Path path=file.toPath().toAbsolutePath().normalize();
				Path datasetPath=BrowserDatasets.datasetPath(path);
				boolean dataset=datasetPath!=null;
				if (dataset) path=datasetPath;
				if (datasetsOnly&&!dataset) continue;
				if (Files.isDirectory(path)&&!dataset) return new Target(path, null);
				if ((dataset||Files.isRegularFile(path))&&path.getParent()!=null) {
					return new Target(path.getParent(), dataset?path:null);
				}
			} catch (RuntimeException e) {
				Logger.errorException(e);
			}
		}
		return null;
	}
}
