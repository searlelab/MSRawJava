package org.searlelab.msrawjava.gui.visualization;

import java.awt.Cursor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.Timer;

import org.searlelab.msrawjava.gui.graphing.XYTrace;
import org.searlelab.msrawjava.io.StripeFileInterface;
import org.searlelab.msrawjava.logging.Logger;
import org.searlelab.msrawjava.model.AcquiredSpectrum;
import org.searlelab.msrawjava.model.ScanSummary;

final class RawBrowserXicController {
	private final StripeFileInterface stripe;
	private final Runnable refreshChromatogramChart;
	private final Runnable resetCurrentSelection;
	private final Runnable refreshTopChartSelection;
	private final Consumer<Cursor> busyCursorUpdater;
	private RawBrowserScanRenderer renderer;
	private List<ScanSummary> allScans=List.of();
	private long xicToken=0L;
	private RawBrowserXicUtils.ParsedXicTargets activeParsedXicTargets=RawBrowserXicUtils.ParsedXicTargets.empty();
	private List<RawBrowserXicUtils.XicTarget> activeXicTargets=List.of();
	private XicTraceData activeXicTraceData=XicTraceData.empty(List.of(), XicToleranceOption.DEFAULT);
	private XicToleranceOption activeXicTolerance=XicToleranceOption.DEFAULT;
	private XicDisplayMode displayMode=XicDisplayMode.INTENSITY;
	private float activeXicMax=0.0f;
	private boolean xicActive=false;
	private int activeXicExtractionCount=0;
	private volatile XicExtractionProgress activeXicProgress;
	private Timer xicProgressTimer;
	private long xicProgressTimerToken=-1L;
	private JLabel xicLabel;
	private JRadioButton xicModeButton;
	private JRadioButton deltaModeButton;
	private JTextField xicField;
	private JComboBox<XicToleranceOption> xicToleranceFilter;
	private JButton extractXicButton;

	RawBrowserXicController(StripeFileInterface stripe, Runnable refreshChromatogramChart, Runnable resetCurrentSelection, Runnable refreshTopChartSelection,
			Consumer<Cursor> busyCursorUpdater) {
		this.stripe=stripe;
		this.refreshChromatogramChart=refreshChromatogramChart;
		this.resetCurrentSelection=resetCurrentSelection;
		this.refreshTopChartSelection=refreshTopChartSelection;
		this.busyCursorUpdater=busyCursorUpdater;
	}

	void setRenderer(RawBrowserScanRenderer renderer) {
		this.renderer=renderer;
	}

	void bindControls(JLabel xicLabel, JRadioButton xicModeButton, JRadioButton deltaModeButton, JTextField xicField, JComboBox<XicToleranceOption> xicToleranceFilter,
			JButton extractXicButton) {
		this.xicLabel=xicLabel;
		this.xicModeButton=xicModeButton;
		this.deltaModeButton=deltaModeButton;
		this.xicField=xicField;
		this.xicToleranceFilter=xicToleranceFilter;
		this.extractXicButton=extractXicButton;
	}

	void setAllScans(List<ScanSummary> allScans) {
		this.allScans=allScans==null?List.of():List.copyOf(allScans);
	}

	void resetDataState() {
		xicActive=false;
		activeParsedXicTargets=RawBrowserXicUtils.ParsedXicTargets.empty();
		activeXicTargets=List.of();
		activeXicTraceData=XicTraceData.empty(List.of(), activeXicTolerance);
		activeXicMax=0.0f;
	}

	boolean handleScanTypeChanged(ScanTypeFilterOption selected) {
		updateControlEnabledState(selected);
		if (selected==null||selected.isAll()) {
			clearState();
			resetCurrentSelection.run();
			return false;
		}
		if (!activeParsedXicTargets.hasAnyTargets()) return false;
		List<RawBrowserXicUtils.XicTarget> targets=selectTargetsForScanType(activeParsedXicTargets, selected);
		if (targets.isEmpty()) {
			clearState();
			resetCurrentSelection.run();
			return false;
		}
		extractTracesAsync(selected, targets, getSelectedTolerance());
		return true;
	}

	void extractFromInput(ScanTypeFilterOption activeScanType) {
		if (activeScanType==null||activeScanType.isAll()) return;
		RawBrowserXicUtils.ParsedXicTargets parsedTargets=RawBrowserXicUtils.parseXicTargets(xicField.getText());
		List<RawBrowserXicUtils.XicTarget> targets=selectTargetsForScanType(parsedTargets, activeScanType);
		if (targets.isEmpty()) {
			clearState();
			refreshChromatogramChart.run();
			resetCurrentSelection.run();
			return;
		}
		activeParsedXicTargets=parsedTargets;
		extractTracesAsync(activeScanType, targets, getSelectedTolerance());
	}

	void runExample(String value, Runnable selectMs1ScanType) {
		selectMs1ScanType.run();
		xicField.setText(value);
	}

	void clearState() {
		xicToken++;
		stopProgressTimer(-1L);
		activeXicProgress=null;
		xicActive=false;
		activeParsedXicTargets=RawBrowserXicUtils.ParsedXicTargets.empty();
		activeXicTargets=List.of();
		activeXicTraceData=XicTraceData.empty(List.of(), activeXicTolerance);
		activeXicMax=0.0f;
	}

	void resetBusyState() {
		stopProgressTimer(-1L);
		activeXicProgress=null;
		activeXicExtractionCount=0;
		updateBusyCursor();
	}

	void updateControlEnabledState(ScanTypeFilterOption activeScanType) {
		boolean enabled=activeScanType!=null&&!activeScanType.isAll();
		if (xicLabel!=null) xicLabel.setEnabled(enabled);
		if (xicModeButton!=null) xicModeButton.setEnabled(enabled);
		if (deltaModeButton!=null) deltaModeButton.setEnabled(enabled);
		if (xicField!=null) xicField.setEnabled(enabled);
		if (xicToleranceFilter!=null) xicToleranceFilter.setEnabled(enabled);
		if (extractXicButton!=null) extractXicButton.setEnabled(enabled);
	}

	boolean isXicModeActive() {
		return xicActive&&!activeXicTargets.isEmpty();
	}

	List<RawBrowserXicUtils.XicTarget> getActiveXicTargets() {
		return activeXicTargets;
	}

	XicTraceData getActiveXicTraceData() {
		XicExtractionProgress progress=activeXicProgress;
		if (progress!=null&&progress.token==xicToken) {
			synchronized (progress.lock) {
				if (progress.extractedCount>0) {
					return XicTraceData.fromProgress(activeXicTargets, activeXicTolerance, progress, progress.extractedCount, progress.maxIntensity);
				}
			}
		}
		return activeXicTraceData;
	}

	XicToleranceOption getActiveXicTolerance() {
		return activeXicTolerance;
	}

	XicDisplayMode getDisplayMode() {
		return displayMode;
	}

	void setDisplayMode(XicDisplayMode displayMode) {
		XicDisplayMode newMode=displayMode==null?XicDisplayMode.INTENSITY:displayMode;
		if (this.displayMode==newMode) return;
		this.displayMode=newMode;
		snapshotRunningProgress(true);
		refreshChromatogramChart.run();
		refreshTopChartSelection.run();
	}

	private void snapshotRunningProgress(boolean markFlushed) {
		XicExtractionProgress progress=activeXicProgress;
		if (progress==null||progress.token!=xicToken) return;
		synchronized (progress.lock) {
			activeXicTraceData=XicTraceData.fromProgress(activeXicTargets, activeXicTolerance, progress, progress.extractedCount, progress.maxIntensity);
			activeXicMax=progress.maxIntensity;
			if (markFlushed) progress.flushedCount=progress.extractedCount;
		}
	}

	float getActiveXicMax() {
		return activeXicMax;
	}

	static List<RawBrowserXicUtils.XicTarget> selectTargetsForScanType(RawBrowserXicUtils.ParsedXicTargets parsedTargets, ScanTypeFilterOption scanType) {
		if (parsedTargets==null||scanType==null||scanType.isAll()) return List.of();
		if (scanType.isMs1()) return parsedTargets.precursorTargets();
		return parsedTargets.fragmentTargets();
	}

	XicToleranceOption getSelectedTolerance() {
		XicToleranceOption selected=xicToleranceFilter==null?null:(XicToleranceOption)xicToleranceFilter.getSelectedItem();
		return selected==null?XicToleranceOption.DEFAULT:selected;
	}

	List<XYTrace> buildEmptyXicTraces(List<RawBrowserXicUtils.XicTarget> targets) {
		return XicTraceData.empty(targets, getSelectedTolerance()).buildIntensityTraces();
	}

	String formatXicTargetLabel(RawBrowserXicUtils.XicTarget target) {
		return String.format(Locale.ROOT, "%s (%.3f m/z)", target.label(), target.mz());
	}

	private void extractTracesAsync(ScanTypeFilterOption scanType, List<RawBrowserXicUtils.XicTarget> targets, XicToleranceOption toleranceOption) {
		final long token=++xicToken;
		final ScanTypeFilterOption scanTypeAtRequest=scanType;
		final List<RawBrowserXicUtils.XicTarget> targetCopy=List.copyOf(targets);
		final XicToleranceOption tolerance=toleranceOption;
		activeXicTargets=targetCopy;
		activeXicTolerance=tolerance;
		activeXicMax=0.0f;
		activeXicTraceData=XicTraceData.empty(targetCopy, tolerance);
		xicActive=true;
		activeXicProgress=null;
		refreshChromatogramChart.run();
		resetCurrentSelection.run();
		beginExtraction();
		startProgressTimer(token);
		new SwingWorker<XicExtractionResult, Void>() {
			@Override
			protected XicExtractionResult doInBackground() throws Exception {
				return extractTraceData(token, scanTypeAtRequest, targetCopy, tolerance);
			}

			@Override
			protected void done() {
				try {
					if (token!=xicToken) return;
					XicExtractionResult result=get();
					activeXicTraceData=result.traceData;
					activeXicMax=result.maxIntensity;
					flushProgress(token, true);
					refreshTopChartSelection.run();
				} catch (Exception ex) {
					Logger.logException(ex);
				} finally {
					stopProgressTimer(token);
					endExtraction();
				}
			}
		}.execute();
	}

	XicExtractionResult extractTraceData(long token, ScanTypeFilterOption scanType, List<RawBrowserXicUtils.XicTarget> targets, XicToleranceOption toleranceOption) {
		ArrayList<ScanSummary> sourceScans=new ArrayList<>();
		for (ScanSummary summary : allScans) {
			if (scanType.includes(summary)) {
				boolean keep=false;
				targetLoop: for (int t=0; t<targets.size(); t++) {
					double target=targets.get(t).mz();
					if (RawBrowserXicUtils.isTargetInScanWindow(target, summary.getScanWindowLower(), summary.getScanWindowUpper())) {
						keep=true;
						break targetLoop;
					}
				}
				if (keep) sourceScans.add(summary);
			}
		}
		sourceScans.sort((a, b) -> Float.compare(a.getScanStartTime(), b.getScanStartTime()));
		double[] xMinutes=new double[sourceScans.size()];
		double[][] intensities=new double[targets.size()][sourceScans.size()];
		double[][] observedMzs=new double[targets.size()][sourceScans.size()];
		double[][] deltas=new double[targets.size()][sourceScans.size()];
		for (int t=0; t<targets.size(); t++) {
			for (int i=0; i<sourceScans.size(); i++) {
				observedMzs[t][i]=Double.NaN;
				deltas[t][i]=Double.NaN;
			}
		}
		XicExtractionProgress progress=new XicExtractionProgress(token, xMinutes, intensities, observedMzs, deltas);
		activeXicProgress=progress;
		for (int i=0; i<sourceScans.size(); i++) {
			ScanSummary summary=sourceScans.get(i);
			xMinutes[i]=summary.getScanStartTime()/60.0;
			float scanMax=0.0f;
			AcquiredSpectrum spectrum;
			try {
				spectrum=stripe.getSpectrum(summary);
			} catch (Exception e) {
				Logger.logException(e);
				spectrum=null;
			}
			if (spectrum!=null) {
				double[] mz=spectrum.getMassArray();
				float[] intensity=spectrum.getIntensityArray();
				for (int t=0; t<targets.size(); t++) {
					double target=targets.get(t).mz();
					if (!RawBrowserXicUtils.isTargetInScanWindow(target, spectrum.getScanWindowLower(), spectrum.getScanWindowUpper())) continue;
					double tol=toleranceOption.toleranceMz(target);
					RawBrowserXicUtils.XicPointExtraction point=RawBrowserXicUtils.extractWeightedPointWithinTolerance(mz, intensity, target, tol);
					intensities[t][i]=point.intensity;
					if (point.hasSignal()) {
						observedMzs[t][i]=point.observedMz;
						deltas[t][i]=RawBrowserXicUtils.deltaForDisplay(point.deltaMz, target, toleranceOption);
					}
					if (point.intensity>scanMax) scanMax=(float)point.intensity;
				}
			}
			synchronized (progress.lock) {
				if (scanMax>progress.maxIntensity) progress.maxIntensity=scanMax;
				progress.extractedCount=i+1;
			}
		}
		float max;
		synchronized (progress.lock) {
			max=progress.maxIntensity;
		}
		XicTraceData traceData=new XicTraceData(targets, toleranceOption, xMinutes, intensities, observedMzs, deltas, max);
		return new XicExtractionResult(traceData, max);
	}

	private void startProgressTimer(long token) {
		stopProgressTimer(-1L);
		xicProgressTimerToken=token;
		xicProgressTimer=new Timer(200, e -> flushProgress(token, false));
		xicProgressTimer.setRepeats(true);
		xicProgressTimer.start();
	}

	private void stopProgressTimer(long token) {
		if (xicProgressTimer==null) return;
		if (token>=0L&&xicProgressTimerToken!=token) return;
		xicProgressTimer.stop();
		xicProgressTimer=null;
		xicProgressTimerToken=-1L;
	}

	private void flushProgress(long token, boolean flushAll) {
		if (token!=xicToken) {
			stopProgressTimer(token);
			return;
		}
		XicExtractionProgress progress=activeXicProgress;
		if (progress==null||progress.token!=token||renderer==null) return;
		int startIndex;
		int endIndex;
		float progressMax;
		synchronized (progress.lock) {
			startIndex=progress.flushedCount;
			int extracted=progress.extractedCount;
			endIndex=flushAll?progress.xMinutes.length:extracted;
			if (endIndex<startIndex) endIndex=startIndex;
			progress.flushedCount=endIndex;
			progressMax=progress.maxIntensity;
			activeXicTraceData=XicTraceData.fromProgress(activeXicTargets, activeXicTolerance, progress, endIndex, progressMax);
		}
		renderer.appendXicProgress(progress, activeXicTargets.size(), startIndex, endIndex);
		if (progressMax!=activeXicMax) {
			activeXicMax=progressMax;
			refreshTopChartSelection.run();
		}
	}

	private void beginExtraction() {
		activeXicExtractionCount++;
		updateBusyCursor();
	}

	private void endExtraction() {
		if (activeXicExtractionCount>0) activeXicExtractionCount--;
		updateBusyCursor();
	}

	private void updateBusyCursor() {
		Cursor cursor=(activeXicExtractionCount>0)?Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR):Cursor.getDefaultCursor();
		busyCursorUpdater.accept(cursor);
	}
}
