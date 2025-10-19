package org.searlelab.msrawjava.io;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class RawFileConvertersTest {
	@Test
	void writeTims_missingDir_throws() {
		Path missing=Path.of("src", "test", "resources", "rawdata", "nope_does_not_exist.d");
		assertThrows(Exception.class, () -> RawFileConverters.writeTims(missing, missing.getParent(), OutputType.mgf, 1.0f, 1.0f));
	}
}
