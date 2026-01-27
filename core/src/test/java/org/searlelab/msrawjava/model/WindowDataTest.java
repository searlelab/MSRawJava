package org.searlelab.msrawjava.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class WindowDataTest {

	@Test
	void twoArgConstructorSetsFieldsAndEmptyIonMobility() {
		WindowData data=new WindowData(0.5f, 100);

		assertEquals(0.5f, data.getAverageDutyCycle(), 1e-6);
		assertEquals(100, data.getNumberOfMSMS());
		assertFalse(data.getIonMobilityRange().isPresent());
		assertFalse(data.getRtRange().isPresent());
	}

	@Test
	void threeArgConstructorSetsAllFields() {
		Range imsRange=new Range(0.6f, 1.2f);
		WindowData data=new WindowData(0.75f, 200, Optional.of(imsRange));

		assertEquals(0.75f, data.getAverageDutyCycle(), 1e-6);
		assertEquals(200, data.getNumberOfMSMS());
		assertTrue(data.getIonMobilityRange().isPresent());
		assertEquals(0.6f, data.getIonMobilityRange().get().getStart(), 1e-6);
		assertEquals(1.2f, data.getIonMobilityRange().get().getStop(), 1e-6);
		assertFalse(data.getRtRange().isPresent());
	}

	@Test
	void threeArgConstructorWithEmptyOptional() {
		WindowData data=new WindowData(0.25f, 50, Optional.empty());

		assertEquals(0.25f, data.getAverageDutyCycle(), 1e-6);
		assertEquals(50, data.getNumberOfMSMS());
		assertFalse(data.getIonMobilityRange().isPresent());
		assertFalse(data.getRtRange().isPresent());
	}

	@Test
	void dutyCycleCanBeZero() {
		WindowData data=new WindowData(0.0f, 0);

		assertEquals(0.0f, data.getAverageDutyCycle(), 1e-6);
		assertEquals(0, data.getNumberOfMSMS());
	}

	@Test
	void dutyCycleCanBeOne() {
		WindowData data=new WindowData(1.0f, 1000);

		assertEquals(1.0f, data.getAverageDutyCycle(), 1e-6);
		assertEquals(1000, data.getNumberOfMSMS());
	}

	@Test
	void ionMobilityRangeIsIndependentOfConstruction() {
		Range imsRange=new Range(0.5f, 1.0f);
		WindowData data=new WindowData(0.5f, 100, Optional.of(imsRange));

		// Get the range and verify it matches
		Range retrieved=data.getIonMobilityRange().get();
		assertEquals(imsRange.getStart(), retrieved.getStart(), 1e-6);
		assertEquals(imsRange.getStop(), retrieved.getStop(), 1e-6);
	}

	@Test
	void rtRangeConstructorSetsRtRange() {
		Range imsRange=new Range(0.5f, 1.0f);
		Range rtRange=new Range(10.0f, 20.0f);
		WindowData data=new WindowData(0.5f, 100, Optional.of(imsRange), Optional.of(rtRange));

		assertTrue(data.getRtRange().isPresent());
		assertEquals(10.0f, data.getRtRange().get().getStart(), 1e-6);
		assertEquals(20.0f, data.getRtRange().get().getStop(), 1e-6);
	}
}
