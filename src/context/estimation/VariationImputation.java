package context.estimation;


import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import agent.base.Agent;
import context.Context;
import context.ContextEntry;
import context.ContextInfo;

public class VariationImputation implements ContextEstimationStrategy {

	@Override
	public ContextEntry impute(Agent self, Map<Agent, List<Context>> agentContext, Context lastObservedContext,
			ContextInfo missingInfo) {

		double lastPerceivedValue = lastObservedContext
				.getSituationAsDouble(missingInfo)[lastObservedContext.getSituationAsDouble(missingInfo).length - 1];

		Map<Agent, Double> directionSuggestions = new HashMap<>();

		// 10 best context per agent (neighbors)
		for (Entry<Agent, List<Context>> e : agentContext.entrySet()) {
			List<Context> bestContexts = e.getValue().stream()
					.sorted((a, b) -> Double.compare(a.compare(lastObservedContext), b.compare(lastObservedContext)))
					.collect(Collectors.toList());

			// measure the variations of the last 2 samples for EACH context in bestContexts
			List<Double> variations = new ArrayList<>();
			for (Context c : bestContexts) {
				double lastVal = c.getSituationAsDouble(missingInfo)[c.size() - 1];
				double theVariation = lastVal - c.getSituationAsDouble(missingInfo)[c.size() - 2];

				if (Arrays.asList(c.getSituation(missingInfo)).stream().filter(x -> x.isEstimation()).findAny()
						.isPresent()) {
					continue;
				}

				variations.add(theVariation);
			}

			// save the mean direction suggested by each agent
			directionSuggestions.put(e.getKey(), variations.stream().mapToDouble(x -> x).average().orElse(0));
		}

		double direction = directionSuggestions.values().stream().mapToDouble(x -> x).average().orElse(0);
		double estimation = lastPerceivedValue + direction;
		ContextEntry ee = new ContextEntry(missingInfo, Instant.now(), estimation);
		ee.setIsEstimation(true);

		return ee;
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return null;
	}
}
