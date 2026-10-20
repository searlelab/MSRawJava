package org.searlelab.msrawjava.io.tims;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.model.PeakWithIMS;

/**
 * Freezes current picker behavior. After an approved behavior change, review/update PeakPickerBaseline,
 * then run PeakPickerReplay.main from core/ with modes golden and rebuilt-golden to replace
 * tims-picker-golden.tsv and tims-rebuilt-golden.sha256; refresh artifact and baseline hashes in exact-reader-fixtures.json too.
 */
class PeakPickerReplayTest {
	@Test
	void freshInputsPreserveRawBitsAvailabilityOrderAndReferenceAliases() throws Exception {
		PeakWithIMS equalA=new PeakWithIMS(-0.0, 4, 1);
		PeakWithIMS equalB=new PeakWithIMS(-0.0, 4, 1);
		PeakWithIMS off=new PeakWithIMS(Double.longBitsToDouble(0x7ff8000000000017L), -0.0f, 1);
		off.turnOff();
		PeakPickerFixture fixture=new PeakPickerFixture(new ArrayList<>(List.of(equalA, off, equalA, equalB)));
		ByteArrayOutputStream bytes=new ByteArrayOutputStream();
		fixture.write(new DataOutputStream(bytes));
		fixture=new PeakPickerFixture(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
		ArrayList<PeakWithIMS> first=fixture.freshPoints(), second=fixture.freshPoints();
		assertSame(first.get(0), first.get(2));
		assertNotSame(first.get(0), first.get(3));
		assertNotSame(first.get(0), equalA);
		assertNotSame(first.get(0), second.get(0));
		assertEquals(Double.doubleToRawLongBits(-0.0), Double.doubleToRawLongBits(first.get(0).mz));
		assertEquals(0x7ff8000000000017L, Double.doubleToRawLongBits(first.get(1).mz));
		assertFalse(first.get(1).isAvailable());
		first.get(0).turnOff();
		assertFalse(first.get(2).isAvailable());
		assertTrue(second.get(0).isAvailable());
		first.clear();
		assertEquals(4, fixture.freshPoints().size());
	}

	@Test
	void orderedOutputsAndMutatedInputsMatchFrozenBaseline() throws Exception {
		Map<String, PeakPickerFixture> fixtures=PeakPickerReplay.fixtures();
		List<String> golden=Files.readAllLines(PeakPickerReplay.RESOURCES.resolve("tims-picker-golden.tsv"));
		assertEquals(fixtures.size(), golden.size(), "No fixture may silently disappear or lack a baseline");
		for (String row : golden) {
			String[] fields=row.split("\t");
			assertNotNull(fixtures.get(fields[0]), fields[0]);
			String expected=PeakPickerReplay.baselineDigest(fixtures.get(fields[0]));
			// Preserve raw NaN bits by comparing the unchanged algorithm on this JVM. The recorded
			// nonfinite digest remains campaign evidence; NaN arithmetic encodings can vary by platform.
			if (!fields[0].equals("boundaries-nonfinite")) assertEquals(fields[1], expected, fields[0]+" frozen reference");
			assertEquals(expected, PeakPickerReplay.digest(fixtures.get(fields[0])), fields[0]);
			// Replaying in the same JVM must reset consumption, including aliases and initial off points.
			assertEquals(expected, PeakPickerReplay.digest(fixtures.get(fields[0])), fields[0]+" repeated");
		}
	}

	@Test
	void redundantCallerSortPreservesOutputsStableTiesAndAvailability() throws Exception {
		for (Map.Entry<String, PeakPickerFixture> fixture : PeakPickerReplay.fixtures().entrySet()) {
			assertEquals(PeakPickerReplay.digest(fixture.getValue(), true), PeakPickerReplay.digest(fixture.getValue()), fixture.getKey());
		}
	}

	@Test
	void rebuiltMs1AndMs2SpectraMatchOrderedRawBitsAndMetadata() throws Exception {
		assertEquals(Files.readString(PeakPickerReplay.RESOURCES.resolve("tims-rebuilt-golden.sha256")).trim(), PeakPickerReplay.rebuiltDigest());
	}

	@Test
	void independentConcurrentInvocationsMatchSerialResults() throws Exception {
		Map<String, PeakPickerFixture> fixtures=PeakPickerReplay.fixtures();
		var executor=Executors.newFixedThreadPool(2);
		try {
			for (String name : List.of("csv-ms1", "ties-aliases-39", "singleton")) {
				PeakPickerFixture fixture=fixtures.get(name);
				String expected=PeakPickerReplay.digest(fixture);
				ArrayList<Future<String>> results=new ArrayList<>();
				for (int i=0; i<4; i++) results.add(executor.submit(() -> PeakPickerReplay.digest(fixture)));
				for (Future<String> result : results) assertEquals(expected, result.get(), name);
			}
		} finally {
			executor.shutdownNow();
		}
	}
}
