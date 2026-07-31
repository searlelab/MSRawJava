package org.searlelab.msrawjava.io.utils;

import org.searlelab.msrawjava.API;

/**
 * Simple immutable pair container.
 */
public class Pair<X, Y> {
	public final X x;
	public final Y y;

	@API(status = API.Status.STABLE, since = "v26.7.31")
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
