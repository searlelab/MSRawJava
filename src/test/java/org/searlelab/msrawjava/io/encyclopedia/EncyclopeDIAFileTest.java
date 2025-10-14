package org.searlelab.msrawjava.io.encyclopedia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.WindowData;

class EncyclopeDIAFileTest {

	@TempDir
	Path tmp;

	private static PrecursorScan ms1(String name, int index, float rtSeconds, double scanLo, double scanHi, double[] mz, float[] inten, float[] ims) {
		return new PrecursorScan(name, index, rtSeconds, 0, scanLo, scanHi, null, mz, inten, ims);
	}

	private static FragmentScan ms2(String name, int index, float rtSeconds, double isoLo, double isoHi, byte z, double[] mz, float[] inten, float[] ims) {
		return new FragmentScan(name, "prec", index, rtSeconds, 0, null, isoLo, isoHi, mz, inten, ims, z, 0.0, 3000.0);
	}

	private static HashMap<Range, WindowData> singleRange() {
		HashMap<Range, WindowData> m=new HashMap<>();
		m.put(new Range(495.0, 505.0), new WindowData(0.5f, 1));
		return m;
	}

	@Test
	void writesMinimalDiaDatabase_withTablesAndRowCounts() throws Exception {
		// Build tiny inputs
		ArrayList<PrecursorScan> ms1s=new ArrayList<>();
		ms1s.add(ms1("ms1-1", 1, 1.0f, 100.0, 1000.0, new double[] {100.0, 200.0}, new float[] {10.0f, 0.0f}, null));

		ArrayList<FragmentScan> ms2s=new ArrayList<>();
		ms2s.add(ms2("ms2-2", 2, 2.0f, 499.9, 500.1, (byte)2, new double[] {150.0, 250.0}, new float[] {5.0f, 0.0f}, null));

		HashMap<Range, WindowData> ranges=singleRange();

		// Write .DIA
		EncyclopeDIAFile dia=new EncyclopeDIAFile();
		dia.openFile();
		dia.setFileName("tiny_run", "/data/tiny_run");
		dia.setRanges(ranges);
		dia.addMetadata("Instrument", "TestRig");
		dia.addSpectra(ms1s, ms2s);

		java.nio.file.Path out=tmp.resolve("tiny.dia");
		dia.saveAsFile(out.toFile());
		dia.close();

		assertTrue(Files.exists(out), "DIA file should be created");

		// Open SQLite and verify schema + counts
		try (Connection c=DriverManager.getConnection("jdbc:sqlite:"+out.toString())) {
			DatabaseMetaData md=c.getMetaData();

			// Tables present
			assertTableExists(md, "metadata");
			assertTableExists(md, "ranges");
			assertTableExists(md, "spectra");
			assertTableExists(md, "precursor");
			assertTableExists(md, "fractions");

			// Row counts
			assertEquals(1, count(c, "select count(*) from ranges"));
			assertEquals(1, count(c, "select count(*) from spectra"));
			assertEquals(1, count(c, "select count(*) from precursor"));

			// Metadata row exists (we inserted Instrument)
			assertTrue(count(c, "select count(*) from metadata")>=1);
			assertEquals("TestRig", string(c, "select Value from metadata where Key='Instrument'"));

			// Sanity check a spectra row has isolation window and spectrum index
			assertTrue(count(c, "select count(*) from spectra where SpectrumIndex=2")==1);
		}
	}

	private static void assertTableExists(DatabaseMetaData md, String tableName) throws SQLException {
		try (ResultSet rs=md.getTables(null, null, tableName, null)) {
			assertTrue(rs.next(), "Expected table not found: "+tableName);
		}
	}

	private static int count(Connection c, String sql) throws SQLException {
		try (Statement s=c.createStatement(); ResultSet rs=s.executeQuery(sql)) {
			return rs.next()?rs.getInt(1):0;
		}
	}

	private static String string(Connection c, String sql) throws SQLException {
		try (Statement s=c.createStatement(); ResultSet rs=s.executeQuery(sql)) {
			return rs.next()?rs.getString(1):null;
		}
	}
}
