package org.searlelab.msrawjava.io;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.msrawjava.io.utils.Pair;
import org.searlelab.msrawjava.logging.ProgressIndicator;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.ScanSummary;
import org.searlelab.msrawjava.model.WindowData;
import org.searlelab.msrawjava.threading.ProcessingThreadPool;

class RawFileConvertersStandardTest {

	@TempDir
	Path tmp;

	@Test
	void writeStandard_writesMgf_andUpdatesProgress() throws Exception {
		Path outputDir=tmp.resolve("out");
		Files.createDirectories(outputDir);

		FakeStripeFile raw=new FakeStripeFile(tmp.resolve("sample.raw").toFile());
		raw.setGradientLength(1.0f);
		raw.setRanges(Map.of(new Range(400.0f, 500.0f), new WindowData(0.5f, 2)));
		raw.setMetadata(Map.of("filename", "sample.raw", "filelocation", "/tmp/sample.raw"));
		raw.setScans(singleMs1(), singleMs2());

		ConversionParameters params=ConversionParameters.builder().outType(OutputType.mgf).build();

		CapturingProgress progress=new CapturingProgress();
		ProcessingThreadPool pool=ProcessingThreadPool.createDefault();
		boolean ok;
		try {
			ok=RawFileConverters.writeStandard(pool, raw, outputDir, params, progress);
		} finally {
			pool.close();
		}

		assertTrue(ok, "Expected writeStandard to return true");
		Path outFile=outputDir.resolve("sample.mgf");
		assertTrue(Files.exists(outFile), "Expected output MGF to exist");
		String text=Files.readString(outFile);
		assertTrue(text.contains("BEGIN IONS"), "MGF should contain at least one spectrum block");
		assertTrue(progress.getTotalProgress()>=1.0f, "Progress should reach completion");
		assertTrue(progress.hasMessageContaining("Finished converting"), "Should log a completion message");
	}

	@Test
	void writeStandard_cancelledEarly_returnsFalse() throws Exception {
		Path outputDir=tmp.resolve("out_cancel");
		Files.createDirectories(outputDir);

		FakeStripeFile raw=new FakeStripeFile(tmp.resolve("cancel.raw").toFile());
		raw.setGradientLength(1.0f);
		raw.setRanges(Collections.emptyMap());
		raw.setMetadata(Collections.emptyMap());
		raw.setScans(singleMs1(), singleMs2());

		ConversionParameters params=ConversionParameters.builder().outType(OutputType.mgf).build();

		CapturingProgress progress=new CapturingProgress();
		progress.cancel();

		ProcessingThreadPool pool=ProcessingThreadPool.createDefault();
		boolean ok;
		try {
			ok=RawFileConverters.writeStandard(pool, raw, outputDir, params, progress);
		} finally {
			pool.close();
		}

		assertFalse(ok, "Expected writeStandard to return false when canceled");
		Path outFile=outputDir.resolve("cancel.mgf");
		assertFalse(Files.exists(outFile), "Canceled conversion should not save output");
	}

	private static ArrayList<PrecursorScan> singleMs1() {
		ArrayList<PrecursorScan> ms1s=new ArrayList<>();
		ms1s.add(new PrecursorScan("ms1", 1, 0.5f, 0, 400.0, 500.0, null, new double[] {401.0}, new float[] {10.0f}, null));
		return ms1s;
	}

	private static ArrayList<FragmentScan> singleMs2() {
		ArrayList<FragmentScan> ms2s=new ArrayList<>();
		ms2s.add(new FragmentScan("ms2", "prec", 2, 450.0, 0.7f, 0, null, 449.5, 450.5, new double[] {450.1}, new float[] {5.0f}, null, (byte)2, 0.0, 2000.0));
		return ms2s;
	}

	private static final class CapturingProgress implements ProgressIndicator {
		private volatile float totalProgress;
		private volatile boolean canceled;
		private final ArrayList<String> messages=new ArrayList<>();

		@Override
		public void update(String message) {
			messages.add(message);
		}

		@Override
		public void update(String message, float totalProgress) {
			messages.add(message);
			this.totalProgress=totalProgress;
		}

		@Override
		public float getTotalProgress() {
			return totalProgress;
		}

		@Override
		public boolean isCanceled() {
			return canceled;
		}

		void cancel() {
			this.canceled=true;
		}

		boolean hasMessageContaining(String needle) {
			return messages.stream().anyMatch(m -> m.contains(needle));
		}
	}

	private static final class FakeStripeFile implements StripeFileInterface {
		private final File file;
		private boolean open=true;
		private float gradientLength=1.0f;
		private Map<Range, WindowData> ranges=new HashMap<>();
		private Map<String, String> metadata=Collections.emptyMap();
		private ArrayList<PrecursorScan> ms1s=new ArrayList<>();
		private ArrayList<FragmentScan> ms2s=new ArrayList<>();

		private FakeStripeFile(File file) {
			this.file=file;
		}

		void setGradientLength(float gradientLength) {
			this.gradientLength=gradientLength;
		}

		void setRanges(Map<Range, WindowData> ranges) {
			this.ranges=new HashMap<>(ranges);
		}

		void setMetadata(Map<String, String> metadata) {
			this.metadata=new HashMap<>(metadata);
		}

		void setScans(ArrayList<PrecursorScan> ms1s, ArrayList<FragmentScan> ms2s) {
			this.ms1s=ms1s;
			this.ms2s=ms2s;
		}

		@Override
		public Map<Range, WindowData> getRanges() {
			return ranges;
		}

		@Override
		public Map<String, String> getMetadata() {
			return metadata;
		}

		@Override
		public void openFile(File userFile) throws IOException, SQLException {
			open=true;
		}

		@Override
		public ArrayList<PrecursorScan> getPrecursors(float minRT, float maxRT) {
			return minRT==0.0f?ms1s:new ArrayList<>();
		}

		@Override
		public ArrayList<FragmentScan> getStripes(double targetMz, float minRT, float maxRT, boolean sqrt) {
			return getStripes(new Range((float)targetMz, (float)targetMz), minRT, maxRT, sqrt);
		}

		@Override
		public ArrayList<FragmentScan> getStripes(Range targetMzRange, float minRT, float maxRT, boolean sqrt) {
			return minRT==0.0f?ms2s:new ArrayList<>();
		}

		@Override
		public ArrayList<ScanSummary> getScanSummaries(float minRT, float maxRT) {
			ArrayList<ScanSummary> out=new ArrayList<>();
			for (PrecursorScan ms1 : ms1s) {
				out.add(new ScanSummary(ms1.getSpectrumName(), ms1.getSpectrumIndex(), ms1.getScanStartTime(), ms1.getFraction(), -1.0, true,
						ms1.getIonInjectionTime(), ms1.getIsolationWindowLower(), ms1.getIsolationWindowUpper(), ms1.getScanWindowLower(),
						ms1.getScanWindowUpper(), (byte)0));
			}
			for (FragmentScan ms2 : ms2s) {
				out.add(new ScanSummary(ms2.getSpectrumName(), ms2.getSpectrumIndex(), ms2.getScanStartTime(), ms2.getFraction(), ms2.getPrecursorMZ(), false,
						ms2.getIonInjectionTime(), ms2.getIsolationWindowLower(), ms2.getIsolationWindowUpper(), ms2.getScanWindowLower(),
						ms2.getScanWindowUpper(), ms2.getCharge()));
			}
			return out;
		}

		@Override
		public org.searlelab.msrawjava.model.AcquiredSpectrum getSpectrum(ScanSummary summary) {
			if (summary==null) return null;
			for (PrecursorScan ms1 : ms1s) {
				if (ms1.getSpectrumIndex()==summary.getSpectrumIndex()) return ms1;
			}
			for (FragmentScan ms2 : ms2s) {
				if (ms2.getSpectrumIndex()==summary.getSpectrumIndex()) return ms2;
			}
			return null;
		}

		@Override
		public float getTIC() {
			return 0f;
		}

		@Override
		public Pair<float[], float[]> getTICTrace() {
			return new Pair<>(new float[] {0f}, new float[] {0f});
		}

		@Override
		public float getGradientLength() {
			return gradientLength;
		}

		@Override
		public void close() {
			open=false;
		}

		@Override
		public boolean isOpen() {
			return open;
		}

		@Override
		public File getFile() {
			return file;
		}

		@Override
		public String getOriginalFileName() {
			return file.getName();
		}
	}
}
