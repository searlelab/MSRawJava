package org.searlelab.msrawjava.io;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.searlelab.msrawjava.Version;
import org.searlelab.msrawjava.io.utils.StreamCopy;
import org.searlelab.msrawjava.model.AcquiredSpectrum;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.WindowData;

/**
 * MZMLOutputFile writes mzML in a streaming manner, assembling instrument/run metadata and spectra into a consistent,
 * indexed XML artifact. It emphasizes deterministic serialization and controlled memory use, mapping the project’s
 * model types to mzML structures while keeping CV terms and checksums coherent.
 */
public class MZMLOutputFile implements OutputSpectrumFile {

	private static final String SPECTRUM_LIST_COUNT_PLACEHOLDER="SPECTRUM_LIST_COUNT_PLACEHOLDER";

	public static final String MZML_EXTENSION=".mzML";

	private Path tempPath=null;
	private BufferedWriter out=null;
	private boolean headerWritten=false;
	private boolean spectrumListOpen=false;
	private long spectrumCount=0;
	private Map<String, String> meta=new HashMap<>();

	private String sourcePath=null;
	private String sourceName=null;

	@Override
	public String getFileExtension() {
		return MZML_EXTENSION;
	}

	@Override
	public void setRanges(HashMap<Range, WindowData> ranges) {
		/* ignore */ }

	@Override
	public void addMetadata(Map<String, String> data) throws IOException, SQLException {
		if (data!=null) {
			meta.putAll(data);
		}
	}

	public void openFile() throws IOException {
		tempPath=Files.createTempFile("mzml_stream_", MZML_EXTENSION);
		out=Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8);
	}

	@Override
	public void setFileName(String fileName, String fileLocation) throws IOException, SQLException {
		this.sourceName=(fileName!=null?fileName:meta.getOrDefault("filename", "source"));
		this.sourcePath=(fileLocation!=null?fileLocation:meta.getOrDefault("filelocation", null));

		writeMZMLHeader();
	}

	@Override
	public void saveAsFile(File userFile) throws IOException, SQLException {
		if (spectrumListOpen) {
			closeSpectrumListAndRun();
		}
		if (out!=null) {
			out.flush();
			out.close();
			out=null;
		}
		if (userFile.getParentFile()!=null) {
			Files.createDirectories(userFile.getParentFile().toPath());
		}

		// Replace placeholder with spectrumList count
	    StreamCopy.streamReplace(tempPath, userFile.toPath(), SPECTRUM_LIST_COUNT_PLACEHOLDER, Long.toString(spectrumCount));
	}

	@Override
	public void addSpectra(ArrayList<PrecursorScan> precursors, ArrayList<FragmentScan> stripes) throws Exception {
		if (!headerWritten) writeMZMLHeader();
		if (!spectrumListOpen) openSpectrumList();
		
		ArrayList<AcquiredSpectrum> spectra=new ArrayList<AcquiredSpectrum>();
		spectra.addAll(precursors);
		spectra.addAll(stripes);
		spectra.sort(Comparator.comparingDouble(AcquiredSpectrum::getScanStartTime));
		for (AcquiredSpectrum spectrum : spectra) {
			if (spectrum instanceof PrecursorScan) {
				writeSpectrumMS1((PrecursorScan)spectrum);
			} else if (spectrum instanceof FragmentScan) {
				writeSpectrumMS2((FragmentScan)spectrum);
			}
		}
		out.flush();
	}

	@Override
	public void close() {
		try {
			if (spectrumListOpen) {
				closeSpectrumListAndRun();
			}
			if (out!=null) {
				out.flush();
				out.close();
			}
		} catch (Exception ignore) {
		} finally {
			out=null;
		}
	}

	private void writeMZMLHeader() throws IOException {
		if (headerWritten) return;

		// XML declaration + mzML root + CV list
		out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		out.write("<mzML xmlns=\"http://psi.hupo.org/ms/mzml\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
				+" xsi:schemaLocation=\"http://psi.hupo.org/ms/mzml http://psi.hupo.org/ms/mzml/schema/mzML1.1.1.xsd\""+" version=\"1.1.1\">\n");

		out.write("  <cvList count=\"2\">\n");
		out.write("    <cv id=\"MS\" fullName=\"Proteomics Standards Initiative Mass Spectrometry Ontology\" version=\"4.1.136\" "
				+"URI=\"https://raw.githubusercontent.com/HUPO-PSI/psi-ms-CV/master/psi-ms.obo\"/>\n");
		out.write("    <cv id=\"UO\" fullName=\"Unit Ontology\" version=\"09:04:2014\" "
				+"URI=\"https://raw.githubusercontent.com/bio-ontology-research-group/unit-ontology/master/unit.obo\"/>\n");
		out.write("  </cvList>\n");

		writeFileDescription();
		writeReferenceableParamGroupList();
		writeSoftwareList();
		writeInstrumentConfigurationList();
		writeDataProcessingList();

		out.write("  <run id=\"run1\" defaultInstrumentConfigurationRef=\"IC1\">\n");

		headerWritten=true;
	}

	private void writeFileDescription() throws IOException {
		out.write("  <fileDescription>\n");
		out.write("    <fileContent>\n");
		out.write("      <cvParam cvRef=\"MS\" accession=\"MS:1000579\" name=\"MS1 spectrum\" value=\"\"/>\n");
		out.write("      <cvParam cvRef=\"MS\" accession=\"MS:1000580\" name=\"MSn spectrum\" value=\"\"/>\n");
		out.write("    </fileContent>\n");

		out.write("    <sourceFileList count=\"1\">\n");
		String srcId="SRC1";
		String srcName=(sourceName!=null?sourceName:"source");
		if (srcName.endsWith(".d")) {
			// hack to use tdf
			srcName="Analysis.tdf";
		}
		String srcLoc=(sourcePath!=null?"file://"+escapeXml(sourcePath):"file:///");
		out.write("      <sourceFile id=\""+srcId+"\" name=\""+escapeXml(srcName)+"\" location=\""+srcLoc+"\">\n");
		out.write("        <cvParam cvRef=\"MS\" accession=\"MS:1000776\" name=\"scan number only nativeID format\" value=\"\"/>\n");

		String formatAcc=guessFileFormatAccession(srcName, meta);
		String formatName=cvName(formatAcc);
		out.write("        <cvParam cvRef=\"MS\" accession=\""+formatAcc+"\" name=\""+formatName+"\" value=\"\"/>\n");

		// SHA-1 checksum
		String sha1;
		if (sourcePath.endsWith(".d")) {
			// hack to use tdf
			sha1=computeSHA1Safe(sourcePath+File.separator+"Analysis.tdf");
		} else {
			sha1=computeSHA1Safe(sourcePath);
		}
		
		if (sha1==null) sha1="0000000000000000000000000000000000000000"; // worst case placeholder
		out.write("        <cvParam cvRef=\"MS\" accession=\"MS:1000569\" name=\"SHA-1\" value=\""+sha1+"\"/>\n");

		out.write("      </sourceFile>\n");
		out.write("    </sourceFileList>\n");

		out.write("  </fileDescription>\n");
	}

	private void writeReferenceableParamGroupList() throws IOException {
		out.write("  <referenceableParamGroupList count=\"1\">\n");
		out.write("    <referenceableParamGroup id=\"CommonInstrumentParams\">\n");

		// Instrument family / model
		String vendor=meta.getOrDefault("meta.InstrumentVendor", "");
		String model=meta.getOrDefault("meta.InstrumentName", meta.getOrDefault("instrument.model", "")).toLowerCase(Locale.ROOT);

		if (vendor.toLowerCase(Locale.ROOT).contains("bruker")||model.contains("tims")) {
			// Bruker timsTOF series
			out.write("      <cvParam cvRef=\"MS\" accession=\"MS:1003123\" name=\"Bruker Daltonics timsTOF series\" value=\"\"/>\n");
		} else if (model.contains("orbitrap")||vendor.toLowerCase(Locale.ROOT).contains("thermo")) {
			// Generic Thermo model bucket + serial
			out.write("      <cvParam cvRef=\"MS\" accession=\"MS:1000492\" name=\"Thermo Electron instrument model\" value=\"\"/>\n");
		} else {
			// Fallback: generic instrument model term 
			out.write("      <cvParam cvRef=\"MS\" accession=\"MS:1000031\" name=\"instrument model\" value=\"\"/>\n");
		}

		// instrument serial number if present
		String serial=meta.getOrDefault("meta.InstrumentSerialNumber", meta.getOrDefault("instrument.serial_number", null));
		if (serial!=null&&!serial.isEmpty()) {
			out.write("      <cvParam cvRef=\"MS\" accession=\"MS:1000529\" name=\"instrument serial number\" value=\""+escapeXml(serial)+"\"/>\n");
		}

		out.write("    </referenceableParamGroup>\n");
		out.write("  </referenceableParamGroupList>\n");
	}

	private void writeSoftwareList() throws IOException {
		boolean hasAcq=meta.containsKey("meta.AcquisitionSoftware");
		out.write("  <softwareList count=\""+(hasAcq?"2":"1")+"\">\n");

		if (hasAcq) {
			String acq=meta.get("meta.AcquisitionSoftware");
			String acqVer=meta.getOrDefault("meta.AcquisitionSoftwareVersion", "unknown");
			out.write("    <software id=\"ACQ\" version=\""+escapeXml(acqVer)+"\">\n");
			out.write("      <cvParam cvRef=\"MS\" accession=\"MS:1000531\" name=\"acquisition software\" value=\""+escapeXml(acq)+"\"/>\n");
			out.write("    </software>\n");
		}

		out.write("    <software id=\"msrawjava\" version=\""+escapeXml(Version.getVersion())+"\">\n");
		out.write("      <cvParam cvRef=\"MS\" accession=\"MS:1000799\" name=\"custom unreleased software tool\" value=\"msrawjava\"/>\n");
		out.write("    </software>\n");
		out.write("  </softwareList>\n");
	}

	private void writeInstrumentConfigurationList() throws IOException {
		out.write("  <instrumentConfigurationList count=\"1\">\n");
		out.write("    <instrumentConfiguration id=\"IC1\">\n");
		out.write("      <referenceableParamGroupRef ref=\"CommonInstrumentParams\"/>\n");
		out.write("      <componentList count=\"3\">\n");

		// Source
		out.write("        <source order=\"1\">\n");
		out.write("          <cvParam cvRef=\"MS\" accession=\"MS:1000073\" name=\"electrospray ionization\" value=\"\"/>\n");
		out.write("        </source>\n");

		String vendorLc=meta.getOrDefault("meta.InstrumentVendor", "").toLowerCase(Locale.ROOT);
		String modelLc=meta.getOrDefault("meta.InstrumentName", meta.getOrDefault("instrument.model", "")).toLowerCase(Locale.ROOT);
		out.write("        <analyzer order=\"2\">\n");
		if (vendorLc.contains("thermo")) {
			if (modelLc.contains("orbitrap")) {
				out.write("          <cvParam cvRef=\"MS\" accession=\"MS:1000484\" name=\"orbitrap\" value=\"\"/>\n");
			} else if (modelLc.contains("astral")) {
				out.write("          <cvParam cvRef=\"MS\" accession=\"MS:1000084\" name=\"time-of-flight\" value=\"\"/>\n");
			} else if (modelLc.contains("stellar")) {
				out.write("          <cvParam cvRef=\"MS\" accession=\"MS:1000291\" name=\"linear ion trap\" value=\"\"/>\n");
			}
		} else {
			out.write("          <cvParam cvRef=\"MS\" accession=\"MS:1000084\" name=\"time-of-flight\" value=\"\"/>\n");
		}
		out.write("        </analyzer>\n");

		out.write("        <detector order=\"3\">\n");
		if (vendorLc.contains("thermo")||modelLc.contains("orbitrap")) {
			out.write("          <cvParam cvRef=\"MS\" accession=\"MS:1000624\" name=\"inductive detector\" value=\"\"/>\n");
		} else {
			out.write("          <cvParam cvRef=\"MS\" accession=\"MS:1000114\" name=\"microchannel plate detector\" value=\"\"/>\n");
		}
		out.write("        </detector>\n");

		out.write("      </componentList>\n");
		if (meta.containsKey("meta.AcquisitionSoftware")) {
			out.write("      <softwareRef ref=\"ACQ\"/>\n");
		}
		out.write("    </instrumentConfiguration>\n");
		out.write("  </instrumentConfigurationList>\n");
	}

	private void writeDataProcessingList() throws IOException {
		out.write("  <dataProcessingList count=\"1\">\n");
		out.write("    <dataProcessing id=\"msrawjava_processing\">\n");
		out.write("      <processingMethod order=\"0\" softwareRef=\"msrawjava\">\n");
		out.write("        <cvParam cvRef=\"MS\" accession=\"MS:1000544\" name=\"Conversion to mzML\" value=\"\"/>\n");
		out.write("      </processingMethod>\n");
		out.write("    </dataProcessing>\n");
		out.write("  </dataProcessingList>\n");
	}

	private void openSpectrumList() throws IOException {
		// ★ Write a stable placeholder we’ll replace at saveAsFile(...)
		out.write("    <spectrumList count=\""+SPECTRUM_LIST_COUNT_PLACEHOLDER+"\" defaultDataProcessingRef=\"msrawjava_processing\">\n");
		spectrumListOpen=true;
	}

	private void closeSpectrumListAndRun() throws IOException {
		out.write("    </spectrumList>\n");
		out.write("  </run>\n");
		out.write("</mzML>\n");
		spectrumListOpen=false;
	}

	private void writeSpectrumMS1(PrecursorScan scan) throws IOException {
		final int n=Math.min(scan.getMassArray().length, scan.getIntensityArray().length);
		String id="scan="+scan.getSpectrumIndex();
		out.write("      <spectrum id=\""+id+"\" index=\""+(spectrumCount++)+"\" defaultArrayLength=\""+n+"\">\n");
		out.write("        <cvParam cvRef=\"MS\" accession=\"MS:1000511\" name=\"ms level\" value=\"1\"/>\n");
		out.write("        <cvParam cvRef=\"MS\" accession=\"MS:1000579\" name=\"MS1 spectrum\" value=\"\"/>\n");
		out.write("        <cvParam cvRef=\"MS\" accession=\"MS:1000127\" name=\"centroid spectrum\" value=\"\"/>\n");
		out.write("        <cvParam cvRef=\"MS\" accession=\"MS:1000130\" name=\"positive scan\" value=\"\"/>\n");
		
		out.write("        <cvParam cvRef=\"MS\" accession=\"MS:1000505\" name=\"base peak intensity\" value=\""
				+fmtIntens(scan.getBasePeak().intensity)+"\" unitCvRef=\"MS\" unitAccession=\"MS:1000131\" unitName=\"number of detector counts\"/>\n");
		out.write("        <cvParam cvRef=\"MS\" accession=\"MS:1000285\" name=\"total ion current\" value=\""
				+fmtIntens(scan.getTIC())+"\"/>\n");

		writeIonMobilityLimitsUserParams(scan.getIonMobilityArray());

		out.write("        <scanList count=\"1\">\n");
		out.write("          <cvParam cvRef=\"MS\" accession=\"MS:1000795\" name=\"no combination\" value=\"\"/>\n");
		out.write("          <scan instrumentConfigurationRef=\"IC1\">\n");
		out.write("            <cvParam cvRef=\"MS\" accession=\"MS:1000016\" name=\"scan start time\" value=\""
				+fmtTimeInSec(scan.getScanStartTime())+"\" unitCvRef=\"UO\" unitAccession=\"UO:0000010\" unitName=\"second\"/>\n");
		if (scan.getIonInjectionTime()!=null) {
			out.write("            <cvParam cvRef=\"MS\" accession=\"MS:1000927\" name=\"ion injection time\" value=\""
					+fmtTimeInSec(scan.getIonInjectionTime())+"\" unitCvRef=\"UO\" unitAccession=\"UO:0000028\" unitName=\"millisecond\"/>\n");
		}
		
		writeScanWindow(scan.getScanWindowLower(), scan.getScanWindowUpper());
		out.write("          </scan>\n");
		out.write("        </scanList>\n");

		writeBinaryDataArrayList(scan.getMassArray(), scan.getIntensityArray());
		out.write("      </spectrum>\n");
	}

	private void writeSpectrumMS2(FragmentScan scan) throws IOException {
		final int n=Math.min(scan.getMassArray().length, scan.getIntensityArray().length);
		String id="scan="+scan.getSpectrumIndex();
		out.write("      <spectrum id=\""+id+"\" index=\""+(spectrumCount++)+"\" defaultArrayLength=\""+n+"\">\n");
		out.write("        <cvParam cvRef=\"MS\" accession=\"MS:1000511\" name=\"ms level\" value=\"2\"/>\n");
		out.write("        <cvParam cvRef=\"MS\" accession=\"MS:1000580\" name=\"MSn spectrum\" value=\"\"/>\n");
		out.write("        <cvParam cvRef=\"MS\" accession=\"MS:1000127\" name=\"centroid spectrum\" value=\"\"/>\n");
		out.write("        <cvParam cvRef=\"MS\" accession=\"MS:1000130\" name=\"positive scan\" value=\"\"/>\n");

		out.write("        <cvParam cvRef=\"MS\" accession=\"MS:1000505\" name=\"base peak intensity\" value=\""
				+fmtIntens(scan.getBasePeak().intensity)+"\" unitCvRef=\"MS\" unitAccession=\"MS:1000131\" unitName=\"number of detector counts\"/>\n");
		out.write("        <cvParam cvRef=\"MS\" accession=\"MS:1000285\" name=\"total ion current\" value=\""
				+fmtIntens(scan.getTIC())+"\"/>\n");
		
		writeIonMobilityLimitsUserParams(scan.getIonMobilityArray());

		out.write("        <scanList count=\"1\">\n");
		out.write("          <cvParam cvRef=\"MS\" accession=\"MS:1000795\" name=\"no combination\" value=\"\"/>\n");
		out.write("          <scan instrumentConfigurationRef=\"IC1\">\n");
		out.write("            <cvParam cvRef=\"MS\" accession=\"MS:1000016\" name=\"scan start time\" value=\""
				+fmtTimeInSec(scan.getScanStartTime())+"\" unitCvRef=\"UO\" unitAccession=\"UO:0000010\" unitName=\"second\"/>\n");

		if (scan.getIonInjectionTime()!=null) {
			out.write("            <cvParam cvRef=\"MS\" accession=\"MS:1000927\" name=\"ion injection time\" value=\""
					+fmtTimeInSec(scan.getIonInjectionTime())+"\" unitCvRef=\"UO\" unitAccession=\"UO:0000028\" unitName=\"millisecond\"/>\n");
		}
		
		writeScanWindow(scan.getScanWindowLower(), scan.getScanWindowUpper());
		out.write("          </scan>\n");
		out.write("        </scanList>\n");

		out.write("        <precursorList count=\"1\">\n");
		out.write("          <precursor>\n");
		out.write("              <isolationWindow>\n");
		double isolationCenter=(scan.getIsolationWindowUpper()+scan.getIsolationWindowLower())/2.0;
		double isolationOffset=(scan.getIsolationWindowUpper()-scan.getIsolationWindowLower())/2.0;

		out.write("                <cvParam cvRef=\"MS\" accession=\"MS:1000827\" name=\"isolation window target m/z\" value=\""+fmtMz(isolationCenter)
				+"\" unitCvRef=\"MS\" unitAccession=\"MS:1000040\" unitName=\"m/z\"/>\n");
		out.write("                <cvParam cvRef=\"MS\" accession=\"MS:1000828\" name=\"isolation window lower offset\" value=\""+fmtMz(isolationOffset)
				+"\" unitCvRef=\"MS\" unitAccession=\"MS:1000040\" unitName=\"m/z\"/>\n");
		out.write("                <cvParam cvRef=\"MS\" accession=\"MS:1000829\" name=\"isolation window upper offset\" value=\""+fmtMz(isolationOffset)
				+"\" unitCvRef=\"MS\" unitAccession=\"MS:1000040\" unitName=\"m/z\"/>\n");
		out.write("              </isolationWindow>\n");
		
		
		out.write("            <selectedIonList count=\"1\">\n");
		out.write("              <selectedIon>\n");
		out.write("                <cvParam cvRef=\"MS\" accession=\"MS:1000744\" name=\"selected ion m/z\" value=\""+fmtMz(scan.getPrecursorMZ())
				+"\" unitCvRef=\"MS\" unitAccession=\"MS:1000040\" unitName=\"m/z\"/>\n");
		byte z=scan.getCharge();
		if (z!=0) {
			out.write("                <cvParam cvRef=\"MS\" accession=\"MS:1000041\" name=\"charge state\" value=\""+z+"\"/>\n");
		}
		out.write("              </selectedIon>\n");
		out.write("            </selectedIonList>\n");
		out.write("            <activation>\n");
		String act=meta.getOrDefault("activation.method", "MS:1000422");
		out.write("              <cvParam cvRef=\"MS\" accession=\""+act+"\" name=\""+cvName(act)+"\" value=\"\"/>\n");
		out.write("            </activation>\n");
		out.write("          </precursor>\n");
		out.write("        </precursorList>\n");

		writeBinaryDataArrayList(scan.getMassArray(), scan.getIntensityArray());
		out.write("      </spectrum>\n");
	}

	private void writeIonMobilityLimitsUserParams(Optional<float[]> imsOpt) throws IOException {
		if (imsOpt.isPresent()) {
			float[] a=imsOpt.get();
			double min=Double.POSITIVE_INFINITY, max=Double.NEGATIVE_INFINITY;
			for (float v : a) {
				if (v<min) min=v;
				if (v>max) max=v;
			}
			if (min<Double.POSITIVE_INFINITY&&max>Double.NEGATIVE_INFINITY) {
				out.write("        <userParam name=\"ion mobility lower limit\" value=\""+fmtIMS(min)
						+"\" type=\"xsd:double\" unitAccession=\"MS:1002814\" unitName=\"volt-second per square centimeter\"/>\n");
				out.write("        <userParam name=\"ion mobility upper limit\" value=\""+fmtIMS(max)
						+"\" type=\"xsd:double\" unitAccession=\"MS:1002814\" unitName=\"volt-second per square centimeter\"/>\n");
			}
		}
	}

	private void writeBinaryDataArrayList(double[] mz, float[] intensity) throws IOException {
		int n=Math.min(mz.length, intensity.length);
		String mz64=encode64(toBytes64(mz, n));
		String i32=encode64(toBytes32(intensity, n));

		out.write("        <binaryDataArrayList count=\"2\">\n");

		// m/z array (64-bit float) + m/z unit
		out.write("          <binaryDataArray encodedLength=\""+mz64.length()+"\">\n");
		out.write(
				"            <cvParam cvRef=\"MS\" accession=\"MS:1000514\" name=\"m/z array\" value=\"\" unitCvRef=\"MS\" unitAccession=\"MS:1000040\" unitName=\"m/z\"/>\n");
		out.write("            <cvParam cvRef=\"MS\" accession=\"MS:1000523\" name=\"64-bit float\" value=\"\"/>\n");
		out.write("            <cvParam cvRef=\"MS\" accession=\"MS:1000576\" name=\"no compression\" value=\"\"/>\n");
		out.write("            <binary>"+mz64+"</binary>\n");
		out.write("          </binaryDataArray>\n");

		// intensity array (32-bit float) + counts unit
		out.write("          <binaryDataArray encodedLength=\""+i32.length()+"\">\n");
		out.write(
				"            <cvParam cvRef=\"MS\" accession=\"MS:1000515\" name=\"intensity array\" value=\"\" unitCvRef=\"MS\" unitAccession=\"MS:1000131\" unitName=\"number of detector counts\"/>\n");
		out.write("            <cvParam cvRef=\"MS\" accession=\"MS:1000521\" name=\"32-bit float\" value=\"\"/>\n");
		out.write("            <cvParam cvRef=\"MS\" accession=\"MS:1000576\" name=\"no compression\" value=\"\"/>\n");
		out.write("            <binary>"+i32+"</binary>\n");
		out.write("          </binaryDataArray>\n");

		out.write("        </binaryDataArrayList>\n");
	}

	private void writeScanWindow(double lower, double upper) throws IOException {
		out.write("            <scanWindowList count=\"1\">\n");
		out.write("              <scanWindow>\n");
		out.write("                <cvParam cvRef=\"MS\" accession=\"MS:1000501\" name=\"scan window lower limit\" value=\""+fmtMz(lower)
				+"\" unitCvRef=\"MS\" unitAccession=\"MS:1000040\" unitName=\"m/z\"/>\n");
		out.write("                <cvParam cvRef=\"MS\" accession=\"MS:1000500\" name=\"scan window upper limit\" value=\""+fmtMz(upper)
				+"\" unitCvRef=\"MS\" unitAccession=\"MS:1000040\" unitName=\"m/z\"/>\n");
		out.write("              </scanWindow>\n");
		out.write("            </scanWindowList>\n");
	}

	private static String fmtMz(double v) {
		return String.format("%.6f", v);
	}

	private static String fmtIntens(double v) {
		return String.format("%.2f", v);
	}

	private static String fmtIMS(double v) {
		return String.format("%.3f", v);
	}

	private static String fmtTimeInSec(double v) {
		return String.format("%.3f", v);
	}

	private static String encode64(byte[] bytes) {
		return Base64.getEncoder().encodeToString(bytes);
	}

	private static byte[] toBytes64(double[] a, int n) {
		// little-endian IEEE-754 doubles
		byte[] out=new byte[n*8];
		for (int i=0; i<n; i++) {
			long bits=Double.doubleToRawLongBits(a[i]);
			int o=i*8;
			out[o]=(byte)(bits);
			out[o+1]=(byte)(bits>>>8);
			out[o+2]=(byte)(bits>>>16);
			out[o+3]=(byte)(bits>>>24);
			out[o+4]=(byte)(bits>>>32);
			out[o+5]=(byte)(bits>>>40);
			out[o+6]=(byte)(bits>>>48);
			out[o+7]=(byte)(bits>>>56);
		}
		return out;
	}

	private static byte[] toBytes32(float[] a, int n) {
		byte[] out=new byte[n*4];
		for (int i=0; i<n; i++) {
			int bits=Float.floatToRawIntBits(a[i]);
			int o=i*4;
			out[o]=(byte)(bits);
			out[o+1]=(byte)(bits>>>8);
			out[o+2]=(byte)(bits>>>16);
			out[o+3]=(byte)(bits>>>24);
		}
		return out;
	}

	private static String escapeXml(String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

	private static String cvName(String accession) {
		switch (accession) {
			case "MS:1000563":
				return "Thermo RAW format";
			case "MS:1000584":
				return "mzML format";
			case "MS:1000567":
				return "Bruker/Agilent YEP format";
			case "MS:1000442":
				return "time-of-flight mass analyzer";
			case "MS:1000484":
				return "orbitrap";
			case "MS:1002817":
				return "Bruker TDF format";
			case "MS:1002818":
				return "Bruker TDF nativeID format";
			case "MS:1000422":
				return "beam-type collision-induced dissociation";
			default:
				return accession;
		}
	}

	private static String guessFileFormatAccession(String fileName, Map<String, String> meta) {
		String lower=(fileName==null?"":fileName.toLowerCase(Locale.ROOT));
		if (lower.endsWith(".raw")) return "MS:1000563"; // Thermo RAW format
		if (lower.endsWith(".mzml")) return "MS:1000584"; // mzML
		if (lower.endsWith(".yep")) return "MS:1000567"; // Bruker/Agilent YEP
		if (lower.endsWith(".d")||lower.endsWith(".tdf")) return "MS:1002817"; // Bruker timsTOF .d (TDF)
		return "MS:1000584";
	}

	private static String computeSHA1Safe(String path) {
		if (path==null) return null;
		try (InputStream is=Files.newInputStream(Paths.get(path))) {
			MessageDigest md=MessageDigest.getInstance("SHA-1");
			try (DigestInputStream dis=new DigestInputStream(is, md)) {
				byte[] buf=new byte[1<<20];
				while (dis.read(buf)>=0) {
					/* consume */ }
			} catch (Exception e) {
				e.printStackTrace();
			}
			byte[] d=md.digest();
			StringBuilder sb=new StringBuilder(40);
			for (byte b : d)
				sb.append(String.format("%02x", b));
			return sb.toString();
		} catch (Exception e) {
			return null;
		}
	}
}