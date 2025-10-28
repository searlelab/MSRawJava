package org.searlelab.msrawjava.io.tims;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.io.utils.Triplet;
import org.searlelab.msrawjava.model.PeakInterface;
import org.searlelab.msrawjava.model.PeakWithIMS;

class TimsReaderTest {

	public static void main2(String[] args) {
		Path dPath=Paths.get("/Users/searle.brian/Documents/temp/bruker/2025-07-05_17-56-24_One-column-separation.d");
		TimsReader reader=TimsReader.open(dPath, Optional.empty());

		Object res=TimsNative.readRawFrameRange(reader.getDatasetHandle(), 4532-1, 883, 883);
		Object[] arr=(Object[])res;
		double[] tofMz=(double[])arr[0];

		res=TimsNative.readRawFrameTofIntRange(reader.getDatasetHandle(), 4532-1, 883, 883);
		Object[] raw=(Object[])res;
		int[] tofRaw=(int[])raw[0];
		int[] intensRaw=(int[])raw[1];

		for (int i=0; i<intensRaw.length; i++) {
			if (intensRaw[i]>200) {
				System.out.println(tofMz[i]+"\t"+tofRaw[i]+"\t"+intensRaw[i]);
			}
		}
		
		//var chart2 = SpectrumChart.buildChart(tofMz, intensRaw);
		//SpectrumChart.show(chart2);
	}
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
		int digitizerNumSamples=397888;
		double mzLower=95.0;
		double mzUpper=1705.0;
		MzCalibrationPoly calibrator=new MzCalibrationPoly(digitizerNumSamples, mzLower, mzUpper, params);
		
		TimsReader reader=TimsReader.open(dPath, Optional.of(calibrator));
		double realT1=25.6515312405121;
		Triplet<double[], float[], int[]> triplet=reader.readRawFrameAndCalibrate(2676-1, 831, 856, realT1); // MS2
		//Triplet<double[], float[], int[]> triplet=reader.readFrameWithRange(2676-1, 831, 856); // MS2
		//Triplet<double[], float[], int[]> triplet=reader.readFrameWithRange(2673-1, 832, 856); // MS1
		
		System.out.println("Calibrate: "+reader.calibrateMz(575.6, realT1));
		System.out.println("Calibrate: "+reader.calibrateMz(575.985, realT1));

		assertNotNull(triplet);
		
		float[] ims=new float[triplet.z.length];
		for (int i=0; i<ims.length; i++) {
			ims[i]=OneOverK0AcqRangeUpper+(OneOverK0AcqRangeLower-OneOverK0AcqRangeUpper)*((triplet.z[i]-1.0f)/scanMax);
		}
		
		//var chart2 = SpectrumChart.buildChart(triplet.x, triplet.y);
		//SpectrumChart.show(chart2);

		//var chart1=MobilogramHeatmap.buildChart(ims, triplet.x, triplet.y, 800);
		//MobilogramHeatmap.show(chart1);
		
		ArrayList<PeakWithIMS> peaks=new ArrayList<PeakWithIMS>();

		float msmsIntensityThreshold=1.0f;
		for (int i=0; i<triplet.x.length; i++) {
			if (triplet.y[i]>msmsIntensityThreshold) {
				peaks.add(new PeakWithIMS(triplet.x[i], (float)triplet.y[i], (float)ims[i]));
			}
		}
		//System.out.println(" --> "+MatrixMath.sum(triplet.y)+", "+triplet.y.length+", "+peaks.size());
		
		//ArrayList<ArrayList<Peak>> chromatograms=TIMSPeakPicker.getIMSChromatograms(peaks, 2.0f*msmsIntensityThreshold);
		//var chart4 = IMSChromatogramChart.buildChart(chromatograms);
		//IMSChromatogramChart.show(chart4);
		
		for (PeakInterface peak : peaks) {
			peak.turnOn();
		}

		//ArrayList<Peak> picked=TIMSPeakPicker.peakPickAcrossIMS(peaks, 2.0f*msmsIntensityThreshold);
		ArrayList<PeakWithIMS> picked=TIMSPeakPicker.peakPickAcrossIMS(peaks);
		
		double[] newMassArray=new double[picked.size()];
		float[] newIntensityArray=new float[picked.size()];
		int[] newIonMobilityArray=new int[picked.size()];
		for (int i=0; i<picked.size(); i++) {
			PeakWithIMS peak=picked.get(i);
			newMassArray[i]=peak.mz;
			newIntensityArray[i]=peak.intensity;
			newIonMobilityArray[i]=Math.round(peak.ims);

			//if (peak.intensity>100) System.out.println(peak.mz+"\t"+peak.intensity);
			
		}
		//var chart3 = SpectrumChart.buildChart(newMassArray, newIntensityArray);
		//SpectrumChart.show(chart3);

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
		int digitizerNumSamples=397888;
		double mzLower=100.0;
		double mzUpper=1700.0;
		MzCalibrationPoly calibrator=new MzCalibrationPoly(digitizerNumSamples, mzLower, mzUpper, params);
		
		TimsReader reader=TimsReader.open(dPath, Optional.of(calibrator));
		long startTime=System.currentTimeMillis();

		for (int i=0; i<130; i++) {
			Triplet<double[], float[], int[]> triplet=reader.readFrame(i);
			System.out.println(triplet.toString());
			assertNotNull(triplet);
			assertTrue(triplet.x.length==triplet.y.length);
			assertTrue(triplet.x.length==triplet.z.length);
		}
		
		double calibrateMz=reader.calibrateMz(1000);
		assertEquals(997.5038783578941, calibrateMz, 0.001);
		assertEquals(1000, reader.uncalibrateMz(calibrateMz), 0.01);

		Random random=new Random(42);
		for (int i=0; i<1000; i++) {
			double original=random.nextDouble(100, 1700);
			calibrateMz=reader.calibrateMz(original);
			assertEquals(original, reader.uncalibrateMz(calibrateMz), 0.01);
		}
		
		System.out.println("Total time: "+(System.currentTimeMillis()-startTime));
	}

}
