package agent.contactSensor.impl;

import java.util.Arrays;
import java.util.List;

import agent.contactSensor.interfaces.CorrelationStrategy;
import context.Context;
import smile.math.distance.DynamicTimeWarping;

public class DTWCorrelationEvaluation implements CorrelationStrategy {

	@Override
	public double measure(Context a, Context b) {
		return DynamicTimeWarping.d(a.asDoubleArray(), b.asDoubleArray());
	}

	@Override
	public double measure(double[] a, double[] b) {
		return DynamicTimeWarping.d(a, b);
	}

	@Override
	public double best(double corrA, double corrB) {

		if (corrA < corrB) {
			return corrA;
		}
		return corrB;
	}

	@Override
	public double best(double[] corr) {
		return Arrays.stream(corr).boxed().mapToDouble(x->x).min().orElse(Double.NaN);
	}

	@Override
	public double best(List<Double> corr) {
		return corr.stream().mapToDouble(x->x).min().orElse(Double.NaN);
	}

}
