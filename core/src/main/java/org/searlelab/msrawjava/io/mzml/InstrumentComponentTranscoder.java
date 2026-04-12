package org.searlelab.msrawjava.io.mzml;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Joiner;

public final class InstrumentComponentTranscoder {
	private static final String ENTRY_DELIM=",";

	private InstrumentComponentTranscoder() {
	}

	private enum Key {
		ORDER("order") {
			@Override
			String encodePart(InstrumentComponent component) {
				return Integer.toString(component.order);
			}

			@Override
			InstrumentComponent.Builder decodeAppendImplementation(InstrumentComponent.Builder builder, String encodedEntryValue) {
				return builder.setOrder(Integer.parseInt(encodedEntryValue));
			}
		},
		CVREF("cvRef") {
			@Override
			String encodePart(InstrumentComponent component) {
				return component.cvRef;
			}

			@Override
			InstrumentComponent.Builder decodeAppendImplementation(InstrumentComponent.Builder builder, String encodedEntryValue) {
				return builder.setCvRef(encodedEntryValue);
			}
		},
		ACCESSION_ID("accessionId") {
			@Override
			String encodePart(InstrumentComponent component) {
				return component.accessionId;
			}

			@Override
			InstrumentComponent.Builder decodeAppendImplementation(InstrumentComponent.Builder builder, String encodedEntryValue) {
				return builder.setAccessionId(encodedEntryValue);
			}
		},
		NAME("name") {
			@Override
			String encodePart(InstrumentComponent component) {
				return component.name;
			}

			@Override
			InstrumentComponent.Builder decodeAppendImplementation(InstrumentComponent.Builder builder, String encodedEntryValue) {
				return builder.setName(encodedEntryValue);
			}
		},
		TYPE("type") {
			@Override
			String encodePart(InstrumentComponent component) {
				return component.type.name;
			}

			@Override
			InstrumentComponent.Builder decodeAppendImplementation(InstrumentComponent.Builder builder, String encodedEntryValue) {
				InstrumentComponent.Type.getTypeByName(encodedEntryValue).ifPresent(builder::setType);
				return builder;
			}
		};

		final String key;

		Key(String key) {
			this.key=key;
		}

		String encode(InstrumentComponent component) {
			return key+":"+encodePart(component);
		}

		abstract String encodePart(InstrumentComponent component);

		InstrumentComponent.Builder decodeAppendGeneral(InstrumentComponent.Builder builder, String encodedEntries) {
			Pattern compile=Pattern.compile(key+":([^"+ENTRY_DELIM+"]+)");
			Matcher matcher=compile.matcher(encodedEntries);
			if (matcher.find()) {
				decodeAppendImplementation(builder, matcher.group(1));
			}
			return builder;
		}

		abstract InstrumentComponent.Builder decodeAppendImplementation(InstrumentComponent.Builder builder, String encodedEntryValue);
	}

	public static String encode(InstrumentComponent component) {
		return Joiner.on(ENTRY_DELIM).join(Arrays.stream(Key.values()).map(key -> key.encode(component)).iterator());
	}

	public static InstrumentComponent decode(String encoded) {
		InstrumentComponent.Builder builder=InstrumentComponent.builder();
		Arrays.stream(Key.values()).forEach(key -> key.decodeAppendGeneral(builder, encoded));
		return builder.build();
	}
}
