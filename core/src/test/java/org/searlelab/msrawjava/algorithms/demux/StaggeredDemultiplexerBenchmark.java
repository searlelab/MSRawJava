package org.searlelab.msrawjava.algorithms.demux;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.algorithms.StaggeredDemultiplexer;
import org.searlelab.msrawjava.io.encyclopedia.EncyclopeDIAFile;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PPMMassTolerance;
import org.searlelab.msrawjava.model.Range;

class StaggeredDemultiplexerBenchmark {

	@Test
	void benchmarkDemuxOnFile() throws Exception {
		String filePath=System.getProperty("demux.bench.file");
		assumeTrue(filePath!=null&&!filePath.isEmpty(), "Set -Ddemux.bench.file=path/to/file.dia to run");
		runBenchmark(Paths.get(filePath));
	}

	public static void main(String[] args) throws Exception {
		String filePath=args.length>0?args[0]:System.getProperty("demux.bench.file");
		if (filePath==null||filePath.isEmpty()) {
			System.out.println("Usage: StaggeredDemultiplexerBenchmark <file.dia> or -Ddemux.bench.file=...");
			return;
		}
		runBenchmark(Paths.get(filePath));
	}

	private static void runBenchmark(Path filePath) throws Exception {
		EncyclopeDIAFile file=new EncyclopeDIAFile();
		file.openFile(filePath.toFile());

		Map<Range, org.searlelab.msrawjava.model.WindowData> ranges=file.getRanges();
		ArrayList<Range> windowList=new ArrayList<>(ranges.keySet());
		windowList.sort(null);

		List<DemuxCycle> cycles=extractCycles(file, windowList);
		if (cycles.size()<5) {
			System.out.println("Need at least 5 cycles for demultiplexing, skipping");
			file.close();
			return;
		}

		StaggeredDemultiplexer demux=new StaggeredDemultiplexer(windowList, new PPMMassTolerance(10.0));

		int maxCycles=Integer.getInteger("demux.bench.cycles", cycles.size());
		int startIndex=2;
		int endIndex=Math.min(cycles.size()-2, startIndex+maxCycles);
		int scanNumber=1;
		int totalOutputs=0;

		long start=System.nanoTime();
		for (int i=startIndex; i<endIndex; i++) {
			DemuxCycle cycleM2=cycles.get(i-2);
			DemuxCycle cycleM1=cycles.get(i-1);
			DemuxCycle cycleCenter=cycles.get(i);
			DemuxCycle cycleP1=cycles.get(i+1);
			DemuxCycle cycleP2=cycles.get(i+2);

			ArrayList<FragmentScan> javaResult=demux.demultiplex(cycleM2.spectra, cycleM1.spectra, cycleCenter.spectra, cycleP1.spectra, cycleP2.spectra,
					scanNumber);
			scanNumber+=javaResult.size();
			totalOutputs+=javaResult.size();
		}
		long elapsed=System.nanoTime()-start;

		System.out.printf("Demux benchmark: %d cycles, %d outputs, %.3f s%n", (endIndex-startIndex), totalOutputs, elapsed/1e9);

		file.close();
	}

	private static List<DemuxCycle> extractCycles(EncyclopeDIAFile file, ArrayList<Range> windows) throws Exception {
		List<FragmentScan> allSpectra=new ArrayList<>();
		float gradientLength=file.getGradientLength();

		for (Range window : windows) {
			double centerMz=window.getMiddle();
			ArrayList<FragmentScan> spectra=file.getStripes(centerMz, 0, gradientLength, false);
			allSpectra.addAll(spectra);
		}

		allSpectra.sort(Comparator.comparingDouble(FragmentScan::getScanStartTime));

		int windowCount=windows.size();
		int expectedCycles=allSpectra.size()/windowCount;
		List<DemuxCycle> cycles=new ArrayList<>();

		for (int cycleIdx=0; cycleIdx<expectedCycles; cycleIdx++) {
			int startIdx=cycleIdx*windowCount;
			int endIdx=Math.min(startIdx+windowCount, allSpectra.size());

			if (endIdx-startIdx>=windowCount*0.9) {
				ArrayList<FragmentScan> cycleSpectra=new ArrayList<>();
				for (int i=startIdx; i<endIdx; i++) {
					cycleSpectra.add(allSpectra.get(i));
				}
				cycleSpectra.sort(Comparator.comparingDouble(s -> (s.getIsolationWindowLower()+s.getIsolationWindowUpper())/2.0));
				float startRT=cycleSpectra.get(0).getScanStartTime();
				cycles.add(new DemuxCycle(cycleSpectra, startRT));
			}
		}

		return cycles;
	}

	private static class DemuxCycle {
		final ArrayList<FragmentScan> spectra;
		final float startRT;

		DemuxCycle(ArrayList<FragmentScan> spectra, float startRT) {
			this.spectra=spectra;
			this.startRT=startRT;
		}
	}
}
