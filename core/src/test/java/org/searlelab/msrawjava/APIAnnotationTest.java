package org.searlelab.msrawjava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.algorithms.demux.DemuxConfig;
import org.searlelab.msrawjava.io.ConversionParameters;
import org.searlelab.msrawjava.io.ConversionOptions;
import org.searlelab.msrawjava.io.ConversionOptionsBuilder;
import org.searlelab.msrawjava.io.ConversionRequest;
import org.searlelab.msrawjava.io.ConversionResult;
import org.searlelab.msrawjava.io.ConversionStatus;
import org.searlelab.msrawjava.io.OutputType;
import org.searlelab.msrawjava.io.RawFileConversion;
import org.searlelab.msrawjava.io.RawFileConverters;
import org.searlelab.msrawjava.io.StripeFileInterface;
import org.searlelab.msrawjava.io.StripeFileSource;
import org.searlelab.msrawjava.io.encyclopedia.EncyclopeDIAFile;
import org.searlelab.msrawjava.io.mzml.MzmlFile;
import org.searlelab.msrawjava.io.thermo.ThermoIndexingMode;
import org.searlelab.msrawjava.io.thermo.ThermoRawFile;
import org.searlelab.msrawjava.io.thermo.ThermoServerPool;
import org.searlelab.msrawjava.io.tims.BrukerTIMSFile;
import org.searlelab.msrawjava.io.tims.TIMSPeakPicker;
import org.searlelab.msrawjava.io.utils.DataAcquisitionType;
import org.searlelab.msrawjava.io.utils.Pair;
import org.searlelab.msrawjava.io.utils.RawFileStructureTools;
import org.searlelab.msrawjava.logging.ProgressIndicator;
import org.searlelab.msrawjava.model.AcquiredSpectrum;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.MassTolerance;
import org.searlelab.msrawjava.model.PeakInterface;
import org.searlelab.msrawjava.model.PeakWithIMS;
import org.searlelab.msrawjava.model.PPMMassTolerance;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.ScanSummary;
import org.searlelab.msrawjava.model.Spectrum;
import org.searlelab.msrawjava.model.WindowData;
import org.searlelab.msrawjava.threading.ProcessingThreadPool;

class APIAnnotationTest {
	private static final String SINCE="v26.7.31";

	@Test
	void annotationDefinitionIsDocumentedAndComplete() throws Exception {
		assertNotNull(API.class.getAnnotation(java.lang.annotation.Documented.class));
		assertEquals(java.lang.annotation.RetentionPolicy.RUNTIME, API.class.getAnnotation(java.lang.annotation.Retention.class).value());
		assertEquals(API.Status.class, API.class.getDeclaredMethod("status").getReturnType());
		assertEquals(String.class, API.class.getDeclaredMethod("since").getReturnType());
		assertEquals(3, API.Status.values().length);
	}

	@Test
	void facadeTypesAreStable() {
		assertStable(RawFileConversion.class);
		assertStable(ConversionOptions.class);
		assertStable(ConversionOptionsBuilder.class);
		assertStable(ConversionRequest.class);
		assertStable(ConversionResult.class);
		assertStable(ConversionStatus.class);
		assertStable(OutputType.class);
		assertStable(ProgressIndicator.class);
		assertStableMethod(ConversionOptions.class, "builder");
		assertStableMethod(ConversionOptions.class, "getOutputType");
		assertStableMethod(ConversionOptions.class, "getMinimumMS1Intensity");
		assertStableMethod(ConversionOptions.class, "getMinimumMS2Intensity");
		assertStableMethod(ConversionOptions.class, "getDemultiplex");
		assertStableMethod(ConversionOptions.class, "getPrecursorMarginSize");
		assertStableMethod(ConversionOptions.class, "getDemuxTolerance");
		assertStableMethod(ConversionOptions.class, "getDemuxConfig");
		assertStableMethod(ConversionOptionsBuilder.class, "outputType", OutputType.class);
		assertStableMethod(ConversionOptionsBuilder.class, "minimumMS1Intensity", float.class);
		assertStableMethod(ConversionOptionsBuilder.class, "minimumMS2Intensity", float.class);
		assertStableMethod(ConversionOptionsBuilder.class, "demultiplex", boolean.class);
		assertStableMethod(ConversionOptionsBuilder.class, "demultiplex", Optional.class);
		assertStableMethod(ConversionOptionsBuilder.class, "precursorMarginSize", double.class);
		assertStableMethod(ConversionOptionsBuilder.class, "precursorMarginSize", Optional.class);
		assertStableMethod(ConversionOptionsBuilder.class, "demuxTolerance", MassTolerance.class);
		assertStableMethod(ConversionOptionsBuilder.class, "demuxConfig", DemuxConfig.class);
		assertStableMethod(ConversionOptionsBuilder.class, "build");
		assertStableMethod(ConversionRequest.class, "of", Path.class, ConversionOptions.class);
		assertStableMethod(ConversionRequest.class, "toDirectory", Path.class, Path.class, Integer.class, ConversionOptions.class,
				ProgressIndicator.class);
		assertStableMethod(ConversionRequest.class, "toPath", Path.class, Path.class, Integer.class, ConversionOptions.class, ProgressIndicator.class);
		assertStableMethod(ConversionRequest.class, "getInputPath");
		assertStableMethod(ConversionRequest.class, "getOutputDirectory");
		assertStableMethod(ConversionRequest.class, "getOutputPath");
		assertStableMethod(ConversionRequest.class, "getProcessingThreads");
		assertStableMethod(ConversionRequest.class, "getOptions");
		assertStableMethod(ConversionRequest.class, "getProgressIndicator");
		assertStableMethod(ConversionResult.class, "getOutputPath");
		assertStableMethod(ConversionResult.class, "getStatus");
		assertStableMethod(ProgressIndicator.class, "update", String.class);
		assertStableMethod(ProgressIndicator.class, "update", String.class, float.class);
		assertStableMethod(ProgressIndicator.class, "getTotalProgress");
		assertStableMethod(ProgressIndicator.class, "isCanceled");
		assertStableMethod(RawFileConversion.class, "convert", ConversionRequest.class);
		assertStableMethod(RawFileConversion.class, "convert", ConversionRequest.class, ProcessingThreadPool.class);
		assertStableMethod(RawFileConversion.class, "convert", StripeFileInterface.class, ConversionRequest.class, ProcessingThreadPool.class);
	}

	@Test
	void readerAndLifecycleDeclarationsAreStable() {
		assertStable(StripeFileInterface.class);
		assertStable(StripeFileSource.class);
		assertAllDeclaredMethodsStable(StripeFileInterface.class);
		assertAllDeclaredMethodsStable(StripeFileSource.class);
		assertStableMethod(StripeFileInterface.class, "getFile");
		assertStableMethod(StripeFileInterface.class, "getMetadata");
		assertStableMethod(StripeFileInterface.class, "getOriginalFileName");
		assertStableMethod(StripeFileInterface.class, "getPrecursors", float.class, float.class);
		assertStableMethod(StripeFileInterface.class, "getRanges");
		assertStableMethod(StripeFileInterface.class, "getStripes", double.class, float.class, float.class, boolean.class);
		assertStableMethod(StripeFileInterface.class, "isOpen");
		assertStableMethod(StripeFileInterface.class, "openFile", File.class);
		assertStableMethod(StripeFileInterface.class, "close");
		assertStableMethod(ThermoServerPool.class, "isThermoReaderAvailable");
		assertStableMethod(ThermoServerPool.class, "startAsync");
		assertStableMethod(ThermoServerPool.class, "shutdown");
		assertStableMethod(RawFileStructureTools.class, "getDataType", Map.class);
		assertStableConstructor(MzmlFile.class);
		assertStableMethod(MzmlFile.class, "openFile", File.class);
		assertStableMethod(MzmlFile.class, "close");
		assertStableConstructor(ProcessingThreadPool.class, int.class, int.class);
		assertStableMethod(ProcessingThreadPool.class, "close");
	}

	@Test
	void encyclopediaReaderAndModelContractsAreStable() {
		assertStable(Spectrum.class);
		assertStable(AcquiredSpectrum.class);
		assertStable(PeakInterface.class);
		assertStable(PeakWithIMS.class);
		assertAllDeclaredMethodsStable(Spectrum.class);
		assertAllDeclaredMethodsStable(AcquiredSpectrum.class);
		assertAllDeclaredMethodsStable(PeakInterface.class);
		assertStableConstructor(PeakWithIMS.class, double.class, float.class, float.class);
		assertStableMethod(PeakWithIMS.class, "getIMS");
		assertStable(DataAcquisitionType.class);
		assertStableField(DataAcquisitionType.class, "DDA");
		assertStableField(DataAcquisitionType.class, "PRM");
		assertStableField(DataAcquisitionType.class, "DIA");
		assertStable(ThermoIndexingMode.class);
		assertStableField(ThermoIndexingMode.class, "LAZY");
		assertStableField(ThermoIndexingMode.class, "FULL");
		assertStable(ThermoRawFile.class);
		assertStableConstructor(ThermoRawFile.class);
		assertStableMethod(ThermoRawFile.class, "openFile", File.class, ThermoIndexingMode.class);
		assertStableMethod(ThermoRawFile.class, "getStructureMetadata");
		assertStable(BrukerTIMSFile.class);
		assertStableConstructor(BrukerTIMSFile.class);
		assertStable(EncyclopeDIAFile.class);
		assertStableConstructor(EncyclopeDIAFile.class);
		assertStable(TIMSPeakPicker.class);
		assertStableMethod(TIMSPeakPicker.class, "peakPickAcrossIMS", java.util.ArrayList.class);
		assertStableField(RawFileStructureTools.class, "METADATA_DATA_ACQUISITION_TYPE");
		assertStableField(RawFileStructureTools.class, "METADATA_IS_STAGGERED");
		assertStableMethod(RawFileStructureTools.class, "isStaggered", Map.class);
	}

	@Test
	void modelDeclarationsAreStable() {
		assertStableConstructor(Range.class, double.class, double.class);
		assertStableConstructor(Range.class, float.class, float.class);
		assertStableConstructor(WindowData.class, float.class, int.class);
		assertStableConstructor(PrecursorScan.class, String.class, int.class, float.class, int.class, double.class, double.class,
				Float.class, double[].class, float[].class, float[].class);
		assertStableConstructor(PrecursorScan.class, String.class, int.class, float.class, int.class, double.class, double.class,
				Float.class, double[].class, float[].class, float[].class, Float.class);
		assertStableConstructor(FragmentScan.class, String.class, String.class, int.class, double.class, float.class, int.class, Float.class,
				double.class, double.class, double[].class, float[].class, float[].class, byte.class, double.class, double.class);
		assertStableConstructor(FragmentScan.class, String.class, String.class, int.class, double.class, float.class, int.class, Float.class,
				double.class, double.class, double.class, double[].class, float[].class, float[].class, byte.class, double.class, double.class);
		assertStableConstructor(ScanSummary.class, String.class, int.class, float.class, int.class, double.class, boolean.class, Float.class,
				double.class, double.class, double.class, double.class, byte.class);
		assertStableConstructor(ScanSummary.class, String.class, int.class, float.class, int.class, float.class, double.class, boolean.class, Float.class,
				double.class, double.class, double.class, double.class, byte.class);
		assertStableConstructor(Pair.class, Object.class, Object.class);
		assertStableMethod(PrecursorScan.class, "getScanStartTime");
		assertStableMethod(PrecursorScan.class, "getTIC");
		assertStableMethod(FragmentScan.class, "getScanStartTime");
		assertStableMethod(FragmentScan.class, "sqrt");
		assertStableMethod(Range.class, "contains", double.class);
		assertStableMethod(Range.class, "contains", float.class);
		assertStableMethod(Range.class, "getMiddle");
		assertStableMethod(Range.class, "getStart");
		assertStableMethod(Range.class, "getStop");
		assertStableMethod(WindowData.class, "getAverageDutyCycle");
		assertStableMethod(ScanSummary.class, "getSpectrumIndex");
		assertStableMethod(ScanSummary.class, "getScanStartTime");
		assertStableMethod(ScanSummary.class, "getTic");
	}

	@Test
	void toleranceAndDemuxConfigurationAreStable() {
		assertStable(MassTolerance.class);
		assertStable(PPMMassTolerance.class);
		assertStable(org.searlelab.msrawjava.io.tims.TIMSMassTolerance.class);
		assertStableConstructor(MassTolerance.class);
		assertStableMethod(MassTolerance.class, "getToleranceInMz", double.class, double.class);
		assertStableMethod(MassTolerance.class, "compareTo", double.class, double.class);
		assertStableMethod(MassTolerance.class, "getIndices", double[].class, double.class);
		assertStableMethod(MassTolerance.class, "getIndices", gnu.trove.list.array.TDoubleArrayList.class, double.class);
		assertStableMethod(MassTolerance.class, "getIndices", org.searlelab.msrawjava.model.PeakInterface[].class,
				org.searlelab.msrawjava.model.PeakInterface.class);
		assertStableMethod(MassTolerance.class, "getIndices", java.util.List.class, org.searlelab.msrawjava.model.PeakInterface.class);
		assertStableConstructor(PPMMassTolerance.class, double.class);
		assertStableMethod(PPMMassTolerance.class, "getPpmTolerance");
		assertStableMethod(PPMMassTolerance.class, "getToleranceInMz", double.class, double.class);
		assertStableConstructor(org.searlelab.msrawjava.io.tims.TIMSMassTolerance.class);
		assertStableConstructor(org.searlelab.msrawjava.io.tims.TIMSMassTolerance.class, boolean.class);
		assertStableMethod(org.searlelab.msrawjava.io.tims.TIMSMassTolerance.class, "getToleranceInMz", double.class, double.class);
		assertStable(DemuxConfig.class);
		assertStable(DemuxConfig.InterpolationMethod.class);
		assertStable(DemuxConfig.Builder.class);
		assertStableField(DemuxConfig.class, "MIN_K");
		assertStableField(DemuxConfig.class, "MAX_K");
		assertStableField(DemuxConfig.class, "DEFAULT_K");
		assertStableField(DemuxConfig.class, "DEFAULT_INCLUDE_EDGE_SUBWINDOWS");
		assertStableField(DemuxConfig.InterpolationMethod.class, "CUBIC_HERMITE");
		assertStableField(DemuxConfig.InterpolationMethod.class, "LOG_QUADRATIC");
		assertStableConstructor(DemuxConfig.class);
		assertStableConstructor(DemuxConfig.class, int.class, DemuxConfig.InterpolationMethod.class);
		assertStableConstructor(DemuxConfig.class, int.class, DemuxConfig.InterpolationMethod.class, boolean.class);
		assertStableMethod(DemuxConfig.class, "getK");
		assertStableMethod(DemuxConfig.class, "getInterpolationMethod");
		assertStableMethod(DemuxConfig.class, "isIncludeEdgeSubWindows");
		assertStableMethod(DemuxConfig.class, "getNumCacheEntries");
		assertStableMethod(DemuxConfig.class, "toString");
		assertStableConstructor(DemuxConfig.Builder.class);
		assertStableMethod(DemuxConfig.Builder.class, "k", int.class);
		assertStableMethod(DemuxConfig.Builder.class, "interpolationMethod", DemuxConfig.InterpolationMethod.class);
		assertStableMethod(DemuxConfig.Builder.class, "useCubicHermite");
		assertStableMethod(DemuxConfig.Builder.class, "useLogQuadratic");
		assertStableMethod(DemuxConfig.Builder.class, "includeEdgeSubWindows", boolean.class);
		assertStableMethod(DemuxConfig.Builder.class, "excludeEdgeSubWindows");
		assertStableMethod(DemuxConfig.Builder.class, "build");
		assertStableMethod(DemuxConfig.class, "builder");
		Constructor<?>[] massToleranceConstructors=MassTolerance.class.getDeclaredConstructors();
		assertEquals(1, massToleranceConstructors.length);
		for (Constructor<?> constructor : massToleranceConstructors) {
			assertFalse(Modifier.isPublic(constructor.getModifiers()));
			assertEquals(Modifier.PROTECTED, constructor.getModifiers());
		}
	}

	@Test
	void legacyCompatibilityDeclarationsAreDeprecated() {
		assertDeprecatedMethod(ConversionParameters.class, "builder");
		assertDeprecated(ConversionParameters.Builder.class);
		assertDeprecatedMethod(RawFileConverters.class, "writeStandard", ProcessingThreadPool.class, StripeFileInterface.class, Path.class,
				ConversionParameters.class, ProgressIndicator.class);
		assertDeprecatedMethod(RawFileConverters.class, "writeDemux", ProcessingThreadPool.class, StripeFileInterface.class, Path.class,
				ConversionParameters.class, ProgressIndicator.class);
		assertDeprecatedMethod(RawFileConverters.class, "writeTims", ProcessingThreadPool.class, Path.class, Path.class, ConversionParameters.class,
				ProgressIndicator.class);
	}

	@Test
	void unrelatedDeclarationsRemainUnannotated() {
		assertNoAPI(ConversionParameters.class);
		assertNoAPI(RawFileConverters.class);
		assertNoAPI(method(RawFileConverters.class, "writeThermo", ProcessingThreadPool.class, Path.class, Path.class, ConversionParameters.class,
				ProgressIndicator.class));
		assertNoAPI(method(Range.class, "addBuffer", float.class));
	}

	private static void assertStable(AnnotatedElement element) {
		assertAPI(element, API.Status.STABLE);
	}

	private static void assertAllDeclaredMethodsStable(Class<?> type) {
		for (Method method : type.getDeclaredMethods()) {
			if (!method.isSynthetic()) assertStable(method);
		}
	}

	private static void assertDeprecated(AnnotatedElement element) {
		assertAPI(element, API.Status.DEPRECATED);
	}

	private static void assertNoAPI(AnnotatedElement element) {
		assertNull(element.getAnnotation(API.class), "Unexpected @API on "+element);
	}

	private static void assertStableMethod(Class<?> type, String name, Class<?>... parameterTypes) {
		assertStable(method(type, name, parameterTypes));
	}

	private static void assertStableField(Class<?> type, String name) {
		try {
			assertStable(type.getDeclaredField(name));
		} catch (NoSuchFieldException e) {
			throw new AssertionError(e);
		}
	}

	private static void assertDeprecatedMethod(Class<?> type, String name, Class<?>... parameterTypes) {
		assertDeprecated(method(type, name, parameterTypes));
	}

	private static void assertStableConstructor(Class<?> type, Class<?>... parameterTypes) {
		assertStable(constructor(type, parameterTypes));
	}

	private static void assertAPI(AnnotatedElement element, API.Status status) {
		API api=element.getAnnotation(API.class);
		assertNotNull(api, "Missing @API on "+element);
		assertEquals(status, api.status(), "Unexpected @API status on "+element);
		assertEquals(SINCE, api.since(), "Unexpected @API since on "+element);
	}

	private static Method method(Class<?> type, String name, Class<?>... parameterTypes) {
		try {
			return type.getDeclaredMethod(name, parameterTypes);
		} catch (NoSuchMethodException e) {
			throw new AssertionError(e);
		}
	}

	private static Constructor<?> constructor(Class<?> type, Class<?>... parameterTypes) {
		try {
			return type.getDeclaredConstructor(parameterTypes);
		} catch (NoSuchMethodException e) {
			throw new AssertionError(e);
		}
	}
}
