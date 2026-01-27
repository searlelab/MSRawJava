package org.searlelab.msrawjava.algorithms.demux;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DemuxConfigTest {

	@Test
	void testDefaults() {
		DemuxConfig config=new DemuxConfig();
		assertEquals(DemuxConfig.DEFAULT_K, config.getK());
		assertEquals(DemuxConfig.InterpolationMethod.CUBIC_HERMITE, config.getInterpolationMethod());
		assertTrue(config.isIncludeEdgeSubWindows());
		assertEquals(1<<DemuxConfig.DEFAULT_K, config.getNumCacheEntries());
	}

	@Test
	void testBuilderOverrides() {
		DemuxConfig config=DemuxConfig.builder().k(9).useLogQuadratic().excludeEdgeSubWindows().build();

		assertEquals(9, config.getK());
		assertEquals(DemuxConfig.InterpolationMethod.LOG_QUADRATIC, config.getInterpolationMethod());
		assertFalse(config.isIncludeEdgeSubWindows());
		assertEquals(1<<9, config.getNumCacheEntries());
	}

	@Test
	void testInvalidKThrows() {
		assertThrows(IllegalArgumentException.class, () -> new DemuxConfig(6, DemuxConfig.InterpolationMethod.CUBIC_HERMITE));
		assertThrows(IllegalArgumentException.class, () -> new DemuxConfig(10, DemuxConfig.InterpolationMethod.CUBIC_HERMITE));
	}

	@Test
	void testToStringContainsSettings() {
		DemuxConfig config=new DemuxConfig(7, DemuxConfig.InterpolationMethod.LOG_QUADRATIC, false);
		String text=config.toString();
		assertTrue(text.contains("k=7"));
		assertTrue(text.contains("LOG_QUADRATIC"));
		assertTrue(text.contains("includeEdges=false"));
	}
}
