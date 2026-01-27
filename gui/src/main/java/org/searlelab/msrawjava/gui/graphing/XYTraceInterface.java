package org.searlelab.msrawjava.gui.graphing;

import java.awt.Color;
import java.util.Optional;

import org.searlelab.msrawjava.io.utils.Pair;

public interface XYTraceInterface {

	Optional<Color> getColor();

	Optional<Float> getThickness();

	String getName();

	GraphType getType();

	Pair<double[], double[]> toArrays();

	int size();

	public double getMaxY();
}