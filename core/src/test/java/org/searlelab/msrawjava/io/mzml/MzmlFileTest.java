package org.searlelab.msrawjava.io.mzml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;
import java.util.zip.Deflater;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.msrawjava.io.utils.Pair;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.ScanSummary;
import org.searlelab.msrawjava.model.WindowData;

class MzmlFileTest {

	@TempDir
	Path tmp;

	// ---- encoding helpers ----

	private static String encode64Double(double[] values) {
		ByteBuffer buf=ByteBuffer.allocate(values.length*8).order(ByteOrder.LITTLE_ENDIAN);
		for (double v : values) buf.putDouble(v);
		return Base64.getEncoder().encodeToString(buf.array());
	}

	private static String encode32Float(float[] values) {
		ByteBuffer buf=ByteBuffer.allocate(values.length*4).order(ByteOrder.LITTLE_ENDIAN);
		for (float v : values) buf.putFloat(v);
		return Base64.getEncoder().encodeToString(buf.array());
	}

	private static String encode64DoubleZlib(double[] values) {
		ByteBuffer buf=ByteBuffer.allocate(values.length*8).order(ByteOrder.LITTLE_ENDIAN);
		for (double v : values) buf.putDouble(v);
		return zlibAndBase64(buf.array());
	}

	private static String encode32FloatZlib(float[] values) {
		ByteBuffer buf=ByteBuffer.allocate(values.length*4).order(ByteOrder.LITTLE_ENDIAN);
		for (float v : values) buf.putFloat(v);
		return zlibAndBase64(buf.array());
	}

	private static String zlibAndBase64(byte[] data) {
		Deflater deflater=new Deflater();
		deflater.setInput(data);
		deflater.finish();
		byte[] out=new byte[data.length*2];
		int len=deflater.deflate(out);
		deflater.end();
		byte[] compressed=new byte[len];
		System.arraycopy(out, 0, compressed, 0, len);
		return Base64.getEncoder().encodeToString(compressed);
	}

	// ---- mzML generation helpers ----

	private static String mzmlHeader() {
		return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"+"<mzML xmlns=\"http://psi.hupo.org/ms/mzml\" version=\"1.1.1\">\n"
				+"  <cvList count=\"2\">\n"+"    <cv id=\"MS\" fullName=\"PSI-MS\" version=\"4.1.136\" URI=\"\"/>\n"
				+"    <cv id=\"UO\" fullName=\"Unit Ontology\" version=\"1\" URI=\"\"/>\n"+"  </cvList>\n"+"  <run id=\"run1\">\n"
				+"    <spectrumList count=\"PLACEHOLDER\" defaultDataProcessingRef=\"dp\">\n";
	}

	private static String mzmlFooter() {
		return "    </spectrumList>\n"+"  </run>\n"+"</mzML>\n";
	}

	private static String ms1Spectrum(int index, float rtSeconds, double scanLo, double scanHi, double[] mz, float[] intensity, float tic, boolean zlib) {
		String mzBin=zlib?encode64DoubleZlib(mz):encode64Double(mz);
		String intBin=zlib?encode32FloatZlib(intensity):encode32Float(intensity);
		String compAcc=zlib?"MS:1000574":"MS:1000576";
		int n=Math.min(mz.length, intensity.length);
		return "      <spectrum id=\"scan="+index+"\" index=\""+index+"\" defaultArrayLength=\""+n+"\">\n"
				+"        <cvParam cvRef=\"MS\" accession=\"MS:1000511\" name=\"ms level\" value=\"1\"/>\n"
				+"        <cvParam cvRef=\"MS\" accession=\"MS:1000285\" name=\"total ion current\" value=\""+tic+"\"/>\n"
				+"        <scanList count=\"1\">\n"+"          <scan>\n"
				+"            <cvParam cvRef=\"MS\" accession=\"MS:1000016\" name=\"scan start time\" value=\""+rtSeconds
				+"\" unitCvRef=\"UO\" unitAccession=\"UO:0000010\" unitName=\"second\"/>\n"+"            <scanWindowList count=\"1\">\n"
				+"              <scanWindow>\n"+"                <cvParam cvRef=\"MS\" accession=\"MS:1000501\" value=\""+scanLo+"\"/>\n"
				+"                <cvParam cvRef=\"MS\" accession=\"MS:1000500\" value=\""+scanHi+"\"/>\n"+"              </scanWindow>\n"
				+"            </scanWindowList>\n"+"          </scan>\n"+"        </scanList>\n"+"        <binaryDataArrayList count=\"2\">\n"
				+"          <binaryDataArray>\n"+"            <cvParam cvRef=\"MS\" accession=\"MS:1000514\" name=\"m/z array\"/>\n"
				+"            <cvParam cvRef=\"MS\" accession=\"MS:1000523\" name=\"64-bit float\"/>\n"+"            <cvParam cvRef=\"MS\" accession=\""
				+compAcc+"\"/>\n"+"            <binary>"+mzBin+"</binary>\n"+"          </binaryDataArray>\n"+"          <binaryDataArray>\n"
				+"            <cvParam cvRef=\"MS\" accession=\"MS:1000515\" name=\"intensity array\"/>\n"
				+"            <cvParam cvRef=\"MS\" accession=\"MS:1000521\" name=\"32-bit float\"/>\n"+"            <cvParam cvRef=\"MS\" accession=\""
				+compAcc+"\"/>\n"+"            <binary>"+intBin+"</binary>\n"+"          </binaryDataArray>\n"+"        </binaryDataArrayList>\n"
				+"      </spectrum>\n";
	}

	private static String ms2Spectrum(int index, float rtSeconds, double isoTarget, double isoOffset, double precMz, byte charge, double[] mz,
			float[] intensity, boolean zlib) {
		String mzBin=zlib?encode64DoubleZlib(mz):encode64Double(mz);
		String intBin=zlib?encode32FloatZlib(intensity):encode32Float(intensity);
		String compAcc=zlib?"MS:1000574":"MS:1000576";
		int n=Math.min(mz.length, intensity.length);
		return "      <spectrum id=\"scan="+index+"\" index=\""+index+"\" defaultArrayLength=\""+n+"\">\n"
				+"        <cvParam cvRef=\"MS\" accession=\"MS:1000511\" name=\"ms level\" value=\"2\"/>\n"
				+"        <cvParam cvRef=\"MS\" accession=\"MS:1000285\" name=\"total ion current\" value=\"0\"/>\n"+"        <scanList count=\"1\">\n"
				+"          <scan>\n"+"            <cvParam cvRef=\"MS\" accession=\"MS:1000016\" name=\"scan start time\" value=\""+rtSeconds
				+"\" unitCvRef=\"UO\" unitAccession=\"UO:0000010\" unitName=\"second\"/>\n"+"          </scan>\n"+"        </scanList>\n"
				+"        <precursorList count=\"1\">\n"+"          <precursor>\n"+"            <isolationWindow>\n"
				+"              <cvParam cvRef=\"MS\" accession=\"MS:1000827\" value=\""+isoTarget+"\"/>\n"
				+"              <cvParam cvRef=\"MS\" accession=\"MS:1000828\" value=\""+isoOffset+"\"/>\n"
				+"              <cvParam cvRef=\"MS\" accession=\"MS:1000829\" value=\""+isoOffset+"\"/>\n"+"            </isolationWindow>\n"
				+"            <selectedIonList count=\"1\">\n"+"              <selectedIon>\n"
				+"                <cvParam cvRef=\"MS\" accession=\"MS:1000744\" value=\""+precMz+"\"/>\n"
				+"                <cvParam cvRef=\"MS\" accession=\"MS:1000041\" value=\""+charge+"\"/>\n"+"              </selectedIon>\n"
				+"            </selectedIonList>\n"+"          </precursor>\n"+"        </precursorList>\n"+"        <binaryDataArrayList count=\"2\">\n"
				+"          <binaryDataArray>\n"+"            <cvParam cvRef=\"MS\" accession=\"MS:1000514\" name=\"m/z array\"/>\n"
				+"            <cvParam cvRef=\"MS\" accession=\"MS:1000523\" name=\"64-bit float\"/>\n"+"            <cvParam cvRef=\"MS\" accession=\""
				+compAcc+"\"/>\n"+"            <binary>"+mzBin+"</binary>\n"+"          </binaryDataArray>\n"+"          <binaryDataArray>\n"
				+"            <cvParam cvRef=\"MS\" accession=\"MS:1000515\" name=\"intensity array\"/>\n"
				+"            <cvParam cvRef=\"MS\" accession=\"MS:1000521\" name=\"32-bit float\"/>\n"+"            <cvParam cvRef=\"MS\" accession=\""
				+compAcc+"\"/>\n"+"            <binary>"+intBin+"</binary>\n"+"          </binaryDataArray>\n"+"        </binaryDataArrayList>\n"
				+"      </spectrum>\n";
	}

	private File writeMzml(String content) throws Exception {
		File f=tmp.resolve("test.mzML").toFile();
		Files.writeString(f.toPath(), content);
		return f;
	}

	// ---- Tests ----

	@Test
	void parsesRoundTripMetadataUserParams() throws Exception {
		String xml="<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"+"<mzML xmlns=\"http://psi.hupo.org/ms/mzml\" version=\"1.1.1\">\n"
				+"  <cvList count=\"1\"><cv id=\"MS\" fullName=\"PSI-MS\" version=\"4.1.136\" URI=\"\"/></cvList>\n"+"  <fileDescription>\n"
				+"    <fileContent><cvParam cvRef=\"MS\" accession=\"MS:1000579\" value=\"\"/></fileContent>\n"+"    <sourceFileList count=\"1\">\n"
				+"      <sourceFile id=\"SRC1\" name=\"x\" location=\"file:///x\">\n"
				+"        <userParam name=\"msrawjava.metadata.totalPrecursorTIC\" value=\"1852649200000\"/>\n"
				+"        <userParam name=\"msrawjava.metadata.gradientLength\" value=\"1234.5\"/>\n"+"      </sourceFile>\n"+"    </sourceFileList>\n"
				+"  </fileDescription>\n"+"  <run id=\"run1\">\n"+"    <spectrumList count=\"0\" defaultDataProcessingRef=\"dp\"/>\n"+"  </run>\n"+"</mzML>\n";

		MzmlFile reader=new MzmlFile();
		reader.openFile(writeMzml(xml));
		Map<String, String> meta=reader.getMetadata();
		assertEquals("1852649200000", meta.get("totalPrecursorTIC"));
		assertEquals("1234.5", meta.get("gradientLength"));
		reader.close();
	}

	@Test
	void parsesMS1WithUncompressedBinaryArrays() throws Exception {
		double[] mz= {100.0, 200.0, 300.0};
		float[] inten= {10.0f, 20.0f, 30.0f};
		String xml=mzmlHeader()+ms1Spectrum(0, 60.0f, 100.0, 1200.0, mz, inten, 60.0f, false)+mzmlFooter();

		MzmlFile reader=new MzmlFile();
		reader.openFile(writeMzml(xml));
		assertTrue(reader.isOpen());

		ArrayList<PrecursorScan> precursors=reader.getPrecursors(0, 120);
		assertEquals(1, precursors.size());

		PrecursorScan scan=precursors.get(0);
		assertEquals(60.0f, scan.getScanStartTime(), 0.01f);
		assertEquals(3, scan.getMassArray().length);
		assertEquals(100.0, scan.getMassArray()[0], 1e-6);
		assertEquals(200.0, scan.getMassArray()[1], 1e-6);
		assertEquals(300.0, scan.getMassArray()[2], 1e-6);
		assertEquals(10.0f, scan.getIntensityArray()[0], 1e-4f);
		assertEquals(20.0f, scan.getIntensityArray()[1], 1e-4f);
		assertEquals(30.0f, scan.getIntensityArray()[2], 1e-4f);

		reader.close();
	}

	@Test
	void parsesMS1WithZlibCompression() throws Exception {
		double[] mz= {150.0, 250.0, 350.0};
		float[] inten= {5.0f, 15.0f, 25.0f};
		String xml=mzmlHeader()+ms1Spectrum(0, 30.0f, 100.0, 1200.0, mz, inten, 45.0f, true)+mzmlFooter();

		MzmlFile reader=new MzmlFile();
		reader.openFile(writeMzml(xml));

		ArrayList<PrecursorScan> precursors=reader.getPrecursors(0, 60);
		assertEquals(1, precursors.size());

		PrecursorScan scan=precursors.get(0);
		assertEquals(3, scan.getMassArray().length);
		assertEquals(150.0, scan.getMassArray()[0], 1e-6);
		assertEquals(250.0, scan.getMassArray()[1], 1e-6);
		assertEquals(350.0, scan.getMassArray()[2], 1e-6);
		assertEquals(5.0f, scan.getIntensityArray()[0], 1e-4f);

		reader.close();
	}

	@Test
	void parsesMS2SpectrumWithIsolationWindow() throws Exception {
		double[] mz= {200.0, 400.0};
		float[] inten= {100.0f, 200.0f};
		// isolation: target=500, offset=12.5 -> window 487.5-512.5
		String xml=mzmlHeader()+ms2Spectrum(0, 45.0f, 500.0, 12.5, 500.0, (byte)2, mz, inten, false)+mzmlFooter();

		MzmlFile reader=new MzmlFile();
		reader.openFile(writeMzml(xml));

		ArrayList<FragmentScan> stripes=reader.getStripes(500.0, 0, 90, false);
		assertEquals(1, stripes.size());

		FragmentScan scan=stripes.get(0);
		assertEquals(45.0f, scan.getScanStartTime(), 0.01f);
		assertEquals(487.5, scan.getIsolationWindowLower(), 0.01);
		assertEquals(512.5, scan.getIsolationWindowUpper(), 0.01);
		assertEquals(2, scan.getCharge());
		assertEquals(2, scan.getMassArray().length);

		reader.close();
	}

	@Test
	void rtFilteringWorksPrecursors() throws Exception {
		double[] mz= {100.0};
		float[] inten= {10.0f};
		String xml=mzmlHeader()+ms1Spectrum(0, 10.0f, 100.0, 1200.0, mz, inten, 10.0f, false)
				+ms1Spectrum(1, 30.0f, 100.0, 1200.0, mz, inten, 10.0f, false)+ms1Spectrum(2, 50.0f, 100.0, 1200.0, mz, inten, 10.0f, false)+mzmlFooter();

		MzmlFile reader=new MzmlFile();
		reader.openFile(writeMzml(xml));

		assertEquals(3, reader.getPrecursors(0, 60).size());
		assertEquals(2, reader.getPrecursors(5, 35).size());
		assertEquals(1, reader.getPrecursors(25, 35).size());
		assertEquals(0, reader.getPrecursors(100, 200).size());

		reader.close();
	}

	@Test
	void rtFilteringWorksStripes() throws Exception {
		double[] mz= {200.0};
		float[] inten= {50.0f};
		String xml=mzmlHeader()+ms2Spectrum(0, 10.0f, 500.0, 12.5, 500.0, (byte)2, mz, inten, false)
				+ms2Spectrum(1, 30.0f, 500.0, 12.5, 500.0, (byte)2, mz, inten, false)
				+ms2Spectrum(2, 50.0f, 500.0, 12.5, 500.0, (byte)2, mz, inten, false)+mzmlFooter();

		MzmlFile reader=new MzmlFile();
		reader.openFile(writeMzml(xml));

		assertEquals(3, reader.getStripes(500.0, 0, 60, false).size());
		assertEquals(1, reader.getStripes(500.0, 25, 35, false).size());
		assertEquals(0, reader.getStripes(500.0, 100, 200, false).size());

		reader.close();
	}

	@Test
	void diaRangesComputedFromMS2Windows() throws Exception {
		double[] mz= {200.0};
		float[] inten= {10.0f};
		// Two different isolation windows
		String xml=mzmlHeader()+ms2Spectrum(0, 10.0f, 500.0, 12.5, 500.0, (byte)2, mz, inten, false)
				+ms2Spectrum(1, 12.0f, 500.0, 12.5, 500.0, (byte)2, mz, inten, false)
				+ms2Spectrum(2, 14.0f, 600.0, 10.0, 600.0, (byte)2, mz, inten, false)+mzmlFooter();

		MzmlFile reader=new MzmlFile();
		reader.openFile(writeMzml(xml));

		Map<Range, WindowData> ranges=reader.getRanges();
		assertEquals(2, ranges.size());

		Range r1=new Range(487.5, 512.5);
		assertTrue(ranges.containsKey(r1));
		assertEquals(2, ranges.get(r1).getNumberOfMSMS());

		Range r2=new Range(590.0, 610.0);
		assertTrue(ranges.containsKey(r2));
		assertEquals(1, ranges.get(r2).getNumberOfMSMS());

		reader.close();
	}

	@Test
	void getScanSummariesReturnsBothMS1AndMS2() throws Exception {
		double[] mz= {200.0};
		float[] inten= {10.0f};
		String xml=mzmlHeader()+ms1Spectrum(0, 10.0f, 100.0, 1200.0, mz, inten, 10.0f, false)
				+ms2Spectrum(1, 12.0f, 500.0, 12.5, 500.0, (byte)2, mz, inten, false)+mzmlFooter();

		MzmlFile reader=new MzmlFile();
		reader.openFile(writeMzml(xml));

		ArrayList<ScanSummary> summaries=reader.getScanSummaries(0, 60);
		assertEquals(2, summaries.size());
		assertTrue(summaries.get(0).isPrecursor());
		assertFalse(summaries.get(1).isPrecursor());

		reader.close();
	}

	@Test
	void ticTraceAndTotalFromMS1() throws Exception {
		double[] mz= {100.0};
		float[] inten= {10.0f};
		String xml=mzmlHeader()+ms1Spectrum(0, 10.0f, 100.0, 1200.0, mz, inten, 100.0f, false)
				+ms1Spectrum(1, 20.0f, 100.0, 1200.0, mz, inten, 200.0f, false)+ms1Spectrum(2, 30.0f, 100.0, 1200.0, mz, inten, 150.0f, false)+mzmlFooter();

		MzmlFile reader=new MzmlFile();
		reader.openFile(writeMzml(xml));

		assertEquals(450.0f, reader.getTIC(), 0.01f);

		Pair<float[], float[]> trace=reader.getTICTrace();
		assertEquals(3, trace.x.length);
		assertEquals(3, trace.y.length);
		assertEquals(10.0f, trace.x[0], 0.01f);
		assertEquals(100.0f, trace.y[0], 0.01f);
		assertEquals(200.0f, trace.y[1], 0.01f);

		reader.close();
	}

	@Test
	void gradientLengthFromAllSpectra() throws Exception {
		double[] mz= {100.0};
		float[] inten= {10.0f};
		String xml=mzmlHeader()+ms1Spectrum(0, 10.0f, 100.0, 1200.0, mz, inten, 50.0f, false)
				+ms2Spectrum(1, 40.0f, 500.0, 12.5, 500.0, (byte)2, mz, inten, false)
				+ms1Spectrum(2, 70.0f, 100.0, 1200.0, mz, inten, 50.0f, false)+mzmlFooter();

		MzmlFile reader=new MzmlFile();
		reader.openFile(writeMzml(xml));

		assertEquals(60.0f, reader.getGradientLength(), 0.01f);

		reader.close();
	}

	@Test
	void emptyFileHandled() throws Exception {
		String xml=mzmlHeader()+mzmlFooter();

		MzmlFile reader=new MzmlFile();
		reader.openFile(writeMzml(xml));

		assertEquals(0, reader.getPrecursors(0, 100).size());
		assertEquals(0, reader.getStripes(500.0, 0, 100, false).size());
		assertEquals(0, reader.getScanSummaries(0, 100).size());
		assertEquals(0.0f, reader.getTIC(), 0.01f);
		assertEquals(0.0f, reader.getGradientLength(), 0.01f);
		assertTrue(reader.getRanges().isEmpty());

		reader.close();
	}

	@Test
	void scanTimeInMinutesConverted() throws Exception {
		double[] mz= {100.0};
		float[] inten= {10.0f};
		// Use minutes unit (UO:0000031)
		String xml=mzmlHeader()+"      <spectrum id=\"scan=0\" index=\"0\" defaultArrayLength=\"1\">\n"
				+"        <cvParam cvRef=\"MS\" accession=\"MS:1000511\" name=\"ms level\" value=\"1\"/>\n"
				+"        <cvParam cvRef=\"MS\" accession=\"MS:1000285\" name=\"total ion current\" value=\"10\"/>\n"+"        <scanList count=\"1\">\n"
				+"          <scan>\n"
				+"            <cvParam cvRef=\"MS\" accession=\"MS:1000016\" name=\"scan start time\" value=\"1.5\" unitCvRef=\"UO\" unitAccession=\"UO:0000031\" unitName=\"minute\"/>\n"
				+"          </scan>\n"+"        </scanList>\n"+"        <binaryDataArrayList count=\"2\">\n"+"          <binaryDataArray>\n"
				+"            <cvParam cvRef=\"MS\" accession=\"MS:1000514\" name=\"m/z array\"/>\n"
				+"            <cvParam cvRef=\"MS\" accession=\"MS:1000523\" name=\"64-bit float\"/>\n"
				+"            <cvParam cvRef=\"MS\" accession=\"MS:1000576\"/>\n"+"            <binary>"+encode64Double(mz)+"</binary>\n"
				+"          </binaryDataArray>\n"+"          <binaryDataArray>\n"
				+"            <cvParam cvRef=\"MS\" accession=\"MS:1000515\" name=\"intensity array\"/>\n"
				+"            <cvParam cvRef=\"MS\" accession=\"MS:1000521\" name=\"32-bit float\"/>\n"
				+"            <cvParam cvRef=\"MS\" accession=\"MS:1000576\"/>\n"+"            <binary>"+encode32Float(inten)+"</binary>\n"
				+"          </binaryDataArray>\n"+"        </binaryDataArrayList>\n"+"      </spectrum>\n"+mzmlFooter();

		MzmlFile reader=new MzmlFile();
		reader.openFile(writeMzml(xml));

		ArrayList<PrecursorScan> precursors=reader.getPrecursors(0, 120);
		assertEquals(1, precursors.size());
		// 1.5 minutes = 90 seconds
		assertEquals(90.0f, precursors.get(0).getScanStartTime(), 0.01f);

		reader.close();
	}

	@Test
	void getSpectrumReturnsCorrectScan() throws Exception {
		double[] mz1= {100.0};
		float[] inten1= {10.0f};
		double[] mz2= {200.0};
		float[] inten2= {20.0f};
		String xml=mzmlHeader()+ms1Spectrum(0, 10.0f, 100.0, 1200.0, mz1, inten1, 10.0f, false)
				+ms1Spectrum(1, 20.0f, 100.0, 1200.0, mz2, inten2, 20.0f, false)+mzmlFooter();

		MzmlFile reader=new MzmlFile();
		reader.openFile(writeMzml(xml));

		ArrayList<ScanSummary> summaries=reader.getScanSummaries(0, 30);
		assertEquals(2, summaries.size());

		var spectrum=reader.getSpectrum(summaries.get(1));
		assertNotNull(spectrum);
		assertEquals(200.0, spectrum.getMassArray()[0], 1e-6);

		reader.close();
	}

	@Test
	void getStripesRangeOverload() throws Exception {
		double[] mz= {300.0};
		float[] inten= {50.0f};
		String xml=mzmlHeader()+ms2Spectrum(0, 10.0f, 500.0, 12.5, 500.0, (byte)2, mz, inten, false)
				+ms2Spectrum(1, 20.0f, 600.0, 10.0, 600.0, (byte)2, mz, inten, false)+mzmlFooter();

		MzmlFile reader=new MzmlFile();
		reader.openFile(writeMzml(xml));

		// Range that overlaps both windows
		Range wide=new Range(480.0, 620.0);
		assertEquals(2, reader.getStripes(wide, 0, 30, false).size());

		// Range that only overlaps the first
		Range narrow=new Range(490.0, 515.0);
		assertEquals(1, reader.getStripes(narrow, 0, 30, false).size());

		reader.close();
	}
}
