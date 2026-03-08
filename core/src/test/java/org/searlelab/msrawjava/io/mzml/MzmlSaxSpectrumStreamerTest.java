package org.searlelab.msrawjava.io.mzml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.Deflater;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;

class MzmlSaxSpectrumStreamerTest {

	@TempDir
	Path tmp;

	@Test
	void streamDecodesMixedPrecisionsCompressionAndSequentialFallback() throws Exception {
		double[] mz1= {100.0, 101.0};
		double[] intAsDouble= {10.0, 20.0};
		double[] mz2= {500.0, 501.0};
		float[] int2= {30.0f, 40.0f};

		String spectrum0="      <spectrum id=\"s0\" index=\"0\" defaultArrayLength=\"2\">\n"
				+"        <binaryDataArrayList count=\"2\">\n"
				+"          <binaryDataArray>\n"
				+"            <cvParam accession=\"MS:1000514\"/>\n"
				+"            <cvParam accession=\"MS:1000521\"/>\n"
				+"            <cvParam accession=\"MS:1000576\"/>\n"
				+"            <binary>"+encode32Float(new float[] {(float)mz1[0], (float)mz1[1]})+"</binary>\n"
				+"          </binaryDataArray>\n"
				+"          <binaryDataArray>\n"
				+"            <cvParam accession=\"MS:1000515\"/>\n"
				+"            <cvParam accession=\"MS:1000523\"/>\n"
				+"            <cvParam accession=\"MS:1000576\"/>\n"
				+"            <binary>"+encode64Double(intAsDouble)+"</binary>\n"
				+"          </binaryDataArray>\n"
				+"        </binaryDataArrayList>\n"
				+"      </spectrum>\n";

		String spectrum1="      <spectrum id=\"s1\" defaultArrayLength=\"2\">\n"
				+"        <binaryDataArrayList count=\"2\">\n"
				+"          <binaryDataArray>\n"
				+"            <cvParam accession=\"MS:1000514\"/>\n"
				+"            <cvParam accession=\"MS:1000523\"/>\n"
				+"            <cvParam accession=\"MS:1000574\"/>\n"
				+"            <binary>\n              "+encode64DoubleZlib(mz2)+"\n            </binary>\n"
				+"          </binaryDataArray>\n"
				+"          <binaryDataArray>\n"
				+"            <cvParam accession=\"MS:1000515\"/>\n"
				+"            <cvParam accession=\"MS:1000521\"/>\n"
				+"            <cvParam accession=\"MS:1000574\"/>\n"
				+"            <cvParam accession=\"MS:9999999\"/>\n"
				+"            <binary>\n              "+encode32FloatZlib(int2)+"\n            </binary>\n"
				+"          </binaryDataArray>\n"
				+"        </binaryDataArrayList>\n"
				+"      </spectrum>\n";

		File file=writeMzml(spectrum0+spectrum1);

		MzmlFile.MzmlScanEntry ms1=new MzmlFile.MzmlScanEntry();
		ms1.index=0;
		ms1.spectrumId="s0";
		ms1.msLevel=1;
		ms1.scanStartTime=11.0f;

		MzmlFile.MzmlScanEntry ms2=new MzmlFile.MzmlScanEntry();
		ms2.index=1; // no index attribute in spectrum1, sequential fallback should map to 1
		ms2.spectrumId="s1";
		ms2.msLevel=2;
		ms2.scanStartTime=12.0f;
		ms2.isolationTarget=500.0;
		ms2.isolationLowerOffset=5.0;
		ms2.isolationUpperOffset=5.0;
		ms2.charge=3;
		ms2.precursorMz=0.0;

		ArrayList<MzmlFile.MzmlScanEntry> index=new ArrayList<>();
		index.add(ms1);
		index.add(ms2);

		List<PrecursorScan> precursors=new ArrayList<>();
		List<FragmentScan> fragments=new ArrayList<>();

		new MzmlSaxSpectrumStreamer(file, index, (precursor, fragment) -> {
			if (precursor!=null) precursors.add(precursor);
			if (fragment!=null) fragments.add(fragment);
		}).stream();

		assertEquals(1, precursors.size());
		assertEquals(1, fragments.size());
		assertEquals(100.0, precursors.get(0).getMassArray()[0], 1e-6);
		assertEquals(10.0f, precursors.get(0).getIntensityArray()[0], 1e-5f);
		assertEquals(100.0, precursors.get(0).getScanWindowLower(), 1e-6);
		assertEquals(101.0, precursors.get(0).getScanWindowUpper(), 1e-6);

		assertEquals(1, fragments.get(0).getSpectrumIndex());
		assertEquals(500.0, fragments.get(0).getPrecursorMZ(), 1e-6); // derived from isolation window center
		assertEquals(495.0, fragments.get(0).getIsolationWindowLower(), 1e-6);
		assertEquals(505.0, fragments.get(0).getIsolationWindowUpper(), 1e-6);
		assertEquals(30.0f, fragments.get(0).getIntensityArray()[0], 1e-4f);
	}

	@Test
	void streamSkipsUnknownIndexAndIgnoresWhitespaceOnlyBinary() throws Exception {
		String xmlSpectrum="      <spectrum index=\"5\" id=\"unknown\" defaultArrayLength=\"0\">\n"
				+"        <binaryDataArrayList count=\"1\">\n"
				+"          <binaryDataArray>\n"
				+"            <cvParam accession=\"MS:1000514\"/>\n"
				+"            <cvParam accession=\"MS:1000523\"/>\n"
				+"            <cvParam accession=\"MS:1000576\"/>\n"
				+"            <binary>   \n\t  </binary>\n"
				+"          </binaryDataArray>\n"
				+"        </binaryDataArrayList>\n"
				+"      </spectrum>\n";
		File file=writeMzml(xmlSpectrum);

		ArrayList<MzmlFile.MzmlScanEntry> index=new ArrayList<>();
		int[] calls= {0};
		new MzmlSaxSpectrumStreamer(file, index, (p, f) -> calls[0]++).stream();
		assertEquals(0, calls[0]);
	}

	@Test
	void streamPropagatesConsumerIOExceptionDirectly() throws Exception {
		File file=writeMzml(singleSimpleSpectrum(0, encode64Double(new double[] {100.0}), encode32Float(new float[] {10.0f})));
		ArrayList<MzmlFile.MzmlScanEntry> index=new ArrayList<>();
		MzmlFile.MzmlScanEntry ms1=new MzmlFile.MzmlScanEntry();
		ms1.index=0;
		ms1.spectrumId="s0";
		ms1.msLevel=1;
		index.add(ms1);

		IOException ex=assertThrows(IOException.class, () -> new MzmlSaxSpectrumStreamer(file, index, (p, f) -> {
			throw new IOException("consumer-io");
		}).stream());
		assertTrue(ex.getMessage().contains("consumer-io"));
	}

	@Test
	void streamWrapsConsumerCheckedException() throws Exception {
		File file=writeMzml(singleSimpleSpectrum(0, encode64Double(new double[] {100.0}), encode32Float(new float[] {10.0f})));
		ArrayList<MzmlFile.MzmlScanEntry> index=new ArrayList<>();
		MzmlFile.MzmlScanEntry ms1=new MzmlFile.MzmlScanEntry();
		ms1.index=0;
		ms1.spectrumId="s0";
		ms1.msLevel=1;
		index.add(ms1);

		IOException ex=assertThrows(IOException.class, () -> new MzmlSaxSpectrumStreamer(file, index, (p, f) -> {
			throw new Exception("consumer-checked");
		}).stream());
		assertTrue(ex.getMessage().contains("Error streaming mzML spectra"));
	}

	@Test
	void streamWrapsBinaryDecodeErrors() throws Exception {
		String spectrum="      <spectrum id=\"s0\" index=\"0\" defaultArrayLength=\"1\">\n"
				+"        <binaryDataArrayList count=\"1\">\n"
				+"          <binaryDataArray>\n"
				+"            <cvParam accession=\"MS:1000514\"/>\n"
				+"            <cvParam accession=\"MS:1000523\"/>\n"
				+"            <cvParam accession=\"MS:1000576\"/>\n"
				+"            <binary>!!!not-base64!!!</binary>\n"
				+"          </binaryDataArray>\n"
				+"        </binaryDataArrayList>\n"
				+"      </spectrum>\n";
		File file=writeMzml(spectrum);

		ArrayList<MzmlFile.MzmlScanEntry> index=new ArrayList<>();
		MzmlFile.MzmlScanEntry ms1=new MzmlFile.MzmlScanEntry();
		ms1.index=0;
		ms1.spectrumId="s0";
		ms1.msLevel=1;
		index.add(ms1);

		IOException ex=assertThrows(IOException.class, () -> new MzmlSaxSpectrumStreamer(file, index, (p, f) -> {}).stream());
		assertTrue(ex.getMessage().contains("Error streaming mzML spectra"));
	}

	@Test
	void privateHelpersHandleFallbackNamesAndWhitespace() throws Exception {
		Method elementName=MzmlSaxSpectrumStreamer.class.getDeclaredMethod("elementName", String.class, String.class);
		elementName.setAccessible(true);
		assertEquals("local", elementName.invoke(null, "local", "qName"));
		assertEquals("qName", elementName.invoke(null, "", "qName"));

		Method compactBase64=MzmlSaxSpectrumStreamer.class.getDeclaredMethod("compactBase64", CharSequence.class);
		compactBase64.setAccessible(true);
		assertEquals("AABB", compactBase64.invoke(null, "AA BB\n"));
		assertEquals("AABB", compactBase64.invoke(null, "AABB"));
	}

	private File writeMzml(String spectraBody) throws Exception {
		String xml="<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
				+"<mzML xmlns=\"http://psi.hupo.org/ms/mzml\" version=\"1.1.1\">\n"
				+"  <cvList count=\"1\"><cv id=\"MS\" fullName=\"PSI-MS\" version=\"4.1.136\" URI=\"\"/></cvList>\n"
				+"  <run id=\"run1\">\n"
				+"    <spectrumList count=\"2\" defaultDataProcessingRef=\"dp\">\n"
				+spectraBody
				+"    </spectrumList>\n"
				+"  </run>\n"
				+"</mzML>\n";
		File out=tmp.resolve("streamer.mzML").toFile();
		Files.writeString(out.toPath(), xml);
		return out;
	}

	private static String singleSimpleSpectrum(int index, String mzBinary, String intensityBinary) {
		return "      <spectrum id=\"s"+index+"\" index=\""+index+"\" defaultArrayLength=\"1\">\n"
				+"        <binaryDataArrayList count=\"2\">\n"
				+"          <binaryDataArray>\n"
				+"            <cvParam accession=\"MS:1000514\"/>\n"
				+"            <cvParam accession=\"MS:1000523\"/>\n"
				+"            <cvParam accession=\"MS:1000576\"/>\n"
				+"            <binary>"+mzBinary+"</binary>\n"
				+"          </binaryDataArray>\n"
				+"          <binaryDataArray>\n"
				+"            <cvParam accession=\"MS:1000515\"/>\n"
				+"            <cvParam accession=\"MS:1000521\"/>\n"
				+"            <cvParam accession=\"MS:1000576\"/>\n"
				+"            <binary>"+intensityBinary+"</binary>\n"
				+"          </binaryDataArray>\n"
				+"        </binaryDataArrayList>\n"
				+"      </spectrum>\n";
	}

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
		byte[] out=new byte[data.length*2+64];
		int len=deflater.deflate(out);
		deflater.end();
		byte[] trimmed=new byte[len];
		System.arraycopy(out, 0, trimmed, 0, len);
		return Base64.getEncoder().encodeToString(trimmed);
	}
}
