package context.comparison;

import java.util.Arrays;
import java.util.stream.IntStream;

import context.Context;

/**
 * Comparator variable width contexts
 * 
 * @author Davide Andrea Guastella (SMAC-IRIT)
 *
 */
public class VarWidthComparator implements ContextComparatorStrategy {

	@Override
	public double compare(Context c1, Context c2) {

		if (c1.isEmpty() || c2.isEmpty()) {
			return 0;
		}

		int minLength = Math.min(c1.size(), c2.size());
		int maxLength = Math.max(c1.size(), c2.size());
		Context shortC = c1;
		Context longC = c2;

		if (c1.size() > c2.size()) {
			shortC = c2;
			longC = c1;
		}

		if (maxLength - minLength == 0) {
			return compareSequences(shortC.asDoubleArray(), longC.asDoubleArray());
		}
		// Delta = offset. Try to evaluate the shortest sequence to part of the longest
		// sequence in order to compare the same number of elements.

		double meanMinArea = Double.MAX_VALUE;
		for (int delta = 0; delta < maxLength - minLength; delta++) {
			// evaluate each information

			// take the shortest sequence
			double[] shortContext = shortC.asDoubleArray();

			// then the longest
			double[] longContext = longC.asDoubleArray();

			// take a part of the longest sequence of the same length of the shortest
			double[] subLongContext = Arrays.copyOfRange(longContext, delta, delta + minLength);

			// evaluate the area between the shortest and the part of the longest
			// double meanArea = compareSequences(shortContext, subLongContext);

			// double meanAreaGauss = compareSequencesGauss(shortContext, subLongContext);
			double meanArea = compareSequencesGauss(shortContext, subLongContext);

			// increase the area by the difference of the size of the sequences.
			meanArea = (c1.size() != c2.size()) ? meanArea * Math.abs(c1.size() - c2.size()) : meanArea;
			if (meanArea < meanMinArea) {
				meanMinArea = meanArea;
			}
		}

		return meanMinArea;
	}

	/**
	 * Evaluate the area subtended between the two sequences. The sequences have to
	 * be of the same length
	 * 
	 * @param seqA
	 * @param seqB
	 * 
	 * @return
	 */
	private double compareSequences(double[] seqA, double[] seqB) {
		if (seqA.length != seqB.length) {
			throw new IllegalArgumentException("sequences have to be of the same length ("
					+ Double.toString(seqA.length) + " != " + Double.toString(seqB.length) + ")");
		}

		double area = IntStream.range(0, seqA.length).mapToDouble(i -> Math.abs(seqA[i] - seqB[i])).sum() / seqA.length;
		return area;
	}

	/**
	 * Reference: {@link https://en.wikipedia.org/wiki/Shoelace_formula}
	 * 
	 * @param seqA
	 * @param seqB
	 * @return
	 */
	private double compareSequencesGauss(double[] seqA, double[] seqB) {
		if (seqA.length != seqB.length) {
			throw new IllegalArgumentException("sequences have to be of the same length ("
					+ Double.toString(seqA.length) + " != " + Double.toString(seqB.length) + ")");
		}

		double area = 0;
		for (int j = 0; j < seqB.length - 1; j++) {
			area += (seqA[j] * seqB[j + 1]) - (seqA[j + 1] * seqB[j]);
		}
		return Math.abs(area) / 2;
	}

}
