package org.searlelab.msrawjava.gui.visualization;


final class XicToleranceOption {
	enum Unit {
		PPM,
		DA
	}

	static final XicToleranceOption DEFAULT=new XicToleranceOption("10 ppm", Unit.PPM, 10.0);

	final String label;
	final Unit unit;
	final double value;

	XicToleranceOption(String label, Unit unit, double value) {
		this.label=label;
		this.unit=unit;
		this.value=value;
	}

	double toleranceMz(double mz) {
		if (unit==Unit.DA) return value;
		return Math.abs(mz)*value/1_000_000.0;
	}

	static XicToleranceOption[] valuesForUi() {
		return new XicToleranceOption[] {new XicToleranceOption("5 ppm", Unit.PPM, 5.0), DEFAULT, new XicToleranceOption("25 ppm", Unit.PPM, 25.0),
				new XicToleranceOption("100 ppm", Unit.PPM, 100.0), new XicToleranceOption("0.4 m/z", Unit.DA, 0.4),
				new XicToleranceOption("1.0 m/z", Unit.DA, 1.0)};
	}

	@Override
	public String toString() {
		return label;
	}
}
