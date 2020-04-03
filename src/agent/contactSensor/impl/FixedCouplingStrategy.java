package agent.contactSensor.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;

import agent.AmbientContextAgent;
import agent.ContactSensorAgent;
import agent.contactSensor.interfaces.CorrelationStrategy;
import context.ContextInfo;

public class FixedCouplingStrategy extends CouplingStrategyImpl {

	public FixedCouplingStrategy(CorrelationStrategy correlationStrategy) {
		super(correlationStrategy);
	}

	@Override
	public Map<Pair<AmbientContextAgent, AmbientContextAgent>, Double> act(ContactSensorAgent self, List<AmbientContextAgent> neighbors,
			int idx) {
		Map<Pair<AmbientContextAgent, AmbientContextAgent>, Double> correlationMap = new HashMap<>();

		List<ContextInfo> infos = neighbors.stream().map(x -> x.getSupportedInfo()).distinct()
				.collect(Collectors.toList());

		List<Pair<AmbientContextAgent, AmbientContextAgent>> pairs = new ArrayList<>();

		for (ContextInfo info : infos) {
			pairs.addAll(getPairs(self.getAgentName(), neighbors, info));
		}

		pairs = pairs.stream().filter(x -> x.getLeft() != null && x.getRight() != null).collect(Collectors.toList());

		for (Pair<AmbientContextAgent, AmbientContextAgent> pair : pairs) {


			
			double correlation = 0;
			boolean allGood = false;

			while (!allGood) {
				try {
					if (!APPLY_WAVELET) {
						correlation = evaluateCorrelation(pair.getLeft(), pair.getRight(), idx);
					} else {
						correlation = evaluateCorrelationWithWavelet(pair.getLeft(), pair.getRight());
					}
					allGood = true;
				} catch (IndexOutOfBoundsException ex) {
					try {
						Thread.sleep(5);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
			
			correlationMap.put(pair, correlation);
			/*
			if (!APPLY_WAVELET) {
				correlationMap.put(pair, evaluateCorrelation(pair.getLeft(), pair.getRight(), idx));
			} else {
				correlationMap.put(pair, evaluateCorrelationWithWavelet(pair.getLeft(), pair.getRight()));
			}*/
		}

		return correlationMap;
	}

	List<Pair<AmbientContextAgent, AmbientContextAgent>> getPairs(String contactSensorName, List<AmbientContextAgent> neighbors,
			ContextInfo info) {

		switch (contactSensorName) {
		case "C1":
			return Arrays.asList(Pair.of(getAg(neighbors, "I2", info), getAg(neighbors, "S4", info)),
					Pair.of(getAg(neighbors, "I2", info), getAg(neighbors, "S3", info)));

		case "C3":
			return Arrays.asList(Pair.of(getAg(neighbors, "S3", info), getAg(neighbors, "S4", info)));

		case "C4":
			/*
			 * return Arrays.asList(Pair.of(getAg(neighbors, "S3", info), getAg(neighbors,
			 * "I2", info)), Pair.of(getAg(neighbors, "S2", info), getAg(neighbors, "I2",
			 * info)));
			 */
			return Arrays.asList(Pair.of(getAg(neighbors, "S3", info), getAg(neighbors, "S2", info)));

		case "C5":
			return Arrays.asList(Pair.of(getAg(neighbors, "I5", info), getAg(neighbors, "S9", info)),
					Pair.of(getAg(neighbors, "6.218", info), getAg(neighbors, "S9", info)),
					Pair.of(getAg(neighbors, "6.221B", info), getAg(neighbors, "S9", info)),
					Pair.of(getAg(neighbors, "6.221", info), getAg(neighbors, "S9", info)));

		case "C6":
			return Arrays.asList(/* Pair.of(getAg(neighbors, "6.224"), getAg(neighbors, "6.227")), */
					Pair.of(getAg(neighbors, "6.221", info), getAg(neighbors, "6.224", info)),
					Pair.of(getAg(neighbors, "6.221", info), getAg(neighbors, "6.227", info)));

		case "C8":
			return new ArrayList<>();// return Arrays.asList(Pair.of(getAg(neighbors, "6.227"), getAg(neighbors,
										// "S1")));

		case "C9":
			return Arrays.asList(Pair.of(getAg(neighbors, "6.105", info), getAg(neighbors, "S5", info)));

		default:
			return new ArrayList<>();
		}

	}

	private AmbientContextAgent getAg(List<AmbientContextAgent> neighbors, String name, ContextInfo info) {

		Optional<AmbientContextAgent> o = neighbors.stream()
				.filter(x -> x.getAgentName().equals(name) && x.getSupportedInfo() == info).findFirst();
		if (o.isPresent()) {
			return o.get();
		}

		// System.err.println("agent [" + name + "] not found");

		return null;

	}
}
