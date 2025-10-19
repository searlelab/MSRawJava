package org.searlelab.msrawjava.io.tims;

/**
 * Interface for ToF calibration.
 */
public interface MzCalibrator {

	/**
	 * Convert ToF integer indexes to m/z values
	 * @param tof
	 * @param realT1
	 * @return
	 */
	double[] tofToMz(int[] tof, double realT1);
	

	/**
	 * Convert m/z values to ToF integer indexes
	 * @param mz
	 * @param realT1
	 * @return
	 */
	public int[] mzToTof(double[] mz, double realT1);

	/**
	 * Undo linear correction and apply this correction
	 * @param uncorrectedMz
	 * @param realT1
	 * @return
	 */
	double[] uncorrectedMzToMz(double[] uncorrectedMz, double realT1);

	/**
	 * get the average T1
	 * @return
	 */
	double getGlobalT1();
	
	/**
	 * Returns default linear calibrator
	 * @return
	 */
	MzCalibrationLinear getLinear();

}