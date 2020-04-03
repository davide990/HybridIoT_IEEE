package context.comparison;

import java.util.ArrayList;
import java.util.List;

import context.Context;

public class ContextComparatorImpl implements ContextComparatorStrategy {

	@Override
	public double compare(Context c1, Context c2) {

		if (c1.isEmpty() || c2.isEmpty()) {
			return 0;
		}

		double[] c1InfoSeq = c1.asDoubleArray();
		double[] meanPoly = c2.asDoubleArray();

		List<Double> polyPointsX = new ArrayList<>();
		for (int i = 0; i < meanPoly.length; i++) {
			polyPointsX.add(Math.abs(meanPoly[i] - c1InfoSeq[i]));
		}

		return polyPointsX.stream().mapToDouble(x -> x).average().getAsDouble() / polyPointsX.size();
	}

}
