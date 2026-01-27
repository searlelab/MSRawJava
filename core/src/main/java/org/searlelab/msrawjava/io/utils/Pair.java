package org.searlelab.msrawjava.io.utils;

public class Pair<X, Y> {
	public final X x;
	public final Y y;

	public Pair(X x, Y y) {
		this.x=x;
		this.y=y;
	}

	public X getX() {
		return x;
	}

	public Y getY() {
		return y;
	}

	@Override
	public String toString() {
		return x+" and "+y;
	}
}