package org.searlelab.msrawjava.io.mzml;

import static org.searlelab.msrawjava.io.mzml.MzmlConstants.CV_CHARGE_STATE;
import static org.searlelab.msrawjava.io.mzml.MzmlConstants.CV_ION_INJECTION_TIME;
import static org.searlelab.msrawjava.io.mzml.MzmlConstants.CV_ISOLATION_WINDOW_LOWER_OFFSET;
import static org.searlelab.msrawjava.io.mzml.MzmlConstants.CV_ISOLATION_WINDOW_TARGET_MZ;
import static org.searlelab.msrawjava.io.mzml.MzmlConstants.CV_ISOLATION_WINDOW_UPPER_OFFSET;
import static org.searlelab.msrawjava.io.mzml.MzmlConstants.CV_MS_LEVEL;
import static org.searlelab.msrawjava.io.mzml.MzmlConstants.CV_SCAN_START_TIME;
import static org.searlelab.msrawjava.io.mzml.MzmlConstants.CV_SCAN_WINDOW_LOWER_LIMIT;
import static org.searlelab.msrawjava.io.mzml.MzmlConstants.CV_SCAN_WINDOW_UPPER_LIMIT;
import static org.searlelab.msrawjava.io.mzml.MzmlConstants.CV_SELECTED_ION_MZ;
import static org.searlelab.msrawjava.io.mzml.MzmlConstants.CV_TOTAL_ION_CURRENT;
import static org.searlelab.msrawjava.io.mzml.MzmlConstants.UO_MINUTE;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.DataFormatException;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.searlelab.msrawjava.io.StripeFileInterface;
import org.searlelab.msrawjava.io.StructuredMetadataProvider;
import org.searlelab.msrawjava.io.utils.DataAcquisitionType;
import org.searlelab.msrawjava.io.utils.Pair;
import org.searlelab.msrawjava.io.utils.RawFileStructureTools;
import org.searlelab.msrawjava.model.AcquiredSpectrum;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.ScanSummary;
import org.searlelab.msrawjava.model.WindowData;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

/**
 * MzmlFile reads mzML files (PSI standard XML format for mass spectrometry data) and implements StripeFileInterface
 * so that mzML can be treated as a first-class input format alongside Thermo .raw, Bruker .d, and EncyclopeDIA .dia.
 *
 * Uses a two-pass architecture:
 * - Index pass (openFile): streams through the mzML extracting per-spectrum metadata into an in-memory index.
 * - Data pass (on demand): re-reads the file decoding binary arrays only for spectra matching the query.
 */
public class MzmlFile implements StripeFileInterface, StructuredMetadataProvider {
	private static final String METADATA_USERPARAM_PREFIX="msrawjava.metadata.";
	private File userFile;
	private boolean open=false;

	// Index built during openFile
	private final ArrayList<MzmlScanEntry> index=new ArrayList<>();
	private final HashMap<Range, WindowData> ranges=new HashMap<>();
	private final HashMap<String, String> metadata=new HashMap<>();

	// TIC data harvested during index pass (MS1 only)
	private final ArrayList<Float> ms1Rts=new ArrayList<>();
	private final ArrayList<Float> ms1Tics=new ArrayList<>();
	private final HashMap<Integer, MzmlScanEntry> indexBySpectrumIndex=new HashMap<>();
	private final MzmlSpectrumReader spectrumReader=new MzmlSpectrumReader(this, index, indexBySpectrumIndex);
	private DataAcquisitionType dataAcquisitionType=DataAcquisitionType.DDA;
	private boolean staggered=false;
	private double precursorMarginSize=0.0;
	private Optional<Date> runStartTime=Optional.empty();
	private Multimap<String, String> softwareAccessionIdToVersion=ImmutableMultimap.of();
	private ImmutableMultimap<InstrumentId, InstrumentComponent> instrumentConfigurations=ImmutableMultimap.of();

	@FunctionalInterface
	public interface SpectrumConsumer {
		void accept(PrecursorScan precursor, FragmentScan fragment) throws Exception;
	}

	@Override
	public void openFile(File userFile) throws IOException, SQLException {
		this.userFile=userFile;
		spectrumReader.close();
		index.clear();
		ranges.clear();
		metadata.clear();
		ms1Rts.clear();
		ms1Tics.clear();
		indexBySpectrumIndex.clear();
		spectrumReader.clear();
		dataAcquisitionType=DataAcquisitionType.DDA;
		staggered=false;
		precursorMarginSize=0.0;
		runStartTime=Optional.empty();
		softwareAccessionIdToVersion=ImmutableMultimap.of();
		instrumentConfigurations=ImmutableMultimap.of();

		try {
			buildIndex();
		} catch (XMLStreamException e) {
			throw new IOException("Error parsing mzML: "+e.getMessage(), e);
		}
		computeRanges();
		determineStructure();
		open=true;
	}

	/**
	 * Index pass: stream through the mzML extracting per-spectrum metadata without decoding binary arrays.
	 */
	private void buildIndex() throws IOException, XMLStreamException {
		XMLInputFactory factory=XMLInputFactory.newInstance();
		factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
		factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
		ArrayDeque<String> tagStack=new ArrayDeque<>();
		ImmutableMultimap.Builder<String, String> softwareBuilder=ImmutableMultimap.builder();
		ImmutableMultimap.Builder<InstrumentId, InstrumentComponent> instrumentBuilder=ImmutableMultimap.builder();
		String currentSoftwareVersion=null;
		InstrumentId.Builder currentInstrumentIdBuilder=null;
		ArrayList<InstrumentComponent> currentInstrumentComponents=new ArrayList<>();
		InstrumentComponent.Builder currentInstrumentComponentBuilder=null;

		try (FileInputStream fis=new FileInputStream(userFile)) {
			XMLStreamReader reader=factory.createXMLStreamReader(fis);
			try {
				int spectrumIdx=0;
				long offsetWrapBase=0L;
				long previousUnsignedOffset=0L;
				boolean hasPreviousOffset=false;
				while (reader.hasNext()) {
					int event=reader.next();
					if (event==XMLStreamConstants.START_ELEMENT) {
						String localName=reader.getLocalName();
						if ("spectrum".equals(localName)) {
							long unsignedOffset=Integer.toUnsignedLong(reader.getLocation().getCharacterOffset());
							if (hasPreviousOffset&&unsignedOffset<previousUnsignedOffset) {
								offsetWrapBase+=(1L<<32);
							}
							long absoluteOffset=offsetWrapBase+unsignedOffset;
							previousUnsignedOffset=unsignedOffset;
							hasPreviousOffset=true;
							MzmlScanEntry entry=parseSpectrumMetadata(reader, spectrumIdx, absoluteOffset);
							index.add(entry);
							spectrumIdx++;
						} else if ("software".equals(localName)) {
							currentSoftwareVersion=reader.getAttributeValue(null, "version");
							String id=reader.getAttributeValue(null, "id");
							String version=reader.getAttributeValue(null, "version");
							if (id!=null&&version!=null) metadata.put("software."+id, version);
							tagStack.addLast(localName);
						} else if ("userParam".equals(localName)) {
							parseMetadataUserParam(reader);
							tagStack.addLast(localName);
						} else {
							tagStack.addLast(localName);
							if ("run".equals(localName)) {
								String startTimeStamp=reader.getAttributeValue(null, "startTimeStamp");
								if (startTimeStamp!=null) {
									try {
										runStartTime=Optional.of(Date.from(OffsetDateTime.parse(startTimeStamp).toInstant()));
									} catch (DateTimeParseException ignored) {
										runStartTime=Optional.empty();
									}
								}
							} else if ("instrumentConfiguration".equals(localName)) {
								currentInstrumentIdBuilder=InstrumentId.builder().setInstrumentConfigurationId(reader.getAttributeValue(null, "id"));
								currentInstrumentComponents=new ArrayList<>();
							} else if (InstrumentComponent.Type.getTypeByName(localName).isPresent()) {
								currentInstrumentComponentBuilder=InstrumentComponent.builder().setType(InstrumentComponent.Type.getTypeByName(localName).get());
								String order=reader.getAttributeValue(null, "order");
								if (order!=null) currentInstrumentComponentBuilder.setOrder(Integer.parseInt(order));
							} else if ("cvParam".equals(localName)) {
								String parent=parentTag(tagStack);
								String accession=reader.getAttributeValue(null, "accession");
								String name=reader.getAttributeValue(null, "name");
								String cvRef=reader.getAttributeValue(null, "cvRef");
								if ("software".equals(parent)&&currentSoftwareVersion!=null&&accession!=null) {
									softwareBuilder.put(accession, currentSoftwareVersion);
								} else if ("instrumentConfiguration".equals(parent)&&currentInstrumentIdBuilder!=null) {
									currentInstrumentIdBuilder.setAccession(accession).setName(name);
								} else if (InstrumentComponent.Type.getTypeByName(parent).isPresent()&&currentInstrumentComponentBuilder!=null) {
									currentInstrumentComponentBuilder.setCvRef(cvRef).setAccessionId(accession).setName(name);
								}
							}
						}
					} else if (event==XMLStreamConstants.END_ELEMENT) {
						String localName=reader.getLocalName();
						if (InstrumentComponent.Type.getTypeByName(localName).isPresent()) {
							if (currentInstrumentComponentBuilder!=null) {
								currentInstrumentComponents.add(currentInstrumentComponentBuilder.build());
								currentInstrumentComponentBuilder=null;
							}
						} else if ("instrumentConfiguration".equals(localName)) {
							if (currentInstrumentIdBuilder!=null) {
								instrumentBuilder.putAll(currentInstrumentIdBuilder.build(), currentInstrumentComponents);
								currentInstrumentIdBuilder=null;
								currentInstrumentComponents=new ArrayList<>();
							}
						} else if ("software".equals(localName)) {
							currentSoftwareVersion=null;
						}
						if (!tagStack.isEmpty()) {
							tagStack.removeLast();
						}
					}
				}
				softwareAccessionIdToVersion=softwareBuilder.build();
				instrumentConfigurations=instrumentBuilder.build();
			} finally {
				reader.close();
			}
		}
	}

	private static String parentTag(ArrayDeque<String> tagStack) {
		if (tagStack.size()<2) return null;
		String current=tagStack.removeLast();
		String parent=tagStack.peekLast();
		tagStack.addLast(current);
		return parent;
	}

	private void parseMetadataUserParam(XMLStreamReader reader) {
		String name=reader.getAttributeValue(null, "name");
		if (name==null||!name.startsWith(METADATA_USERPARAM_PREFIX)) return;
		String key=name.substring(METADATA_USERPARAM_PREFIX.length());
		if (key.isBlank()) return;
		String value=reader.getAttributeValue(null, "value");
		metadata.put(key, value!=null?value:"");
	}

	/**
	 * Parses a single spectrum element for metadata only.
	 * Skips binary data but captures cvParams for MS level, RT, isolation windows, precursor info, TIC, etc.
	 */
	private MzmlScanEntry parseSpectrumMetadata(XMLStreamReader reader, int seqIndex, long offsetHint) throws XMLStreamException {
		String spectrumId=reader.getAttributeValue(null, "id");
		String indexAttr=reader.getAttributeValue(null, "index");
		int xmlIndex=indexAttr!=null?Integer.parseInt(indexAttr):seqIndex;

		MzmlScanEntry entry=new MzmlScanEntry();
		entry.spectrumId=spectrumId;
		entry.index=xmlIndex;
		entry.sequentialIndex=seqIndex;
		entry.spectrumOffsetHint=offsetHint;

		int depth=1;
		boolean inScan=false;
		boolean inPrecursor=false;
		boolean inIsolationWindow=false;
		boolean inSelectedIon=false;

		while (reader.hasNext()&&depth>0) {
			int event=reader.next();
			if (event==XMLStreamConstants.START_ELEMENT) {
				depth++;
				String localName=reader.getLocalName();
				switch (localName) {
					case "scan":
						inScan=true;
						break;
					case "precursor":
						inPrecursor=true;
						break;
					case "isolationWindow":
						inIsolationWindow=true;
						break;
					case "selectedIon":
						inSelectedIon=true;
						break;
					case "cvParam":
						parseCvParam(reader, entry, inScan, inPrecursor, inIsolationWindow, inSelectedIon);
						break;
					case "scanWindow":
						// scan window cvParams are inside this element
						parseScanWindow(reader, entry);
						depth--; // parseScanWindow consumes the end element
						break;
				}
			} else if (event==XMLStreamConstants.END_ELEMENT) {
				depth--;
				String localName=reader.getLocalName();
				switch (localName) {
					case "scan":
						inScan=false;
						break;
					case "precursor":
						inPrecursor=false;
						break;
					case "isolationWindow":
						inIsolationWindow=false;
						break;
					case "selectedIon":
						inSelectedIon=false;
						break;
				}
			}
		}

		// Capture TIC for MS1 spectra
		if (entry.msLevel==1) {
			ms1Rts.add(entry.scanStartTime);
			ms1Tics.add(entry.tic);
		}
		indexBySpectrumIndex.put(entry.index, entry);

		return entry;
	}

	private void parseCvParam(XMLStreamReader reader, MzmlScanEntry entry, boolean inScan, boolean inPrecursor,
			boolean inIsolationWindow, boolean inSelectedIon) {
		String accession=reader.getAttributeValue(null, "accession");
		String value=reader.getAttributeValue(null, "value");
		if (accession==null) return;

		switch (accession) {
			case CV_MS_LEVEL:
				if (value!=null) entry.msLevel=Integer.parseInt(value);
				break;
			case CV_SCAN_START_TIME:
				if (value!=null&&inScan) {
					float time=Float.parseFloat(value);
					String unitAcc=reader.getAttributeValue(null, "unitAccession");
					if (UO_MINUTE.equals(unitAcc)) {
						time=time*60.0f;
					}
					entry.scanStartTime=time;
				}
				break;
			case CV_ION_INJECTION_TIME:
				if (value!=null&&inScan) {
					entry.ionInjectionTime=Float.parseFloat(value)/1000.0f; // ms -> seconds
				}
				break;
			case CV_TOTAL_ION_CURRENT:
				if (value!=null) entry.tic=Float.parseFloat(value);
				break;
			case CV_ISOLATION_WINDOW_TARGET_MZ:
				if (value!=null&&inIsolationWindow) {
					entry.isolationTarget=Double.parseDouble(value);
					entry.hasIsolationTarget=true;
				}
				break;
			case CV_ISOLATION_WINDOW_LOWER_OFFSET:
				if (value!=null&&inIsolationWindow) {
					entry.isolationLowerOffset=Double.parseDouble(value);
				}
				break;
			case CV_ISOLATION_WINDOW_UPPER_OFFSET:
				if (value!=null&&inIsolationWindow) {
					entry.isolationUpperOffset=Double.parseDouble(value);
				}
				break;
			case CV_SELECTED_ION_MZ:
				if (value!=null&&inSelectedIon) {
					entry.precursorMz=Double.parseDouble(value);
				}
				break;
			case CV_CHARGE_STATE:
				if (value!=null&&inSelectedIon) {
					entry.charge=Byte.parseByte(value);
				}
				break;
		}
	}

	private void parseScanWindow(XMLStreamReader reader, MzmlScanEntry entry) throws XMLStreamException {
		int depth=1;
		while (reader.hasNext()&&depth>0) {
			int event=reader.next();
			if (event==XMLStreamConstants.START_ELEMENT) {
				depth++;
				if ("cvParam".equals(reader.getLocalName())) {
					String accession=reader.getAttributeValue(null, "accession");
					String value=reader.getAttributeValue(null, "value");
					if (accession!=null&&value!=null) {
						if (CV_SCAN_WINDOW_LOWER_LIMIT.equals(accession)) {
							entry.scanWindowLower=Double.parseDouble(value);
						} else if (CV_SCAN_WINDOW_UPPER_LIMIT.equals(accession)) {
							entry.scanWindowUpper=Double.parseDouble(value);
						}
					}
				}
			} else if (event==XMLStreamConstants.END_ELEMENT) {
				depth--;
			}
		}
	}

	/**
	 * Compute DIA ranges from observed MS2 isolation windows.
	 */
	private void computeRanges() {
		// Group MS2 spectra by isolation window Range
		HashMap<Range, ArrayList<MzmlScanEntry>> windowMap=new HashMap<>();
		for (MzmlScanEntry entry : index) {
			if (entry.msLevel!=2) continue;
			Range range=entry.getIsolationRange();
			if (range==null) continue;

			// Find existing range with tolerance match
			Range matchedKey=null;
			for (Range existing : windowMap.keySet()) {
				if (existing.equals(range)) {
					matchedKey=existing;
					break;
				}
			}
			if (matchedKey==null) {
				matchedKey=range;
				windowMap.put(matchedKey, new ArrayList<>());
			}
			windowMap.get(matchedKey).add(entry);
		}

		// Compute WindowData for each range
		for (Map.Entry<Range, ArrayList<MzmlScanEntry>> e : windowMap.entrySet()) {
			ArrayList<MzmlScanEntry> entries=e.getValue();
			int count=entries.size();
			float minRt=Float.MAX_VALUE, maxRt=-Float.MAX_VALUE;
			for (MzmlScanEntry s : entries) {
				if (s.scanStartTime<minRt) minRt=s.scanStartTime;
				if (s.scanStartTime>maxRt) maxRt=s.scanStartTime;
			}

			// Estimate average duty cycle from time between consecutive scans in this window
			float avgDutyCycle=0f;
			if (count>1) {
				entries.sort((a, b) -> Float.compare(a.scanStartTime, b.scanStartTime));
				float totalDelta=0f;
				for (int i=1; i<count; i++) {
					totalDelta+=entries.get(i).scanStartTime-entries.get(i-1).scanStartTime;
				}
				avgDutyCycle=totalDelta/(count-1);
			}

			Optional<Range> rtRange=(count>0)?Optional.of(new Range(minRt, maxRt)):Optional.empty();
			ranges.put(e.getKey(), new WindowData(avgDutyCycle, count, Optional.empty(), rtRange));
		}
	}

	@Override
	public Map<Range, WindowData> getRanges() {
		return RawFileStructureTools.trimRanges(ranges, precursorMarginSize);
	}

	@Override
	public Map<String, String> getMetadata() {
		LinkedHashMap<String, String> out=new LinkedHashMap<>(metadata);
		out.putAll(RawFileStructureTools.structureMetadata(dataAcquisitionType, staggered, precursorMarginSize));
		return out;
	}

	@Override
	public double getPrecursorMarginSize() {
		return precursorMarginSize;
	}

	@Override
	public void setPrecursorMarginSize(double precursorMarginSize) {
		this.precursorMarginSize=Math.max(0.0, precursorMarginSize);
		spectrumReader.clear();
	}

	@Override
	public ArrayList<PrecursorScan> getPrecursors(float minRT, float maxRT) throws IOException, SQLException, DataFormatException {
		return spectrumReader.getPrecursors(minRT, maxRT);
	}

	@Override
	public ArrayList<FragmentScan> getStripes(double targetMz, float minRT, float maxRT, boolean sqrt) throws IOException, SQLException {
		return spectrumReader.getStripes(targetMz, minRT, maxRT, sqrt);
	}

	@Override
	public ArrayList<FragmentScan> getStripes(Range targetMzRange, float minRT, float maxRT, boolean sqrt) throws IOException, SQLException {
		return spectrumReader.getStripes(targetMzRange, minRT, maxRT, sqrt);
	}

	@Override
	public ArrayList<ScanSummary> getScanSummaries(float minRT, float maxRT) {
		return spectrumReader.getScanSummaries(minRT, maxRT);
	}

	@Override
	public AcquiredSpectrum getSpectrum(ScanSummary summary) throws IOException, SQLException, DataFormatException {
		return spectrumReader.getSpectrum(summary);
	}

	@Override
	public float getTIC() {
		float total=0f;
		for (Float tic : ms1Tics) {
			total+=tic;
		}
		return total;
	}

	@Override
	public Pair<float[], float[]> getTICTrace() {
		float[] rts=new float[ms1Rts.size()];
		float[] tics=new float[ms1Tics.size()];
		for (int i=0; i<ms1Rts.size(); i++) {
			rts[i]=ms1Rts.get(i);
			tics[i]=ms1Tics.get(i);
		}
		return new Pair<>(rts, tics);
	}

	@Override
	public float getGradientLength() {
		if (index.isEmpty()) return 0f;
		float min=Float.MAX_VALUE, max=-Float.MAX_VALUE;
		for (MzmlScanEntry entry : index) {
			if (entry.scanStartTime<min) min=entry.scanStartTime;
			if (entry.scanStartTime>max) max=entry.scanStartTime;
		}
		return max-min;
	}

	@Override
	public boolean isOpen() {
		return open;
	}

	@Override
	public File getFile() {
		return userFile;
	}

	@Override
	public String getOriginalFileName() {
		return userFile!=null?userFile.getName():null;
	}

	@Override
	public void close() {
		spectrumReader.close();
		open=false;
	}

	@Override
	public Optional<Date> getRunStartTime() {
		return runStartTime;
	}

	@Override
	public Multimap<String, String> getSoftwareAccessionIdToVersion() {
		return softwareAccessionIdToVersion;
	}

	@Override
	public ImmutableMultimap<InstrumentId, InstrumentComponent> getInstrumentConfigurations() {
		return instrumentConfigurations;
	}

	/**
	 * Streams all spectra from mzML in a single pass and emits decoded scans via the supplied consumer.
	 * Intended for high-throughput conversion paths to avoid repeated file re-reads.
	 */
	public void streamAllSpectra(SpectrumConsumer consumer) throws IOException {
		if (!open) {
			throw new IOException("mzML file is not open");
		}
		Objects.requireNonNull(consumer, "consumer");
		new MzmlSaxSpectrumStreamer(userFile, index, (precursor, fragment) -> {
			consumer.accept(precursor, fragment==null?null:fragment.trimIsolationWindow(precursorMarginSize));
		}).stream();
	}

	private void determineStructure() {
		dataAcquisitionType=RawFileStructureTools.getDataType(ranges);
		if (dataAcquisitionType==DataAcquisitionType.DIA) {
			staggered=RawFileStructureTools.isStaggered(ranges);
			precursorMarginSize=RawFileStructureTools.getPrecursorMarginSize(ranges).orElse(0.0);
		} else {
			staggered=false;
			precursorMarginSize=0.0;
		}
	}

	/** Number of spectra indexed during openFile(), used for progress reporting. */
	public int getSpectrumCount() {
		return index.size();
	}


	// ---- Index entry ----

	static class MzmlScanEntry {
		String spectrumId;
		int index;
		int sequentialIndex=0;
		long spectrumOffsetHint=-1L;
		int msLevel=1;
		float scanStartTime=0f;
		Float ionInjectionTime=null;
		float tic=0f;

		// isolation window (offsets from target)
		double isolationTarget=0;
		boolean hasIsolationTarget=false;
		double isolationLowerOffset=0;
		double isolationUpperOffset=0;

		// scan window
		double scanWindowLower=0;
		double scanWindowUpper=0;

		// precursor info
		double precursorMz=0;
		byte charge=0;

		double getIsolationWindowLower() {
			return isolationTarget-isolationLowerOffset;
		}

		double getIsolationWindowUpper() {
			return isolationTarget+isolationUpperOffset;
		}

		double getIsolationWindowTarget() {
			if (hasIsolationTarget) return isolationTarget;
			return (getIsolationWindowLower()+getIsolationWindowUpper())/2.0;
		}

		double getPrecursorMzOrIsolationTarget() {
			return precursorMz==0?getIsolationWindowTarget():precursorMz;
		}

		Range getIsolationRange() {
			double lower=getIsolationWindowLower();
			double upper=getIsolationWindowUpper();
			if (lower==0&&upper==0) return null;
			return new Range(lower, upper);
		}
	}
}
