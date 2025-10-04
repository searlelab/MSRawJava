package org.searlelab.msrawjava.io.encyclopedia;

import java.util.StringTokenizer;

import org.searlelab.msrawjava.Logger;

public class Version implements Comparable<Version> {
	private final int major;
	private final int minor;
	private final int revision;
	private final boolean snapshot;

	public Version(int major, int minor, int revision) {
		this(major, minor, revision, false);
	}
	
	public Version(int major, int minor, int revision, boolean snapshot) {
		this.major=major;
		this.minor=minor;
		this.revision=revision;
		this.snapshot=snapshot;
	}

	public Version(String versionString) {
		if (versionString==null) {
			major=0;
			minor=0;
			revision=0;
			snapshot=true;
		} else {
			String[] vals=versionString.split("-");
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
