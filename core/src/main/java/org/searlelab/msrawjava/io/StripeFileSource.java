package org.searlelab.msrawjava.io;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import org.searlelab.msrawjava.API;

/**
 * Factory for opening fresh {@link StripeFileInterface} readers against one logical source file.
 */
@API(status = API.Status.STABLE, since = "v26.7.31")
public interface StripeFileSource {
	@API(status = API.Status.STABLE, since = "v26.7.31")
	StripeFileInterface openReader() throws IOException, SQLException;

	@API(status = API.Status.STABLE, since = "v26.7.31")
	File getReferenceFile();

	@API(status = API.Status.STABLE, since = "v26.7.31")
	String getOriginalFileName();
}
