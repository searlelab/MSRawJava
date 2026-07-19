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
		controller.bindControls(new JLabel(), null, null, new JTextField(), tolerances, new JButton());

		assertEquals(XicToleranceOption.DEFAULT, controller.getSelectedTolerance());
	}

	@Test
	void displayMode_defaultsToIntensityAndToggleRefreshesWithoutChangingExtractionState() {
		AtomicReference<Integer> refreshCount=new AtomicReference<>(0);
		RawBrowserXicController controller=new RawBrowserXicController(null, () -> refreshCount.set(refreshCount.get()+1), () -> {}, () -> {}, cursor -> {});

		assertEquals(XicDisplayMode.INTENSITY, controller.getDisplayMode());

		controller.setDisplayMode(XicDisplayMode.DELTA);
		assertEquals(XicDisplayMode.DELTA, controller.getDisplayMode());
		assertEquals(1, refreshCount.get());

		controller.setDisplayMode(XicDisplayMode.DELTA);
		assertEquals(1, refreshCount.get());
		assertFalse(controller.isXicModeActive());
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

	@Test
	void extractFromInput_reportsRejectedTargetsWhenNothingCanBeExtracted() {
		RawBrowserXicController controller=newController();
		JLabel feedback=new JLabel();
		controller.bindControls(feedback, null, null, new JTextField("BADTOKEN"), null, new JButton());

		controller.extractFromInput(ScanTypeFilterOption.ms1());

		assertEquals("Rejected: BADTOKEN", feedback.getText());
		assertFalse(controller.isXicModeActive());
	}

	private RawBrowserXicController newController() {
		return new RawBrowserXicController(null, () -> {}, () -> {}, () -> {}, cursor -> {});
	}
}
