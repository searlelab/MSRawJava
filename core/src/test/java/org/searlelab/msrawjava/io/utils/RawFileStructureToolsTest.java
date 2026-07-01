package org.searlelab.msrawjava.io.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.algorithms.demux.DemuxDesignMatrix;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.WindowData;

class RawFileStructureToolsTest {
	@Test
	void emptyRanges_areDda() {
		assertEquals(DataAcquisitionType.DDA, RawFileStructureTools.getDataType(Map.of()));
	}

	@Test
	void tooManyWindows_areDda() {
		LinkedHashMap<Range, WindowData> ranges=new LinkedHashMap<>();
		for (int i=0; i<10001; i++) {
			ranges.put(new Range(i, i+1), window());
		}
		assertEquals(DataAcquisitionType.DDA, RawFileStructureTools.getDataType(ranges));
	}

	@Test
	void lowObservationWindow_isDda() {
		LinkedHashMap<Range, WindowData> ranges=windows(400, 402, 402, 404);
		ranges.put(new Range(404, 406), new WindowData(1f, 4, Optional.empty(), Optional.of(new Range(0, 10))));
		assertEquals(DataAcquisitionType.DDA, RawFileStructureTools.getDataType(ranges));
	}

	@Test
	void singleRepeatedIsolationWindow_isPrm() {
		assertEquals(DataAcquisitionType.PRM, RawFileStructureTools.getDataType(windows(400, 408)));
	}

	@Test
	void contiguousWindows_areDia() {
		assertEquals(DataAcquisitionType.DIA, RawFileStructureTools.getDataType(windows(400, 402, 402, 404, 404, 406)));
	}
	
	@Test
	void boundaryTolerancePreventsFalsePrmOrDdaCall() {
		double offset=RawFileStructureTools.WINDOW_BOUNDARY_TOLERANCE*0.75;
		LinkedHashMap<Range, WindowData> ranges=windows(400, 408, 408+offset, 416, 416+offset, 424);
		assertEquals(DataAcquisitionType.DIA, RawFileStructureTools.getDataType(ranges));
	}

	@Test
	void firstSingleOrphan_isPrm() {
		LinkedHashMap<Range, WindowData> ranges=windows(400, 402, 450, 452, 452, 454);
		assertEquals(DataAcquisitionType.PRM, RawFileStructureTools.getDataType(ranges));
	}

	@Test
	void multiWindowIslandAlone_isNotPrm() {
		LinkedHashMap<Range, WindowData> ranges=windows(400, 402, 402, 404, 450, 452, 452, 454);
		assertEquals(DataAcquisitionType.DIA, RawFileStructureTools.getDataType(ranges));
	}

	@Test
	void rtSchedulingPreventsFalseOrphan() {
		LinkedHashMap<Range, WindowData> ranges=new LinkedHashMap<>();
		ranges.put(new Range(400, 402), window(0, 10));
		ranges.put(new Range(500, 502), window(20, 30));
		assertEquals(DataAcquisitionType.DIA, RawFileStructureTools.getDataType(ranges));
	}

	@Test
	void marginPattern_returnsPerSideTrim() {
		LinkedHashMap<Range, WindowData> ranges=windows(499.5, 502.5, 501.5, 504.5, 503.5, 506.5);
		assertFalse(RawFileStructureTools.isStaggered(ranges));
		assertEquals(0.5, RawFileStructureTools.getPrecursorMarginSize(ranges).orElseThrow(), 1e-6);
	}

	@Test
	void fiveMzWindowsWithTwoMzOverlap_areMargins() {
		LinkedHashMap<Range, WindowData> ranges=windows(399, 404, 402, 407, 405, 410, 408, 413, 411, 416);
		assertFalse(RawFileStructureTools.isStaggered(ranges));
		assertEquals(1.0, RawFileStructureTools.getPrecursorMarginSize(ranges).orElseThrow(), 1e-6);
	}

	@Test
	void staggeredPattern_isStaggeredAndHasNoMargin() {
		LinkedHashMap<Range, WindowData> ranges=windows(500, 504, 502, 506, 504, 508, 506, 510);
		assertTrue(RawFileStructureTools.isStaggered(ranges));
		assertTrue(RawFileStructureTools.getPrecursorMarginSize(ranges).isEmpty());
	}

	@Test
	void sixMzWindowsWithThreeMzOverlap_areStaggered() {
		LinkedHashMap<Range, WindowData> ranges=windows(398.5, 404.5, 401.5, 407.5, 404.5, 410.5, 407.5, 413.5, 410.5, 416.5, 413.5, 419.5);
		assertTrue(RawFileStructureTools.isStaggered(ranges));
		assertTrue(RawFileStructureTools.getPrecursorMarginSize(ranges).isEmpty());
	}

	@Test
	void narrowHalfOverlapWindows_areStaggeredNotMargins() {
		LinkedHashMap<Range, WindowData> ranges=windows(399.5, 401.5, 400.5, 402.5, 401.5, 403.5, 402.5, 404.5, 403.5, 405.5, 404.5, 406.5);
		assertTrue(RawFileStructureTools.isStaggered(ranges));
		assertTrue(RawFileStructureTools.getPrecursorMarginSize(ranges).isEmpty());
	}

	@Test
	void substantialButNotHalfWidthOverlap_isNotStaggered() {
		LinkedHashMap<Range, WindowData> ranges=windows(500, 510, 507, 517, 514, 524, 521, 531);
		assertFalse(RawFileStructureTools.isStaggered(ranges));
	}

	private static LinkedHashMap<Range, WindowData> windows(double... bounds) {
		LinkedHashMap<Range, WindowData> ranges=new LinkedHashMap<>();
		for (int i=0; i<bounds.length; i+=2) {
			ranges.put(new Range(bounds[i], bounds[i+1]), window());
		}
		return ranges;
	}

	private static WindowData window() {
		return window(0, 10);
	}

	private static WindowData window(double rtStart, double rtStop) {
		return new WindowData(1f, 10, Optional.empty(), Optional.of(new Range(rtStart, rtStop)));
	}
}
