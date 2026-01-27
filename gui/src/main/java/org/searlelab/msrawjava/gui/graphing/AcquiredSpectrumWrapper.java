package org.searlelab.msrawjava.gui.graphing;

import java.awt.Color;
import java.util.Optional;

import org.searlelab.msrawjava.algorithms.MatrixMath;
import org.searlelab.msrawjava.io.utils.Pair;
import org.searlelab.msrawjava.model.AcquiredSpectrum;

/**
 * Wraps AcquiredSpectrum as an XYTraceInterface.
 */
public class AcquiredSpectrumWrapper implements XYTraceInterface {
	private final AcquiredSpectrum spectrum;

	public AcquiredSpectrumWrapper(AcquiredSpectrum spectrum) {
		this.spectrum=spectrum;
	}

	@Override
	public Optional<Color> getColor() {
		return Optional.empty();
	}

	@Override
	public Optional<Float> getThickness() {
		return Optional.empty();
	}

	@Override
	public String getName() {
		return spectrum.getSpectrumName();
	}

	@Override
	public GraphType getType() {
		return GraphType.spectrum;
	}

	@Override
	public Pair<double[], double[]> toArrays() {
		return new Pair<double[], double[]>(spectrum.getMassArray(), MatrixMath.toDoubleArray(spectrum.getIntensityArray()));
	}

	@Override
	public int size() {
		return spectrum.getMassArray().length;
	}

	public AcquiredSpectrum getSpectrum() {
		return spectrum;
	}

	@Override
	public double getMaxY() {
		return MatrixMath.max(spectrum.getIntensityArray());
	}
}
