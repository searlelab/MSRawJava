package org.searlelab.msrawjava.algorithms;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MatrixMathTest {

	private PrintStream origOut;
	private ByteArrayOutputStream outBuf;

	@BeforeEach
	void setup() {
		origOut=System.out;
		outBuf=new ByteArrayOutputStream();
		System.setOut(new PrintStream(outBuf, true, StandardCharsets.UTF_8));
	}

	@AfterEach
	void teardown() {
		System.setOut(origOut);
	}

	private static void assertMatrixEquals(double[][] expected, double[][] actual, double eps) {
		assertEquals(expected.length, actual.length, "row count");
		for (int i=0; i<expected.length; i++) {
			assertArrayEquals(expected[i], actual[i], eps, "row "+i);
		}
	}

	private String stdout() {
		return outBuf.toString(StandardCharsets.UTF_8);
	}

	@Test
	void max_min_range_mean_sum() {
		double[] v= {1.0, -2.0, 5.0, 3.0};
		assertEquals(5.0, MatrixMath.max(v), 0.0);
		assertEquals(-2.0, MatrixMath.min(v), 0.0);
		assertEquals(7.0, MatrixMath.sum(v), 1e-12);
		assertEquals(7.0/4.0, MatrixMath.mean(v), 1e-12);
		assertEquals(7.0, MatrixMath.getRange(new double[] {0.0, 7.0}), 1e-12);

		float[] vf= {1f, 2f, 3f};
		assertEquals(6f, MatrixMath.sum(vf), 1e-6);
	}

	@Test
	void print_variants_emitBracketedLists() {
		double[][] m2= {{1.0, 2.0}, {3.0, 4.0}};
		MatrixMath.print(m2);
		String out1=stdout();
		assertTrue(out1.contains("[1.0, 2.0]"));
		assertTrue(out1.contains("[3.0, 4.0]"));
		outBuf.reset();

		float[] af= {1f, 2f, 3f};
		MatrixMath.print(af);
		String out2=stdout();
		assertTrue(out2.contains("[1.0, 2.0, 3.0]"));
		outBuf.reset();

		double[] ad= {4.0, 5.0};
		MatrixMath.print(ad);
		String out3=stdout();
		assertTrue(out3.contains("[4.0, 5.0]"));
	}

	@Test
	void transpose_getColumn() {
		double[][] m= {{1.0, 2.0, 3.0}, {4.0, 5.0, 6.0}}; // 2x3
		double[][] t=MatrixMath.transpose(m); // 3x2
		assertEquals(3, t.length);
		assertEquals(2, t[0].length);
		assertArrayEquals(new double[] {1.0, 4.0}, t[0], 1e-12);
		assertArrayEquals(new double[] {2.0, 5.0}, t[1], 1e-12);
		assertArrayEquals(new double[] {3.0, 6.0}, t[2], 1e-12);

		assertArrayEquals(new double[] {2.0, 5.0}, MatrixMath.getColumn(m, 1), 1e-12);
	}

	@Test
	void matrixAndScalarMultiplication_andDivision_andDot_andMatVec() {
		double[][] A= {{1.0, 2.0, 3.0}, {4.0, 5.0, 6.0}}; // 2x3
		double[][] B= {{7.0, 8.0}, {9.0, 10.0}, {11.0, 12.0}}; // 3x2

		// A * B
		double[][] AB_expected= {{58.0, 64.0}, {139.0, 154.0}};
		assertMatrixEquals(AB_expected, MatrixMath.multiply(A, B), 1e-12);

		// scalar multiply & divide
		double[][] twoA=MatrixMath.multiply(A, 2.0);
		assertMatrixEquals(new double[][] {{2, 4, 6}, {8, 10, 12}}, twoA, 1e-12);
		double[][] halfA=MatrixMath.divide(A, 2.0);
		assertMatrixEquals(new double[][] {{0.5, 1.0, 1.5}, {2.0, 2.5, 3.0}}, halfA, 1e-12);

		// matrix-vector
		double[] v= {1.0, 2.0, 3.0};
		assertArrayEquals(new double[] {14.0, 32.0}, MatrixMath.multiply(A, v), 1e-12);

		// dot product
		assertEquals(32.0, MatrixMath.multiply(new double[] {1, 2, 3}, new double[] {4, 5, 6}), 1e-12);
	}

	@Test
	void add_and_subtract_variants() {
		double[] a= {1.0, 2.0, 3.0};
		double[] b= {4.0, 5.0, 6.0};
		assertArrayEquals(new double[] {5.0, 7.0, 9.0}, MatrixMath.add(a, b), 1e-12);
		assertArrayEquals(new double[] {-3.0, -3.0, -3.0}, MatrixMath.subtract(a, b), 1e-12);

		double[][] M= {{1, 2, 3}, {4, 5, 6}};
		double[][] Ones= {{1, 1, 1}, {1, 1, 1}};
		assertMatrixEquals(new double[][] {{0, 1, 2}, {3, 4, 5}}, MatrixMath.subtract(M, Ones), 1e-12);

		assertMatrixEquals(new double[][] {{0, 0, 0}, {3, 3, 3}}, MatrixMath.subtract(M, new double[] {1, 2, 3}), 1e-12);
	}

	@Test
	void invert_multipliedReturnsIdentity_withPivotingGaussian() {
		double[][] A= {{4.0, 7.0}, {2.0, 6.0}};
		double[][] inv=MatrixMath.invert(new double[][] {{4.0, 7.0}, {2.0, 6.0}});
		double[][] I=MatrixMath.multiply(A, inv);
		double[][] expectedI= {{1.0, 0.0}, {0.0, 1.0}};
		assertMatrixEquals(expectedI, I, 1e-9);
	}

	@Test
	void gaussian_pivotsOnZeroLeading_toAvoidZeroPivot() {
		double[][] A= {{0.0, 1.0}, {1.0, 0.0}};
		int[] index=new int[2];
		MatrixMath.gaussian(A, index);
		// Expect first pivot row to be row 1 (index 1) because A[0][0] is zero
		assertEquals(1, index[0]);
		// Ensure the chosen pivot element is non-zero
		assertTrue(Math.abs(A[index[0]][0])>0.0);
	}

	@Test
	void covarianceMatrix_onSimpleCorrelatedData() {
		double[][] data= {{1.0, 2.0}, {3.0, 4.0}, {5.0, 6.0}};
		double[][] cov=MatrixMath.calculateCovarianceMatrix(data);
		double v=8.0/3.0; // variance and covariance for this simple data
		assertMatrixEquals(new double[][] {{v, v}, {v, v}}, cov, 1e-9);
	}
}
