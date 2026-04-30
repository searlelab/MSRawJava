package org.searlelab.msrawjava.io;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

/**
 * Factory for opening fresh {@link StripeFileInterface} readers against one logical source file.
 */
public interface StripeFileSource {
	StripeFileInterface openReader() throws IOException, SQLException;

	File getReferenceFile();

	String getOriginalFileName();
}
