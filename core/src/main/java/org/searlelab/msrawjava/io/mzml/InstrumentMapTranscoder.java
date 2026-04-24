package org.searlelab.msrawjava.io.mzml;

import java.util.List;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMultimap;

public final class InstrumentMapTranscoder {
	private static final String MULTI_INSTRUMENT_DELIMITER="[MULTI-INSTRUMENT-DELIMITER]";
	private static final String INSTRUMENT_ID_AND_COMPONENT_DELIMITER="[INSTRUMENT-ID-COMPONENT-DELIMITER]";
	private static final String INSTRUMENT_COMPONENT_DELIMITER=";";

	private InstrumentMapTranscoder() {
	}

	public static String encode(ImmutableMultimap<InstrumentId, InstrumentComponent> instrumentConfigurations) {
		return Joiner.on(MULTI_INSTRUMENT_DELIMITER)
				.join(instrumentConfigurations.asMap().entrySet().stream().map(entry -> InstrumentIdTranscoder.encode(entry.getKey())
						+INSTRUMENT_ID_AND_COMPONENT_DELIMITER
						+Joiner.on(INSTRUMENT_COMPONENT_DELIMITER)
								.join(entry.getValue().stream().map(InstrumentComponentTranscoder::encode).iterator()))
						.iterator());
	}

	public static ImmutableMultimap<InstrumentId, InstrumentComponent> decode(String encoded) {
		ImmutableMultimap.Builder<InstrumentId, InstrumentComponent> builder=ImmutableMultimap.builder();
		Splitter.on(MULTI_INSTRUMENT_DELIMITER).split(encoded).forEach(instrumentConfiguration -> {
			List<String> idAndComponents=Splitter.on(INSTRUMENT_ID_AND_COMPONENT_DELIMITER).splitToList(instrumentConfiguration);
			if (idAndComponents.size()==2) {
				InstrumentId id=InstrumentIdTranscoder.decode(idAndComponents.get(0));
				Splitter.on(INSTRUMENT_COMPONENT_DELIMITER).split(idAndComponents.get(1)).forEach(encodedComponent -> {
					builder.put(id, InstrumentComponentTranscoder.decode(encodedComponent));
				});
			}
		});
		return builder.build();
	}
}
