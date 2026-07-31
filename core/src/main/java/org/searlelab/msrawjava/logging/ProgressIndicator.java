package org.searlelab.msrawjava.logging;

import org.searlelab.msrawjava.API;

@API(status = API.Status.STABLE, since = "v26.7.31")
public interface ProgressIndicator {
	@API(status = API.Status.STABLE, since = "v26.7.31")
	public void update(String message);

	@API(status = API.Status.STABLE, since = "v26.7.31")
	public void update(String message, float totalProgress);

	@API(status = API.Status.STABLE, since = "v26.7.31")
	public float getTotalProgress();

	@API(status = API.Status.STABLE, since = "v26.7.31")
	public boolean isCanceled();
}
