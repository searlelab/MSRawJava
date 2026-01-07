package org.searlelab.msrawjava.algorithms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.msrawjava.io.OutputType;
import org.searlelab.msrawjava.io.RawFileConverters;
import org.searlelab.msrawjava.io.encyclopedia.EncyclopeDIAFile;
import org.searlelab.msrawjava.logging.LoggingProgressIndicator;
import org.searlelab.msrawjava.model.PPMMassTolerance;
import org.searlelab.msrawjava.model.Range;

class StaggeredDemultiplexerTest {
	@Test
	public void smokeTest(@TempDir Path outDir) throws Exception {
		// VATVSLPR (29.9 min apex)
		
		long time=System.currentTimeMillis();
		//outDir=Paths.get("/Users/searle.brian/Documents/temp/demux/");

		EncyclopeDIAFile rawFile=new EncyclopeDIAFile();
		rawFile.openFile(Paths.get("src/test/resources/rawdata/HeLa_16mzst_29to31min.dia").toFile());
		RawFileConverters.writeDemux(rawFile, outDir, OutputType.mgf, new LoggingProgressIndicator(), new PPMMassTolerance(10.0));

		System.out.println(System.currentTimeMillis()-time);
	}
	
	@Test
	void testSubRanges() throws Exception {
		ArrayList<Range> ranges=getTestRanges();
		
		double rangeSum=0.0;
		for (Range range : ranges) {
			rangeSum+=range.getRange();
		}
		double rangeAverage=rangeSum/ranges.size();
		
		ArrayList<RangeCounter> counters=StaggeredDemultiplexer.getSubRanges(ranges);

		double counterSum=0.0;
		for (RangeCounter counter : counters) {
			counterSum+=counter.range.getRange();
		}
		double counterAverage=counterSum/counters.size();
		assertTrue(ranges.size()<counters.size());
		assertEquals(76, ranges.size());
		assertEquals(77, counters.size());
		
		assertEquals(16.0, rangeAverage, 0.01);
		assertEquals(8.0, counterAverage, 0.01);
		assertEquals(2.0, rangeAverage/counterAverage, 0.01);
	}

	private static ArrayList<Range> getTestRanges() {
		ArrayList<Range> ranges=new ArrayList<Range>();
		ranges.add(new Range(400.4356, 416.4356));
		ranges.add(new Range(416.4428, 432.4428));
		ranges.add(new Range(432.4501, 448.4501));
		ranges.add(new Range(448.4574, 464.4574));
		ranges.add(new Range(464.4647, 480.4647));
		ranges.add(new Range(480.4719, 496.4719));
		ranges.add(new Range(496.4792, 512.4792));
		ranges.add(new Range(512.4865, 528.4865));
		ranges.add(new Range(528.4938, 544.4938));
		ranges.add(new Range(544.501, 560.501));
		ranges.add(new Range(560.5083, 576.5083));
		ranges.add(new Range(576.5156, 592.5156));
		ranges.add(new Range(592.5228, 608.5228));
		ranges.add(new Range(608.5302, 624.5302));
		ranges.add(new Range(624.5374, 640.5374));
		ranges.add(new Range(640.5447, 656.5447));
		ranges.add(new Range(656.552, 672.552));
		ranges.add(new Range(672.5592, 688.5592));
		ranges.add(new Range(688.5665, 704.5665));
		ranges.add(new Range(704.5737, 720.5737));
		ranges.add(new Range(720.5811, 736.5811));
		ranges.add(new Range(736.5884, 752.5884));
		ranges.add(new Range(752.5956, 768.5956));
		ranges.add(new Range(768.6029, 784.6029));
		ranges.add(new Range(784.6101, 800.6101));
		ranges.add(new Range(800.6174, 816.6174));
		ranges.add(new Range(816.6248, 832.6248));
		ranges.add(new Range(832.632, 848.632));
		ranges.add(new Range(848.6393, 864.6393));
		ranges.add(new Range(864.6466, 880.6466));
		ranges.add(new Range(880.6538, 896.6538));
		ranges.add(new Range(896.6611, 912.6611));
		ranges.add(new Range(912.6683, 928.6683));
		ranges.add(new Range(928.6757, 944.6757));
		ranges.add(new Range(944.6829, 960.6829));
		ranges.add(new Range(960.6902, 976.6902));
		ranges.add(new Range(976.6975, 992.6975));
		ranges.add(new Range(992.7047, 1008.7047));
		ranges.add(new Range(392.4319, 408.4319));
		ranges.add(new Range(408.4392, 424.4392));
		ranges.add(new Range(424.4465, 440.4465));
		ranges.add(new Range(440.4537, 456.4537));
		ranges.add(new Range(456.461, 472.461));
		ranges.add(new Range(472.4683, 488.4683));
		ranges.add(new Range(488.4756, 504.4756));
		ranges.add(new Range(504.4828, 520.4828));
		ranges.add(new Range(520.4901, 536.4901));
		ranges.add(new Range(536.4974, 552.4974));
		ranges.add(new Range(552.5046, 568.5046));
		ranges.add(new Range(568.512, 584.512));
		ranges.add(new Range(584.5192, 600.5192));
		ranges.add(new Range(600.5265, 616.5265));
		ranges.add(new Range(616.5338, 632.5338));
		ranges.add(new Range(632.541, 648.541));
		ranges.add(new Range(648.5483, 664.5483));
		ranges.add(new Range(664.5555, 680.5555));
		ranges.add(new Range(680.5629, 696.5629));
		ranges.add(new Range(696.5702, 712.5702));
		ranges.add(new Range(712.5774, 728.5774));
		ranges.add(new Range(728.5847, 744.5847));
		ranges.add(new Range(744.5919, 760.5919));
		ranges.add(new Range(760.5992, 776.5992));
		ranges.add(new Range(776.6066, 792.6066));
		ranges.add(new Range(792.6138, 808.6138));
		ranges.add(new Range(808.6211, 824.6211));
		ranges.add(new Range(824.6284, 840.6284));
		ranges.add(new Range(840.6356, 856.6356));
		ranges.add(new Range(856.6429, 872.6429));
		ranges.add(new Range(872.6502, 888.6502));
		ranges.add(new Range(888.6575, 904.6575));
		ranges.add(new Range(904.6647, 920.6647));
		ranges.add(new Range(920.672, 936.672));
		ranges.add(new Range(936.6793, 952.6793));
		ranges.add(new Range(952.6865, 968.6865));
		ranges.add(new Range(968.6939, 984.6939));
		ranges.add(new Range(984.7011, 1000.7011));
		return ranges;
	}
	
}
