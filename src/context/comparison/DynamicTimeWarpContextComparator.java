package context.comparison;

import java.util.HashSet;
import java.util.Set;

import context.Context;
import smile.math.distance.DynamicTimeWarping;

public class DynamicTimeWarpContextComparator implements ContextComparatorStrategy {
	@Override
	public double compare(Context c1, Context c2) {

		if (c1.isEmpty() || c2.isEmpty()) {
			return Double.MAX_VALUE;
		}
		Set<Double> scores = new HashSet<>();
		double[] d1 = c1.asDoubleArray();
		double[] d2 = c2.asDoubleArray();
		scores.add(DynamicTimeWarping.d(d1, d2));
		return DynamicTimeWarping.d(d1, d2);
	}
}
