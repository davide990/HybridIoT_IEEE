package context.estimation;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;

import agent.base.Agent;
import context.Context;
import context.ContextInfo;

public class ContextEstimationImpl implements ContextEstimationStrategy {

	@Override
	public Double impute(Agent self, Map<Agent, List<Context>> agentContext, Context lastObservedContext,
			ContextInfo missingInfo, Optional<List<Pair<Integer, Double>>> weights) {

		// Get the most similar contexts of the agent that needs to estimate the
		// information
		List<Context> myBestContexts = agentContext.get(self);

		// Assemble a list of pairs, each one containing a context and the score
		// obtained from the comparison with the last observed context
		List<Pair<Context, Double>> contextSimilarities = myBestContexts.stream()
				.filter(x -> lastObservedContext.size() == x.size()).filter(x -> !x.equals(lastObservedContext))
				.map(c -> Pair.of(c, lastObservedContext.compare(c))).collect(Collectors.toList());

		// Evaluate the max weight, used to normalize weights later
		double maxWeight = contextSimilarities.stream().mapToDouble(x -> x.getValue()).max().orElse(Double.NaN);

		double theNum = 0, theDiv = 0;

		List<Pair<Double, Double>> kv = new ArrayList<>();

		// For each context of the agent (which ID is the value of the map), weight the
		// average distance among the most similar contexts (of the agent itself and the
		// neighbor agents) and keep the weight in a list
		for (ListIterator<Pair<Context, Double>> iterator = contextSimilarities.listIterator(); iterator.hasNext();) {
			// Get the current item
			Pair<Context, Double> pair = (Pair<Context, Double>) iterator.next();
			// Get the context
			Context theContext = pair.getLeft();
			// Take the diff of the last 2 entries
			double val = theContext.asDoubleArray()[theContext.asDoubleArray().length - 1];
			double val1 = theContext.asDoubleArray()[theContext.asDoubleArray().length - 2];
			double valDiff = val - val1;
			double weight = 0;

			// If the weights associated to each context have been given in input, use them
			if (weights.isPresent()) {
				Optional<Pair<Integer, Double>> pp = weights.get().stream()
						.filter(x -> x.getLeft() == theContext.getFinalDataIdx()).findFirst();

				if (!pp.isPresent()) {
					continue;
				}

				// Weight is inversely proportional to the distance between contexts
				// ==> the less is the distance, the higher is the weight
				weight = weights.get().stream().filter(x -> x.getLeft() == theContext.getFinalDataIdx())
						.findFirst().get().getValue();
			} else {
				weight = maxWeight - pair.getValue();
				
				//weight = pair.getValue();
			}

			// Add the value difference and the weight in a list
			kv.add(Pair.of(valDiff, weight));
			iterator.set(Pair.of(theContext, weight));
		}

		// evaluate the weighted average of the difference values and the weights for
		// the used contexts
		theNum = kv.stream().mapToDouble(x -> x.getLeft() * x.getRight()).sum();
		theDiv = kv.stream().mapToDouble(x -> x.getRight()).sum();

		// Impute the missing value
		double weight = theNum / theDiv;
		if (!Double.isFinite(weight)) {
			double val = lastObservedContext.asDoubleArray()[lastObservedContext.asDoubleArray().length - 1];
			double val1 = lastObservedContext.asDoubleArray()[lastObservedContext.asDoubleArray().length - 2];
			weight = val - val1;
		}
		return weight;
	}

	@Override
	public String getName() {
		return null;
	}
}
