package agent.contactSensor.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import org.apache.commons.lang3.tuple.Pair;

/**
 * A particular implementation of the method for evaluating the threshold
 * between correlation values. To be used when Pearson correlation is used
 * 
 * @author Davide Andrea Guastella (SMAC-IRIT)
 *
 */
public class PearsonThresholdEvaluation extends ThresholdEvaluationImpl {

	@Override
	public int getClass(double corr, Double[] thresholds) {

		List<Double> theThs = new ArrayList<>();

		Arrays.stream(thresholds).forEach(x -> theThs.add(x));

		theThs.add(0, -1.);
		theThs.add(1.);

		return super.getClass(corr, theThs.stream().toArray(Double[]::new));
	}

	@Override
	public double getUndeterminedClass() {
		return 1;
	}

	// @Override
	public int getClassOld(double corr, Double[] thresholds) {

		//double[] ths = new double[] { -1, 0, 1 };

		List<Double> theThs = new ArrayList<>();
		theThs.add(Math.abs(corr - (-1)));
		theThs.add(Math.abs(corr));
		theThs.add(Math.abs(corr - (1)));
		int classs = IntStream.range(0, theThs.size()).mapToObj(x -> Pair.of(x, theThs.get(x)))
				.min((p1, p2) -> Double.compare(p1.getRight(), p2.getRight())).get().getLeft();

		return classs;
	}

}
