package org.searlelab.msrawjava.io.tims;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.gui.IMSChromatogramChart;
import org.searlelab.msrawjava.gui.MobilogramHeatmap;
import org.searlelab.msrawjava.gui.SpectrumChart;
import org.searlelab.msrawjava.io.utils.Triplet;
import org.searlelab.msrawjava.model.Peak;

class TimsReaderTest {
	public static void main(String[] args) {
		Path dPath=Paths.get("/Users/searle.brian/Documents/temp/bruker/20181024_RFdemoPlasma110_100ng_100samplesday_S4-A11_1_2631.d");
		long startTime=System.currentTimeMillis();

		var params = new MzCalibrationParams(
			    0.2, 24864, 25.6560637140618, 27.3444130769615,
			    27, 0,
			    315.351730103478, 157256.258704659, 0.0, 0.0, 0.0
			);
		
		float OneOverK0AcqRangeLower=0.582617f;
		float OneOverK0AcqRangeUpper=1.534496f;
		int scanMax=918;
		
		TimsReader reader=TimsReader.open(dPath, params);
		// NOTE -1 required now!
		double realT1=25.6515312405121;
		Triplet<double[], float[], int[]> triplet=reader.readRawFrameAndCalibrate(2676-1, 831, 856, realT1); // MS2
		//Triplet<double[], float[], int[]> triplet=reader.readFrameWithRange(2673-1, 832, 856); // MS1

		assertNotNull(triplet);
		
		float[] ims=new float[triplet.z.length];
		for (int i=0; i<ims.length; i++) {
			ims[i]=OneOverK0AcqRangeUpper+(OneOverK0AcqRangeLower-OneOverK0AcqRangeUpper)*((triplet.z[i]-1.0f)/scanMax);
		}

		var chart1=MobilogramHeatmap.buildChart(ims, triplet.x, triplet.y, 800);
		MobilogramHeatmap.show(chart1);
		
		var chart2 = SpectrumChart.buildChart(triplet.x, triplet.y);
		SpectrumChart.show(chart2);
		
		ArrayList<Peak> peaks=new ArrayList<Peak>();

		float msmsIntensityThreshold=1.0f;
		for (int i=0; i<triplet.x.length; i++) {
			if (triplet.y[i]>msmsIntensityThreshold) {
				peaks.add(new Peak(triplet.x[i], (float)triplet.y[i], (float)ims[i]));
			}
		}
		
		ArrayList<ArrayList<Peak>> chromatograms=TIMSPeakPicker.getIMSChromatograms(peaks, 2.0f*msmsIntensityThreshold);
		var chart4 = IMSChromatogramChart.buildChart(chromatograms);
		IMSChromatogramChart.show(chart4);
		
		for (Peak peak : peaks) {
			peak.turnOn();
		}

		ArrayList<Peak> picked=TIMSPeakPicker.peakPickAcrossIMS(peaks, 2.0f*msmsIntensityThreshold);
		
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

		var params = new MzCalibrationParams(
			    0.2, 24864, 25.6560637140618, 27.3444130769615,
			    27, 0,
			    315.351730103478, 157256.258704659, 0.0, 0.0, 0.0
			);
		TimsReader reader=TimsReader.open(dPath, params);
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
