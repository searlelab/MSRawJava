package org.searlelab.msrawjava.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class OutputTypeTest {

	@Test
	void testOutputType() {
		Path dPath=Path.of("src", "test", "resources", "rawdata", "230711_idleflow_400-1000mz_25mz_diaPasef_10sec.d");

		Path outPath=OutputType.encyclopedia.getOutputFilePath(dPath.getParent(), "dummy_name");
		assertEquals("src/test/resources/rawdata/dummy_name.dia", outPath.toString());

		outPath=OutputType.mgf.getOutputFilePath(dPath.getParent(), "dummy_name");
		assertEquals("src/test/resources/rawdata/dummy_name.mgf", outPath.toString());

	}

}
