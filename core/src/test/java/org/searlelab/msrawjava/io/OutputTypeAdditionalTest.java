package org.searlelab.msrawjava.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.io.encyclopedia.EncyclopeDIAFile;

class OutputTypeAdditionalTest {

	@Test
	void changeExtension_handlesNamesWithoutDots() {
		assertEquals("file.mzML", OutputType.changeExtension("file", ".mzML"));
		assertEquals("file.mgf", OutputType.changeExtension("file", ".mgf"));
	}

	@Test
	void outputFilePath_supportsMzML() {
		Path out=Path.of("out");
		Path mzml=OutputType.mzML.getOutputFilePath(out, "sample.raw");
		assertEquals(out.resolve("sample.mzML"), mzml);
	}

	@Test
	void getOutputSpectrumFile_opensWriters() throws Exception {
		OutputSpectrumFile mgf=OutputType.mgf.getOutputSpectrumFile();
		assertTrue(mgf instanceof MGFOutputFile);
		mgf.close();

		OutputSpectrumFile mzml=OutputType.mzML.getOutputSpectrumFile();
		assertTrue(mzml instanceof MZMLOutputFile);
		mzml.close();

		OutputSpectrumFile dia=OutputType.EncyclopeDIA.getOutputSpectrumFile();
		assertTrue(dia instanceof EncyclopeDIAFile);
		dia.close();
	}
}
