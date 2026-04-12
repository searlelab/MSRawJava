package org.searlelab.msrawjava.io.mzml;

import java.util.Objects;
import java.util.Optional;

public final class InstrumentComponent {
	public final int order;
	public final String cvRef;
	public final String accessionId;
	public final String name;
	public final Type type;

	public enum Type {
		SOURCE("source"),
		ANALYZER("analyzer"),
		DETECTOR("detector");

		public final String name;

		Type(String name) {
			this.name=name;
		}

		public static Optional<Type> getTypeByName(String name) {
			for (Type t : Type.values()) {
				if (t.name.equalsIgnoreCase(name)) {
					return Optional.of(t);
				}
			}
			return Optional.empty();
		}
	}

	private InstrumentComponent(Type type, int order, String cvRef, String accessionId, String name) {
		this.type=type;
		this.order=order;
		this.cvRef=cvRef;
		this.accessionId=accessionId;
		this.name=name;
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof InstrumentComponent
				&& Objects.equals(type, ((InstrumentComponent)obj).type)
				&& order==((InstrumentComponent)obj).order
				&& Objects.equals(cvRef, ((InstrumentComponent)obj).cvRef)
				&& Objects.equals(accessionId, ((InstrumentComponent)obj).accessionId)
				&& Objects.equals(name, ((InstrumentComponent)obj).name);
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, order, cvRef, accessionId, name);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private int order;
		private String cvRef;
		private String accessionId;
		private String name;
		private Type type;

		public Builder setOrder(int order) {
			this.order=order;
			return this;
		}

		public Builder setCvRef(String cvRef) {
			this.cvRef=cvRef;
			return this;
		}

		public Builder setAccessionId(String accessionId) {
			this.accessionId=accessionId;
			return this;
		}

		public Builder setName(String name) {
			this.name=name;
			return this;
		}

		public Builder setType(Type type) {
			this.type=type;
			return this;
		}

		public InstrumentComponent build() {
			return new InstrumentComponent(type, order, cvRef, accessionId, name);
		}
	}
}
