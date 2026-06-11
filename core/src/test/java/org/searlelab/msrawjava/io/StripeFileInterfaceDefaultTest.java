package org.searlelab.msrawjava.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.zip.DataFormatException;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.io.utils.Pair;
import org.searlelab.msrawjava.model.AcquiredSpectrum;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.ScanSummary;
import org.searlelab.msrawjava.model.WindowData;

class StripeFileInterfaceDefaultTest {

	@Test
	void defaultPrecursorMarginAndMetadataAreEmptyAndNoOp() throws Exception {
		StripeFileInterface stripe=new StubStripeFile();

		assertEquals(0.0, stripe.getPrecursorMarginSize(), 0.0);
		stripe.setPrecursorMarginSize(7.5);
		assertEquals(0.0, stripe.getPrecursorMarginSize(), 0.0);

		Pair<String[], String[]> metadata=stripe.getScanMetadata(null);
		assertArrayEquals(new String[0], metadata.x);
		assertArrayEquals(new String[0], metadata.y);
	}

	private static final class StubStripeFile implements StripeFileInterface {
		@Override
		public Map<Range, WindowData> getRanges() {
			return Map.of();
		}

		@Override
		public Map<String, String> getMetadata() throws IOException, SQLException {
			return Map.of();
		}

		@Override
		public void openFile(File userFile) throws IOException, SQLException {
		}

		@Override
		public ArrayList<PrecursorScan> getPrecursors(float minRT, float maxRT) throws IOException, SQLException, DataFormatException {
			return new ArrayList<>();
		}

		@Override
		public ArrayList<FragmentScan> getStripes(double targetMz, float minRT, float maxRT, boolean sqrt) throws IOException, SQLException {
			return new ArrayList<>();
		}

		@Override
		public ArrayList<FragmentScan> getStripes(Range targetMzRange, float minRT, float maxRT, boolean sqrt) throws IOException, SQLException {
			return new ArrayList<>();
		}

		@Override
		public ArrayList<ScanSummary> getScanSummaries(float minRT, float maxRT) throws IOException, SQLException {
			return new ArrayList<>();
		}

		@Override
		public AcquiredSpectrum getSpectrum(ScanSummary summary) throws IOException, SQLException, DataFormatException {
			return null;
		}

		@Override
		public float getTIC() throws IOException, SQLException {
			return 0;
		}

		@Override
		public Pair<float[], float[]> getTICTrace() throws IOException, SQLException {
			return new Pair<>(new float[0], new float[0]);
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
			return false;
		}

		@Override
		public File getFile() {
			return null;
		}

		@Override
		public String getOriginalFileName() {
			return "";
		}
	}
}
