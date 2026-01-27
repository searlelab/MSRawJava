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

	// Tests for float array operations
	@Test
	void max_float_findsMaximum() {
		float[] v= {1.0f, 5.0f, 3.0f, -2.0f, 4.0f};
		assertEquals(5.0f, MatrixMath.max(v), 1e-6f);
	}

	@Test
	void max_float_withSingleElement() {
		float[] v= {42.0f};
		assertEquals(42.0f, MatrixMath.max(v), 1e-6f);
	}

	@Test
	void max_float_withAllNegative() {
		float[] v= {-5.0f, -2.0f, -10.0f};
		assertEquals(-2.0f, MatrixMath.max(v), 1e-6f);
	}

	@Test
	void min_float_findsMinimum() {
		float[] v= {1.0f, 5.0f, 3.0f, -2.0f, 4.0f};
		assertEquals(-2.0f, MatrixMath.min(v), 1e-6f);
	}

	@Test
	void min_float_withSingleElement() {
		float[] v= {42.0f};
		assertEquals(42.0f, MatrixMath.min(v), 1e-6f);
	}

	@Test
	void min_float_withAllPositive() {
		float[] v= {5.0f, 2.0f, 10.0f};
		assertEquals(2.0f, MatrixMath.min(v), 1e-6f);
	}

	@Test
	void mean_float_calculatesAverage() {
		float[] v= {1.0f, 2.0f, 3.0f, 4.0f};
		assertEquals(2.5f, MatrixMath.mean(v), 1e-6f);
	}

	@Test
	void mean_float_withSingleElement() {
		float[] v= {42.0f};
		assertEquals(42.0f, MatrixMath.mean(v), 1e-6f);
	}

	@Test
	void sum_float_calculatesSum() {
		float[] v= {1.0f, 2.0f, 3.0f, 4.0f};
		assertEquals(10.0f, MatrixMath.sum(v), 1e-6f);
	}

	// Tests for multiply/divide operations
	@Test
	void multiply_floatArray_byScalar() {
		float[] v= {1.0f, 2.0f, 3.0f};
		float[] result=MatrixMath.multiply(v, 2.5f);
		assertArrayEquals(new float[] {2.5f, 5.0f, 7.5f}, result, 1e-6f);
	}

	@Test
	void multiply_floatArray_byZero() {
		float[] v= {1.0f, 2.0f, 3.0f};
		float[] result=MatrixMath.multiply(v, 0.0f);
		assertArrayEquals(new float[] {0.0f, 0.0f, 0.0f}, result, 1e-6f);
	}

	@Test
	void multiply_doubleArray_byScalar() {
		double[] v= {1.0, 2.0, 3.0};
		double[] result=MatrixMath.multiply(v, 2.5);
		assertArrayEquals(new double[] {2.5, 5.0, 7.5}, result, 1e-12);
	}

	@Test
	void multiply_doubleArray_byNegative() {
		double[] v= {1.0, 2.0, 3.0};
		double[] result=MatrixMath.multiply(v, -1.0);
		assertArrayEquals(new double[] {-1.0, -2.0, -3.0}, result, 1e-12);
	}

	@Test
	void divide_floatArray_byScalar() {
		float[] v= {10.0f, 20.0f, 30.0f};
		float[] result=MatrixMath.divide(v, 2.0f);
		assertArrayEquals(new float[] {5.0f, 10.0f, 15.0f}, result, 1e-6f);
	}

	@Test
	void divide_floatArray_byLargeNumber() {
		float[] v= {1.0f, 2.0f, 3.0f};
		float[] result=MatrixMath.divide(v, 1000.0f);
		assertArrayEquals(new float[] {0.001f, 0.002f, 0.003f}, result, 1e-6f);
	}

	@Test
	void divide_doubleArray_byScalar() {
		double[] v= {10.0, 20.0, 30.0};
		double[] result=MatrixMath.divide(v, 2.0);
		assertArrayEquals(new double[] {5.0, 10.0, 15.0}, result, 1e-12);
	}

	@Test
	void divide_doubleArray_byNegative() {
		double[] v= {10.0, 20.0, 30.0};
		double[] result=MatrixMath.divide(v, -10.0);
		assertArrayEquals(new double[] {-1.0, -2.0, -3.0}, result, 1e-12);
	}

	// Tests for log10 operations
	@Test
	void log10_float_calculatesLogarithms() {
		float[] v= {1.0f, 10.0f, 100.0f, 1000.0f};
		float[] result=MatrixMath.log10(v);
		assertArrayEquals(new float[] {0.0f, 1.0f, 2.0f, 3.0f}, result, 1e-6f);
	}

	@Test
	void log10_float_withDecimalValues() {
		float[] v= {0.1f, 0.01f};
		float[] result=MatrixMath.log10(v);
		assertArrayEquals(new float[] {-1.0f, -2.0f}, result, 1e-6f);
	}

	@Test
	void log10_double_calculatesLogarithms() {
		double[] v= {1.0, 10.0, 100.0, 1000.0};
		double[] result=MatrixMath.log10(v);
		assertArrayEquals(new double[] {0.0, 1.0, 2.0, 3.0}, result, 1e-12);
	}

	@Test
	void log10_double_withDecimalValues() {
		double[] v= {0.1, 0.01, 0.001};
		double[] result=MatrixMath.log10(v);
		assertArrayEquals(new double[] {-1.0, -2.0, -3.0}, result, 1e-12);
	}

	// Tests for type conversion
	@Test
	void toDoubleArray_convertsFloatToDouble() {
		float[] floatArray= {1.5f, 2.5f, 3.5f};
		double[] doubleArray=MatrixMath.toDoubleArray(floatArray);
		assertArrayEquals(new double[] {1.5, 2.5, 3.5}, doubleArray, 1e-12);
	}

	@Test
	void toDoubleArray_withEmptyArray() {
		float[] floatArray= {};
		double[] doubleArray=MatrixMath.toDoubleArray(floatArray);
		assertEquals(0, doubleArray.length);
	}

	@Test
	void toDoubleArray_withSingleElement() {
		float[] floatArray= {42.0f};
		double[] doubleArray=MatrixMath.toDoubleArray(floatArray);
		assertArrayEquals(new double[] {42.0}, doubleArray, 1e-12);
	}

	@Test
	void toFloatArray_convertsDoubleToFloat() {
		double[] doubleArray= {1.5, 2.5, 3.5};
		float[] floatArray=MatrixMath.toFloatArray(doubleArray);
		assertArrayEquals(new float[] {1.5f, 2.5f, 3.5f}, floatArray, 1e-6f);
	}

	@Test
	void toFloatArray_withEmptyArray() {
		double[] doubleArray= {};
		float[] floatArray=MatrixMath.toFloatArray(doubleArray);
		assertEquals(0, floatArray.length);
	}

	@Test
	void toFloatArray_withSingleElement() {
		double[] doubleArray= {42.0};
		float[] floatArray=MatrixMath.toFloatArray(doubleArray);
		assertArrayEquals(new float[] {42.0f}, floatArray, 1e-6f);
	}

	// Tests for edge cases
	@Test
	void max_double_withEmptyArray() {
		double[] v= {};
		// Should return -Double.MAX_VALUE for empty array
		assertEquals(-Double.MAX_VALUE, MatrixMath.max(v), 1e-12);
	}

	@Test
	void min_double_withEmptyArray() {
		double[] v= {};
		// Should return Double.MAX_VALUE for empty array
		assertEquals(Double.MAX_VALUE, MatrixMath.min(v), 1e-12);
	}

	@Test
	void max_float_withEmptyArray() {
		float[] v= {};
		// Should return -Float.MAX_VALUE for empty array
		assertEquals(-Float.MAX_VALUE, MatrixMath.max(v), 1e-6f);
	}

	@Test
	void min_float_withEmptyArray() {
		float[] v= {};
		// Should return Float.MAX_VALUE for empty array
		assertEquals(Float.MAX_VALUE, MatrixMath.min(v), 1e-6f);
	}

	@Test
	void getRange_withEqualValues() {
		double[] v= {5.0, 5.0, 5.0};
		assertEquals(0.0, MatrixMath.getRange(v), 1e-12);
	}

	@Test
	void getRange_withSingleElement() {
		double[] v= {42.0};
		assertEquals(0.0, MatrixMath.getRange(v), 1e-12);
	}

	@Test
	void getRange_withNegativeValues() {
		double[] v= {-10.0, -5.0, -20.0};
		assertEquals(15.0, MatrixMath.getRange(v), 1e-12);
	}

	@Test
	void multiply_doubleVector_dotProduct_withZeroResult() {
		double[] a= {1.0, 0.0, -1.0};
		double[] b= {-1.0, 5.0, 1.0};
		assertEquals(-2.0, MatrixMath.multiply(a, b), 1e-12);
	}

	@Test
	void multiply_doubleVector_dotProduct_orthogonal() {
		double[] a= {1.0, 0.0, 0.0};
		double[] b= {0.0, 1.0, 0.0};
		assertEquals(0.0, MatrixMath.multiply(a, b), 1e-12);
	}

	@Test
	void invert_identityMatrix_returnsIdentity() {
		double[][] identity= {{1.0, 0.0}, {0.0, 1.0}};
		double[][] inv=MatrixMath.invert(new double[][] {{1.0, 0.0}, {0.0, 1.0}});
		assertMatrixEquals(identity, inv, 1e-9);
	}

	@Test
	void invert_3x3Matrix() {
		// Matrix with known inverse
		double[][] A= {{1.0, 2.0, 3.0}, {0.0, 1.0, 4.0}, {5.0, 6.0, 0.0}};
		// Need to make a copy because invert() modifies the input matrix
		double[][] ACopy= {{1.0, 2.0, 3.0}, {0.0, 1.0, 4.0}, {5.0, 6.0, 0.0}};
		double[][] inv=MatrixMath.invert(ACopy);
		double[][] product=MatrixMath.multiply(A, inv);

		// Verify A * A^-1 = I
		double[][] identity= {{1.0, 0.0, 0.0}, {0.0, 1.0, 0.0}, {0.0, 0.0, 1.0}};
		assertMatrixEquals(identity, product, 1e-9);
	}

	@Test
	void multiply_matrixVector_withZeroVector() {
		double[][] A= {{1.0, 2.0}, {3.0, 4.0}};
		double[] v= {0.0, 0.0};
		assertArrayEquals(new double[] {0.0, 0.0}, MatrixMath.multiply(A, v), 1e-12);
	}

	@Test
	void subtract_vectorVector_withEqualVectors() {
		double[] a= {1.0, 2.0, 3.0};
		double[] b= {1.0, 2.0, 3.0};
		assertArrayEquals(new double[] {0.0, 0.0, 0.0}, MatrixMath.subtract(a, b), 1e-12);
	}

	@Test
	void add_vectorVector_withZeroVector() {
		double[] a= {1.0, 2.0, 3.0};
		double[] b= {0.0, 0.0, 0.0};
		assertArrayEquals(new double[] {1.0, 2.0, 3.0}, MatrixMath.add(a, b), 1e-12);
	}

	@Test
	void transpose_singleRowMatrix() {
		double[][] m= {{1.0, 2.0, 3.0}};
		double[][] t=MatrixMath.transpose(m);
		assertEquals(3, t.length);
		assertEquals(1, t[0].length);
		assertArrayEquals(new double[] {1.0}, t[0], 1e-12);
		assertArrayEquals(new double[] {2.0}, t[1], 1e-12);
		assertArrayEquals(new double[] {3.0}, t[2], 1e-12);
	}

	@Test
	void transpose_singleColumnMatrix() {
		double[][] m= {{1.0}, {2.0}, {3.0}};
		double[][] t=MatrixMath.transpose(m);
		assertEquals(1, t.length);
		assertEquals(3, t[0].length);
		assertArrayEquals(new double[] {1.0, 2.0, 3.0}, t[0], 1e-12);
	}

	@Test
	void getColumn_fromRectangularMatrix() {
		double[][] m= {{1.0, 2.0, 3.0}, {4.0, 5.0, 6.0}, {7.0, 8.0, 9.0}};
		assertArrayEquals(new double[] {1.0, 4.0, 7.0}, MatrixMath.getColumn(m, 0), 1e-12);
		assertArrayEquals(new double[] {3.0, 6.0, 9.0}, MatrixMath.getColumn(m, 2), 1e-12);
	}

	@Test
	void print_doubleArray_emptyArray() {
		double[] v= {};
		MatrixMath.print(v);
		String out=stdout();
		assertTrue(out.contains("[]"));
	}

	@Test
	void print_floatArray_emptyArray() {
		float[] v= {};
		MatrixMath.print(v);
		String out=stdout();
		assertTrue(out.contains("[]"));
	}
}
