package org.searlelab.msrawjava.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.DataFormatException;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.io.utils.Pair;
import org.searlelab.msrawjava.model.AcquiredSpectrum;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.ScanSummary;
import org.searlelab.msrawjava.model.WindowData;

class StripeFileSourceTest {
	@Test
	void openReader_returnsFreshReaderEachTime() throws Exception {
		File referenceFile=new File("source.raw");
		AtomicInteger openedCount=new AtomicInteger(0);
		StripeFileSource source=new StripeFileSource() {
			@Override
			public StripeFileInterface openReader() throws IOException, SQLException {
				return new StubStripeFile(referenceFile, openedCount.incrementAndGet());
			}

			@Override
			public File getReferenceFile() {
				return referenceFile;
			}

			@Override
			public String getOriginalFileName() {
				return referenceFile.getName();
			}
		};

		StripeFileInterface first=source.openReader();
		StripeFileInterface second=source.openReader();
		try {
			assertNotSame(first, second);
			assertEquals(referenceFile, source.getReferenceFile());
			assertEquals("source.raw", source.getOriginalFileName());
			assertEquals("source.raw", first.getOriginalFileName());
			assertEquals("source.raw", second.getOriginalFileName());
			assertEquals(2, openedCount.get());
		} finally {
			first.close();
			second.close();
		}
	}

	private static final class StubStripeFile implements StripeFileInterface {
		private final File file;
		private final int id;

		private StubStripeFile(File file, int id) {
			this.file=file;
			this.id=id;
		}

		@Override
		public Map<Range, WindowData> getRanges() {
			return new HashMap<Range, WindowData>();
		}

		@Override
		public Map<String, String> getMetadata() {
			return new HashMap<String, String>();
		}

		@Override
		public void openFile(File userFile) {
		}

		@Override
		public ArrayList<PrecursorScan> getPrecursors(float minRT, float maxRT) throws IOException, SQLException, DataFormatException {
			return new ArrayList<PrecursorScan>();
		}

		@Override
		public ArrayList<FragmentScan> getStripes(double targetMz, float minRT, float maxRT, boolean sqrt) throws IOException, SQLException {
			return new ArrayList<FragmentScan>();
		}

		@Override
		public ArrayList<FragmentScan> getStripes(Range targetMzRange, float minRT, float maxRT, boolean sqrt) throws IOException, SQLException {
			return new ArrayList<FragmentScan>();
		}

		@Override
		public ArrayList<ScanSummary> getScanSummaries(float minRT, float maxRT) throws IOException, SQLException {
			return new ArrayList<ScanSummary>();
		}

		@Override
		public AcquiredSpectrum getSpectrum(ScanSummary summary) throws IOException, SQLException, DataFormatException {
			return null;
		}

		@Override
		public float getTIC() throws IOException, SQLException {
			return id;
		}

		@Override
		public Pair<float[], float[]> getTICTrace() throws IOException, SQLException {
			return new Pair<float[], float[]>(new float[0], new float[0]);
		}

		@Override
		public float getGradientLength() throws IOException, SQLException {
			return 0;
		}

		@Override
		public void close() {
		}

		@Override
		public boolean isOpen() {
			return true;
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
