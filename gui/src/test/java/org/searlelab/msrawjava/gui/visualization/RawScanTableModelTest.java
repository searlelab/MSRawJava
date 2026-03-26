package org.searlelab.msrawjava.gui.visualization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.model.ScanSummary;

class RawScanTableModelTest {

	@Test
	void precursorAndTicColumns_showExpectedValues() {
		RawScanTableModel model=new RawScanTableModel();
		model.updateEntries(List.of(ms1Summary(), ms2Summary()));

		assertNull(model.getValueAt(0, 3));
		assertEquals(508.5123, (Double)model.getValueAt(1, 3), 1e-6);
		assertEquals(1111.5f, (Float)model.getValueAt(0, 4), 1e-6f);
		assertEquals(2222.25f, (Float)model.getValueAt(1, 4), 1e-6f);
	}

	private static ScanSummary ms1Summary() {
		return new ScanSummary("ms1", 1, 60f, 1, 1111.5f, -1.0, true, null, 0.0, 0.0, 400.0, 1200.0, (byte)0);
	}

	private static ScanSummary ms2Summary() {
		return new ScanSummary("ms2", 2, 61f, 1, 2222.25f, 508.5123, false, null, 500.0, 517.0, 0.0, 0.0, (byte)2);
	}
}
