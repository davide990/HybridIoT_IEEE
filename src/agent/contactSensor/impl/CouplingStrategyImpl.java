package agent.contactSensor.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;

import agent.AmbientContextAgent;
import agent.ContactSensorAgent;
import agent.base.Perception;
import agent.contactSensor.interfaces.CorrelationStrategy;
import agent.contactSensor.interfaces.CouplingStrategy;
import smile.wavelet.DaubechiesWavelet;
import smile.wavelet.WaveletShrinkage;

public class CouplingStrategyImpl implements CouplingStrategy {

	
	protected final static boolean APPLY_WAVELET = false;
	private final static int DAUBECHIES_COEFF = 6;

	/**
	 * The strategy used to evaluate the correlation between agents
	 */
	private final CorrelationStrategy correlationStrategy;

	public CouplingStrategyImpl(CorrelationStrategy correlaionStrategy) {
		this.correlationStrategy = correlaionStrategy;
	}

	public Map<Pair<AmbientContextAgent, AmbientContextAgent>, Double> act(ContactSensorAgent self, List<AmbientContextAgent> neighbors,
			int pairsCount, int idx) {
		Map<Pair<AmbientContextAgent, AmbientContextAgent>, Double> map = new HashMap<>();

		// Sort the neighbors according to the distance to this agent
		List<AmbientContextAgent> sortedNeighbors = neighbors.stream()
				.sorted((a1, a2) -> Double.compare(a1.getConfidenceZone().minDistance(self.getPosition()),
						a2.getConfidenceZone().minDistance(self.getPosition())))
				.collect(Collectors.toList());

		while (!sortedNeighbors.isEmpty() && map.keySet().size() < pairsCount) {
			AmbientContextAgent A1 = sortedNeighbors.remove(0);

			if (!A1.getLastContext().isPresent()) {
				continue;
			}

			if (!sortedNeighbors.isEmpty()) {

				// Get the first agent of the same type as A1
				AmbientContextAgent A2 = sortedNeighbors.stream().filter(x -> x.getType() == A1.getType()).findFirst().get();

				double correlation = 0;
				boolean allGood = false;

				while (!allGood) {
					try {
						correlation = evaluateCorrelation(A1, A2, idx);
						allGood = true;
					} catch (IndexOutOfBoundsException ex) {
						try {
							Thread.sleep(5);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}

				// LearnerAgent A2 = sortedNeighbors.remove(0);
				map.put(Pair.of(A1, A2), correlation);
			}

		}

		return map;
	}

	/**
	 * @see CouplingStrategy#act(Agent, )
	 */
	public Map<Pair<AmbientContextAgent, AmbientContextAgent>, Double> act(ContactSensorAgent self, List<AmbientContextAgent> neighbors, int idx) {
		Map<Pair<AmbientContextAgent, AmbientContextAgent>, Double> map = new HashMap<>();

		// Sort the neighbors according to the distance to this agent
		List<AmbientContextAgent> sortedNeighbors = neighbors.stream()
				.sorted((a1, a2) -> Double.compare(a1.getConfidenceZone().minDistance(self.getPosition()),
						a2.getConfidenceZone().minDistance(self.getPosition())))
				.collect(Collectors.toList());

		while (!sortedNeighbors.isEmpty()) {
			AmbientContextAgent A1 = sortedNeighbors.remove(0);

			if (!A1.getLastContext().isPresent()) {
				continue;
			}

			if (!sortedNeighbors.isEmpty()) {
				AmbientContextAgent A2 = sortedNeighbors.remove(0);
				
				
				
				double correlation = 0;
				boolean allGood = false;

				while (!allGood) {
					try {
						correlation = evaluateCorrelation(A1, A2, idx);
						allGood = true;
					} catch (IndexOutOfBoundsException ex) {
						try {
							Thread.sleep(5);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
				
				
				
				
				map.put(Pair.of(A1, A2), correlation);
				
				//map.put(Pair.of(A1, A2), evaluateFixedCorrelation(A1, A2));
			}

		}

		return map;
	}

	// TODO probabilmente le finestre corte hanno *molto spesso* delle correlazioni
	// molto elevate
	protected double evaluateCorrelation(AmbientContextAgent a, AmbientContextAgent b) {

		final List<Perception> percA = a.getPerceptions();
		final List<Perception> percB = b.getPerceptions();

		// the index from which you start to build the sample windows
		int i = Math.min(percA.size(), percB.size()) - 1;

		List<Double> windowA = new ArrayList<>();
		List<Double> windowB = new ArrayList<>();
		List<Double> correlations = new ArrayList<>();

		for (int j = i; j > Math.max(0, i - CORRELATION_WINDOW_SIZE) && j >= 0; j--) {
			windowA.add(0, percA.get(j).getValue());
			windowB.add(0, percB.get(j).getValue());
		}

		if (windowA.size() >= CORRELATION_WINDOW_SIZE) {

			 windowA = normalizeData(windowA);
			 windowB = normalizeData(windowB);

			correlations.add(correlationStrategy.measure(windowA.stream().mapToDouble(x -> x).toArray(),
					windowB.stream().mapToDouble(x -> x).toArray()));
		}

		return correlationStrategy.best(correlations);
	}

	// TODO probabilmente le finestre corte hanno *molto spesso* delle correlazioni
	// molto elevate
	protected double evaluateCorrelation(AmbientContextAgent a, AmbientContextAgent b, int idx) throws IndexOutOfBoundsException {

		final List<Perception> percA = a.getPerceptions();
		final List<Perception> percB = b.getPerceptions();

		// the index from which you start to build the sample windows
		// int i = Math.min(percA.size(), percB.size()) - 1;

		List<Double> windowA = new ArrayList<>();
		List<Double> windowB = new ArrayList<>();
		List<Double> correlations = new ArrayList<>();

		for (int j = idx; j > Math.max(0, idx - CORRELATION_WINDOW_SIZE) && j >= 0; j--) {
			windowA.add(0, percA.get(j).getValue());
			windowB.add(0, percB.get(j).getValue());
		}

		if (windowA.size() >= CORRELATION_WINDOW_SIZE) {

			// windowA = normalizeData(windowA);
			// windowB = normalizeData(windowB);

			correlations.add(correlationStrategy.measure(windowA.stream().mapToDouble(x -> x).toArray(),
					windowB.stream().mapToDouble(x -> x).toArray()));
		}

		return correlationStrategy.best(correlations);
	}

	private List<Double> normalizeData(List<Double> windowA) {

		double minA = windowA.stream().mapToDouble(x -> x).min().getAsDouble();
		double maxA = windowA.stream().mapToDouble(x -> x).max().getAsDouble();

		return windowA.stream().map(x -> x - ((maxA + minA) / 2)).collect(Collectors.toList());

	}

	// TODO probabilmente le finestre corte hanno *molto spesso* delle correlazioni
	// molto elevate
	protected double evaluateCorrelationWithWavelet(AmbientContextAgent a, AmbientContextAgent b) {

		final List<Perception> percA = a.getPerceptions();
		final List<Perception> percB = b.getPerceptions();

		// the index from which you start to build the sample windows
		int i = Math.min(percA.size(), percB.size()) - 1;

		List<Double> windowA = new ArrayList<>();
		List<Double> windowB = new ArrayList<>();
		List<Double> correlations = new ArrayList<>();

		for (int j = i; j > Math.max(0, i - CORRELATION_WINDOW_SIZE) && j >= 0; j--) {
			windowA.add(0, percA.get(j).getValue());
			windowB.add(0, percB.get(j).getValue());
		}

		if (windowA.size() >= 2) {
			double[] _windowA = windowA.stream().mapToDouble(x -> x).toArray();
			double[] _windowB = windowB.stream().mapToDouble(x -> x).toArray();

			while (_windowA.length < CORRELATION_WINDOW_SIZE) {
				_windowA = ArrayUtils.add(_windowA, 0);
				_windowB = ArrayUtils.add(_windowB, 0);
			}

			WaveletShrinkage.denoise(_windowA, new DaubechiesWavelet(DAUBECHIES_COEFF), true);
			WaveletShrinkage.denoise(_windowB, new DaubechiesWavelet(DAUBECHIES_COEFF), true);
			correlations.add(correlationStrategy.measure(_windowA, _windowB));
		}

		return correlationStrategy.best(correlations);
	}
/*
	private double evaluateFixedCorrelation(LearnerAgent a, LearnerAgent b) {

		final List<Perception> percA = a.getPerceptions();
		final List<Perception> percB = b.getPerceptions();

		// the index from which you start to build the sample windows
		int i = Math.min(percA.size(), percB.size()) - 1;

		List<Double> windowA = new ArrayList<>();
		List<Double> windowB = new ArrayList<>();

		for (int j = i; j > i - CORRELATION_WINDOW_SIZE && j >= 0; j--) {
			windowA.add(0, percA.get(j).getValue());
			windowB.add(0, percB.get(j).getValue());
		}

		if (windowA.size() < 2 || windowB.size() < 1) {
			return 0;
		}

		return correlationStrategy.measure(windowA.stream().mapToDouble(x -> x).toArray(),
				windowB.stream().mapToDouble(x -> x).toArray());
	}*/

	public CorrelationStrategy getCorrelaionStrategy() {
		return correlationStrategy;
	}

}
