package org.searlelab.msrawjava.io;

import org.searlelab.msrawjava.API;

/** Terminal status returned by a conversion writer. */
@API(status = API.Status.STABLE, since = "v26.7.31")
public enum ConversionStatus {
	COMPLETED, CANCELED
}
