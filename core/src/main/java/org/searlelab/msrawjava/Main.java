package org.searlelab.msrawjava;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;

import org.searlelab.msrawjava.io.ExportParameters;
import org.searlelab.msrawjava.io.OutputType;
import org.searlelab.msrawjava.io.RawFileConverters;
import org.searlelab.msrawjava.io.VendorFileFinder;
import org.searlelab.msrawjava.io.VendorFiles;
import org.searlelab.msrawjava.io.thermo.ThermoRawFile;
import org.searlelab.msrawjava.io.thermo.ThermoServerPool;
import org.searlelab.msrawjava.logging.Logger;
import org.searlelab.msrawjava.logging.LoggingProgressIndicator;
import org.searlelab.msrawjava.threading.ProcessingThreadPool;

/**
 * Main is the command-line entry point for MSRawJava. It parses options, discovers vendor inputs via VendorFileFinder,
 * and selects the appropriate reader (e.g., BrukerTIMSFile or ThermoRawFile). The class coordinates batch
 * orchestration, logging, and deterministic serialization for reproducible runs.
 */
public class Main {
	/** Main CLI entry point for raw file conversion. */
	public static void main(String[] args) throws Exception {
		ExportParameters params=parseParametersFromCommandline(args);
		if (params==null) {
			System.exit(1);
		}
		convertKnownFiles(params);
		Logger.logLine("Finished processing, bye!");
	}

	/** Parses CLI flags for downstream processing. Can return null. If null, program should exit. */
	public static ExportParameters parseParametersFromCommandline(String[] args) {
		ArrayList<File> fileList=new ArrayList<File>();
		OutputType outType=OutputType.EncyclopeDIA; // default
		Path outputDirPath=null;
		float minimumMS1Intensity=3.0f;
		float minimumMS2Intensity=1.0f;

		System.out.println("Welcome to MSRawJava version "+Version.getVersion());
        for (int i = 0; i < args.length; i++) {
        	switch (args[i].toLowerCase()) {
        		case "-h":
        			System.out.println("Help (-h):");
        			System.out.println("  Specify Thermo .raw or Bruker .d files or any directories that contain those files.");
        			System.out.println("  You can specify files or directories. Paths can be relative.");
        			System.out.println();
        			System.out.println("Options:");
        			System.out.println("  -dia                  Produces EncyclopeDIA .DIA files (default)");
        			System.out.println("  -mgf                  Produces MGF files");
        			System.out.println("  -mzml                 Produces mzML files");
        			System.out.println("  -outputDirPath [path] Where new files get written (default same directory as input)");
        			System.out.println("  -minMS1Threshold [#]  Sets a minimum MS1 intensity threshold for timsTOF (default 3.0)");
        			System.out.println("  -minMS2Threshold [#]  Sets a minimum MS2 intensity threshold for timsTOF (default 1.0)");
        			System.out.println();
        			System.out.println("Examples:");
        			System.out.println("> java -jar MSRawJava path/to/raws/");
        			System.out.println("> java -jar MSRawJava -mgf ../../path/to/raws/");
        			System.out.println("> java -jar MSRawJava -mzml /mnt/vol1/path/to/raws/ -minMS1Threshold 10.0 -minMS2Threshold 5.0");

        			return null;
        			
        		case "-dia":
        		case "-encyclopedia":
        			outType=OutputType.EncyclopeDIA;
        			continue;
        			
        		case "-mgf":
        			outType=OutputType.mgf;
        			continue;
        			
        		case "-mzml":
        			outType=OutputType.mzml;
        			continue;
        			
        		case "-minms1threshold":
        			if (i + 1 < args.length) {
            			minimumMS1Intensity=Float.parseFloat(args[++i]);
        			} else {
        				Logger.errorLine("The option \"-minMS1Threshold\" requires a number afterwards.");
            			return null;
        			}
        			continue;
        			
        		case "-minms2threshold":
        			if (i + 1 < args.length) {
        				minimumMS2Intensity=Float.parseFloat(args[++i]);
        			} else {
        				Logger.errorLine("The option \"-minMS2Threshold\" requires a number afterwards.");
            			return null;
        			}
        			continue;
        			
        		case "-outputdirpath":
        			if (i + 1 < args.length) {
        				outputDirPath=new File(args[++i]).toPath();
        			} else {
        				Logger.errorLine("The option \"-outputDirPath\" requires a path afterwards.");
            			return null;
        			}
        			continue;
        			
        		default:
        			fileList.add(new File(args[i]));
        			continue;
        	}
        }
        System.out.println("Found "+fileList.size()+" starting paths, export format: "+outType);
		
		if (fileList.size()==0) {
			System.out.println("Access help through (-h).");
			return null;
		}
		
		ExportParameters params=new ExportParameters(fileList, outType, outputDirPath, minimumMS1Intensity, minimumMS2Intensity);
		return params;
	}

	/** Discovers vendor files and writes outputs using the selected format. */
	public static void convertKnownFiles(ExportParameters params) throws IOException, InterruptedException, Exception {
		ProcessingThreadPool pool=ProcessingThreadPool.createDefault();
		VendorFiles files=new VendorFiles();
		for (File f : params.getFileList()) {
			if (f.exists()&&f.canRead()) {
				VendorFileFinder.findAndAddRawAndD(f.toPath(), files);
			}
		}

		if (files.getThermoFiles().size()>0) {
			Logger.logLine("Found "+files.getThermoFiles().size()+" total Thermo Raw files");
			try {
				Logger.logLine("Setting up Thermo .raw reader...");
				ThermoServerPool.port();

				for (Path path : files.getThermoFiles()) {
					Logger.logLine("Processing .raw "+path);

					Path outputPath=params.getOutputDirPath()==null?path.getParent():params.getOutputDirPath();
					Logger.logLine("Writing "+params.getOutType()+" file to "+outputPath.toString());
										
					ThermoRawFile rawFile=new ThermoRawFile();		
					rawFile.openFile(path);

					RawFileConverters.writeStandard(pool, rawFile, outputPath, params.getOutType(), new LoggingProgressIndicator());
					Logger.logLine("Finished writing "+params.getOutType()+" file");
				}

			} finally {
				ThermoServerPool.shutdown();
			}
		}

		if (files.getBrukerDirs().size()>0) {
			Logger.logLine("Found "+files.getBrukerDirs().size()+" total timsTOF files");
			for (Path path : files.getBrukerDirs()) {
				Logger.logLine("Processing .d "+path);

				Path outputPath=params.getOutputDirPath()==null?path.getParent():params.getOutputDirPath();
				Logger.logLine("Writing "+params.getOutType()+" file to "+outputPath.toString());
				
				RawFileConverters.writeTims(pool, path, outputPath, params.getOutType(), new LoggingProgressIndicator(), params.getMinimumMS1Intensity(), params.getMinimumMS2Intensity());
				Logger.logLine("Finished writing "+params.getOutType()+" file");
			}
		}
		pool.close();
	}
}
