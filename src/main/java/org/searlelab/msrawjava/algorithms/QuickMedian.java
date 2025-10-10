package org.searlelab.msrawjava.algorithms;

public class QuickMedian {
	public static float median(float[] data) {
		return select(data, 0.5f);
	}

	public static float iqr(float[] data) {
		return select(data, 0.75f)-select(data, 0.25f);
	}

	public static float range90(float[] data) {
		return select(data, 0.95f)-select(data, 0.05f);
	}

	public static int randomInt(int seed) {
		seed=seed*1103515245+12345;
		return seed%2147483647;
	}

	public static float floatFromRandomInt(int random) {
		return ((random/(float)2147483647)+1f)/2f;
	}

	/**
	 * this is destructive to the array order! (it sorts it)
	 * 
	 * @param data
	 * @param desiredPercentage
	 * @return
	 */
	public static float select(float[] data, float desiredPercentage) {
		if (data.length==0) return 0.0f;
		if (data.length==1) return data[0];

		// The exact position of the value we want to select. Note that this might fall between two values.
		final float pos=(data.length-1)*desiredPercentage;

		// The fractional part of the position gives the proportion we should use to combine values if we fall between two.
		final float prop=pos%1;

		// The desired target -- one more than the integral part of the position.
		int targetIndex=Math.round(pos-prop)+1;

		int left=0;
		int right=data.length-1;

		int seed=randomInt(data.length);

		while (true) {
			seed=randomInt(seed);
			if (left==right) {
				if (left<data.length-1) {
					// guaranteed to be locally sorted because this point will always be sandwiched between two pivot points
					return ((1-prop)*data[left])+(prop*data[left+1]);
				} else {
					return data[left];
				}
			}
			int pivotIndex=left+(int)Math.round((right-left)*floatFromRandomInt(seed));
			if (pivotIndex<0) {
				pivotIndex=0;
			}
			int pivotNewIndex=partition(data, left, right, pivotIndex);

			int pivotDist=pivotNewIndex-left+1;
			if (pivotDist==targetIndex) {
				if (pivotNewIndex<(data.length-1)) {
					float lowest=data[pivotNewIndex+1];
					for (int i=pivotNewIndex+2; i<=right; i++) {
						if (data[i]<lowest) {
							lowest=data[i];
						}
					}
					return ((1-prop)*data[pivotNewIndex])+(prop*lowest);
				} else {
					return data[pivotNewIndex];
				}
			} else if (targetIndex<pivotDist) {
				// the target is left of the pivot
				if (pivotNewIndex<1) {
					// There's no data left of the pivot -- this means left is zero (because pivotNewIndex >= left always).
					// We just set right to zero and let the loop terminate next time around (because left==right).
					right=0;
				} else {
					right=pivotNewIndex-1;
				}
			} else {
				// the target is right of the pivot
				targetIndex=targetIndex-pivotDist;
				left=pivotNewIndex+1;
			}
		}
	}

	private static int partition(float[] data, int left, int right, int pivotIndex) {
		float pivotValue=data[pivotIndex];
		swap(data, pivotIndex, right);
		int storeIndex=left;
		for (int i=left; i<=right; i++) {
			if (data[i]<pivotValue) {
				swap(data, storeIndex, i);
				storeIndex++;
			}
		}
		swap(data, right, storeIndex);
		return storeIndex;
	}

	/**
	 * Method to swap to elements in an array.
	 * 
	 * @param a
	 *            an array of objects.
	 * @param index1
	 *            the index of the first object.
	 * @param index2
	 *            the index of the second object.
	 */
	public static final void swap(float[] a, int index1, int index2) {
		float tmp=a[index1];
		a[index1]=a[index2];
		a[index2]=tmp;
	}
}
