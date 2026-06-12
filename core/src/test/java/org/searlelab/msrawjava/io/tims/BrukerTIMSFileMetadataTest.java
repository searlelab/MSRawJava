package org.searlelab.msrawjava.io.tims;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class BrukerTIMSFileMetadataTest {
	@Test
	void extractRunStartTime_parsesAcquisitionDateTimeWhenPresent() throws Exception {
		Path path=Path.of("src", "test", "resources", "rawdata", "230711_idleflow_400-1000mz_25mz_diaPasef_10sec.d");
		Assumptions.assumeTrue(Files.exists(path), "Fixture .d not present: "+path);

		Map<String, String> metadata=readGlobalMetadata(path);

		assertEquals(Date.from(Instant.parse("2023-07-11T20:28:01.167Z")), BrukerTIMSFile.extractRunStartTime(metadata).orElseThrow());
	}

	@Test
	void extractRunStartTime_returnsEmptyWhenAcquisitionDateMissing() throws Exception {
		Path path=Path.of("src", "test", "resources", "rawdata", "dda_test.d");
		Assumptions.assumeTrue(Files.exists(path), "Fixture .d not present: "+path);

		Map<String, String> metadata=readGlobalMetadata(path);

		assertTrue(BrukerTIMSFile.extractRunStartTime(metadata).isEmpty());
	}

	@Test
	void metadataFailureSummary_reportsMissingColumnWithoutStackTrace() {
		SQLException failure=new SQLException("[SQLITE_ERROR] SQL error or missing database (no such column: t2)");

		assertEquals("missing column t2", BrukerTIMSFile.metadataFailureSummary(failure));
	}

	@Test
	void metadataFailureSummary_reportsMissingTableWithoutStackTrace() {
		SQLException failure=new SQLException("[SQLITE_ERROR] SQL error or missing database (no such table: GlobalMetadata)");

		assertEquals("missing table GlobalMetadata", BrukerTIMSFile.metadataFailureSummary(failure));
	}

	@Test
	void metadataFailureSummary_keepsGenericFailureMessage() {
		SQLException failure=new SQLException("database disk image is malformed");

		assertEquals("database disk image is malformed", BrukerTIMSFile.metadataFailureSummary(failure));
	}

	private static Map<String, String> readGlobalMetadata(Path dPath) throws Exception {
		LinkedHashMap<String, String> metadata=new LinkedHashMap<>();
		try (Connection conn=DriverManager.getConnection("jdbc:sqlite:"+dPath.resolve("analysis.tdf").toAbsolutePath());
				PreparedStatement ps=conn.prepareStatement("SELECT Key, Value FROM GlobalMetadata");
				ResultSet rs=ps.executeQuery()) {
			while (rs.next()) {
				metadata.put(rs.getString(1), rs.getString(2));
			}
		}
		return metadata;
	}
}
