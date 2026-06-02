package org.searlelab.msrawjava.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GuiProcessingActivityTest {

	@BeforeEach
	void setUp() {
		GuiProcessingActivity.resetForTests();
	}

	@AfterEach
	void tearDown() {
		GuiProcessingActivity.resetForTests();
	}

	@Test
	void inactiveByDefault() {
		assertFalse(GuiProcessingActivity.isForegroundWorkActive());
		assertEquals(0, GuiProcessingActivity.activeForegroundWorkCount());
	}

	@Test
	void activeWhileTokenIsOpen() throws Exception {
		AutoCloseable token=GuiProcessingActivity.beginForegroundWork();

		assertTrue(GuiProcessingActivity.isForegroundWorkActive());
		assertEquals(1, GuiProcessingActivity.activeForegroundWorkCount());

		token.close();
		assertFalse(GuiProcessingActivity.isForegroundWorkActive());
		assertEquals(0, GuiProcessingActivity.activeForegroundWorkCount());
	}

	@Test
	void overlappingTokensRemainActiveUntilAllClose() throws Exception {
		AutoCloseable first=GuiProcessingActivity.beginForegroundWork();
		AutoCloseable second=GuiProcessingActivity.beginForegroundWork();

		assertTrue(GuiProcessingActivity.isForegroundWorkActive());
		assertEquals(2, GuiProcessingActivity.activeForegroundWorkCount());

		first.close();
		assertTrue(GuiProcessingActivity.isForegroundWorkActive());
		assertEquals(1, GuiProcessingActivity.activeForegroundWorkCount());

		second.close();
		assertFalse(GuiProcessingActivity.isForegroundWorkActive());
		assertEquals(0, GuiProcessingActivity.activeForegroundWorkCount());
	}

	@Test
	void tokenCloseIsIdempotent() throws Exception {
		AutoCloseable token=GuiProcessingActivity.beginForegroundWork();

		token.close();
		token.close();

		assertFalse(GuiProcessingActivity.isForegroundWorkActive());
		assertEquals(0, GuiProcessingActivity.activeForegroundWorkCount());
	}

	@Test
	void listenersFireOnlyOnInactiveActiveTransitions() throws Exception {
		AtomicInteger calls=new AtomicInteger(0);
		GuiProcessingActivity.addListener(calls::incrementAndGet);

		AutoCloseable first=GuiProcessingActivity.beginForegroundWork();
		AutoCloseable second=GuiProcessingActivity.beginForegroundWork();
		second.close();
		first.close();

		assertEquals(2, calls.get());
	}
}
