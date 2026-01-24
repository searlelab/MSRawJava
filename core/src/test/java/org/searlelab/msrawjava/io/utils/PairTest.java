package org.searlelab.msrawjava.io.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class PairTest {

	@Test
	void storesValuesAndExposesAccessors() {
		Pair<String, Integer> pair=new Pair<>("left", 42);
		assertEquals("left", pair.getX());
		assertEquals(42, pair.getY());
		assertEquals("left and 42", pair.toString());
	}

	@Test
	void supportsNullElements() {
		Pair<String, String> pair=new Pair<>(null, "right");
		assertNull(pair.getX());
		assertEquals("right", pair.getY());
		assertEquals("null and right", pair.toString());
	}
}
