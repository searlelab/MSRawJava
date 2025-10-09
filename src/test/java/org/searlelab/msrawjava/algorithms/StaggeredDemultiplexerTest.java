package org.searlelab.msrawjava.algorithms;

import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.io.thermo.ThermoRawFile;
import org.searlelab.msrawjava.io.thermo.ThermoServerPool;

class StaggeredDemultiplexerTest {
	
	@Test
	void testDDASubRanges() throws Exception {
		ThermoRawFile file=new ThermoRawFile();
		
		file.openFile(Paths.get("/Users/searle.brian/Documents/temp/thermo/HeLa_BCS_GPFDIA_90min_1.raw"));
		
		long time=System.currentTimeMillis();
		StaggeredDemultiplexer.getSubRanges(file);
		System.out.println(System.currentTimeMillis()-time);
		file.close();
		
		ThermoServerPool.shutdown();
	}
	
}
