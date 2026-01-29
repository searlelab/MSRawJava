package org.searlelab.msrawjava.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class TdfFormatExceptionTest {

	@Test
	void constructors_preserveMessageAndCause() {
		Exception root=new Exception("root");
		TdfFormatException withCause=new TdfFormatException("bad tdf", root);
		assertEquals("bad tdf", withCause.getMessage());
		assertEquals(root, withCause.getCause());

		TdfFormatException msgOnly=new TdfFormatException("just msg");
		assertEquals("just msg", msgOnly.getMessage());
		assertNull(msgOnly.getCause());

		TdfFormatException causeOnly=new TdfFormatException(root);
		assertNotNull(causeOnly.getCause());
		assertEquals(root, causeOnly.getCause());
	}
}
