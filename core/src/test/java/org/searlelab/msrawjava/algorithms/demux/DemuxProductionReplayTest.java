package org.searlelab.msrawjava.algorithms.demux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

import org.ejml.data.DMatrixRMaj;
import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.benchmarks.ReplayMemory;
import org.searlelab.msrawjava.algorithms.StaggeredDemultiplexer;
import org.searlelab.msrawjava.io.tims.PeakPickerReplay;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PPMMassTolerance;
import org.searlelab.msrawjava.model.Range;

public class DemuxProductionReplayTest {
	static ArrayList<DMatrixRMaj[]> systems() throws Exception {
		try (DataInputStream in=new DataInputStream(new GZIPInputStream(Files.newInputStream(ProductionDemuxCapture.ROOT.resolve("demux-production-systems.bin.gz"))))) {
			assertEquals(0x4e4e4c31, in.readInt());
			ArrayList<DMatrixRMaj[]> systems=new ArrayList<>();
			int count=in.readInt();
			for (int i=0; i<count; i++) systems.add(new DMatrixRMaj[] {ProductionDemuxCapture.readMatrix(in), ProductionDemuxCapture.readMatrix(in), ProductionDemuxCapture.readMatrix(in)});
			return systems;
		}
	}

	@Test
	void capturedNnlsSystemsMatchEveryRawBitAndLeaveInputsUnchanged() throws Exception {
		NNLSSolver solver=new NNLSSolver();
		ArrayList<DMatrixRMaj[]> systems=systems();
		assertFalse(systems.isEmpty());
		for (DMatrixRMaj[] system : systems) {
			DMatrixRMaj a=system[0].copy(), b=system[1].copy();
			assertBits(system[2], solver.solve(a, b));
			assertBits(system[0], a);
			assertBits(system[1], b);
		}
	}

	@Test
	void capturedInterpolationMatchesEveryRawBit() throws Exception {
		try (DataInputStream in=new DataInputStream(new GZIPInputStream(Files.newInputStream(ProductionDemuxCapture.ROOT.resolve("demux-production-systems.bin.gz"))))) {
			assertEquals(0x4e4e4c31, in.readInt());
			int systems=in.readInt();
			for (int i=0; i<systems*3; i++) ProductionDemuxCapture.readMatrix(in);
			int count=in.readInt();
			assertTrue(count>0);
			for (int i=0; i<count; i++) {
				double[][] rows=new double[3][];
				for (int j=0; j<3; j++) {
					rows[j]=new double[in.readInt()];
					for (int k=0; k<rows[j].length; k++) rows[j][k]=Double.longBitsToDouble(in.readLong());
				}
				assertEquals(Double.doubleToRawLongBits(rows[2][1]), Double.doubleToRawLongBits(new CubicHermiteInterpolator().interpolate(rows[0], rows[1], rows[2][0])));
			}
			assertEquals(-1, in.read());
		}
	}

	@Test
	void completeProductionContextMatchesOrderedSpectraAndMetadata() throws Exception {
		Context context=new Context();
		assertEquals(context.expected, context.replay());
		assertEquals(context.expected, context.replay());
	}

	private static void assertBits(DMatrixRMaj expected, DMatrixRMaj actual) {
		assertEquals(expected.numRows, actual.numRows);
		assertEquals(expected.numCols, actual.numCols);
		for (int i=0; i<expected.getNumElements(); i++) assertEquals(Double.doubleToRawLongBits(expected.data[i]), Double.doubleToRawLongBits(actual.data[i]), "matrix cell "+i);
	}

	static class Context {
		final StaggeredDemultiplexer demux;
		final ArrayList<ArrayList<FragmentScan>> cycles=new ArrayList<>();
		final int number;
		final String expected;

		Context() throws Exception {
			try (DataInputStream in=new DataInputStream(new GZIPInputStream(Files.newInputStream(ProductionDemuxCapture.ROOT.resolve("demux-production-context.bin.gz"))))) {
				assertEquals(0x444d5831, in.readInt());
				DemuxConfig config=new DemuxConfig(in.readInt(), DemuxConfig.InterpolationMethod.valueOf(in.readUTF()), in.readBoolean());
				PPMMassTolerance tolerance=new PPMMassTolerance(in.readDouble());
				number=in.readInt();
				ArrayList<Range> windows=new ArrayList<>();
				int count=in.readInt();
				for (int i=0; i<count; i++) windows.add(new Range(in.readDouble(), in.readDouble()));
				for (int c=0; c<5; c++) {
					ArrayList<FragmentScan> cycle=new ArrayList<>();
					count=in.readInt();
					for (int i=0; i<count; i++) cycle.add(PeakPickerReplay.readScan(in));
					assertEquals(windows.size(), cycle.size());
					cycles.add(cycle);
				}
				expected=in.readUTF();
				assertEquals(-1, in.read());
				demux=new StaggeredDemultiplexer(windows, tolerance, config);
			}
		}

		String replay() throws Exception {
			return ProductionDemuxCapture.spectrumDigest(demux.demultiplex(cycles.get(0), cycles.get(1), cycles.get(2), cycles.get(3), cycles.get(4), number));
		}
	}

	public static void main(String[] args) throws Exception {
		System.err.println("NNLS_ORIGIN="+NNLSSolver.class.getProtectionDomain().getCodeSource().getLocation());
		ArrayList<DMatrixRMaj[]> systems=systems();
		Context context=new Context();
		NNLSSolver solver=new NNLSSolver();
		int warmup=Integer.parseInt(args[1]), repeats=Integer.parseInt(args[2]);
		com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
		long tid=Thread.currentThread().getId(), nanos=0, cpu=0, bytes=0, checksum=0;
		ReplayMemory memory=new ReplayMemory();
		for (int i=-warmup; i<repeats; i++) {
			if (i==0) System.err.println("MEASURE_START_MS="+System.currentTimeMillis());
			long a=bean.getThreadAllocatedBytes(tid), c=bean.getCurrentThreadCpuTime(), t=System.nanoTime();
			if (args[0].equals("systems")) {
				for (DMatrixRMaj[] system : systems) checksum^=Double.doubleToRawLongBits(solver.solve(system[0], system[1]).get(0, 0));
			} else {
				checksum+=context.replay().hashCode();
			}
			long end=System.nanoTime();
			if (i>=0) { nanos+=end-t; cpu+=bean.getCurrentThreadCpuTime()-c; bytes+=bean.getThreadAllocatedBytes(tid)-a; }
			memory.sample();
		}
		System.err.println("MEASURE_END_MS="+System.currentTimeMillis());
		System.out.printf(Locale.ROOT, "{\"fixture\":\"%s\",\"repetitions\":%d,\"ns\":%d,\"cpu_ns\":%d,\"bytes\":%d,\"checksum\":%d%s}%n", args[0], repeats, nanos, cpu, bytes, checksum, memory.jsonFields());
	}
}
