package org.searlelab.msrawjava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VersionTest {

	@Test
	void getVersion_returnsNonNull() {
		String v=Version.getVersion();
		assertNotNull(v);
		assertTrue(!v.isEmpty());
	}

	@Test
	void compareToAndEquals_handleOrdering() {
		Version v1=new Version(1, 2, 3, false);
		Version v2=new Version(1, 2, 4, false);
		Version v3=new Version(1, 2, 3, false);

		assertTrue(v2.compareTo(v1)>0);
		assertEquals(0, v1.compareTo(v3));
		assertTrue(v1.equals(v3));
		assertTrue(!v1.equals(v2));
	}
}
