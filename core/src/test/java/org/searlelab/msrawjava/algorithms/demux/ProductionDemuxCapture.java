package org.searlelab.msrawjava.algorithms.demux;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPOutputStream;

import org.ejml.data.DMatrixRMaj;
import org.searlelab.msrawjava.algorithms.StaggeredDemultiplexer;
import org.searlelab.msrawjava.io.ConversionParameters;
import org.searlelab.msrawjava.io.OutputType;
import org.searlelab.msrawjava.io.RawFileConverters;
import org.searlelab.msrawjava.io.encyclopedia.EncyclopeDIAFile;
import org.searlelab.msrawjava.io.tims.PeakPickerReplay;
import org.searlelab.msrawjava.logging.LoggingProgressIndicator;
import org.searlelab.msrawjava.model.DemultiplexedFragmentScan;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.MassTolerance;
import org.searlelab.msrawjava.model.PPMMassTolerance;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.threading.ProcessingThreadPool;

/** Test-only recorder injected into an exported converter, never the production reader. */
public class ProductionDemuxCapture extends StaggeredDemultiplexer {
	static final Path ROOT=Path.of("src/test/resources/spectra");
	private final ArrayList<Range> windows;
	private final DemuxConfig config;
	private final ArrayList<DMatrixRMaj[]> systems=new ArrayList<>();
	private final ArrayList<double[][]> interpolations=new ArrayList<>();
	private boolean first=true;
	private boolean recording;

	public ProductionDemuxCapture(ArrayList<Range> windows, MassTolerance tolerance, DemuxConfig config) {
		super(windows, tolerance, config);
		this.windows=new ArrayList<>(windows);
		this.config=config;
		if (!(tolerance instanceof PPMMassTolerance)||config.getInterpolationMethod()!=DemuxConfig.InterpolationMethod.CUBIC_HERMITE) {
			throw new IllegalArgumentException("Capture recipe uses explicit 10 ppm and cubic interpolation");
		}
		try {
			Field nnls=StaggeredDemultiplexer.class.getDeclaredField("nnlsSolver");
			nnls.setAccessible(true);
			nnls.set(this, new NNLSSolver() {
				@Override
				public DMatrixRMaj solve(DMatrixRMaj a, DMatrixRMaj b) {
					boolean capture=recording&&systems.size()<256;
					DMatrixRMaj aa=capture?a.copy():null, bb=capture?b.copy():null;
					DMatrixRMaj x=super.solve(a, b);
					if (capture) systems.add(new DMatrixRMaj[] {aa, bb, x.copy()});
					return x;
				}
			});
			Field interpolation=StaggeredDemultiplexer.class.getDeclaredField("interpolator");
			interpolation.setAccessible(true);
			interpolation.set(this, new CubicHermiteInterpolator() {
				@Override
				public double interpolate(double[] times, double[] intensities, double target) {
					double value=super.interpolate(times, intensities, target);
					if (recording&&interpolations.size()<256) interpolations.add(new double[][] {times.clone(), intensities.clone(), {target, value}});
					return value;
				}
			});
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public synchronized ArrayList<DemultiplexedFragmentScan> demultiplex(ArrayList<FragmentScan> m2, ArrayList<FragmentScan> m1,
			ArrayList<FragmentScan> center, ArrayList<FragmentScan> p1, ArrayList<FragmentScan> p2, int number) {
		boolean capture=first;
		first=false;
		recording=capture;
		ArrayList<DemultiplexedFragmentScan> result=super.demultiplex(m2, m1, center, p1, p2, number);
		recording=false;
		if (capture) {
			try {
				try (DataOutputStream out=output(ROOT.resolve("demux-production-context.bin.gz"))) {
					out.writeInt(0x444d5831);
					out.writeInt(config.getK());
					out.writeUTF(config.getInterpolationMethod().name());
					out.writeBoolean(config.isIncludeEdgeSubWindows());
					out.writeDouble(10.0);
					out.writeInt(number);
					out.writeInt(windows.size());
					for (Range w : windows) { out.writeDouble(w.getStart()); out.writeDouble(w.getStop()); }
					for (ArrayList<FragmentScan> cycle : List.of(m2, m1, center, p1, p2)) {
						out.writeInt(cycle.size());
						for (FragmentScan scan : cycle) PeakPickerReplay.writeScan(out, scan);
					}
					out.writeUTF(spectrumDigest(result));
				}
				try (DataOutputStream out=output(ROOT.resolve("demux-production-systems.bin.gz"))) {
					out.writeInt(0x4e4e4c31);
					out.writeInt(systems.size());
					for (DMatrixRMaj[] system : systems) for (DMatrixRMaj matrix : system) writeMatrix(out, matrix);
					out.writeInt(interpolations.size());
					for (double[][] interpolation : interpolations) for (double[] row : interpolation) {
						out.writeInt(row.length);
						for (double x : row) out.writeLong(Double.doubleToRawLongBits(x));
					}
				}
				System.out.println("Captured production demux: windows="+windows.size()+", cycle sizes="+List.of(m2.size(),m1.size(),center.size(),p1.size(),p2.size())
						+", systems="+systems.size()+", interpolations="+interpolations.size()+", output="+spectrumDigest(result));
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		}
		return result;
	}

	private static DataOutputStream output(Path path) throws IOException {
		return new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(path)));
	}

	static void writeMatrix(DataOutput out, DMatrixRMaj matrix) throws IOException {
		out.writeInt(matrix.numRows); out.writeInt(matrix.numCols);
		for (int i=0; i<matrix.getNumElements(); i++) out.writeLong(Double.doubleToRawLongBits(matrix.data[i]));
	}

	static DMatrixRMaj readMatrix(DataInput in) throws IOException {
		DMatrixRMaj matrix=new DMatrixRMaj(in.readInt(), in.readInt());
		for (int i=0; i<matrix.getNumElements(); i++) matrix.data[i]=Double.longBitsToDouble(in.readLong());
		return matrix;
	}

	static String spectrumDigest(ArrayList<DemultiplexedFragmentScan> spectra) throws Exception {
		MessageDigest digest=MessageDigest.getInstance("SHA-256");
		try (DataOutputStream out=new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
			out.writeInt(spectra.size());
			for (DemultiplexedFragmentScan scan : spectra) {
				PeakPickerReplay.writeScan(out, scan);
				out.writeInt(scan.getOriginalSpectrumIndex());
				out.writeInt(scan.getDemuxCode());
			}
		}
		StringBuilder hex=new StringBuilder();
		for (byte b : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", b&255));
		return hex.toString();
	}

	public static void main(String[] args) throws Exception {
		// The exported RawFileConverters must instantiate this recorder. Its section reads and CycleAssembler are unchanged.
		EncyclopeDIAFile reader=new EncyclopeDIAFile();
		try (ProcessingThreadPool pool=new ProcessingThreadPool(1, 4)) {
			reader.openFile(Path.of("src/test/resources/rawdata/HeLa_16mzst_29to31min.dia").toFile());
			ConversionParameters params=ConversionParameters.builder().outType(OutputType.mgf).demultiplex(true)
					.demuxTolerance(new PPMMassTolerance(10.0)).build();
			RawFileConverters.writeDemux(pool, reader, Path.of(args[0]), params,
					new LoggingProgressIndicator(LoggingProgressIndicator.Mode.SILENT, false));
		} finally {
			reader.close();
		}
		if (!Files.exists(ROOT.resolve("demux-production-context.bin.gz"))) throw new IllegalStateException("Recorder was not injected");
	}
}
