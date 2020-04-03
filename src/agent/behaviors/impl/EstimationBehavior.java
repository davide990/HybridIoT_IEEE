package agent.behaviors.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.tuple.Pair;

import agent.AmbientContextAgent;
import agent.base.Agent;
import agent.base.AgentState;
import agent.base.AgentType;
import agent.behaviors.SimpleBehavior;
import agent.environment.Environment;
import confidenceZone.ConfidenceZone;
import configuration.MASConfig;
import context.Context;
import context.ContextEntry;
import context.ContextInfo;
import context.descriptor.ContextDescriptor;
import context.dynamicContext.FixedWidthContextFinder;
import context.dynamicContext.OptimalContextFinder;
import context.estimation.ContextEstimationStrategy;

public class EstimationBehavior extends SimpleBehavior {

	private static final int MAXIMUM_ESTIMATED_ENTRIES_IN_CONTEXTS = 0;

	public static final boolean PROMOTE_CONTEXT_WITHOUT_ESTIMATION = true;

	/**
	 * The strategy used to find the contexts
	 */
	protected OptimalContextFinder contextFinder;

	public static final int MIN_NUMBER_SAMPLE_OBSERVED = FixedWidthContextFinder.CONTEXT_SIZE;

	public static final int CONTEXT_TO_USE_FOR_ESTIMATION = 10;
	public static final int CONTEXT_FROM_NEIGHBORS_TO_USE_FOR_ESTIMATION = 10;

	/**
	 * Strategy used by this agent to provide an estimation when no real sensor
	 * agents are present in the confidence zone.
	 */
	protected Optional<ContextEstimationStrategy> estimationStrategy;

	public EstimationBehavior(Agent owner) {
		super(owner);
		// TODO Auto-generated constructor stub
	}

	@Override
	public void action() {
		// TODO Auto-generated method stub

	}

	@Override
	public void onStart() {
		if (!(getAgent() instanceof AmbientContextAgent)) {
			throw new IllegalArgumentException("Not an Ambient Context Agent");
		}
	}

	private AmbientContextAgent getAgentAsACA() {
		if (!(getAgent() instanceof AmbientContextAgent)) {
			throw new IllegalArgumentException("Not an Ambient Context Agent");
		}
		return (AmbientContextAgent) getAgent();
	}

	@Override
	public boolean done() {
		// TODO Auto-generated method stub
		return false;
	}
	
	
	
	
	//-------------

	/**
	 * In case no real sensor is associated to this agent, an estimation is provided
	 * by this function. If other agents are within the confidence zone, these are
	 * used to provide an estimation value. Otherwise, the agent use its previously
	 * observed contexts to provide a reliable estimation value.
	 * 
	 * @param missingInfo the type of information to be estimated
	 * 
	 * @return a context entry containing an estimated information
	 */
	protected ContextEntry estimateMissingData(Optional<Context> lastContext, ContextInfo missingInfo) {
		// ----------------------------------------------------------
		// USE CONFIDENCE ZONE
		// ----------------------------------------------------------
		if (confidenceZone.isPresent()) {
			ContextEntry estimation = estimateByConfidenceZone(missingInfo);
			if (!estimation.isEmpty()) {
				synchronized (stateLock) {
					state = AgentState.ESTIMATE_WITH_CONFIDENCEZONE;
				}
				return estimation;
			}
		}

		if (!lastContext.isPresent()) {
			getLogger().error("[" + getAgentName() + "] No contexts available for estimating missing information.");
			return ContextEntry.empty(missingInfo);
		}

		// ----------------------------------------------------------
		// USE CONTEXTS
		// ----------------------------------------------------------
		if (!estimationStrategy.isPresent()) {
			throw new IllegalStateException("no imputation strategy provided.");
		}

		// Estimate missing data using contextual data cooperation
		ContextEntry defEstimation = estimateByContextualDataCooperation(getSensor().getCurrentSampleIdx(),
				lastContext.get(), missingInfo);

		synchronized (stateLock) {
			state = AgentState.ESTIMATE_WITH_CONTEXTS;
		}

		// getLogger().info("Imputed at #" +
		// Integer.toString(dataReceiver.get().getCurrentSampleIdx()) + ": "
		// + defEstimation.toString());

		return defEstimation;
	}

	/**
	 * When a confidence zone is available, an estimation is evaluated using the
	 * agent within the confidence zone. If an estimation cannot be evaluated (due
	 * to a lack of agents within the confidence zone), an empty context entry is
	 * returned.
	 * 
	 * @param missingInfo the type of information that has to be estimated
	 * 
	 * @return a context entry containing the estimated information. An empty entry
	 *         is returned if the estimation cannot be evaluated
	 */
	private ContextEntry estimateByConfidenceZone(ContextInfo missingInfo) {
		ContextEntry estimation = ContextEntry.empty(missingInfo);
		if (!neighborsBehavior.isPresent()) {
			throw new IllegalStateException("no cooperative behavior for confidence zone provided");
		}

		// TODO MODIF 25/11
		// Object[] ret = neighborsBehavior.get().act(this, this, 0, lastContext);
		// Object[] ret = neighborsBehavior.get().act(this, this, 0, null);

		Double ret = neighborsBehavior.get().act(this, this, 0, null);

		// if (Double.isFinite((Double) ret[0])) {
		if (Double.isFinite(ret)) {
			// estimation = new ContextEntry(missingInfo, Instant.now(), (Double) ret[0]);
			estimation = new ContextEntry(missingInfo, Instant.now(), ret);
			estimation.setIsEstimation(true);
		}

		if (!estimation.isEmpty()) {
			getLogger().error("[" + getAgentName()
					+ "] Error in estimating data with confidence zone. Estimating with contexts.\n");
			return ContextEntry.empty(missingInfo);

		}

		if (listener.isPresent()) {
			listener.get().imputed(this, lastContext.orElse(new Context(this, supportedInfo)), estimation, null,
					dataSensor.get().getCurrentData());
		}

		return estimation;
	}

	/**
	 * 
	 * @param lastContext
	 * @param missingInfo
	 * @return
	 */
	private ContextEntry estimateByContextualDataCooperation(int idx, Context lastContext, ContextInfo missingInfo) {
		// Search for similar contexts
		Map<Agent, List<Context>> cx = new HashMap<>();
		List<Context> mySimilarContexts = getMostSimilarContexts(CONTEXT_TO_USE_FOR_ESTIMATION, lastContext,
				PROMOTE_CONTEXT_WITHOUT_ESTIMATION);

		// Take the indexes of my similar contexts
		List<Integer> myContextIdxs = mySimilarContexts.stream().map(x -> x.getFinalDataIdx())
				.collect(Collectors.toList());
		cx.put(this, mySimilarContexts);

		// estimate the missing data by using agent contexts
		double myEstimationWeight = estimationStrategy.get().impute(this, cx, lastContext, missingInfo,
				Optional.empty());

		ContextEntry defEstimation = new ContextEntry(missingInfo, Instant.now(),
				myEstimationWeight + getLastPerceivedValue());

		// Estimate by using neighbors contexts
		if (MASConfig.USE_COOPERATION_FOR_ESTIMATION) {
			Map<Agent, Double> neighborEstimations = new HashMap<>();
			neighborEstimations = estimateByCooperation(getNeighborsWithWhichCooperate(), lastContext,
					getSensor().getCurrentSampleIdx(), missingInfo, myContextIdxs);
			defEstimation = evaluateEstimation(neighborEstimations, myEstimationWeight);
		}

		defEstimation.setIsEstimation(true);

		if (listener.isPresent()) {
			listener.get().imputed(this, lastContext, defEstimation, cx, dataSensor.get().getCurrentData());
		}

		// Return the estimated entry
		return defEstimation;
	}


	/**
	 * Get {@code num} contexts similar to {@code refContext}.
	 * 
	 * @param num                             maximum number of contexts to find
	 * @param refContext                      the context to be used for comparison
	 * @param promoteContextsWithNoEstimation if {@code true}, promote the contexts
	 *                                        with less than
	 *                                        {@code MAXIMUM_ESTIMATED_ENTRIES_IN_CONTEXTS}
	 *                                        estimated entries
	 * @return a list containing the most similar contexts to {@code refContext}
	 */

	public synchronized List<Context> getMostSimilarContexts(int num, Context refContext,
			boolean promoteContextsWithNoEstimation) {
		ContextDescriptor descriptor = ContextDescriptor.getNumericContextDescriptor(refContext);

		List<ContextDescriptor> sortedDescriptors = getContexts().keySet().stream()
				.sorted((d1, d2) -> Double.compare(descriptor.distance(d1), descriptor.distance(d2)))
				.collect(Collectors.toList());

		List<Context> outList = new ArrayList<>();
		for (ContextDescriptor d : sortedDescriptors) {

			List<Context> lc = new ArrayList<>();
			lc.addAll(getContexts().get(d));

			// lc.sort((c1,c2)->
			// Double.compare(refContext.compare(c1),refContext.compare(c2)) );

			ListIterator<Context> iterator = lc.listIterator();

			while (iterator.hasNext()) {
				Context c = iterator.next();

				if (outList.size() >= num) {
					return outList;
				}

				// take only the contexts that have the same size of the building context
				if (c.size() == refContext.size() && !c.equals(refContext)) {

					if (PROMOTE_CONTEXT_WITHOUT_ESTIMATION
							&& c.getEstimatedEntriesCount() > MAXIMUM_ESTIMATED_ENTRIES_IN_CONTEXTS) {
						continue;
					}

					outList.add(c);
				}
			}
		}

		return outList;
	}
	
	/**
	 * When using cooperation to estimate a missing information, this method is used
	 * to evaluate the set of neighbors with which this agent cooperates.
	 * 
	 * @return
	 */
	protected List<AmbientContextAgent> getNeighborsWithWhichCooperate() {
		// Get only active learner (real) agents
		List<AmbientContextAgent> neighbors = Environment.get().getAmbientContextAgents().stream()
				.filter(x -> x.getType() == AgentType.AMBIENT_CONTEXT_AGENT).map(x -> (AmbientContextAgent) x)
				.filter(x -> x.isRealSensor() && x.isActive() && !x.isPaused()
						&& !x.getAgentName().equals(this.getAgentName()))
				.collect(Collectors.toList());

		return neighbors;
	}

	/**
	 * Estimate a missing value by cooperating with other agents. Involved agents
	 * could be heterogeneous and perceive values of different type with respect to
	 * the values perceived by this agent
	 * 
	 * @param currentContext the last context observed by this agent
	 * @param missingInfo    the type of information for which estimate a value
	 * @return a map containing the estimated entry by each agent
	 */
	// protected List<EstimationResult>
	protected Map<Agent, Double> estimateByCooperation(List<AmbientContextAgent> neighbors, Context currentContext,
			int currentSampleIndex, ContextInfo missingInfo, List<Integer> myContextIdxs) {

		Map<Agent, Double> weights = new HashMap<>();
		if (!heterogeneousAgentsBehavior.isPresent()) {
			throw new IllegalStateException("no cooperative behavior for heterogeneous estimation provided");
		}

		List<Context> contexts = getContexts().entrySet().stream().flatMap(e -> e.getValue().stream())
				.filter(e -> myContextIdxs.contains(e.getFinalDataIdx())).collect(Collectors.toList());

		// Take my contexts and put into a map
		Map<Agent, List<Context>> cx = new HashMap<>();
		cx.put(this, contexts);

		// iterate each neighbor agent
		for (AmbientContextAgent neighborAg : neighbors) {

			// This behavior calculates the similarities between the contexts of the
			// neighbor agent and the current context
			List<Pair<Integer, Double>> cc = heterogeneousAgentsBehavior.get().act(this, neighborAg, currentSampleIndex,
					currentContext, myContextIdxs);

			// Estimate the information
			double weight = estimationStrategy.get().impute(this, cx, currentContext, missingInfo, Optional.of(cc));
			// double weight = estimationStrategy.get().impute(this, cx, currentContext,
			// missingInfo, Optional.empty());

			weights.put(neighborAg, getLastPerceivedValue() + weight);
			// weights.put(neighborAg, weight);
		}
		return weights;
	}


}
