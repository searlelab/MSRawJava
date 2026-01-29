package org.searlelab.msrawjava.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class OutputTypeTest {

	@Test
	void changeExtension_handlesWithOrWithoutDot() {
		assertEquals("file.mzML", OutputType.changeExtension("file.raw", ".mzML"));
		assertEquals("file.mgf", OutputType.changeExtension("file", ".mgf"));
		assertEquals("sample.2.dia", OutputType.changeExtension("sample.dia", ".2.dia"));
	}

	@Test
	void outputFilePath_usesExpectedExtensions() {
		Path out=Path.of("/tmp/out");
		assertEquals(out.resolve("run.dia"), OutputType.EncyclopeDIA.getOutputFilePath(out, "run.raw"));
		assertEquals(out.resolve("run.mgf"), OutputType.mgf.getOutputFilePath(out, "run.raw"));
		assertEquals(out.resolve("run.mzML"), OutputType.mzML.getOutputFilePath(out, "run.raw"));
	}

	@Test
	void outputSpectrumFile_canBeOpenedAndClosed() throws Exception {
		OutputSpectrumFile dia=OutputType.EncyclopeDIA.getOutputSpectrumFile();
		assertNotNull(dia);
		dia.close();

		OutputSpectrumFile mgf=OutputType.mgf.getOutputSpectrumFile();
		assertNotNull(mgf);
		mgf.close();

		OutputSpectrumFile mzml=OutputType.mzML.getOutputSpectrumFile();
		assertNotNull(mzml);
		mzml.close();
	}
}
