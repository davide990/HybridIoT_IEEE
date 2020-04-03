package agent.contactSensor.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.stat.descriptive.rank.Median;

import agent.AmbientContextAgent;
import agent.ContactSensorAgent;
import agent.contactSensor.interfaces.ThresholdEvaluation;
import smile.clustering.KMeans;

public class ThresholdEvaluationImpl implements ThresholdEvaluation {

	private KMeans kmeans;
	private final int MAX_KMEANS_ITER = 10;
	private final int MAX_KMEANS_CLUST = 2;

	private final Median median = new Median();
	int[] labels;

	private List<Double> centroids;

	public ThresholdEvaluationImpl() {
		kmeans = null;
		labels = new int[0];
		centroids = new ArrayList<>();
	}

	@Override
	public Double[] calculate(ContactSensorAgent self, Pair<AmbientContextAgent, AmbientContextAgent> thePair,
			Map<Pair<AmbientContextAgent, AmbientContextAgent>, List<Double>> correlationsMap) {

		List<Double> corrs = correlationsMap.get(thePair);
		corrs.removeIf(x -> !Double.isFinite(x));
		Double[] ths = calculateOld(self, corrs);

		return ths;
	}

	public Double[] calculateOld(ContactSensorAgent self,
			Map<Pair<AmbientContextAgent, AmbientContextAgent>, Double> correlationsMap) {
		return calculateOld(self, correlationsMap.values().stream().collect(Collectors.toList()));
	}

	public Double[] calculateOld(ContactSensorAgent self, List<Double> correlations) {
		double[][] ll = new double[correlations.size()][1];

		for (int i = 0; i < correlations.size(); i++) {
			ll[i][0] = correlations.get(i);
		}

		kmeans = new KMeans(ll, MAX_KMEANS_CLUST, MAX_KMEANS_ITER);
		labels = kmeans.getClusterLabel();

		centroids.clear();

		centroids.addAll(Arrays.stream(kmeans.centroids()).flatMapToDouble(x -> DoubleStream.of(x)).boxed()
				.collect(Collectors.toList()));

		return centroids.stream().toArray(Double[]::new);

	}

	public boolean hasThreshold() {
		return kmeans != null;
	}

	@Override
	public double getFuzzyClass(double corr, Double[] ths) {

		return 0;
	}

	@Override
	public int getClass(double corr, Double[] ths) {

		Arrays.sort(ths);

		boolean foundClass = false;
		int i = 1, Class = 0;

		while (!foundClass && i + 1 < ths.length) {

			if (corr >= ths[i - 1] && corr <= ths[i]) {
				foundClass = true;
			} else {
				i++;
				Class++;
			}

		}

		Map<Integer, Integer> classCount = new HashMap<>();
		if (!classCount.containsKey(Class)) {
			classCount.put(Class, 0);
		}

		classCount.put(Class, classCount.get(Class) + 1);

		Optional<Entry<Integer, Integer>> ll = classCount.entrySet().stream()
				.max((c1, c2) -> Integer.compare(c1.getValue(), c2.getValue()));

		if (ll.isPresent()) {
			return ll.get().getKey();
		}

		return 999;
	}

	@Override
	public List<Double> getCentroids() {
		return centroids;
	}

	public int getLabel(Pair<AmbientContextAgent, AmbientContextAgent> pair,
			Map<Pair<AmbientContextAgent, AmbientContextAgent>, Double> correlationsMap) {

		boolean found = false;
		int idx = 0;
		for (Iterator<Pair<AmbientContextAgent, AmbientContextAgent>> iterator = correlationsMap.keySet().iterator(); iterator
				.hasNext() && !found; idx++) {
			Pair<AmbientContextAgent, AmbientContextAgent> type = iterator.next();

			if (type.equals(pair)) {
				found = true;
			}

		}
		return labels[idx];
	}
	
	@Override
	public double getUndeterminedClass() {
		// TODO Auto-generated method stub
		return 0;
	}

}
