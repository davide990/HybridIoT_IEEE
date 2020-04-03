package context.dynamicContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import agent.AmbientContextAgent;
import agent.base.Agent;
import agent.base.Perception;
import context.Context;
import context.ContextEntry;
import context.ContextInfo;
import context.estimation.ContextEstimationStrategy;
import sensors.DataSensor;

public class VarWidthContextFinder implements OptimalContextFinder {

	private static final int MAX_CONTEXT_SIZE = 14;

	@Override
	public Context getContext(AmbientContextAgent ag, DataSensor sensor, List<Perception> agentPerceptions) {

		// Get the imputation strategy from the agent given in input
		ContextEstimationStrategy imputationStrategy = ag.getImputationStrategy();

		int currentIdx = sensor.getCurrentSampleIdx();

		ContextInfo infoType = ag.getSupportedInfo();

		// Push back
		// note: at least two information are necessary to impute an information
		Context testContext = new Context(ag, infoType);

		// NOTE: getCurrentData() ignores if the sensors is active or not

		// Offset to be used for calculating
		int offsetNext = -1;
		int offset2 = 0;
		int offset3 = 1;
		int i = 2;
	

		try {
			sensor.getCurrentData(-1);
			
			
			//TODO add 11-02-2020
			sensor.getCurrentData(currentIdx - offset2);
		} catch (IndexOutOfBoundsException e) {
			offsetNext = 0;
			offset2 = 1;
			offset3 = 2;
			i = 3;
		}

		Perception k_0 = agentPerceptions.get(currentIdx - offset2);
		Perception k_1 = agentPerceptions.get(currentIdx - offset3);

		// k-0
		testContext.forceAddFirst(Arrays.asList(new ContextEntry(infoType, Instant.now(), k_0)));

		// k-1
		testContext.forceAddFirst(Arrays.asList(new ContextEntry(infoType, Instant.now(), k_1)));

		if (currentIdx + 1 >= sensor.getSamplesCount()) {
			return testContext;
		}

		if (!testContext.isValid()) {
			return new Context(ag, infoType);
		}

		// k+1: the value to be estimated
		//double currentData = sensor.getCurrentData();
		double currentData = agentPerceptions.get(currentIdx).getValue();
		double nextInfo = sensor.getCurrentData(offsetNext);

		List<Pair<Double, Context>> contexts = new ArrayList<>();

		for (i = 2; i <= MAX_CONTEXT_SIZE; i++) {
			// for (int i = 3; i <= MAX_CONTEXT_SIZE; i++) {

			if (currentIdx - i < 0) {
				continue;
			}
			// k-i
			// ContextEntry entry = new ContextEntry(infoType, Instant.now(),
			// sensor.getCurrentData(i));
			
			ContextEntry entry = new ContextEntry(infoType, Instant.now(), agentPerceptions.get(currentIdx - i));
			if(ag.isRealSensor()) {
				entry = new ContextEntry(infoType, Instant.now(), sensor.getCurrentData(i));
			}

			// Push back
			List<ContextEntry> entries = new ArrayList<>();
			entries.add(entry);
			testContext.forceAddFirst(entries);

			Map<Agent, List<Context>> cx = new HashMap<>();
			// my contexts

			List<Context> similarContexts = new ArrayList<>();

			similarContexts.addAll(
					ag.getMostSimilarContexts(10, testContext,AmbientContextAgent.PROMOTE_CONTEXT_WITHOUT_ESTIMATION));

			if (similarContexts.size() < 1) {
				similarContexts.add(testContext);
			}

			cx.put(ag, similarContexts);

			double weight = imputationStrategy.impute(ag, cx, testContext, infoType, Optional.empty());

			// double dist = Math.abs(imputedEntry.getLeft().getEntry().getValue() -
			// nextInfo);

			double dist = Math.abs((currentData + weight) - nextInfo);
			// + var.evaluate(testContext.getSituationAsDouble());

			try {
				// theContexts.put(i - 1, new Pair<>(dist, (Context) testContext.clone()));
				
				contexts.add(Pair.of(dist, (Context) testContext.clone()));
				
			} catch (CloneNotSupportedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

		}

		// Entry<Integer, Pair<Double, Context>> bestContextEntry =
		// theContexts.entrySet().stream()
		// .min((e1, e2) -> Double.compare(e1.getValue().getKey(),
		// e2.getValue().getKey())).get();

		Optional<Pair<Double, Context>> bestContext = contexts.stream()
				.min((e1, e2) -> Double.compare(e1.getKey(), e2.getKey()));

		if (bestContext.isPresent()) {	
			bestContext.get().getValue().setFinalDataIdx(currentIdx);
			return bestContext.get().getValue();
		}
		throw new IllegalStateException("Error while evaluate dynamic-size context.");
		// return bestContext.orElseThrow(() -> new IllegalStateException("Error while
		// evaluate dynamic-size context."))
		// .getValue();

		// return bestContextEntry.getValue().getSecond();
	}

}