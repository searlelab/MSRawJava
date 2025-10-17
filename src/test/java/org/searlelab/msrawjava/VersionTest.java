package org.searlelab.msrawjava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VersionTest {

	@Test
	void parsesBasicAndSnapshot_andToStringReflects() {
		Version v123=new Version("1.2.3");
		assertEquals("1.2.3", v123.toString());
		
		v123=new Version("v1.2.3");
		assertEquals("v1.2.3", v123.toString());

		Version snap=new Version("1.2.3-SNAPSHOT");
		assertTrue(snap.toString().toUpperCase().contains("SNAPSHOT"), "Snapshot should be reflected in toString");
	}

	@Test
	void trimsLeadingV_andComparesLexicographically() {
		Version vA=new Version("v2.0.0");
		Version vB=new Version("2.0.0");
		Version vOld=new Version("1.9.9");
		assertEquals(vA, vB, "Leading 'v' should be ignored");
		assertTrue(vA.amIAbove(vOld));
		assertTrue(vA.compareTo(vOld)>0);
		assertEquals(vA.hashCode(), vB.hashCode());
	}

	@Test
	void handlesUnknownImplementationVersion_nonNull() {
		String s=Version.getVersion();
		assertNotNull(s);
		assertFalse(s.isEmpty(), "getVersion should return a non-empty string");
	}

	@Test
	void nonNumericRevisionFallsBack_andOrdersBelowNumeric() {
		Version a=new Version("1.2.beta"); // falls back to -1 revision internally
		Version b=new Version("1.2.0");
		assertTrue(b.compareTo(a)>0, "1.2.0 should be above non-numeric 1.2.*");
	}
}
