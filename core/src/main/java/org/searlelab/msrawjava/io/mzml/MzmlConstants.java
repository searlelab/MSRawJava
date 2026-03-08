package org.searlelab.msrawjava.io.mzml;

/**
 * Shared CV accession constants for mzML reading and writing.
 * Accession numbers come from the PSI-MS controlled vocabulary (psi-ms.obo) and the Unit Ontology (UO).
 */
public final class MzmlConstants {

	/** File extension for mzML files. */
	public static final String MZML_EXTENSION=".mzML";

	// ---- Spectrum types and properties ----

	/** ms level (1 = MS1, 2 = MS2, etc.) */
	public static final String CV_MS_LEVEL="MS:1000511";
	/** MS1 spectrum */
	public static final String CV_MS1_SPECTRUM="MS:1000579";
	/** MSn spectrum */
	public static final String CV_MSN_SPECTRUM="MS:1000580";
	/** Centroid spectrum */
	public static final String CV_CENTROID_SPECTRUM="MS:1000127";
	/** Positive scan */
	public static final String CV_POSITIVE_SCAN="MS:1000130";

	// ---- Scan metadata ----

	/** Scan start time */
	public static final String CV_SCAN_START_TIME="MS:1000016";
	/** Ion injection time */
	public static final String CV_ION_INJECTION_TIME="MS:1000927";
	/** Base peak intensity */
	public static final String CV_BASE_PEAK_INTENSITY="MS:1000505";
	/** Total ion current */
	public static final String CV_TOTAL_ION_CURRENT="MS:1000285";
	/** No combination */
	public static final String CV_NO_COMBINATION="MS:1000795";

	// ---- Scan window ----

	/** Scan window lower limit */
	public static final String CV_SCAN_WINDOW_LOWER_LIMIT="MS:1000501";
	/** Scan window upper limit */
	public static final String CV_SCAN_WINDOW_UPPER_LIMIT="MS:1000500";

	// ---- Isolation window ----

	/** Isolation window target m/z */
	public static final String CV_ISOLATION_WINDOW_TARGET_MZ="MS:1000827";
	/** Isolation window lower offset */
	public static final String CV_ISOLATION_WINDOW_LOWER_OFFSET="MS:1000828";
	/** Isolation window upper offset */
	public static final String CV_ISOLATION_WINDOW_UPPER_OFFSET="MS:1000829";

	// ---- Precursor / selected ion ----

	/** Selected ion m/z */
	public static final String CV_SELECTED_ION_MZ="MS:1000744";
	/** Charge state */
	public static final String CV_CHARGE_STATE="MS:1000041";

	// ---- Binary data encoding ----

	/** 64-bit float */
	public static final String CV_64_BIT_FLOAT="MS:1000523";
	/** 32-bit float */
	public static final String CV_32_BIT_FLOAT="MS:1000521";
	/** Zlib compression */
	public static final String CV_ZLIB_COMPRESSION="MS:1000574";
	/** No compression */
	public static final String CV_NO_COMPRESSION="MS:1000576";
	/** m/z array */
	public static final String CV_MZ_ARRAY="MS:1000514";
	/** Intensity array */
	public static final String CV_INTENSITY_ARRAY="MS:1000515";

	// ---- Units ----

	/** m/z unit */
	public static final String CV_MZ_UNIT="MS:1000040";
	/** Number of detector counts unit */
	public static final String CV_DETECTOR_COUNTS_UNIT="MS:1000131";
	/** Second (unit) */
	public static final String UO_SECOND="UO:0000010";
	/** Millisecond (unit) */
	public static final String UO_MILLISECOND="UO:0000028";
	/** Minute (unit) */
	public static final String UO_MINUTE="UO:0000031";

	// ---- Native ID format ----

	/** Scan number only nativeID format */
	public static final String CV_SCAN_NUMBER_NATIVE_ID="MS:1000776";

	// ---- Checksum ----

	/** SHA-1 checksum */
	public static final String CV_SHA1="MS:1000569";

	// ---- File formats ----

	/** Thermo RAW format */
	public static final String CV_THERMO_RAW_FORMAT="MS:1000563";
	/** mzML format */
	public static final String CV_MZML_FORMAT="MS:1000584";
	/** Bruker/Agilent YEP format */
	public static final String CV_BRUKER_YEP_FORMAT="MS:1000567";
	/** Bruker TDF format */
	public static final String CV_BRUKER_TDF_FORMAT="MS:1002817";
	/** Bruker TDF nativeID format */
	public static final String CV_BRUKER_TDF_NATIVE_ID="MS:1002818";

	// ---- Software ----

	/** Acquisition software */
	public static final String CV_ACQUISITION_SOFTWARE="MS:1000531";
	/** Custom unreleased software tool */
	public static final String CV_CUSTOM_SOFTWARE="MS:1000799";
	/** Conversion to mzML */
	public static final String CV_CONVERSION_TO_MZML="MS:1000544";

	// ---- Instrument ----

	/** Instrument model (generic) */
	public static final String CV_INSTRUMENT_MODEL="MS:1000031";
	/** Instrument serial number */
	public static final String CV_INSTRUMENT_SERIAL_NUMBER="MS:1000529";
	/** Thermo Electron instrument model */
	public static final String CV_THERMO_INSTRUMENT_MODEL="MS:1000492";
	/** Bruker Daltonics timsTOF series */
	public static final String CV_BRUKER_TIMSTOF_SERIES="MS:1003123";

	// ---- Ion source ----

	/** Electrospray ionization */
	public static final String CV_ELECTROSPRAY_IONIZATION="MS:1000073";

	// ---- Mass analyzers ----

	/** Orbitrap */
	public static final String CV_ORBITRAP="MS:1000484";
	/** Time-of-flight */
	public static final String CV_TIME_OF_FLIGHT="MS:1000084";
	/** Linear ion trap */
	public static final String CV_LINEAR_ION_TRAP="MS:1000291";
	/** Time-of-flight mass analyzer (specific analyzer term) */
	public static final String CV_TOF_MASS_ANALYZER="MS:1000442";

	// ---- Detectors ----

	/** Inductive detector */
	public static final String CV_INDUCTIVE_DETECTOR="MS:1000624";
	/** Microchannel plate detector */
	public static final String CV_MICROCHANNEL_PLATE_DETECTOR="MS:1000114";

	// ---- Activation ----

	/** Beam-type collision-induced dissociation (HCD) */
	public static final String CV_BEAM_TYPE_CID="MS:1000422";

	private MzmlConstants() {}
}
