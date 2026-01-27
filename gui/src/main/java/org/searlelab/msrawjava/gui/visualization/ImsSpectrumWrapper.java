package org.searlelab.msrawjava.gui.visualization;

import org.searlelab.msrawjava.gui.graphing.AcquiredSpectrumWrapper;
import org.searlelab.msrawjava.gui.graphing.GraphType;
import org.searlelab.msrawjava.model.AcquiredSpectrum;

public class ImsSpectrumWrapper extends AcquiredSpectrumWrapper {
	public ImsSpectrumWrapper(AcquiredSpectrum spectrum) {
		super(spectrum);
	}

	@Override
	public GraphType getType() {
		return GraphType.imsspectrum;
	}
}
