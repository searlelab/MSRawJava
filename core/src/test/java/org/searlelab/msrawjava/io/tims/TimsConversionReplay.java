package org.searlelab.msrawjava.io.tims;

import java.io.DataOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.searlelab.msrawjava.io.ConversionParameters;
import org.searlelab.msrawjava.io.OutputType;
import org.searlelab.msrawjava.io.RawFileConverters;
import org.searlelab.msrawjava.io.encyclopedia.EncyclopeDIAFile;
import org.searlelab.msrawjava.logging.LoggingProgressIndicator;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.WindowData;
import org.searlelab.msrawjava.threading.ProcessingThreadPool;

/** Explicit native or retained DIA demux replay: fresh destination, all ordered MS1/MS2 bits and metadata. */
public class TimsConversionReplay {
	public static void main(String[] args) throws Exception {
		System.err.println("CONVERTER_ORIGIN="+RawFileConverters.class.getProtectionDomain().getCodeSource().getLocation());
		System.err.println("PICKER_ORIGIN="+TIMSPeakPicker.class.getProtectionDomain().getCodeSource().getLocation());
		System.err.println("NATIVE_RESOURCE_ORIGIN="+TimsNative.class.getResource("/META-INF/lib/osx-aarch64/libtimsrust_jni.dylib"));
		Path source=Path.of(args.length>1?args[1]:"src/test/resources/rawdata/230711_idleflow_400-1000mz_25mz_diaPasef_10sec.d");
		Path destination=Path.of(args[0]);
		Files.createDirectory(destination); // refuse an existing destination or cache
		long started=System.nanoTime();
		try (ProcessingThreadPool pool=new ProcessingThreadPool(2, 8)) {
			boolean demux=source.toString().toLowerCase(Locale.ROOT).endsWith(".dia");
			ConversionParameters.Builder builder=ConversionParameters.builder().outType(OutputType.EncyclopeDIA).minimumMS1Intensity(3).minimumMS2Intensity(1);
			if (demux) builder.demultiplex(true);
			ConversionParameters parameters=builder.build();
			LoggingProgressIndicator progress=new LoggingProgressIndicator(LoggingProgressIndicator.Mode.SILENT, false);
			boolean complete;
			if (demux) {
				EncyclopeDIAFile input=new EncyclopeDIAFile();
				try {
					input.openFile(source.toFile());
					complete=RawFileConverters.writeDemux(pool, input, destination, parameters, progress);
				} finally {
					input.close();
				}
			} else {
				complete=RawFileConverters.writeTims(pool, source, destination, parameters, progress);
			}
			if (!complete) {
				throw new IllegalStateException("Conversion cancelled");
			}
		}
		long elapsed=System.nanoTime()-started;
		Path output;
		try (var files=Files.list(destination)) { output=files.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".dia")).findFirst().orElseThrow(); }
		MessageDigest digest=MessageDigest.getInstance("SHA-256");
		EncyclopeDIAFile reader=new EncyclopeDIAFile();
		int precursors, fragments;
		try (DataOutputStream out=new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
			reader.openFile(output.toFile());
			ArrayList<PrecursorScan> ms1=reader.getPrecursors(0, Float.MAX_VALUE);
			ArrayList<FragmentScan> ms2=reader.getStripes(new Range(0, Float.MAX_VALUE), 0, Float.MAX_VALUE, false);
			precursors=ms1.size(); fragments=ms2.size();
			out.writeInt(precursors); for (PrecursorScan scan : ms1) PeakPickerReplay.writePrecursor(out, scan);
			out.writeInt(fragments); for (FragmentScan scan : ms2) PeakPickerReplay.writeScan(out, scan);
			for (Map.Entry<String, String> entry : new TreeMap<>(reader.getMetadata()).entrySet()) {
				out.writeUTF(entry.getKey()); out.writeUTF(entry.getValue());
			}
			ArrayList<Range> windows=new ArrayList<>(reader.getRanges().keySet()); windows.sort(null);
			out.writeInt(windows.size());
			for (Range window : windows) {
				out.writeInt(Float.floatToRawIntBits(window.getStart())); out.writeInt(Float.floatToRawIntBits(window.getStop()));
				WindowData data=reader.getRanges().get(window);
				out.writeInt(Float.floatToRawIntBits(data.getAverageDutyCycle())); out.writeInt(data.getNumberOfMSMS());
				for (Optional<Range> range : List.of(data.getIonMobilityRange(), data.getRtRange())) {
					out.writeBoolean(range.isPresent());
					if (range.isPresent()) { out.writeInt(Float.floatToRawIntBits(range.get().getStart())); out.writeInt(Float.floatToRawIntBits(range.get().getStop())); }
				}
			}
		} finally {
			reader.close();
		}
		System.out.printf(Locale.ROOT,"{\"conversion_ns\":%d,\"ms1\":%d,\"ms2\":%d,\"sha256\":\"%s\"}%n",elapsed,precursors,fragments,PeakPickerReplay.hex(digest.digest()));
	}
}
