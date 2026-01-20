package org.searlelab.msrawjava.algorithms.demux;

import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.factory.LinearSolverFactory_DDRM;
import org.ejml.interfaces.linsol.LinearSolverDense;

import java.util.Arrays;

/**
 * Non-Negative Least Squares (NNLS) solver using the Lawson-Hanson algorithm.
 *
 * Solves the problem:
 * minimize ||Ax - b||_2^2
 * subject to x >= 0
 *
 * The Lawson-Hanson algorithm maintains two index sets:
 * - P (passive): indices where the solution can be positive
 * - Z (active): indices constrained to zero
 *
 * It iteratively moves indices between these sets until the optimality
 * conditions are satisfied.
 *
 * Reference: Lawson & Hanson, "Solving Least Squares Problems", 1974
 */
public class NNLSSolver {

	private static final double EPSILON=1e-10;
	private static final int DEFAULT_MAX_ITERATIONS=50;

	private final int maxIterations;

	/**
	 * Creates a solver with default maximum iterations (50).
	 */
	public NNLSSolver() {
		this(DEFAULT_MAX_ITERATIONS);
	}

	/**
	 * Creates a solver with the specified maximum iterations.
	 *
	 * @param maxIterations
	 *            maximum iterations for the active set algorithm
	 */
	public NNLSSolver(int maxIterations) {
		this.maxIterations=maxIterations;
	}

	/**
	 * Solves the NNLS problem: minimize ||Ax - b||_2 subject to x >= 0.
	 *
	 * @param A
	 *            the design matrix (m × n)
	 * @param b
	 *            the observation vector (m × 1)
	 * @return the solution vector x (n × 1) with all non-negative entries
	 */
	public DMatrixRMaj solve(DMatrixRMaj A, DMatrixRMaj b) {
		int m=A.numRows;
		int n=A.numCols;

		// Initialize solution to zero
		DMatrixRMaj x=new DMatrixRMaj(n, 1);

		// Active set Z (indices constrained to zero) - all active initially
		boolean[] inZ=new boolean[n];
		Arrays.fill(inZ, true);

		// Passive set P (indices that can be positive) - empty initially
		// inZ[i] == false means index i is in P

		// Precompute A^T
		DMatrixRMaj At=new DMatrixRMaj(n, m);
		CommonOps_DDRM.transpose(A, At);

		// Precompute A^T * A and A^T * b
		DMatrixRMaj AtA=new DMatrixRMaj(n, n);
		DMatrixRMaj Atb=new DMatrixRMaj(n, 1);
		CommonOps_DDRM.mult(At, A, AtA);
		CommonOps_DDRM.mult(At, b, Atb);

		// w = A^T * (b - A*x) = A^T*b - A^T*A*x = Atb (since x=0)
		DMatrixRMaj w=Atb.copy();

		int iteration=0;
		while (iteration<maxIterations*n) {
			iteration++;

			// Find index in Z with largest w[i]
			int maxIdx=-1;
			double maxW=EPSILON;
			for (int i=0; i<n; i++) {
				if (inZ[i]&&w.get(i, 0)>maxW) {
					maxW=w.get(i, 0);
					maxIdx=i;
				}
			}

			// If no positive w[i] in Z, we're done
			if (maxIdx<0) {
				break;
			}

			// Move index maxIdx from Z to P
			inZ[maxIdx]=false;

			// Inner loop: solve restricted problem and adjust
			while (true) {
				// Count passive indices
				int pCount=0;
				for (int i=0; i<n; i++) {
					if (!inZ[i]) pCount++;
				}

				if (pCount==0) break;

				// Build the restricted problem: A_P * z_P = b
				int[] pIndices=new int[pCount];
				int idx=0;
				for (int i=0; i<n; i++) {
					if (!inZ[i]) pIndices[idx++]=i;
				}

				// Extract columns of A corresponding to P
				DMatrixRMaj Ap=new DMatrixRMaj(m, pCount);
				for (int j=0; j<pCount; j++) {
					for (int i=0; i<m; i++) {
						Ap.set(i, j, A.get(i, pIndices[j]));
					}
				}

				// Solve least squares: Ap * zp = b
				DMatrixRMaj zp=solveLeastSquares(Ap, b);

				// Check if all z_P are positive
				boolean allPositive=true;
				for (int i=0; i<pCount; i++) {
					if (zp.get(i, 0)<=EPSILON) {
						allPositive=false;
						break;
					}
				}

				if (allPositive) {
					// Set x_P = z_P, x_Z = 0
					x.zero();
					for (int i=0; i<pCount; i++) {
						x.set(pIndices[i], 0, zp.get(i, 0));
					}
					break;
				} else {
					// Find alpha and move indices from P to Z
					double alpha=Double.MAX_VALUE;
					int moveIdx=-1;

					for (int i=0; i<pCount; i++) {
						if (zp.get(i, 0)<=EPSILON) {
							double xi=x.get(pIndices[i], 0);
							double zi=zp.get(i, 0);
							double ratio=xi/(xi-zi);
							if (ratio<alpha) {
								alpha=ratio;
								moveIdx=pIndices[i];
							}
						}
					}

					if (alpha<0) alpha=0;
					if (alpha>1) alpha=1;

					// x = x + alpha * (z - x)
					for (int i=0; i<pCount; i++) {
						int pi=pIndices[i];
						double xi=x.get(pi, 0);
						double zi=zp.get(i, 0);
						double newVal=xi+alpha*(zi-xi);
						x.set(pi, 0, Math.max(0, newVal));
					}

					// Move indices with x[i] ≈ 0 from P to Z
					for (int i=0; i<n; i++) {
						if (!inZ[i]&&x.get(i, 0)<=EPSILON) {
							inZ[i]=true;
							x.set(i, 0, 0);
						}
					}
				}
			}

			// Update w = A^T * (b - A*x)
			DMatrixRMaj Ax=new DMatrixRMaj(m, 1);
			CommonOps_DDRM.mult(A, x, Ax);
			DMatrixRMaj residual=new DMatrixRMaj(m, 1);
			CommonOps_DDRM.subtract(b, Ax, residual);
			CommonOps_DDRM.mult(At, residual, w);
		}

		// Ensure non-negativity
		for (int i=0; i<n; i++) {
			if (x.get(i, 0)<0) {
				x.set(i, 0, 0);
			}
		}

		return x;
	}

	/**
	 * Solves a standard least squares problem using QR decomposition.
	 */
	private DMatrixRMaj solveLeastSquares(DMatrixRMaj A, DMatrixRMaj b) {
		int m=A.numRows;
		int n=A.numCols;

		if (n==0) {
			return new DMatrixRMaj(0, 1);
		}

		// Use EJML's linear solver
		LinearSolverDense<DMatrixRMaj> solver=LinearSolverFactory_DDRM.leastSquares(m, n);

		DMatrixRMaj x=new DMatrixRMaj(n, 1);

		if (solver.setA(A.copy())) {
			solver.solve(b, x);
		} else {
			// Fallback: use pseudoinverse via normal equations
			// A^T A x = A^T b
			DMatrixRMaj AtA=new DMatrixRMaj(n, n);
			DMatrixRMaj Atb=new DMatrixRMaj(n, 1);
			DMatrixRMaj At=new DMatrixRMaj(n, m);
			CommonOps_DDRM.transpose(A, At);
			CommonOps_DDRM.mult(At, A, AtA);
			CommonOps_DDRM.mult(At, b, Atb);

			// Add small regularization for numerical stability
			for (int i=0; i<n; i++) {
				AtA.add(i, i, EPSILON);
			}

			LinearSolverDense<DMatrixRMaj> normalSolver=LinearSolverFactory_DDRM.linear(n);
			if (normalSolver.setA(AtA)) {
				normalSolver.solve(Atb, x);
			}
		}

		return x;
	}

	/**
	 * Solves multiple NNLS problems with the same design matrix.
	 * Each column of B is a separate observation vector.
	 *
	 * @param A
	 *            the design matrix (m × n)
	 * @param B
	 *            the observation matrix (m × p), each column is a separate problem
	 * @return the solution matrix X (n × p), each column is the solution for the corresponding column of B
	 */
	public DMatrixRMaj solveMultiple(DMatrixRMaj A, DMatrixRMaj B) {
		int n=A.numCols;
		int p=B.numCols;

		DMatrixRMaj X=new DMatrixRMaj(n, p);
		DMatrixRMaj b=new DMatrixRMaj(B.numRows, 1);
		DMatrixRMaj x;

		for (int j=0; j<p; j++) {
			// Extract column j from B
			CommonOps_DDRM.extractColumn(B, j, b);
			// Solve NNLS
			x=solve(A, b);
			// Insert into result matrix
			for (int i=0; i<n; i++) {
				X.set(i, j, x.get(i, 0));
			}
		}

		return X;
	}
}
