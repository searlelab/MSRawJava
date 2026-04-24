package org.searlelab.msrawjava.io;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Date;
import java.util.Optional;

import org.searlelab.msrawjava.io.mzml.InstrumentComponent;
import org.searlelab.msrawjava.io.mzml.InstrumentId;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

/**
 * Optional reader capability for exposing structured run metadata that is useful when preserving EncyclopeDIA .dia
 * metadata beyond the flat string map in {@link StripeFileInterface#getMetadata()}.
 */
public interface StructuredMetadataProvider {
	Optional<Date> getRunStartTime() throws IOException, SQLException;

	Multimap<String, String> getSoftwareAccessionIdToVersion() throws IOException, SQLException;

	ImmutableMultimap<InstrumentId, InstrumentComponent> getInstrumentConfigurations() throws IOException, SQLException;
}
