package utils;

import org.apache.commons.math3.stat.descriptive.rank.Median;

public class StatUtils {
	/**
	 * Check if given value is an outlier in the data points using MAD
	 *
	 * @param valueToCheck the given values to check
	 * @param dataPoints   the data points
	 * @return true if value is an outlier. Otherwise false.
	 */
	public static boolean isOutlier(double valueToCheck, double[] dataPoints, double scalingFactor) {

		double value = getOutlierScore(valueToCheck, dataPoints, scalingFactor);
		return value >= 3;
	}

	/**
	 * Get outlier score using value to check and historical data
	 *
	 * @param valueToCheck the given values to check
	 * @param dataPoints   the data points
	 * @return outlier score using value to check and historical data
	 */
	public static double getOutlierScore(double valueToCheck, double[] dataPoints, double scalingFactor) {
		double median = new Median().evaluate(dataPoints);
		double mad = getMedianAbsoluteDeviation(dataPoints, median);

		double value = Math.abs((valueToCheck - median) / mad * scalingFactor);

		return value;
	}

	/**
	 * Calculates the Median Absolute Deviation
	 *
	 * @param dataPoints the data points for the calculation
	 * @param median     the median of the data points
	 * @return the Median Absolute Deviation
	 */
	private static double getMedianAbsoluteDeviation(double[] dataPoints, double median) {
		double[] absoluteDeviations = new double[dataPoints.length];
		for (int i = 0; i < dataPoints.length; i++) {
			absoluteDeviations[i] = Math.abs(dataPoints[i] - median);
		}
		return new Median().evaluate(absoluteDeviations);
	}
}
