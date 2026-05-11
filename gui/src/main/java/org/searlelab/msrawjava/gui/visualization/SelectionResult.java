package org.searlelab.msrawjava.gui.visualization;

import java.util.List;

import org.searlelab.msrawjava.io.utils.Pair;
import org.searlelab.msrawjava.model.AcquiredSpectrum;

final class SelectionResult {
	final List<AcquiredSpectrum> entries;
	final AcquiredSpectrum displaySpectrum;
	final Pair<String[], String[]> scanMetadata;
	final float minRT;
	final float maxRT;

	SelectionResult(List<AcquiredSpectrum> entries, AcquiredSpectrum displaySpectrum, Pair<String[], String[]> scanMetadata, float minRT, float maxRT) {
		this.entries=entries;
		this.displaySpectrum=displaySpectrum;
		this.scanMetadata=scanMetadata;
		this.minRT=minRT;
		this.maxRT=maxRT;
	}
}
