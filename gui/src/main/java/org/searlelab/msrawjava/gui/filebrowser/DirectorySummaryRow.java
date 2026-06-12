package org.searlelab.msrawjava.gui.filebrowser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import org.searlelab.msrawjava.gui.utils.PathDisplayNames;
import org.searlelab.msrawjava.io.VendorFile;
import org.searlelab.msrawjava.logging.Logger;

/** Row data for the directory summary. */
final class DirectorySummaryRow implements Comparable<DirectorySummaryRow> {
	final Path path;
	final String fileName;
	final String fileNameLower;
	final VendorFile vendor;
	final long sizeBytes;
	final Date lastModified;

	volatile Float gradientMin; // null until computed
	volatile Float totalTIC; // null until computed
	volatile Date acquiredDate; // null until computed or unavailable
	volatile SparkData spark; // null until computed
	private final AtomicBoolean slowBitsReady=new AtomicBoolean(false);

	DirectorySummaryRow(Path p, VendorFile v, long size, Date lastModified) {
		this.path=p;
		this.fileName=PathDisplayNames.displayNameFor(p);
		this.fileNameLower=fileName.toLowerCase(Locale.ROOT);
		this.vendor=v;
		this.sizeBytes=Math.max(0L, size);
		this.lastModified=lastModified;
	}

	@Override
	public int compareTo(DirectorySummaryRow o) {
		if (o==null) return 1;
		int c=String.CASE_INSENSITIVE_ORDER.compare(this.fileName, o.fileName);
		if (c!=0) return c;
		c=this.fileName.compareTo(o.fileName);
		if (c!=0) return c;
		return Long.compare(this.sizeBytes, o.sizeBytes);
	}

	static DirectorySummaryRow fromThermo(Path p) {
		long size=(Files.isRegularFile(p)?p.toFile().length():0L);
		Date modified=null;
		try {
			modified=new Date(Files.getLastModifiedTime(p).toMillis());
		} catch (IOException e) {
			Logger.errorException(e);
		}
		return new DirectorySummaryRow(p, VendorFile.THERMO, size, modified);
	}

	DirectorySummaryMetrics toMetrics() {
		return new DirectorySummaryMetrics(gradientMin, totalTIC, acquiredDate, spark);
	}

	void applyMetrics(DirectorySummaryMetrics bits) {
		if (bits==null) return;
		this.gradientMin=bits.gradientMin;
		this.totalTIC=bits.totalTIC;
		this.acquiredDate=bits.acquiredDate;
		this.spark=bits.spark;
	}

	boolean markSlowBitsReady() {
		return slowBitsReady.compareAndSet(false, true);
	}

	boolean isSlowBitsReady() {
		return slowBitsReady.get();
	}

	static DirectorySummaryRow fromDia(Path p) {
		long size=(Files.isRegularFile(p)?p.toFile().length():0L);
		Date modified=null;
		try {
			modified=new Date(Files.getLastModifiedTime(p).toMillis());
		} catch (IOException e) {
			Logger.errorException(e);
		}
		return new DirectorySummaryRow(p, VendorFile.ENCYCLOPEDIA, size, modified);
	}

	static DirectorySummaryRow fromMzml(Path p) {
		long size=(Files.isRegularFile(p)?p.toFile().length():0L);
		Date modified=null;
		try {
			modified=new Date(Files.getLastModifiedTime(p).toMillis());
		} catch (IOException e) {
			Logger.errorException(e);
		}
		return new DirectorySummaryRow(p, VendorFile.MZML, size, modified);
	}

	static DirectorySummaryRow fromBruker(Path p) {
		long size;
		Date modified=null;
		try {
			modified=new Date(Files.getLastModifiedTime(p).toMillis());
			size=Files.walk(p).filter(Files::isRegularFile).mapToLong(f -> {
				try {
					return Files.size(f);
				} catch (IOException e) {
					Logger.errorLine("Error getting size of file "+f+": "+e.getMessage());
					return 0L;
				}
			}).sum();
		} catch (IOException e) {
			Logger.errorException(e);
			size=0;
		}
		return new DirectorySummaryRow(p, VendorFile.BRUKER, size, modified);
	}
}
