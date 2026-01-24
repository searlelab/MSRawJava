package org.searlelab.msrawjava.algorithms.demux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import org.ejml.data.DMatrixRMaj;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.searlelab.msrawjava.model.Range;

class DemuxDesignMatrixEdgeTest {

	private PrintStream originalOut;

	@BeforeEach
	void setUp() {
		originalOut=System.out;
	}

	@AfterEach
	void tearDown() {
		System.setOut(originalOut);
	}

	@Test
	void boundaryToleranceMergesNearlyIdenticalEdges() {
		ArrayList<Range> windows=new ArrayList<>();
		windows.add(new Range(400.0f, 410.0f));
		windows.add(new Range(410.005f, 420.0f)); // within tolerance of 0.01

		DemuxDesignMatrix matrix=new DemuxDesignMatrix(windows);
		assertEquals(2, matrix.getNumSubWindows());
		assertEquals(2, matrix.getNumAcquiredPositions());
	}

	@Test
	void rowIndexClampsToBounds() {
		ArrayList<Range> windows=new ArrayList<>();
		windows.add(new Range(400.0f, 420.0f));
		windows.add(new Range(420.0f, 440.0f));
		windows.add(new Range(440.0f, 460.0f));

		DemuxDesignMatrix matrix=new DemuxDesignMatrix(windows);
		assertEquals(0, matrix.getRowIndex(350.0));
		assertEquals(2, matrix.getRowIndex(1000.0));
	}

	@Test
	void extractLocalMatrixPadsWhenFewRows() {
		ArrayList<Range> windows=new ArrayList<>();
		windows.add(new Range(400.0f, 420.0f));
		windows.add(new Range(410.0f, 430.0f));

		DemuxDesignMatrix matrix=new DemuxDesignMatrix(windows);
		DMatrixRMaj local=matrix.extractLocalMatrix(0, 5);
		assertEquals(5, local.numRows);
		assertEquals(5, local.numCols);
	}

	@Test
	void printMatrixEmitsHeader() {
		ArrayList<Range> windows=new ArrayList<>();
		windows.add(new Range(400.0f, 420.0f));
		windows.add(new Range(410.0f, 430.0f));

		DemuxDesignMatrix matrix=new DemuxDesignMatrix(windows);
		ByteArrayOutputStream outBytes=new ByteArrayOutputStream();
		System.setOut(new PrintStream(outBytes, true, StandardCharsets.UTF_8));

		matrix.printMatrix();

		String out=outBytes.toString(StandardCharsets.UTF_8);
		assertTrue(out.contains("Design Matrix"));
	}
}
