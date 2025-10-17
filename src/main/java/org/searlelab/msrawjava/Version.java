package org.searlelab.msrawjava;

import java.util.StringTokenizer;

/**
 * Version centralizes build and runtime version metadata for the project, exposing a stable string (and related fields)
 * used by the CLI, writers, and logs. 
 */
public class Version implements Comparable<Version> {
	private static final String UNKNOWN_VERSION="0.0.0";
	private final int major;
	private final int minor;
	private final int revision;
	private final boolean snapshot;
	private final boolean addV;

	public static String getVersion() {
		Package p=Version.class.getPackage();
		String version=(p!=null)?p.getImplementationVersion():UNKNOWN_VERSION;
		if (version!=null) {
			return version;
		} else {
			return UNKNOWN_VERSION;
		}
	}

	public Version(int major, int minor, int revision, boolean addV) {
		this(major, minor, revision, false, addV);
	}

	public Version(int major, int minor, int revision, boolean snapshot, boolean addV) {
		this.major=major;
		this.minor=minor;
		this.revision=revision;
		this.snapshot=snapshot;
		this.addV=addV;
	}

	public Version(String versionString) {
		if (versionString==null) {
			major=0;
			minor=0;
			revision=0;
			snapshot=true;
			addV=false;
		} else {
			this.addV=versionString.charAt(0)=='v';
			if (addV) {
				// trim off version indicator for tagged versions
				versionString=versionString.substring(1);
			}
			StringTokenizer st=new StringTokenizer(versionString, ".");
			major=Integer.parseInt(st.nextToken());
			minor=Integer.parseInt(st.nextToken());
			if (st.hasMoreTokens()) {
				String last=st.nextToken();
				snapshot=last.endsWith("-SNAPSHOT");

				if (snapshot) {
					revision=Integer.parseInt(last.substring(0, last.indexOf('-')));
				} else {
					int parsedRevision=0;
					try {
						parsedRevision=Integer.parseInt(last);
					} catch (NumberFormatException nfe) {
						Logger.logLine("Unexpected revision "+last+", assuming -1");
						parsedRevision=-1;
					}
					revision=parsedRevision;
				}
			} else {
				revision=0;
				snapshot=false;
			}
		}
	}

	public String toString() {
		StringBuilder sb=new StringBuilder();
		if (addV&&!snapshot) {
			sb.append("v");
		}
		sb.append(major);
		sb.append(".");
		sb.append(minor);
		sb.append(".");
		sb.append(revision);

		if (snapshot) {
			sb.append("-SNAPSHOT");
		}
		return sb.toString();
	}

	public boolean amIAbove(Version v) {
		return compareTo(v)>0;
	}

	@Override
	public int compareTo(Version o) {
		if (o==null) return 1;
		int c=Integer.compare(major, o.major);
		if (c!=0) return c;
		c=Integer.compare(minor, o.minor);
		if (c!=0) return c;
		c=Integer.compare(revision, o.revision);
		return c;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Version) {
			return compareTo((Version)obj)==0;
		} else {
			return false;
		}
	}

	@Override
	public int hashCode() {
		return major+minor*1000+revision*1000000;
	}
}
