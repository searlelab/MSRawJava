package org.searlelab.msrawjava.algorithms;


public class MatrixMath {
	
	public static float max(float[] v) {
		float max=-Float.MAX_VALUE;
		for (int i=0; i<v.length; i++) {
			if (v[i]>max) {
				max=v[i];
			}
		}
		return max;
	}
	
	public static float min(float[] v) {
		float min=Float.MAX_VALUE;
		for (int i=0; i<v.length; i++) {
			if (v[i]<min) {
				min=v[i];
			}
		}
		return min;
	}
	
	public static double max(double[] v) {
		double max=-Double.MAX_VALUE;
		for (int i=0; i<v.length; i++) {
			if (v[i]>max) {
				max=v[i];
			}
		}
		return max;
	}
	
	public static double min(double[] v) {
		double min=Double.MAX_VALUE;
		for (int i=0; i<v.length; i++) {
			if (v[i]<min) {
				min=v[i];
			}
		}
		return min;
	}

	public static void print(double[][] m) {
		for (int i=0; i<m.length; i++) {
			System.out.print("[");
			for (int j=0; j<m[i].length; j++) {
				if (j>0) System.out.print(", ");
				System.out.print(m[i][j]);
			}
			System.out.println("]");
		}
	}

	public static void print(float[] m) {
		System.out.print("[");
		for (int i=0; i<m.length; i++) {
			if (i>0) System.out.print(", ");
			System.out.print(m[i]);
		}
		System.out.println("]");
	}
	public static void print(double[] m) {
		System.out.print("[");
		for (int i=0; i<m.length; i++) {
			if (i>0) System.out.print(", ");
			System.out.print(m[i]);
		}
		System.out.println("]");
	}

	public static double[][] calculateCovarianceMatrix(double[][] matrix) {
		double[] mean=new double[matrix[0].length];
		for (int i=0; i<mean.length; i++) {
			mean[i]=mean(MatrixMath.getColumn(matrix, i));
		}
		
		double[][] normalized=MatrixMath.subtract(matrix, mean);
		double[][] transpose=MatrixMath.transpose(normalized);
		
		int n=normalized.length;
		return MatrixMath.divide(MatrixMath.multiply(transpose, normalized), n);
	}
	
	public static double mean(double[] v) {
		double sum=sum(v);
		return sum/v.length;
	}
	
	public static float mean(float[] v) {
		float sum=sum(v);
		return sum/v.length;
	}

	public static double sum(double[] v) {
		double sum=0.0;
		for (int i=0; i<v.length; i++) {
			sum+=v[i];
		}
		return sum;
	}

	public static float sum(float[] v) {
		float sum=0.0f;
		for (int i=0; i<v.length; i++) {
			sum+=v[i];
		}
		return sum;
	}

	public static double[] multiply(double[] v1, double m) {
		double[] r=new double[v1.length];
		for (int i=0; i<r.length; i++) {
			r[i]=v1[i]*m;
		}
		return r;
	}

	public static float[] multiply(float[] v1, float m) {
		float[] r=new float[v1.length];
		for (int i=0; i<r.length; i++) {
			r[i]=v1[i]*m;
		}
		return r;
	}

	public static double[] divide(double[] v1, double m) {
		double[] r=new double[v1.length];
		for (int i=0; i<r.length; i++) {
			r[i]=v1[i]/m;
		}
		return r;
	}

	public static float[] divide(float[] v1, float m) {
		float[] r=new float[v1.length];
		for (int i=0; i<r.length; i++) {
			r[i]=v1[i]/m;
		}
		return r;
	}
	
	public static double[] toDoubleArray(float[] a) {
		double[] f=new double[a.length];
		for (int i=0; i<f.length; i++) {
			f[i]=a[i];
		}
		return f;
	}
	
	public static float[] toFloatArray(double[] a) {
		float[] f=new float[a.length];
		for (int i=0; i<f.length; i++) {
			f[i]=(float)a[i];
		}
		return f;
	}

	public static float[] log10(float[] v) {
		float[] r=new float[v.length];
		for (int i=0; i<r.length; i++) {
			r[i]=(float)Math.log10(v[i]);
		}
		return r;
	}

	public static double[] log10(double[] v) {
		double[] r=new double[v.length];
		for (int i=0; i<r.length; i++) {
			r[i]=Math.log10(v[i]);
		}
		return r;
	}

	/**
	 * from http://www.sanfoundry.com/java-program-find-inverse-matrix/
	 * @param a
	 * @return
	 */
	public static double[][] invert(double[][] a) {
		int n=a.length;
		double x[][]=new double[n][n];
		double b[][]=new double[n][n];
		int index[]=new int[n];
		for (int i=0; i<n; ++i)
			b[i][i]=1;

		// Transform the matrix into an upper triangle
		gaussian(a, index);

		// Update the matrix b[i][j] with the ratios stored
		for (int i=0; i<n-1; ++i)
			for (int j=i+1; j<n; ++j)
				for (int k=0; k<n; ++k)
					b[index[j]][k]-=a[index[j]][i]*b[index[i]][k];

		// Perform backward substitutions
		for (int i=0; i<n; ++i) {
			x[n-1][i]=b[index[n-1]][i]/a[index[n-1]][n-1];
			for (int j=n-2; j>=0; --j) {
				x[j][i]=b[index[j]][i];
				for (int k=j+1; k<n; ++k) {
					x[j][i]-=a[index[j]][k]*x[k][i];
				}
				x[j][i]/=a[index[j]][j];
			}
		}
		return x;
	}

	// Method to carry out the partial-pivoting Gaussian
	// elimination. Here index[] stores pivoting order.

	public static void gaussian(double a[][], int index[]) {
		int n=index.length;
		double c[]=new double[n];

		// Initialize the index
		for (int i=0; i<n; ++i)
			index[i]=i;

		// Find the rescaling factors, one from each row
		for (int i=0; i<n; ++i) {
			double c1=0;
			for (int j=0; j<n; ++j) {
				double c0=Math.abs(a[i][j]);
				if (c0>c1) c1=c0;
			}
			c[i]=c1;
		}

		// Search the pivoting element from each column
		int k=0;
		for (int j=0; j<n-1; ++j) {
			double pi1=0;
			for (int i=j; i<n; ++i) {
				double pi0=Math.abs(a[index[i]][j]);
				pi0/=c[index[i]];
				if (pi0>pi1) {
					pi1=pi0;
					k=i;
				}
			}

			// Interchange rows according to the pivoting order
			int itmp=index[j];
			index[j]=index[k];
			index[k]=itmp;
			for (int i=j+1; i<n; ++i) {
				double pj=a[index[i]][j]/a[index[j]][j];

				// Record pivoting ratios below the diagonal
				a[index[i]][j]=pj;

				// Modify other elements accordingly
				for (int l=j+1; l<n; ++l)
					a[index[i]][l]-=pj*a[index[j]][l];
			}
		}
	}

	public static double[][] multiply(double[][] m, double[][] n) {
		double[][] r=new double[m.length][n[0].length];
		for (int i=0; i<m.length; i++) {
			for (int j=0; j<n[0].length; j++) {
				for (int k=0; k<m[0].length; k++) {
					r[i][j]+=m[i][k]*n[k][j];
				}
			}
		}
		return r;
	}

	public static double[][] divide(double[][] m, double v) {
		return multiply(m, 1.0/v);
	}
	public static double[][] multiply(double[][] m, double v) {
		double[][] n=new double[m.length][];
		for (int i=0; i<m.length; i++) {
			n[i]=new double[m[i].length];
			for (int j=0; j<m[i].length; j++) {
				n[i][j]=m[i][j]*v;
			}
		}
		return n;
	}
	
	public static double[] multiply(double[][] m, double[] array) {
		double[] n=new double[m.length];
		for (int i=0; i<m.length; i++) {
			for (int j=0; j<m[i].length; j++) {
				n[i]+=m[i][j]*array[j];
			}
		}
		return n;
	}
	
	public static double multiply(double[] a, double[] b) {
		double d=0.0;
		for (int i=0; i<a.length; i++) {
			d+=a[i]*b[i];
		}
		return d;
	}
	
	public static double[][] subtract(double[][] m, double[][] q) {
		double[][] n=new double[m.length][];
		for (int i=0; i<m.length; i++) {
			n[i]=new double[m[i].length];
			for (int j=0; j<m[i].length; j++) {
				n[i][j]=m[i][j]-q[i][j];
			}
		}
		return n;
	}
	
	public static double[][] subtract(double[][] m, double[] array) {
		double[][] n=new double[m.length][];
		for (int i=0; i<m.length; i++) {
			n[i]=new double[m[i].length];
			for (int j=0; j<m[i].length; j++) {
				n[i][j]=m[i][j]-array[j];
			}
		}
		return n;
	}
	
	public static double[] subtract(double[] a, double[] b) {
		double[] r=new double[a.length];
		for (int i=0; i<r.length; i++) {
			r[i]=a[i]-b[i];
		}
		return r;
	}
	
	public static double[] add(double[] a, double[] b) {
		double[] r=new double[a.length];
		for (int i=0; i<r.length; i++) {
			r[i]=a[i]+b[i];
		}
		return r;
	}
	
	public static double getRange(double[] array) {
		double min=Double.MAX_VALUE;
		double max=-Double.MAX_VALUE;
		for (double value : array) {
			if (value>max) max=value;
			if (value<min) min=value;
		}
		return max-min;
	}

	public static double[] getColumn(double[][] m, int col) {
		double[] v=new double[m.length];
		for (int i=0; i<v.length; i++) {
			v[i]=m[i][col];
		}
		return v;
	}
	public static double[][] transpose(double[][] m) {
		double[][] n=new double[m[0].length][];
		for (int i=0; i<n.length; i++) {
			n[i]=new double[m.length];
		}
		for (int i=0; i<m.length; i++) {
			for (int j=0; j<m[i].length; j++) {
				n[j][i]=m[i][j];
			}
		}
		return n;
	}
}