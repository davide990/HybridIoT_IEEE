package agent.contactSensor.interfaces;

import java.util.List;

import context.Context;

public interface CorrelationStrategy {
	double measure(Context a, Context b);

	double measure(double[] a, double[] b);

	double best(double corrA, double corrB);

	double best(double[] corr);

	double best(List<Double> corr);

}
