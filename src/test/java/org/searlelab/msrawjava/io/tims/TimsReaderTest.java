package org.searlelab.msrawjava.io.tims;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.gui.MobilogramHeatmap;
import org.searlelab.msrawjava.gui.SpectrumChart;
import org.searlelab.msrawjava.io.utils.Triplet;
import org.searlelab.msrawjava.model.Peak;

class TimsReaderTest {
	public static void main(String[] args) {
		Path dPath=Paths.get("/Users/searle.brian/Documents/temp/bruker/20181024_RFdemoPlasma110_100ng_100samplesday_S4-A11_1_2631.d");
		long startTime=System.currentTimeMillis();

		TimsReader reader=TimsReader.open(dPath);
		// NOTE -1 required now!
		Triplet<double[], float[], int[]> triplet=reader.readFrameWithRange(2676-1, 831, 856); // MS2
		//Triplet<double[], float[], int[]> triplet=reader.readFrameWithRange(2673-1, 832, 856); // MS1

		assertNotNull(triplet);

		var chart1=MobilogramHeatmap.buildChart(triplet.z, triplet.x, triplet.y, 800);
		MobilogramHeatmap.show(chart1);
		
		var chart2 = SpectrumChart.buildChart(triplet.x, triplet.y);
		SpectrumChart.show(chart2);
		
		ArrayList<Peak> peaks=new ArrayList<Peak>();

		for (int i=0; i<triplet.x.length; i++) {
			peaks.add(new Peak(triplet.x[i], (float)triplet.y[i], (float)triplet.z[i]));
		}
		Collections.sort(peaks);

		ArrayList<Peak> picked=TIMSPeakPicker.peakPickAcrossIMS(peaks, 2.0f*3.0f);
		
		double[] newMassArray=new double[picked.size()];
		float[] newIntensityArray=new float[picked.size()];
		int[] newIonMobilityArray=new int[picked.size()];
		for (int i=0; i<picked.size(); i++) {
			Peak peak=picked.get(i);
			newMassArray[i]=peak.mz;
			newIntensityArray[i]=peak.intensity;
			newIonMobilityArray[i]=Math.round(peak.ims);
		}
		var chart3 = SpectrumChart.buildChart(newMassArray, newIntensityArray);
		SpectrumChart.show(chart3);

		System.out.println("Total time: "+(System.currentTimeMillis()-startTime));
	}

	@Test
	void test() {
		Path dPath=Path.of("src", "test", "resources", "rawdata", "230711_idleflow_400-1000mz_25mz_diaPasef_10sec.d");
		
		TimsReader reader=TimsReader.open(dPath);
		long startTime=System.currentTimeMillis();

		for (int i=0; i<130; i++) {
			Triplet<double[], double[], int[]> triplet=reader.readFrame(i);
			assertNotNull(triplet);
			assertTrue(triplet.x.length==triplet.y.length);
			assertTrue(triplet.x.length==triplet.z.length);
		}
		System.out.println("Total time: "+(System.currentTimeMillis()-startTime));
	}

}
