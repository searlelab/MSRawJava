package org.searlelab.msrawjava.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExportParametersTest {

    @TempDir
    Path tmp;

    @Test
    void storesProvidedValues_andAllowsNullOutputDir() {
        ArrayList<File> files = new ArrayList<>();
        files.add(new File("a.raw"));
        files.add(new File("b.d"));

        OutputType type = OutputType.mzml;
        Path out = tmp.resolve("out");
        float ms1 = 7.5f;
        float ms2 = 3.25f;

        ExportParameters p = new ExportParameters(files, type, out, ms1, ms2);
        assertEquals(files, p.getFileList());
        assertEquals(type, p.getOutType());
        assertEquals(out, p.getOutputDirPath());
        assertEquals(ms1, p.getMinimumMS1Intensity());
        assertEquals(ms2, p.getMinimumMS2Intensity());

        ExportParameters p2 = new ExportParameters(files, type, null, ms1, ms2);
        assertNull(p2.getOutputDirPath(), "null output directory should be allowed");
    }
}
