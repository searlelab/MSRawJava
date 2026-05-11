package org.searlelab.msrawjava.gui.visualization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.awt.Cursor;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.model.Range;

class RawBrowserXicControllerTest {

	@Test
	void selectTargetsForScanType_usesPrecursorTargetsForMs1AndFragmentTargetsForMs2() {
		RawBrowserXicUtils.ParsedXicTargets parsed=RawBrowserXicUtils.parseXicTargets("PEPTIDE++");

		assertEquals(parsed.precursorTargets(), RawBrowserXicController.selectTargetsForScanType(parsed, ScanTypeFilterOption.ms1()));
		assertEquals(parsed.fragmentTargets(), RawBrowserXicController.selectTargetsForScanType(parsed, ScanTypeFilterOption.ms2Range(new Range(400.0, 500.0))));
		assertEquals(List.of(), RawBrowserXicController.selectTargetsForScanType(parsed, ScanTypeFilterOption.allSpectra()));
	}

	@Test
	void buildEmptyXicTraces_formatsLabelsAndUsesOneTracePerTarget() {
		RawBrowserXicController controller=newController();
		RawBrowserXicUtils.ParsedXicTargets parsed=RawBrowserXicUtils.parseXicTargets("445.34, PEPTIDE++");
		List<RawBrowserXicUtils.XicTarget> targets=parsed.precursorTargets();

		assertEquals(targets.size(), controller.buildEmptyXicTraces(targets).size());
		assertEquals("XIC 445.3400 (445.340 m/z)", controller.buildEmptyXicTraces(targets).get(0).getName());
	}

	@Test
	void getSelectedTolerance_defaultsWhenNoSelection() {
		RawBrowserXicController controller=newController();
		JComboBox<XicToleranceOption> tolerances=new JComboBox<>(XicToleranceOption.valuesForUi());
		tolerances.setSelectedItem(null);
		controller.bindControls(new JLabel(), new JTextField(), tolerances, new JButton());

		assertEquals(XicToleranceOption.DEFAULT, controller.getSelectedTolerance());
	}

	@Test
	void clearState_disablesXicModeAndResetBusyStateClearsWaitCursor() {
		AtomicReference<Cursor> cursor=new AtomicReference<>();
		RawBrowserXicController controller=new RawBrowserXicController(null, () -> {}, () -> {}, () -> {}, cursor::set);
		controller.clearState();
		controller.resetBusyState();

		assertFalse(controller.isXicModeActive());
		assertEquals(Cursor.getDefaultCursor(), cursor.get());
	}

	private RawBrowserXicController newController() {
		return new RawBrowserXicController(null, () -> {}, () -> {}, () -> {}, cursor -> {});
	}
}
