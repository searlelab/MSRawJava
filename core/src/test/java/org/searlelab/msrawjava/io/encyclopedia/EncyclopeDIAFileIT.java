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
import java.util.Collections;
import java.util.HashMap;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.msrawjava.io.tims.BrukerTIMSFile;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.WindowData;

class EncyclopeDIAFileIT {

	private static final Path D_PATH=Path.of("src", "test", "resources", "rawdata", "230711_idleflow_400-1000mz_25mz_diaPasef_10sec.d");

	@TempDir
	Path tmp;

	@Test
	void writesDiaFromRealSubset_andSchemaLooksCorrect() throws Exception {
		Assumptions.assumeTrue(Files.exists(D_PATH), "Fixture .d not present: "+D_PATH);

		BrukerTIMSFile file=new BrukerTIMSFile();
		file.openFile(D_PATH);

		ArrayList<PrecursorScan> ms1s=file.getPrecursors(0f, Float.MAX_VALUE);
		HashMap<Range, WindowData> rangeMap=new HashMap<>(file.getRanges());
		ArrayList<Range> ranges=new ArrayList<>(rangeMap.keySet());
		if (ranges.isEmpty()) ranges.add(new Range(0f, Float.MAX_VALUE));
		Collections.sort(ranges);

		ArrayList<FragmentScan> ms2s=new ArrayList<>();
		final int MAX_MS1=5, MAX_MS2=20;
		if (ms1s.size()>MAX_MS1) ms1s=new ArrayList<>(ms1s.subList(0, MAX_MS1));

		for (Range r : ranges) {
			var chunk=file.getStripes(r, 0.0f, Float.MAX_VALUE, false);
			for (FragmentScan s : chunk) {
				ms2s.add(s);
				if (ms2s.size()>=MAX_MS2) break;
			}
			if (ms2s.size()>=MAX_MS2) break;
		}

		Assumptions.assumeTrue(!ms1s.isEmpty()&&!ms2s.isEmpty(), "Need both MS1 and MS2 to exercise DIA writer");

		EncyclopeDIAFile dia=new EncyclopeDIAFile();
		dia.openFile();
		dia.setFileName(D_PATH.getFileName().toString(), D_PATH.toString());
		// Supply real ranges from file
		dia.setRanges(rangeMap);
		// Some minimal metadata
		dia.addMetadata("Instrument", "timsTOF_test_fixture");
		dia.addSpectra(ms1s, ms2s);

		java.nio.file.Path out=tmp.resolve("subset.dia");
		dia.saveAsFile(out.toFile());
		dia.close();
		file.close();

		try (Connection c=DriverManager.getConnection("jdbc:sqlite:"+out.toString())) {
			DatabaseMetaData md=c.getMetaData();
			assertTableExists(md, "metadata");
			assertTableExists(md, "ranges");
			assertTableExists(md, "spectra");
			assertTableExists(md, "precursor");
			assertTableExists(md, "fractions");

			// Counts should match what we wrote
			assertEquals(ms2s.size(), count(c, "select count(*) from spectra"));
			assertEquals(ms1s.size(), count(c, "select count(*) from precursor"));

			// Ranges should be non-empty and cover at least the number of DIA windows
			int nRanges=count(c, "select count(*) from ranges");
			assertTrue(nRanges>0);

			// A couple of representative columns exist (schema sanity)
			assertTrue(hasColumn(c, "spectra", "SpectrumIndex"));
			assertTrue(hasColumn(c, "spectra", "IsolationWindowLower"));
			assertTrue(hasColumn(c, "spectra", "TIC"));
			assertTrue(hasColumn(c, "precursor", "SpectrumIndex"));
			assertTrue(hasColumn(c, "metadata", "Key"));
			assertTrue(hasColumn(c, "metadata", "Value"));
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

	private static boolean hasColumn(Connection c, String table, String column) throws SQLException {
		try (ResultSet rs=c.getMetaData().getColumns(null, null, table, column)) {
			return rs.next();
		}
	}
}
