package agent.contactSensor.impl;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;

import agent.contactSensor.interfaces.CorrelationStrategy;
import context.Context;

public class PearsonCorrelationStrategy implements CorrelationStrategy {
	// private final PearsonsCorrelation pearson;
	private final SpearmansCorrelation pearson;

	public PearsonCorrelationStrategy() {
		pearson = new SpearmansCorrelation();
	}

	public double measure(Context a, Context b) {
		double r = pearson.correlation(a.asDoubleArray(), b.asDoubleArray());
		return r;
//		return Math.abs(r);
	}

	@Override
	public double measure(final double[] a, final double[] b) {
		double r = pearson.correlation(a, b);
		
		return r;
//		return Math.abs(r);
	}

	@Override
	public double best(double corrA, double corrB) {

		// TODO the best should be the one that is the most distant from zero
		return Math.max(corrA, corrB);
	}

	@Override
	public double best(double[] corr) {
		return Arrays.stream(corr).boxed().mapToDouble(x -> x).max().orElse(Double.NaN);
	}

	@Override
	public double best(List<Double> corr) {
		return corr.stream().mapToDouble(x -> x).max().orElse(Double.NaN);
	}

}
