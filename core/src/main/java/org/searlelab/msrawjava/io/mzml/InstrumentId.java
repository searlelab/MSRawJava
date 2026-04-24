package org.searlelab.msrawjava.io.mzml;

import java.util.Objects;

public final class InstrumentId {
	public final String instrumentConfigurationId;
	public final String accession;
	public final String name;

	private InstrumentId(String instrumentConfigurationId, String accession, String name) {
		this.instrumentConfigurationId=instrumentConfigurationId;
		this.accession=accession;
		this.name=name;
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof InstrumentId
				&& Objects.equals(instrumentConfigurationId, ((InstrumentId)obj).instrumentConfigurationId)
				&& Objects.equals(accession, ((InstrumentId)obj).accession)
				&& Objects.equals(name, ((InstrumentId)obj).name);
	}

	@Override
	public int hashCode() {
		return Objects.hash(instrumentConfigurationId, accession, name);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String instrumentConfigurationId;
		private String accession;
		private String name;

		public Builder setInstrumentConfigurationId(String instrumentConfigurationId) {
			this.instrumentConfigurationId=instrumentConfigurationId;
			return this;
		}

		public Builder setAccession(String accession) {
			this.accession=accession;
			return this;
		}

		public Builder setName(String name) {
			this.name=name;
			return this;
		}

		public InstrumentId build() {
			return new InstrumentId(instrumentConfigurationId, accession, name);
		}
	}
}
