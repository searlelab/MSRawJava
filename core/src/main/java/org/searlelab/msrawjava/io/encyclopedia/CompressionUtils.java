package org.searlelab.msrawjava.io.encyclopedia;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;

/**
 * CompressionUtils offers lightweight helpers for compressing and decompressing binary blocks used by on-disk artifacts
 * that benefit from reduced size (e.g., stored spectra or auxiliary blobs). It abstracts the codec and buffering
 * details so writer code can opt into compression while preserving streaming behavior and reproducible output.
 */
public class CompressionUtils {
	public static byte[] compress(byte[] decompressedData) throws IOException {
		Deflater deflater=new Deflater();
		deflater.setInput(decompressedData);

		ByteArrayOutputStream outputStream=new ByteArrayOutputStream(decompressedData.length);

		deflater.finish();
		byte[] buffer=new byte[1024];
		while (!deflater.finished()) {
			int count=deflater.deflate(buffer);
			outputStream.write(buffer, 0, count);
		}
		outputStream.close();
		byte[] compressedData=outputStream.toByteArray();

		deflater.end();

		return compressedData;
	}

	public static byte[] decompress(byte[] compressedData) throws IOException {
		Inflater decompressor=new Inflater();
		decompressor.setInput(compressedData);
		// Create expandable byte array
		ByteArrayOutputStream outputStream=new ByteArrayOutputStream(compressedData.length);
		byte[] buf=new byte[1024];
		while (!decompressor.finished()) {
			try {
				int count=decompressor.inflate(buf);
				outputStream.write(buf, 0, count);
			} catch (DataFormatException e) {
				throw new IllegalStateException("Formatting error when decompressing data!", e);
			}
		}
		outputStream.close();

		return outputStream.toByteArray();
	}

	public static byte[] decompress(byte[] compressedData, int uncompressedLength) throws IOException, DataFormatException {
		Inflater decompresser=new Inflater();
		decompresser.setInput(compressedData);
		byte[] decompressedData=new byte[uncompressedLength];
		decompresser.inflate(decompressedData);
		decompresser.end();
		return decompressedData;
	}

	public static byte[] decompressGzip(byte[] data, int uncompressedLength) throws IOException {
		ByteArrayInputStream bytein=new ByteArrayInputStream(data);
		GZIPInputStream gzin=new GZIPInputStream(bytein);
		ByteArrayOutputStream byteout=new ByteArrayOutputStream();

		int res=0;
		byte[] buf=new byte[1024];
		while (res>=0) {
			res=gzin.read(buf, 0, buf.length);
			if (res>0) {
				byteout.write(buf, 0, res);
			}
		}
		return byteout.toByteArray();
	}
}
