package org.searlelab.msrawjava.io.mzml;

import static org.searlelab.msrawjava.io.mzml.MzmlConstants.CV_32_BIT_FLOAT;
import static org.searlelab.msrawjava.io.mzml.MzmlConstants.CV_64_BIT_FLOAT;
import static org.searlelab.msrawjava.io.mzml.MzmlConstants.CV_INTENSITY_ARRAY;
import static org.searlelab.msrawjava.io.mzml.MzmlConstants.CV_MZ_ARRAY;
import static org.searlelab.msrawjava.io.mzml.MzmlConstants.CV_NO_COMPRESSION;
import static org.searlelab.msrawjava.io.mzml.MzmlConstants.CV_ZLIB_COMPRESSION;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Single-pass SAX spectrum decoder used by conversion paths to avoid repeated mzML re-reads.
 * Metadata for each scan comes from the pre-built MzmlFile index; this parser only decodes binary arrays.
 */
final class MzmlSaxSpectrumStreamer extends DefaultHandler {
	private static final double[] EMPTY_MZ=new double[0];
	private static final float[] EMPTY_INTENSITY=new float[0];

	private final File userFile;
	private final HashMap<Integer, MzmlFile.MzmlScanEntry> entryByIndex=new HashMap<>();
	private final MzmlFile.SpectrumConsumer consumer;

	private int sequentialIndex=0;
	private boolean inSpectrum=false;
	private int spectrumIndex=-1;
	private double[] mzArray;
	private float[] intensityArray;

	private boolean inBinaryDataArray=false;
	private boolean inBinary=false;
	private boolean is64bit=false;
	private boolean is32bit=false;
	private boolean isZlib=false;
	private boolean isMzArray=false;
	private boolean isIntensityArray=false;
	private final StringBuilder binaryData=new StringBuilder(8192);

	MzmlSaxSpectrumStreamer(File userFile, ArrayList<MzmlFile.MzmlScanEntry> index, MzmlFile.SpectrumConsumer consumer) {
		this.userFile=userFile;
		this.consumer=consumer;
		for (MzmlFile.MzmlScanEntry entry : index) {
			entryByIndex.put(entry.index, entry);
		}
	}

	void stream() throws IOException {
		SAXParserFactory factory=SAXParserFactory.newInstance();
		factory.setNamespaceAware(true);
		configureParser(factory);

		try (FileInputStream fis=new FileInputStream(userFile)) {
			factory.newSAXParser().parse(fis, this);
		} catch (ParserConfigurationException e) {
			throw new IOException("Error configuring mzML SAX parser: "+e.getMessage(), e);
		} catch (SAXException e) {
			Throwable cause=e.getCause();
			if (cause instanceof IOException) {
				throw (IOException)cause;
			}
			throw new IOException("Error streaming mzML spectra: "+e.getMessage(), e);
		}
	}

	private static void configureParser(SAXParserFactory factory) {
		try {
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		} catch (Exception e) {
			/* best effort */
		}
		try {
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		} catch (Exception e) {
			/* best effort */
		}
		try {
			factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		} catch (Exception e) {
			/* best effort */
		}
	}

	@Override
	public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
		String name=elementName(localName, qName);
		if ("spectrum".equals(name)) {
			startSpectrum(attributes);
			return;
		}
		if (!inSpectrum) return;

		if ("binaryDataArray".equals(name)) {
			inBinaryDataArray=true;
			is64bit=false;
			is32bit=false;
			isZlib=false;
			isMzArray=false;
			isIntensityArray=false;
			return;
		}
		if ("cvParam".equals(name)&&inBinaryDataArray) {
			parseBinaryCvParam(attributes);
			return;
		}
		if ("binary".equals(name)&&inBinaryDataArray) {
			inBinary=true;
			binaryData.setLength(0);
		}
	}

	@Override
	public void characters(char[] ch, int start, int length) {
		if (inBinary) {
			binaryData.append(ch, start, length);
		}
	}

	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException {
		String name=elementName(localName, qName);
		if (!inSpectrum) return;

		if ("binary".equals(name)&&inBinaryDataArray) {
			inBinary=false;
			decodeCurrentBinary();
			return;
		}
		if ("binaryDataArray".equals(name)) {
			inBinaryDataArray=false;
			return;
		}
		if ("spectrum".equals(name)) {
			endSpectrum();
		}
	}

	private void startSpectrum(Attributes attributes) {
		inSpectrum=true;
		mzArray=null;
		intensityArray=null;

		String indexAttr=attributes.getValue("index");
		if (indexAttr!=null) {
			spectrumIndex=Integer.parseInt(indexAttr);
		} else {
			spectrumIndex=sequentialIndex;
		}
		sequentialIndex++;
	}

	private void parseBinaryCvParam(Attributes attributes) {
		String accession=attributes.getValue("accession");
		if (accession==null) return;
		switch (accession) {
			case CV_64_BIT_FLOAT:
				is64bit=true;
				is32bit=false;
				break;
			case CV_32_BIT_FLOAT:
				is32bit=true;
				is64bit=false;
				break;
			case CV_ZLIB_COMPRESSION:
				isZlib=true;
				break;
			case CV_NO_COMPRESSION:
				isZlib=false;
				break;
			case CV_MZ_ARRAY:
				isMzArray=true;
				isIntensityArray=false;
				break;
			case CV_INTENSITY_ARRAY:
				isIntensityArray=true;
				isMzArray=false;
				break;
			default:
				break;
		}
	}

	private void decodeCurrentBinary() throws SAXException {
		if (binaryData.length()==0) return;
		try {
			String base64=compactBase64(binaryData);
			if (base64.isEmpty()) return;
			byte[] decoded=MzmlBinaryUtils.decodeBase64(base64);
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
		} catch (Exception e) {
			throw new SAXException("Error decoding mzML binary data for spectrum index "+spectrumIndex, e);
		}
	}

	private void endSpectrum() throws SAXException {
		inSpectrum=false;
		MzmlFile.MzmlScanEntry entry=entryByIndex.get(spectrumIndex);
		if (entry==null) return;

		double[] mass=(mzArray!=null)?mzArray:EMPTY_MZ;
		float[] intensity=(intensityArray!=null)?intensityArray:EMPTY_INTENSITY;

		try {
			if (entry.msLevel==1) {
				double scanLower=entry.scanWindowLower;
				double scanUpper=entry.scanWindowUpper;
				if (scanLower==0&&scanUpper==0&&mass.length>0) {
					scanLower=mass[0];
					scanUpper=mass[mass.length-1];
				}
				consumer.accept(
						new PrecursorScan(entry.spectrumId, entry.index, entry.scanStartTime, 0, scanLower, scanUpper, entry.ionInjectionTime, mass, intensity, null),
						null);
				return;
			}

			double isolationLower=entry.getIsolationWindowLower();
			double isolationUpper=entry.getIsolationWindowUpper();
			double precursorMz=entry.precursorMz;
			if (precursorMz==0) precursorMz=(isolationLower+isolationUpper)/2.0;
			double scanLower=entry.scanWindowLower;
			double scanUpper=entry.scanWindowUpper;
			if (scanLower==0&&scanUpper==0) {
				scanLower=isolationLower;
				scanUpper=isolationUpper;
			}
			consumer.accept(null,
					new FragmentScan(entry.spectrumId, "", entry.index, precursorMz, entry.scanStartTime, 0, entry.ionInjectionTime, isolationLower, isolationUpper,
							mass, intensity, null, entry.charge, scanLower, scanUpper));
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new SAXException("Error emitting decoded spectrum index "+entry.index, e);
		}
	}

	private static String elementName(String localName, String qName) {
		if (localName!=null&&!localName.isEmpty()) return localName;
		return qName;
	}

	private static String compactBase64(CharSequence base64) {
		boolean hasWhitespace=false;
		for (int i=0; i<base64.length(); i++) {
			if (Character.isWhitespace(base64.charAt(i))) {
				hasWhitespace=true;
				break;
			}
		}
		if (!hasWhitespace) {
			return base64.toString();
		}
		StringBuilder compact=new StringBuilder(base64.length());
		for (int i=0; i<base64.length(); i++) {
			char c=base64.charAt(i);
			if (!Character.isWhitespace(c)) {
				compact.append(c);
			}
		}
		return compact.toString();
	}
}
