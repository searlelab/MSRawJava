package org.searlelab.msrawjava.io.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResourceTreeExtractorTest {

	private static final String[] RAW_FIXTURES=new String[] {"Astral_GPFDIA_2mz.raw", "Exploris_DIA_16mzst.raw", "Stellar_DDA.raw", "Stellar_DIA_4mz.raw"};

	@TempDir
	Path tmp;

	@Test
	void extractRawdataDirectory_andVerifyKnownFilesByteForByte() throws Exception {
		// Ensure at least one of the expected fixtures is on the classpath
		List<String> present=new ArrayList<>();
		for (String f : RAW_FIXTURES) {
			URL u=getClass().getResource("/rawdata/"+f);
			if (u!=null) present.add(f);
		}
		Assumptions.assumeFalse(present.isEmpty(), "No RAW fixtures found under /rawdata/ on the test classpath");

		// Extract the whole directory to a temp output
		Path out=tmp.resolve("out");
		ResourceTreeExtractor.extractDirectory(getClass(), "/rawdata", out);

		// For each present fixture, compare size and SHA-256 hash to ensure exact copy
		for (String f : present) {
			Path extracted=out.resolve(f);
			assertTrue(Files.exists(extracted), "Extracted file missing: "+extracted);

			long sizeExtracted=Files.size(extracted);
			String shaOriginal, shaExtracted;

			try (InputStream in=getClass().getResourceAsStream("/rawdata/"+f)) {
				assertNotNull(in, "Original resource stream should be available for "+f);
			}

			// Compute SHA-256 for both
			shaOriginal=sha256OfResource("/rawdata/"+f);
			shaExtracted=sha256OfFile(extracted);

			assertEquals(shaOriginal, shaExtracted, "SHA-256 mismatch for "+f);

			// As a softer check, ensure extracted size equals the resource stream length read during hashing
			// (sha256OfResource returns exact bytes read; compare to extracted file size)
			assertEquals(lengthOfResource("/rawdata/"+f), sizeExtracted, "Size mismatch for "+f);
		}

		// Idempotency: re-extract should not corrupt or truncate
		ResourceTreeExtractor.extractDirectory(getClass(), "/rawdata", out);
		for (String f : present) {
			assertEquals(sha256OfResource("/rawdata/"+f), sha256OfFile(out.resolve(f)), "Re-extraction changed file content for "+f);
		}
	}

	private String sha256OfResource(String resPath) throws Exception {
		MessageDigest md=MessageDigest.getInstance("SHA-256");
		try (InputStream in=getClass().getResourceAsStream(resPath); DigestInputStream din=new DigestInputStream(in, md)) {
			byte[] buf=new byte[1<<16];
			while (din.read(buf)!=-1) {
				/* stream */ }
		}
		return HexFormat.of().formatHex(md.digest());
	}

	private long lengthOfResource(String resPath) throws Exception {
		long total=0;
		try (InputStream in=getClass().getResourceAsStream(resPath)) {
			byte[] buf=new byte[1<<16];
			int n;
			while ((n=in.read(buf))!=-1)
				total+=n;
		}
		return total;
	}

	private String sha256OfFile(Path file) throws Exception {
		MessageDigest md=MessageDigest.getInstance("SHA-256");
		try (InputStream in=Files.newInputStream(file); DigestInputStream din=new DigestInputStream(in, md)) {
			byte[] buf=new byte[1<<16];
			while (din.read(buf)!=-1) {
				/* stream */ }
		}
		return HexFormat.of().formatHex(md.digest());
	}
}
