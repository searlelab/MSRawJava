package org.searlelab.msrawjava.io.mzml;

import static org.searlelab.msrawjava.io.mzml.MzmlConstants.*;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.DataFormatException;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.searlelab.msrawjava.io.mzml.MzmlFile.MzmlScanEntry;
import org.searlelab.msrawjava.io.utils.RawFileStructureTools;
import org.searlelab.msrawjava.logging.Logger;
import org.searlelab.msrawjava.model.AcquiredSpectrum;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.ScanSummary;

class MzmlSpectrumReader {
	private static final int SPECTRUM_CACHE_SIZE=256;
	private static final int SPECTRUM_PREFETCH_COUNT=8;
	private static final int CURSOR_MAX_FORWARD_SKIP=64;
	private static final int SPECTRUM_OFFSET_LOOKBACK_BYTES=2*1024*1024;
	private static final int SPECTRUM_OFFSET_SCAN_BYTES=SPECTRUM_OFFSET_LOOKBACK_BYTES+8192;
	private static final int SPECTRUM_FRAGMENT_MAX_BYTES=4*1024*1024;
	private static final int SPECTRUM_HEADER_SCAN_LIMIT=1024;
	private static final byte[] SPECTRUM_START_TAG="<spectrum".getBytes(StandardCharsets.UTF_8);
	private static final byte[] SPECTRUM_END_TAG="</spectrum>".getBytes(StandardCharsets.UTF_8);

	private final MzmlFile owner;
	private final ArrayList<MzmlScanEntry> index;
	private final HashMap<Integer, MzmlScanEntry> indexBySpectrumIndex;
	private final LinkedHashMap<Integer, AcquiredSpectrum> spectrumCache=new LinkedHashMap<Integer, AcquiredSpectrum>(SPECTRUM_CACHE_SIZE, 0.75f, true) {
		private static final long serialVersionUID=1L;

		@Override
		protected boolean removeEldestEntry(Map.Entry<Integer, AcquiredSpectrum> eldest) {
			return size()>SPECTRUM_CACHE_SIZE;
		}
	};
	private FileInputStream spectrumCursorInput;
	private XMLStreamReader spectrumCursorReader;
	private int spectrumCursorLastIndex=Integer.MIN_VALUE;
	private int spectrumCursorSequentialIndex=0;
	private int lastRequestedSequentialIndex=-1;

	MzmlSpectrumReader(MzmlFile owner, ArrayList<MzmlScanEntry> index, HashMap<Integer, MzmlScanEntry> indexBySpectrumIndex) {
		this.owner=owner;
		this.index=index;
		this.indexBySpectrumIndex=indexBySpectrumIndex;
	}

	void clear() {
		spectrumCache.clear();
		lastRequestedSequentialIndex=-1;
	}

	void close() {
		closeSpectrumCursor();
	}

	ArrayList<PrecursorScan> getPrecursors(float minRT, float maxRT) throws IOException, SQLException, DataFormatException {
		ArrayList<MzmlScanEntry> matching=new ArrayList<>();
		for (MzmlScanEntry entry : index) {
			if (entry.msLevel==1&&entry.scanStartTime>=minRT&&entry.scanStartTime<=maxRT) {
				matching.add(entry);
			}
		}

		ArrayList<PrecursorScan> precursors=new ArrayList<>();
		if (matching.isEmpty()) return precursors;

		try {
			parseSpectraData(matching, precursors, null);
		} catch (XMLStreamException e) {
			throw new IOException("Error parsing mzML spectra: "+e.getMessage(), e);
		}
		Collections.sort(precursors);
		return precursors;
	}

	ArrayList<FragmentScan> getStripes(double targetMz, float minRT, float maxRT, boolean sqrt) throws IOException, SQLException {
		ArrayList<MzmlScanEntry> matching=new ArrayList<>();
		for (MzmlScanEntry entry : index) {
			if (entry.msLevel==2&&entry.scanStartTime>=minRT&&entry.scanStartTime<=maxRT) {
				double isoLower=entry.getIsolationWindowLower();
				double isoUpper=entry.getIsolationWindowUpper();
				if (isoLower<=targetMz&&isoUpper>=targetMz) {
					matching.add(entry);
				}
			}
		}

		ArrayList<FragmentScan> fragments=new ArrayList<>();
		if (matching.isEmpty()) return fragments;

		try {
			parseSpectraData(matching, null, fragments);
		} catch (XMLStreamException e) {
			throw new IOException("Error parsing mzML spectra: "+e.getMessage(), e);
		}
		if (sqrt) {
			for (FragmentScan fs : fragments) {
				float[] intensities=fs.getIntensityArray();
				for (int i=0; i<intensities.length; i++) {
					intensities[i]=(float)Math.sqrt(intensities[i]);
				}
			}
		}
		Collections.sort(fragments);
		return fragments;
	}

	ArrayList<FragmentScan> getStripes(Range targetMzRange, float minRT, float maxRT, boolean sqrt) throws IOException, SQLException {
		ArrayList<MzmlScanEntry> matching=new ArrayList<>();
		for (MzmlScanEntry entry : index) {
			if (entry.msLevel==2&&entry.scanStartTime>=minRT&&entry.scanStartTime<=maxRT) {
				double isoLower=entry.getIsolationWindowLower();
				double isoUpper=entry.getIsolationWindowUpper();
				if (isoLower<=targetMzRange.getStop()&&isoUpper>=targetMzRange.getStart()) {
					matching.add(entry);
				}
			}
		}

		ArrayList<FragmentScan> fragments=new ArrayList<>();
		if (matching.isEmpty()) return fragments;

		try {
			parseSpectraData(matching, null, fragments);
		} catch (XMLStreamException e) {
			throw new IOException("Error parsing mzML spectra: "+e.getMessage(), e);
		}
		if (sqrt) {
			for (FragmentScan fs : fragments) {
				float[] intensities=fs.getIntensityArray();
				for (int i=0; i<intensities.length; i++) {
					intensities[i]=(float)Math.sqrt(intensities[i]);
				}
			}
		}
		Collections.sort(fragments);
		return fragments;
	}

	ArrayList<ScanSummary> getScanSummaries(float minRT, float maxRT) {
		ArrayList<ScanSummary> out=new ArrayList<>();
		for (MzmlScanEntry entry : index) {
			if (entry.scanStartTime>=minRT&&entry.scanStartTime<=maxRT) {
				boolean isPrecursor=(entry.msLevel==1);
				double precursorMz=isPrecursor?-1.0:entry.getPrecursorMzOrIsolationTarget();
				double isoLower=entry.getIsolationWindowLower();
				double isoUpper=entry.getIsolationWindowUpper();
				if (!isPrecursor&&owner.getPrecursorMarginSize()>0.0) {
					Range trimmed=RawFileStructureTools.trimRange(new Range(isoLower, isoUpper), owner.getPrecursorMarginSize());
					isoLower=trimmed.getStart();
					isoUpper=trimmed.getStop();
				}
				double scanLower=entry.scanWindowLower;
				double scanUpper=entry.scanWindowUpper;
				if (scanLower==0&&scanUpper==0) {
					scanLower=isoLower;
					scanUpper=isoUpper;
				}
				out.add(new ScanSummary(entry.spectrumId, entry.index, entry.scanStartTime, 0, entry.tic, precursorMz, isPrecursor, entry.ionInjectionTime,
						isoLower, isoUpper, scanLower, scanUpper, entry.charge));
			}
		}
		out.sort((a, b) -> Float.compare(a.getScanStartTime(), b.getScanStartTime()));
		return out;
	}

	AcquiredSpectrum getSpectrum(ScanSummary summary) throws IOException, SQLException, DataFormatException {
		if (summary==null) return null;
		int targetIndex=summary.getSpectrumIndex();
		synchronized (this) {
			AcquiredSpectrum cached=spectrumCache.get(targetIndex);
			if (cached!=null) {
				MzmlScanEntry cachedEntry=indexBySpectrumIndex.get(targetIndex);
				if (cachedEntry!=null) {
					lastRequestedSequentialIndex=cachedEntry.sequentialIndex;
				}
				return cached;
			}
			MzmlScanEntry entry=indexBySpectrumIndex.get(targetIndex);
			if (entry==null) return null;
			try {
				if (shouldUseCursor(targetIndex)) {
					AcquiredSpectrum viaCursor=readSpectrumWithCursor(targetIndex);
					if (viaCursor!=null) {
						lastRequestedSequentialIndex=entry.sequentialIndex;
						return viaCursor;
					}
				}
				AcquiredSpectrum viaOffset=readSpectrumWithOffsetPrefetch(entry, computeOffsetPrefetchCount(entry));
				if (viaOffset!=null) {
					lastRequestedSequentialIndex=entry.sequentialIndex;
					return viaOffset;
				}
				AcquiredSpectrum fallback=readSpectrumWithCursor(targetIndex);
				if (fallback!=null) {
					lastRequestedSequentialIndex=entry.sequentialIndex;
				}
				return fallback;
			} catch (XMLStreamException e) {
				throw new IOException("Error parsing mzML spectrum: "+e.getMessage(), e);
			}
		}
	}


	private boolean shouldUseCursor(int targetIndex) {
		if (spectrumCursorReader==null) return false;
		if (targetIndex<=spectrumCursorLastIndex) return false;
		return (targetIndex-spectrumCursorLastIndex)<=CURSOR_MAX_FORWARD_SKIP;
	}

	private int computeOffsetPrefetchCount(MzmlScanEntry entry) {
		if (lastRequestedSequentialIndex>=0&&entry.sequentialIndex==lastRequestedSequentialIndex+1) {
			return SPECTRUM_PREFETCH_COUNT;
		}
		return 0;
	}

	private AcquiredSpectrum readSpectrumWithOffsetPrefetch(MzmlScanEntry targetEntry, int prefetchCount) throws IOException, XMLStreamException {
		if (targetEntry.spectrumOffsetHint<0) return null;
		int startPos=targetEntry.sequentialIndex;
		int stopExclusive=Math.min(index.size(), startPos+prefetchCount+1);
		for (int i=startPos; i<stopExclusive; i++) {
			MzmlScanEntry entry=index.get(i);
			if (spectrumCache.containsKey(entry.index)) continue;
			AcquiredSpectrum decoded=decodeSpectrumFromOffset(entry);
			if (decoded!=null) {
				spectrumCache.put(entry.index, decoded);
			}
		}
		return spectrumCache.get(targetEntry.index);
	}

	private AcquiredSpectrum decodeSpectrumFromOffset(MzmlScanEntry entry) throws IOException, XMLStreamException {
		byte[] fragment=readSpectrumFragment(entry);
		if (fragment==null||fragment.length==0) return null;
		String wrapped="<mzML><run><spectrumList count=\"1\">"+new String(fragment, StandardCharsets.UTF_8)+"</spectrumList></run></mzML>";

		XMLInputFactory factory=XMLInputFactory.newInstance();
		factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
		factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);

		ArrayList<PrecursorScan> precursors=new ArrayList<>(1);
		ArrayList<FragmentScan> fragments=new ArrayList<>(1);
		XMLStreamReader reader=factory.createXMLStreamReader(new StringReader(wrapped));
		try {
			while (reader.hasNext()) {
				int event=reader.next();
				if (event==XMLStreamConstants.START_ELEMENT&&"spectrum".equals(reader.getLocalName())) {
					parseSpectrumBinaryData(reader, entry, precursors, fragments);
					break;
				}
			}
		} finally {
			reader.close();
		}
		if (!precursors.isEmpty()) return precursors.get(0);
		if (!fragments.isEmpty()) return fragments.get(0);
		return null;
	}

	private byte[] readSpectrumFragment(MzmlScanEntry entry) throws IOException {
		long locationHint=entry.spectrumOffsetHint;
		if (locationHint<0) return null;
		long fileLength=owner.getFile().length();
		long scanStart=Math.max(0L, locationHint-SPECTRUM_OFFSET_LOOKBACK_BYTES);
		int scanBytes=(int)Math.min(SPECTRUM_OFFSET_SCAN_BYTES, Math.max(0L, fileLength-scanStart));
		if (scanBytes<=0) return null;

		try (RandomAccessFile raf=new RandomAccessFile(owner.getFile(), "r")) {
			byte[] searchBuffer=new byte[scanBytes];
			raf.seek(scanStart);
			raf.readFully(searchBuffer);
			int hintInBuffer=(int)Math.min(searchBuffer.length, Math.max(0L, locationHint-scanStart));
			int searchLimit=Math.min(searchBuffer.length, hintInBuffer+4096);
			int scanPos=searchLimit;
			while (true) {
				int relativeStart=findLastPatternBefore(searchBuffer, SPECTRUM_START_TAG, scanPos);
				if (relativeStart<0) break;
				long absoluteStart=scanStart+relativeStart;
				byte[] fragment=readSpectrumFragmentFromOffset(raf, absoluteStart);
				if (fragment!=null&&matchesFragmentHeader(fragment, entry)) {
					return fragment;
				}
				scanPos=relativeStart;
			}

			int forwardStart=indexOf(searchBuffer, SPECTRUM_START_TAG, searchLimit);
			if (forwardStart<0) return null;
			byte[] fragment=readSpectrumFragmentFromOffset(raf, scanStart+forwardStart);
			if (fragment!=null&&matchesFragmentHeader(fragment, entry)) {
				return fragment;
			}
			return null;
		}
	}

	private byte[] readSpectrumFragmentFromOffset(RandomAccessFile raf, long absoluteStart) throws IOException {
		raf.seek(absoluteStart);
		ByteArrayOutputStream out=new ByteArrayOutputStream(64*1024);
		byte[] chunk=new byte[8192];
		int searchFrom=0;
		while (true) {
			int read=raf.read(chunk);
			if (read<0) break;
			out.write(chunk, 0, read);
			if (out.size()>SPECTRUM_FRAGMENT_MAX_BYTES) {
				throw new IOException("mzML spectrum fragment exceeded "+SPECTRUM_FRAGMENT_MAX_BYTES+" bytes");
			}
			byte[] candidate=out.toByteArray();
			int end=indexOf(candidate, SPECTRUM_END_TAG, Math.max(0, searchFrom-SPECTRUM_END_TAG.length));
			if (end>=0) {
				return Arrays.copyOf(candidate, end+SPECTRUM_END_TAG.length);
			}
			searchFrom=candidate.length;
		}
		return null;
	}

	private boolean matchesFragmentHeader(byte[] fragment, MzmlScanEntry entry) {
		int headerEnd=indexOf(fragment, new byte[] {(byte)'>'}, 0);
		if (headerEnd<0) return false;
		int headerLength=Math.min(headerEnd+1, SPECTRUM_HEADER_SCAN_LIMIT);
		String header=new String(fragment, 0, headerLength, StandardCharsets.UTF_8);
		String indexNeedle="index=\""+entry.index+"\"";
		if (header.contains(indexNeedle)) return true;
		return entry.spectrumId!=null&&header.contains("id=\""+entry.spectrumId+"\"");
	}

	private static int findLastPatternBefore(byte[] data, byte[] pattern, int endExclusive) {
		int max=Math.min(endExclusive, data.length)-pattern.length;
		for (int i=max; i>=0; i--) {
			if (startsWith(data, pattern, i)) return i;
		}
		return -1;
	}

	private static int indexOf(byte[] data, byte[] pattern, int fromIndex) {
		int start=Math.max(0, fromIndex);
		int max=data.length-pattern.length;
		for (int i=start; i<=max; i++) {
			if (startsWith(data, pattern, i)) return i;
		}
		return -1;
	}

	private static boolean startsWith(byte[] data, byte[] pattern, int offset) {
		for (int i=0; i<pattern.length; i++) {
			if (data[offset+i]!=pattern[i]) return false;
		}
		return true;
	}

	private AcquiredSpectrum readSpectrumWithCursor(int targetIndex) throws IOException, XMLStreamException {
		ensureSpectrumCursor(targetIndex);
		AcquiredSpectrum found=null;
		int prefetchRemaining=0;

		while (spectrumCursorReader.hasNext()) {
			int event=spectrumCursorReader.next();
			if (event!=XMLStreamConstants.START_ELEMENT||!"spectrum".equals(spectrumCursorReader.getLocalName())) {
				continue;
			}
			int currentIndex=readSpectrumIndexFromCursor(spectrumCursorReader);
			MzmlScanEntry entry=indexBySpectrumIndex.get(currentIndex);
			if (entry==null) {
				skipElement(spectrumCursorReader);
				spectrumCursorLastIndex=Math.max(spectrumCursorLastIndex, currentIndex);
				continue;
			}
			if (found==null&&currentIndex<targetIndex) {
				skipElement(spectrumCursorReader);
				spectrumCursorLastIndex=Math.max(spectrumCursorLastIndex, currentIndex);
				continue;
			}

			AcquiredSpectrum decoded=decodeCursorSpectrum(entry);
			if (decoded!=null) {
				spectrumCache.put(currentIndex, decoded);
			}
			spectrumCursorLastIndex=Math.max(spectrumCursorLastIndex, currentIndex);

			if (currentIndex==targetIndex) {
				found=decoded;
				prefetchRemaining=SPECTRUM_PREFETCH_COUNT;
				if (prefetchRemaining==0) break;
			} else if (found!=null) {
				prefetchRemaining--;
				if (prefetchRemaining<=0) break;
			}
		}

		return found;
	}

	private AcquiredSpectrum decodeCursorSpectrum(MzmlScanEntry entry) throws IOException, XMLStreamException {
		ArrayList<PrecursorScan> precursors=new ArrayList<>(1);
		ArrayList<FragmentScan> fragments=new ArrayList<>(1);
		parseSpectrumBinaryData(spectrumCursorReader, entry, precursors, fragments);
		if (!precursors.isEmpty()) return precursors.get(0);
		if (!fragments.isEmpty()) return fragments.get(0);
		return null;
	}

	private int readSpectrumIndexFromCursor(XMLStreamReader reader) {
		int fallback=spectrumCursorSequentialIndex++;
		String indexAttr=reader.getAttributeValue(null, "index");
		if (indexAttr!=null) {
			return Integer.parseInt(indexAttr);
		}
		return fallback;
	}

	private void ensureSpectrumCursor(int targetIndex) throws IOException, XMLStreamException {
		if (spectrumCursorReader==null||targetIndex<=spectrumCursorLastIndex) {
			resetSpectrumCursor();
		}
	}

	private void resetSpectrumCursor() throws IOException, XMLStreamException {
		closeSpectrumCursor();
		XMLInputFactory factory=XMLInputFactory.newInstance();
		factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
		factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
		spectrumCursorInput=new FileInputStream(owner.getFile());
		spectrumCursorReader=factory.createXMLStreamReader(spectrumCursorInput);
		spectrumCursorLastIndex=Integer.MIN_VALUE;
		spectrumCursorSequentialIndex=0;
	}

	private static void skipElement(XMLStreamReader reader) throws XMLStreamException {
		int depth=1;
		while (reader.hasNext()&&depth>0) {
			int event=reader.next();
			if (event==XMLStreamConstants.START_ELEMENT) {
				depth++;
			} else if (event==XMLStreamConstants.END_ELEMENT) {
				depth--;
			}
		}
	}

	private void closeSpectrumCursor() {
		if (spectrumCursorReader!=null) {
			try {
				spectrumCursorReader.close();
			} catch (Exception e) {
				Logger.errorException(e);
			}
			spectrumCursorReader=null;
		}
		if (spectrumCursorInput!=null) {
			try {
				spectrumCursorInput.close();
			} catch (Exception e) {
				Logger.errorException(e);
			}
			spectrumCursorInput=null;
		}
		spectrumCursorLastIndex=Integer.MIN_VALUE;
		spectrumCursorSequentialIndex=0;
	}

	// ---- Data pass: re-parse the file and decode binary arrays for matching spectra ----

	/**
	 * Re-reads the mzML file, decoding binary arrays only for spectra whose index matches entries in the matching list.
	 * Populates precursors and/or fragments lists.
	 */
	void parseSpectraData(ArrayList<MzmlScanEntry> matching, ArrayList<PrecursorScan> precursors, ArrayList<FragmentScan> fragments)
			throws IOException, XMLStreamException {
		// Build a quick lookup of target indices
		java.util.HashSet<Integer> targetIndices=new java.util.HashSet<>();
		java.util.HashMap<Integer, MzmlScanEntry> entryMap=new java.util.HashMap<>();
		for (MzmlScanEntry entry : matching) {
			targetIndices.add(entry.index);
			entryMap.put(entry.index, entry);
		}

		XMLInputFactory factory=XMLInputFactory.newInstance();
		factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
		factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);

		try (FileInputStream fis=new FileInputStream(owner.getFile())) {
			XMLStreamReader reader=factory.createXMLStreamReader(fis);
			try {
				while (reader.hasNext()) {
					int event=reader.next();
					if (event==XMLStreamConstants.START_ELEMENT&&"spectrum".equals(reader.getLocalName())) {
						String indexAttr=reader.getAttributeValue(null, "index");
						int specIndex=(indexAttr!=null)?Integer.parseInt(indexAttr):-1;

						if (targetIndices.contains(specIndex)) {
							MzmlScanEntry entry=entryMap.get(specIndex);
							parseSpectrumBinaryData(reader, entry, precursors, fragments);
							targetIndices.remove(specIndex);
							if (targetIndices.isEmpty()) break;
						}
					}
				}
			} finally {
				reader.close();
			}
		}
	}

	/**
	 * Parses a single spectrum element, decoding its binary data arrays into PrecursorScan or FragmentScan.
	 */
	private void parseSpectrumBinaryData(XMLStreamReader reader, MzmlScanEntry entry, ArrayList<PrecursorScan> precursors, ArrayList<FragmentScan> fragments)
			throws XMLStreamException, IOException {
		double[] mzArray=null;
		float[] intensityArray=null;

		int depth=1;
		boolean inBinaryDataArray=false;
		boolean is64bit=false;
		boolean is32bit=false;
		boolean isZlib=false;
		boolean isMzArray=false;
		boolean isIntensityArray=false;

		while (reader.hasNext()&&depth>0) {
			int event=reader.next();
			if (event==XMLStreamConstants.START_ELEMENT) {
				depth++;
				String localName=reader.getLocalName();
				if ("binaryDataArray".equals(localName)) {
					inBinaryDataArray=true;
					is64bit=false;
					is32bit=false;
					isZlib=false;
					isMzArray=false;
					isIntensityArray=false;
				} else if ("cvParam".equals(localName)&&inBinaryDataArray) {
					String accession=reader.getAttributeValue(null, "accession");
					if (accession!=null) {
						switch (accession) {
							case CV_64_BIT_FLOAT:
								is64bit=true;
								break;
							case CV_32_BIT_FLOAT:
								is32bit=true;
								break;
							case CV_ZLIB_COMPRESSION:
								isZlib=true;
								break;
							case CV_NO_COMPRESSION:
								isZlib=false;
								break;
							case CV_MZ_ARRAY:
								isMzArray=true;
								break;
							case CV_INTENSITY_ARRAY:
								isIntensityArray=true;
								break;
						}
					}
				} else if ("binary".equals(localName)&&inBinaryDataArray) {
					String base64Text=reader.getElementText();
					depth--; // getElementText() consumes the end element
					if (base64Text!=null&&!base64Text.isBlank()) {
						byte[] decoded=MzmlBinaryUtils.decodeBase64(base64Text);
						if (isZlib) {
							decoded=MzmlBinaryUtils.zlibDecompress(decoded);
						}
						if (isMzArray) {
							if (is64bit) {
								mzArray=MzmlBinaryUtils.bytesToDoubles(decoded);
							} else if (is32bit) {
								mzArray=MzmlBinaryUtils.floatBytesToDoubles(decoded);
							}
						} else if (isIntensityArray) {
							if (is32bit) {
								intensityArray=MzmlBinaryUtils.bytesToFloats(decoded);
							} else if (is64bit) {
								intensityArray=MzmlBinaryUtils.doubleBytesToFloats(decoded);
							}
						}
					}
				}
			} else if (event==XMLStreamConstants.END_ELEMENT) {
				depth--;
				if ("binaryDataArray".equals(reader.getLocalName())) {
					inBinaryDataArray=false;
				}
			}
		}

		if (mzArray==null) mzArray=new double[0];
		if (intensityArray==null) intensityArray=new float[0];

		if (entry.msLevel==1) {
			if (precursors!=null) {
				double scanLower=entry.scanWindowLower;
				double scanUpper=entry.scanWindowUpper;
				if (scanLower==0&&scanUpper==0&&mzArray.length>0) {
					scanLower=mzArray[0];
					scanUpper=mzArray[mzArray.length-1];
				}
				precursors.add(new PrecursorScan(entry.spectrumId, entry.index, entry.scanStartTime, 0, scanLower, scanUpper, entry.ionInjectionTime, mzArray,
						intensityArray, null));
			}
		} else {
			if (fragments!=null) {
				double isoLower=entry.getIsolationWindowLower();
				double isoUpper=entry.getIsolationWindowUpper();
				double isoTarget=entry.getIsolationWindowTarget();
				Range trimmed=RawFileStructureTools.trimRange(new Range(isoLower, isoUpper), owner.getPrecursorMarginSize());
				isoLower=trimmed.getStart();
				isoUpper=trimmed.getStop();
				double precMz=entry.getPrecursorMzOrIsolationTarget();
				double scanLower=entry.scanWindowLower;
				double scanUpper=entry.scanWindowUpper;
				if (scanLower==0&&scanUpper==0) {
					scanLower=isoLower;
					scanUpper=isoUpper;
				}
				fragments.add(new FragmentScan(entry.spectrumId, "", entry.index, precMz, entry.scanStartTime, 0, entry.ionInjectionTime, isoLower,
						isoTarget, isoUpper, mzArray, intensityArray, null, entry.charge, scanLower, scanUpper));
			}
		}
	}

}
