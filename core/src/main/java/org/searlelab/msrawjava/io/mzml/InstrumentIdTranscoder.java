package org.searlelab.msrawjava.io.mzml;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Joiner;

public final class InstrumentIdTranscoder {
	private static final String ENTRY_DELIM=",";

	private InstrumentIdTranscoder() {
	}

	private enum Key {
		CONFIGURATION_ID("configurationId") {
			@Override
			String encodedPart(InstrumentId instrumentId) {
				return instrumentId.instrumentConfigurationId;
			}

			@Override
			InstrumentId.Builder decodeAppendImplementation(InstrumentId.Builder builder, String encodedEntryValue) {
				return builder.setInstrumentConfigurationId(encodedEntryValue);
			}
		},
		ACCESSION("accession") {
			@Override
			String encodedPart(InstrumentId instrumentId) {
				return instrumentId.accession;
			}

			@Override
			InstrumentId.Builder decodeAppendImplementation(InstrumentId.Builder builder, String encodedEntryValue) {
				return builder.setAccession(encodedEntryValue);
			}
		},
		NAME("name") {
			@Override
			String encodedPart(InstrumentId instrumentId) {
				return instrumentId.name;
			}

			@Override
			InstrumentId.Builder decodeAppendImplementation(InstrumentId.Builder builder, String encodedEntryValue) {
				return builder.setName(encodedEntryValue);
			}
		};

		private final String key;

		Key(String key) {
			this.key=key;
		}

		abstract String encodedPart(InstrumentId instrumentId);

		String encode(InstrumentId instrumentId) {
			return key+":"+encodedPart(instrumentId);
		}

		InstrumentId.Builder decodeAppendGeneral(InstrumentId.Builder builder, String encodedEntries) {
			Pattern compile=Pattern.compile(key+":([^"+ENTRY_DELIM+"]+)");
			Matcher matcher=compile.matcher(encodedEntries);
			if (matcher.find()) {
				decodeAppendImplementation(builder, matcher.group(1));
			}
			return builder;
		}

		abstract InstrumentId.Builder decodeAppendImplementation(InstrumentId.Builder builder, String encodedEntryValue);
	}

	public static String encode(InstrumentId instrumentId) {
		return Joiner.on(ENTRY_DELIM).join(Arrays.stream(Key.values()).map(key -> key.encode(instrumentId)).iterator());
	}

	public static InstrumentId decode(String encoded) {
		InstrumentId.Builder builder=InstrumentId.builder();
		Arrays.stream(Key.values()).forEach(key -> key.decodeAppendGeneral(builder, encoded));
		return builder.build();
	}
}
