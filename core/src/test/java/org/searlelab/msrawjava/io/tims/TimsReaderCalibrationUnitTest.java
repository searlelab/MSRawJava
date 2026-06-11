package org.searlelab.msrawjava.io.tims;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class TimsReaderCalibrationUnitTest {

	@Test
	void privateConstructorRejectsZeroDatasetHandle() throws Exception {
		Constructor<TimsReader> ctor=constructor();
		InvocationTargetException ex=assertThrows(InvocationTargetException.class, () -> ctor.newInstance(0L, Optional.empty()));
		assertSame(IllegalStateException.class, ex.getCause().getClass());
	}

	@Test
	void calibrationMethodsReturnInputWhenNoCalibratorIsPresent() throws Exception {
		TimsReader reader=newReader(Optional.empty());

		assertEquals(555.5, reader.calibrateMz(555.5, 12.0), 0.0);
		assertEquals(666.6, reader.calibrateMz(666.6), 0.0);
		assertEquals(777.7, reader.uncalibrateMz(777.7), 0.0);
	}

	@Test
	void calibrationMethodsDelegateToCalibratorWithExpectedT1Values() throws Exception {
		FakeCalibrator calibrator=new FakeCalibrator();
		TimsReader reader=newReader(Optional.of(calibrator));

		assertEquals(101.5, reader.calibrateMz(100.0, 12.5), 0.0);
		assertEquals(12.5, calibrator.lastUncorrectedRealT1, 0.0);

		assertEquals(201.5, reader.calibrateMz(200.0), 0.0);
		assertEquals(33.0, calibrator.lastUncorrectedRealT1, 0.0);

		assertEquals(729.0, reader.uncalibrateMz(300.0), 0.0);
		assertEquals(33.0, calibrator.lastMzToTofRealT1, 0.0);
		assertSame(calibrator.linear, calibrator.getLinear());
	}

	private static TimsReader newReader(Optional<MzCalibrator> calibrator) throws Exception {
		return constructor().newInstance(123L, calibrator);
	}

	private static Constructor<TimsReader> constructor() throws Exception {
		Constructor<TimsReader> ctor=TimsReader.class.getDeclaredConstructor(long.class, Optional.class);
		ctor.setAccessible(true);
		return ctor;
	}

	private static final class FakeCalibrator implements MzCalibrator {
		final MzCalibrationLinear linear=new MzCalibrationLinear(10, 100.0, 400.0, new MzCalibrationParams(0.0, 33.0, 0.0, 0.0, 0, 0,
				0.0, 0.0, 0.0, 0.0, 0.0));
		double lastUncorrectedRealT1=Double.NaN;
		double lastMzToTofRealT1=Double.NaN;

		@Override
		public double[] tofToMz(int[] tof, double realT1) {
			double[] mz=new double[tof.length];
			for (int i=0; i<tof.length; i++) {
				mz[i]=tof[i];
			}
			return mz;
		}

		@Override
		public int[] mzToTof(double[] mz, double realT1) {
			lastMzToTofRealT1=realT1;
			int[] tof=new int[mz.length];
			for (int i=0; i<mz.length; i++) {
				tof[i]=(int)Math.round(Math.sqrt(mz[i]));
			}
			return tof;
		}

		@Override
		public double[] uncorrectedMzToMz(double[] uncorrectedMz, double realT1) {
			lastUncorrectedRealT1=realT1;
			double[] out=new double[uncorrectedMz.length];
			for (int i=0; i<uncorrectedMz.length; i++) {
				out[i]=uncorrectedMz[i]+1.5;
			}
			return out;
		}

		@Override
		public double getGlobalT1() {
			return 33.0;
		}

		@Override
		public MzCalibrationLinear getLinear() {
			return linear;
		}
	}
}
