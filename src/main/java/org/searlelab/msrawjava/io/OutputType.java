package org.searlelab.msrawjava.io;

import java.nio.file.Path;
import java.rmi.UnexpectedException;

import org.searlelab.msrawjava.io.encyclopedia.EncyclopeDIAFile;

public enum OutputType {
	EncyclopeDIA, mgf, mzml;

	public OutputSpectrumFile getOutputSpectrumFile() throws Exception {
		switch (this) {
			case EncyclopeDIA:
				EncyclopeDIAFile dia=new EncyclopeDIAFile();
				dia.openFile();
				return dia;
				
			case mgf:
				MGFOutputFile mgf=new MGFOutputFile();
				mgf.openFile();
				return mgf;
				
			case mzml:
				MZMLOutputFile mzml=new MZMLOutputFile();
				mzml.openFile();
				return mzml;

			default:
				throw new UnexpectedException("Unknown file type "+this);
		}
	}
	
	public Path getOutputFilePath(Path outputDirPath, String filename) {
		switch (this) {
			case EncyclopeDIA:
				return outputDirPath.resolve(changeExtension(filename, EncyclopeDIAFile.DIA_EXTENSION));
			case mgf:
				return outputDirPath.resolve(changeExtension(filename, MGFOutputFile.MGF_EXTENSION));
			case mzml:
				return outputDirPath.resolve(changeExtension(filename, MZMLOutputFile.MZML_EXTENSION));

			default:
				throw new RuntimeException("Unknown file type "+this);
		}
	}

	private static String changeExtension(String filename, String newExtension) {
		int i=filename.lastIndexOf('.');
		if (i>0) {
			String name=filename.substring(0, i);
			return name+newExtension;
		} else {
			return filename+newExtension;
		}
	}
}
