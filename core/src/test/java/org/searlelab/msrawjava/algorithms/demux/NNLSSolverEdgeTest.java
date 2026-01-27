package org.searlelab.msrawjava.algorithms.demux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.ejml.data.DMatrixRMaj;
import org.junit.jupiter.api.Test;

class NNLSSolverEdgeTest {

	@Test
	void solveLeastSquaresHandlesZeroColumns() throws Exception {
		NNLSSolver solver=new NNLSSolver();
		DMatrixRMaj A=new DMatrixRMaj(2, 0);
		DMatrixRMaj b=new DMatrixRMaj(new double[][] {{1.0}, {2.0}});

		Method method=NNLSSolver.class.getDeclaredMethod("solveLeastSquares", DMatrixRMaj.class, DMatrixRMaj.class);
		method.setAccessible(true);
		DMatrixRMaj x=(DMatrixRMaj)method.invoke(solver, A, b);

		assertEquals(0, x.numRows);
		assertEquals(1, x.numCols);
	}

	@Test
	void solveMultipleHandlesEmptyObservationMatrix() {
		NNLSSolver solver=new NNLSSolver();
		DMatrixRMaj A=new DMatrixRMaj(new double[][] {{1.0, 0.0}, {0.0, 1.0}});
		DMatrixRMaj B=new DMatrixRMaj(2, 0);

		DMatrixRMaj X=solver.solveMultiple(A, B);
		assertEquals(2, X.numRows);
		assertEquals(0, X.numCols);
		assertTrue(X.data.length==0||X.getNumElements()==0);
	}
}
