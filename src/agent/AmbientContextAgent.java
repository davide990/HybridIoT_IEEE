package agent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.collections4.list.TreeList;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.rank.Median;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;

import agent.base.Agent;
import agent.base.AgentState;
import agent.base.AgentType;
import agent.base.Perception;
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
import neighborhood.ConfidenceZoneCooperativeBehavior;
import neighborhood.CooperativeBehavior;
import neighborhood.HeterogeneousContextCooperativeBehavior;
import sensors.DataSensor;
import sensors.DataSensorFailureHandler;
import sensors.FileDataSensor;
import sensors.VirtualDataSensor;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

/**
 * 
 * @author Davide Andrea Guastella (SMAC-IRIT)
 *
 */
public class AmbientContextAgent extends Agent {

	private static final int MAXIMUM_ESTIMATED_ENTRIES_IN_CONTEXTS = 0;

	public static final boolean PROMOTE_CONTEXT_WITHOUT_ESTIMATION = true;

	/**
	 * A confidence zone is a polygon that delimits the environment. It is centered
	 * in the point where this agent is located, and is used to provide estimations
	 * by using the available agents that are situated within the confidence zone.
	 */
	protected Optional<ConfidenceZone> confidenceZone;

	/**
	 * The strategy used to find the contexts
	 */
	protected OptimalContextFinder contextFinder;

	public static final int MIN_NUMBER_SAMPLE_OBSERVED = 10;// FixedWidthContextFinder.CONTEXT_SIZE;

	public static final int CONTEXT_TO_USE_FOR_ESTIMATION = 10;
	public static final int CONTEXT_FROM_NEIGHBORS_TO_USE_FOR_ESTIMATION = 10;

	/**
	 * Strategy used by this agent to provide an estimation when no real sensor
	 * agents are present in the confidence zone.
	 */
	protected Optional<ContextEstimationStrategy> estimationStrategy;

	/**
	 * Sensor used to perceive data from the physical environment
	 */
	protected Optional<DataSensor> dataSensor;

	/**
	 * this buffer contains the information currently sent by sensors to this agent.
	 * These information will be added to the current context
	 */
	protected List<ContextEntry> observedInfoBuffer;

	/**
	 * An optional handler that is invoked when the sensor encounters an error when
	 * retrieving data
	 */
	private Optional<DataSensorFailureHandler> sensorFailureHandler;

	/**
	 * A map containing the contexts. The key is a context descriptor, which refers
	 * to the minMax range of values contained by the contexts within the value
	 * list.
	 * 
	 * <br/>
	 * 
	 * This map ensures good performance when finding contexts. This allows to
	 * outperforms the classical list data structures.
	 */
	protected Map<ContextDescriptor, List<Context>> contextMap;
	protected Object contextMapLock;

	/**
	 * The information perceived by the sensor associated to this agent
	 */
	private ContextInfo supportedInfo;

	/**
	 * The behavior that this agent has when it comes to cooperate with neighbors
	 * agents
	 */
	private Optional<CooperativeBehavior<Double>> neighborsBehavior;

	/**
	 * The strategy to be used to estimate missing information by using
	 * heterogeneous agents
	 */
	protected Optional<CooperativeBehavior<List<Pair<Integer, Double>>>> heterogeneousAgentsBehavior;

	/**
	 * Describe the state of this agents (either if estimating or perceiving by real
	 * sensor).
	 */
	protected AgentState state;
	protected Object stateLock;

	protected Optional<Context> lastContext;
	protected Object lastContextLock;

	/**
	 * Create a new instance of {@code AmbientContextAgent}.
	 * 
	 * @param agName
	 * @param position
	 * @param info
	 */
	protected AmbientContextAgent(String agName, Vector2D position, ContextInfo info) {
		this(agName, null, position, info);
	}

	/**
	 * Create a new instance of {@code AmbientContextAgent}. In this case a sensor
	 * is provided as parameter, so the agent is capable of perceiving the physical
	 * environment
	 * 
	 * @param agName
	 * @param position
	 * @param info
	 */
	protected AmbientContextAgent(String agName, DataSensor dr, ContextInfo info) {
		this(agName, dr, Vector2D.NaN, info);
	}

	/**
	 * Create a new instance of {@code AmbientContextAgent}. In this case a sensor
	 * is provided as parameter, so the agent is capable of perceiving the physical
	 * environment. Also, a position must be provided in this case, thus a
	 * confidence zone is created and associated to the agent.
	 * 
	 * @param agName
	 * @param position
	 * @param info
	 */
	protected AmbientContextAgent(String agName, DataSensor dr, Vector2D position, ContextInfo info) {
		super(agName, position, AgentType.AMBIENT_CONTEXT_AGENT);
		state = AgentState.USE_REAL_SENSOR;
		stateLock = new Object();
		contextMapLock = new Object();
		lastContextLock = new Object();
		supportedInfo = info;
		observedInfoBuffer = new ArrayList<>();
		estimationStrategy = Optional.empty();
		lastContext = Optional.empty();
		dataSensor = Optional.ofNullable(dr);
		sensorFailureHandler = Optional.empty();
		listener = Optional.empty();
		contextMap = Collections.synchronizedMap(new HashMap<>());
		heterogeneousAgentsBehavior = Optional
				.of(new HeterogeneousContextCooperativeBehavior<List<Pair<Integer, Double>>>());
	}

	/**
	 * Static factory method for {@code AmbientContextAgent} class
	 * 
	 * @param position
	 * @param info
	 * @return
	 */
	public static AmbientContextAgent getNew(String agentName, Vector2D position, ContextInfo info) {
		return getNew(agentName, null, Vector2D.NaN, info);
	}

	/**
	 * Static factory method for {@code AmbientContextAgent} class
	 * 
	 * @param dr
	 * @param info
	 * @return
	 */
	public static AmbientContextAgent getNew(String agentName, DataSensor dr, ContextInfo info) {
		return getNew(agentName, dr, Vector2D.NaN, info);
	}

	/**
	 * Static factory method for {@code AmbientContextAgent} class
	 * 
	 * @param dr
	 * @param position
	 * @param info
	 * @return
	 */
	public static AmbientContextAgent getNew(String agentName, DataSensor dr, Vector2D position, ContextInfo info) {
		AmbientContextAgent ag = new AmbientContextAgent(agentName, dr, position, info);
		ag.initAgent(position);
		return ag;
	}

	/**
	 * Initialize this agent with a specific position
	 * 
	 * @param position
	 */
	protected void initAgent(Vector2D position) {
		try {
			contextFinder = (OptimalContextFinder) MASConfig.OPTIMAL_CONTEXT_STRATEGY.newInstance();
		} catch (InstantiationException | IllegalAccessException e) {
			e.printStackTrace();
		}

		if (!position.equals(Vector2D.NaN)) {
			confidenceZone = Optional.of(new ConfidenceZone(position));

			getLogger().trace("agent [" + getAgentName() + "] created CZ at " + confidenceZone.get().centroid());
			neighborsBehavior = Optional.of(new ConfidenceZoneCooperativeBehavior<Double>(this));
		} else {
			confidenceZone = Optional.empty();
			neighborsBehavior = Optional.empty();
		}
	}

	// ############################################################################################
	// BEGIN OF REASONING CYCLE METHODS
	// ############################################################################################
	/**
	 * Perceive the data from the physical environment. If a real sensor is
	 * provided, the agent can perceive directly the environment using the sensor.
	 * Otherwise, the data is estimated.
	 */
	@Override
	public void perceive() {
		// [CASE 1] A sensor is provided, this agent do not need to estimate nothing. In
		// this case the data perceived are directly added to the buffer
		if (!dataSensor.isPresent()) {
			return;
		}

		observedInfoBuffer.clear();

		// Initialize a new context entry (empty)
		ContextEntry data = ContextEntry.empty(ContextInfo.NULL);

		try {
			// receive data from sensor
			data = dataSensor.get().receiveData();
			getLogger().trace("[" + getAgentName() + "] perceived " + data.toString());
			observedInfoBuffer.add(data);
		} catch (Exception e) {

			// handle sensor failure
			sensorFailureHandler.ifPresent(x -> x.handleSensorFailure(this, e, getSensor()));

			// abort the current reasoning cycle because the agent cannot decide and act
			abortReasoning();
		}
	}

	@Override
	public void decideAndAct() {
		// add the context to the dictionary
		processPerception();
		// addContextToDictionary();

		dataSensor.get().nextSample();
	}

	// ############################################################################################
	// END OF REASONING CYCLE METHODS
	// ############################################################################################

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((getAgentName() == null) ? 0 : getAgentName().hashCode());
		result = prime * result + ((supportedInfo == null) ? 0 : supportedInfo.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		AmbientContextAgent other = (AmbientContextAgent) obj;
		if (getAgentName() == null) {
			if (other.getAgentName() != null)
				return false;
		} else if (!getAgentName().equals(other.getAgentName()))
			return false;
		if (supportedInfo != other.supportedInfo)
			return false;
		return true;
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
			getLogger().error("[" + getAgentName()
					+ "] No contexts available for estimating missing information. Using neighbors contexts");

			synchronized (stateLock) {
				state = AgentState.ESTIMATE_WITH_CONTEXTS;
			}

			return estimateWithNeighbors();

			// return ContextEntry.empty(missingInfo);
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
	 * Added - 11/02/2020
	 * 
	 * Estimate a missing information using neighbors' contexts. This method is used
	 * when the agent has no contexts in his knowledge base
	 */
	private ContextEntry estimateWithNeighbors() {

		List<AmbientContextAgent> neighbors = getNeighborsWithWhichCooperate();

		neighbors.sort((c1, c2) -> Double.compare(getPosition().distance(c2.getPosition()),
				getPosition().distance(c2.getPosition())));

		neighbors = neighbors.subList(0, Math.min(neighbors.size(), 5));

		List<Context> neighborsContexts = neighbors.stream().filter(x -> x.getLastContext().isPresent())
				.map(x -> x.getLastContext().get()).collect(Collectors.toList());

		double[] lastTwoEntries = neighborsContexts.stream()
				.mapToDouble(x -> (x.getEntries()[x.getEntries().length - 1].getValue()
						+ x.getEntries()[x.getEntries().length - 2].getValue()) / 2)
				.toArray();

		return new ContextEntry(getSupportedInfo(), Instant.now(), new Mean().evaluate(lastTwoEntries));
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

		if (estimation.isEmpty()) {
			getLogger().error("[" + getAgentName()
					+ "] Error in estimating data with confidence zone. Estimating with contexts.\n");
			return ContextEntry.empty(missingInfo);
		}
		// else {
		// if (!estimation.isEmpty()) {
		// getLogger().error("[" + getAgentName()
		// + "] Error in estimating data with confidence zone. Estimating with
		// contexts.\n");

		// return estimation;
		// return ContextEntry.empty(missingInfo);
		// }

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

	private static Percentile PERCENTILE = new Percentile();

	/**
	 * 
	 * @param comparisons
	 * @return
	 */
	@Deprecated
	private List<Double> evaluateBestSubsetNeighbors(List<Double> comparisons) {
		// throw new NotImplementedException();

		double bestScore = Double.MAX_VALUE;
		List<Double> bestNeighbors = new ArrayList<>();

		for (double p = 5; p <= 100; p += 5) {

			// Calculate the threshold using the weights and taking the p-th percentile
			double th = PERCENTILE.evaluate(comparisons.stream().mapToDouble(x -> x).toArray(), p);

			// take the estimations which weight (that is, the distance between the
			// reference context and a similar context), is less than the threshold
			Set<Double> sp = comparisons.stream().filter(x -> x < th).collect(Collectors.toSet());

			if (sp.isEmpty()) {
				continue;
			} // Minimize the average distance between the reference context and the other
				// contexts
			double score = sp.stream().mapToDouble(x -> x).average().getAsDouble();
			if (score < bestScore) {
				bestScore = score;
				bestNeighbors.clear();
				bestNeighbors.addAll(sp);
			}
		}

		return bestNeighbors;

	}

	/**
	 * Evaluate the estimation by weighting the results from neighbor agents and the
	 * estimation of this agent
	 * 
	 * @param agentSuggestions the values that are estimated based on the contexts
	 *                         from neighbors
	 * @param selfEstimation   the value estimated using the contexts of this agent
	 * @return the final estimation that is provided by this agent
	 */
	// protected ContextEntry evaluateEstimation(List<EstimationResult>
	// agentSuggestions, EstimationResult selfEstimation) {
	protected ContextEntry evaluateEstimationOLD(List<Double> agentSuggestions, Double selfEstimation) {
		// double lastPerceivedValue =
		// lastContext.get().asDoubleArray()[lastContext.get().asDoubleArray().length -
		// 1];

		List<Double> weights = new ArrayList<>(agentSuggestions);

		if (agentSuggestions.size() > 0) {
			weights = evaluateBestSubsetNeighbors(agentSuggestions);
		}

		// getBestWeights(agentSuggestions);

		List<Pair<Double, Double>> weightsSorted = weights.stream().map(x -> Pair.of(Math.abs(x - selfEstimation), x))
				.sorted((c1, c2) -> Double.compare(c1.getLeft(), c2.getLeft())).collect(Collectors.toList());

		if (weightsSorted.size() > CONTEXT_FROM_NEIGHBORS_TO_USE_FOR_ESTIMATION) {
			weights = IntStream.range(0, CONTEXT_FROM_NEIGHBORS_TO_USE_FOR_ESTIMATION)
					.mapToObj(x -> weightsSorted.get(x).getRight()).collect(Collectors.toList());
		}
		OptionalDouble neighborsWeight = weights.stream().mapToDouble(x -> x).average();

		double weight = selfEstimation;
		if (neighborsWeight.isPresent() && MASConfig.USE_COOPERATION_FOR_ESTIMATION) {
			weight = (selfEstimation + neighborsWeight.getAsDouble()) / 2;
		}

		ContextEntry defImputation = new ContextEntry(getSupportedInfo(), Instant.now(),
				getLastPerceivedValue() + weight);

		return defImputation;
	}

	/**
	 * 
	 * @param neighborsEstimations
	 * @param myWeight
	 * @return
	 */
	protected ContextEntry evaluateEstimation(Map<Agent, Double> neighborsEstimations, Double myWeight) {
		// double lastPerceivedValue = getSensor().getCurrentData();
		double lastPerceivedValue = getLastPerceivedValue();

		double estimation = lastPerceivedValue + myWeight;

		if (MASConfig.USE_COOPERATION_FOR_ESTIMATION) {
			// mappa <agente, stima proposta>
			Map<Agent, Double> allEstimations = new HashMap<>(neighborsEstimations);

			allEstimations.put(this, estimation);

			double neighborsEstimation = getBestEstimation(allEstimations, (lastPerceivedValue + myWeight));

			// double nEst = new
			// Median().evaluate(allEstimations.values().stream().mapToDouble(x->x).toArray());

			// estimation = nEst;
			estimation = neighborsEstimation;
			// estimation = (estimation + neighborsEstimation) / 2;
		}

		return new ContextEntry(getSupportedInfo(), Instant.now(), estimation);
	}

	/**
	 * 
	 * @param estimations
	 * @param myEstimation
	 * @return
	 */
	double getBestEstimation(Map<Agent, Double> estimations, double myEstimation) {
		final int BIN_COUNT = 5;

		long[] histogram = new long[BIN_COUNT];
		org.apache.commons.math3.random.EmpiricalDistribution distribution = new org.apache.commons.math3.random.EmpiricalDistribution(
				BIN_COUNT);

		distribution.load(estimations.values().stream().mapToDouble(x -> x).toArray());
		int k = 0;
		for (org.apache.commons.math3.stat.descriptive.SummaryStatistics stats : distribution.getBinStats()) {
			histogram[k++] = stats.getN();
		}

		double neighborEstimation = distribution
				.getBinStats().stream().filter(x -> Double.isFinite(x.getMean())).min((c1, c2) -> Double
						.compare(Math.abs(c1.getMean() - myEstimation), Math.abs(c2.getMean() - myEstimation)))
				.get().getMean();

		/*
		 * double neighborEstimation = distribution .getBinStats().stream().max((c1,c2)
		 * -> Double.compare(c1.getN(),c2.getN())).get().getMean();
		 */
		/*
		 * double neighborEstimation = distribution .getBinStats().stream().filter(x ->
		 * Double.isFinite(x.getMean())).min((c1, c2) -> Double
		 * .compare(Math.abs(c1.getMean() - myEstimation), Math.abs(c2.getMean() -
		 * myEstimation))) .get().getMean();
		 */
		SummaryStatistics st = distribution.getBinStats().stream().filter(x -> Double.isFinite(x.getMean())).min((c1,
				c2) -> Double.compare(Math.abs(c1.getMean() - myEstimation), Math.abs(c2.getMean() - myEstimation)))
				.get();

		modifyNeighborsConfidence(estimations, st.getMin(), st.getMax());

		return neighborEstimation;
	}

	protected void modifyNeighborsConfidence(Map<Agent, Double> estimations, double binMin, double binMax) {
		for (Agent a : estimations.keySet()) {
			if (estimations.get(a) >= binMin && estimations.get(a) <= binMax) {
				modifyConfidence(a, Agent.CONFIDENCE_DELTA);
			} else {
				modifyConfidence(a, -Agent.CONFIDENCE_DELTA);
			}
		}
	}

	/**
	 * Use the listener to notify the last perceived information and clear the
	 * information buffer
	 */
	private void perceivedValue(int currentSampleIdx) {
		// create a new perception
		observedInfoBuffer
				.forEach(x -> setPerception(currentSampleIdx, new Perception(x.getValue(), x.isEstimation())));

		// notify the new perception to the listener
		if (!isRealSensor()) {
			System.err.println("listener with " + new ArrayList<>(observedInfoBuffer) + " - "
					+ dataSensor.get().getCurrentSampleIdx());
		}

		listener.ifPresent(x -> x.perceived(getAgentName(), new ArrayList<>(observedInfoBuffer),
				dataSensor.get().getCurrentSampleIdx()));

		// clear the buffer
		observedInfoBuffer.clear();
	}

	/**
	 * Once enough time has elapsed, the current observed context is added into the
	 * dictionary in case no similar contexts already exists.
	 */
	private void processPerception() {

		// still not perceived enough samples to build the first context
		if (getSensor().getCurrentSampleIdx() <= MIN_NUMBER_SAMPLE_OBSERVED) {
			processWithUnsufficientSamplesCount();
			return;
		}

		// Check if the current information in buffer is missing, thus this agent has to
		// evaluate an estimation
		boolean toEstimate = observedInfoBuffer.stream().filter(x -> x.isEmpty()).count() > 0;
		if (toEstimate) {

			// estimate the missing information using the last observed context
			ContextEntry estimation = estimateMissingData(getLastContext(), supportedInfo);

			if (estimation.isEmpty()) {
				return;
			}

			// remove the sample from buffer containing the missing information
			observedInfoBuffer.removeIf(x -> x.isEmpty());

			// get the concerned situation
			observedInfoBuffer.add(estimation);

			if (!isRealSensor()) {
				VirtualDataSensor vds = ((VirtualDataSensor) dataSensor.get());
				vds.setEstimation(estimation.getValue(), dataSensor.get().getCurrentSampleIdx());
			}
		}

		// Once i've estimated an information, notify the estimated value as the
		// perceived value
		perceivedValue(dataSensor.get().getCurrentSampleIdx());

		// Find the best context for the current information
		Context newContext = contextFinder.getContext(this, getSensor(), getPerceptions());

		addNewContextToMap(newContext);

		if (!newContext.isEmpty()) {
			getListener().addedContext(this, newContext);
			// observedInfoBuffer.clear();
			synchronized (lastContextLock) {
				lastContext = Optional.of(newContext);
			}
		}

		if (toEstimate) {
			//System.err
			//		.println("[" + getAgentName() + "] criticality --------> " + Double.toString(computeCriticality()));
		}

	}

	/**
	 * This is used when the agent must process the perceptions and the number of
	 * perceived information is lower or equal to [MIN_NUMBER_SAMPLE_OBSERVED].
	 */
	private void processWithUnsufficientSamplesCount() {

		// Add the first context
		if (getSensor().getCurrentSampleIdx() == MIN_NUMBER_SAMPLE_OBSERVED) {
			perceivedValue(dataSensor.get().getCurrentSampleIdx());
			// build the context
			Context bestContext = contextFinder.getContext(this, getSensor(), getPerceptions());

			if (bestContext.isEmpty()) {
				return;
			}
			// update the last observed context
			synchronized (lastContextLock) {
				lastContext = Optional.of(bestContext);
			}

			// contextBuffer.add(bestContext);
			getListener().addedContext(this, bestContext);
			addNewContextToMap(bestContext);

			// Update the last context
			synchronized (lastContextLock) {
				lastContext = Optional.of(bestContext);
			}

			return;
		}

		// still not perceived enough samples to build the first context
		if (getSensor().getCurrentSampleIdx() < MIN_NUMBER_SAMPLE_OBSERVED) {
			perceivedValue(dataSensor.get().getCurrentSampleIdx());

		}
	}

	private void addNewContextToMap(Context newContext) {
		if (newContext.isEmpty()) {
			return;
		}

		// Get the range of values of the new context
		ContextDescriptor descriptor = ContextDescriptor.getNumericContextDescriptor(newContext);
		Optional<ContextDescriptor> dd = getContexts().keySet().stream().filter(x -> descriptor.includedIn(x))
				.findFirst();

		// Add the new context to the contexts map
		synchronized (contextMapLock) {
			if (!dd.isPresent()) {
				// Create a new tree set structure that keeps the contexts sorted by their
				// number of estimated entries
				// add the tree set to the context map
				contextMap.put(descriptor, new TreeList<Context>());

				// add the context
				contextMap.get(descriptor).add(newContext);

				if (PROMOTE_CONTEXT_WITHOUT_ESTIMATION) {
					contextMap.get(descriptor).sort(
							(o1, o2) -> Integer.compare(o1.getEstimatedEntriesCount(), o2.getEstimatedEntriesCount()));
				}
			} else {
				contextMap.get(dd.get()).add(newContext);
				if (PROMOTE_CONTEXT_WITHOUT_ESTIMATION) {
					contextMap.get(dd.get()).sort(
							(o1, o2) -> Integer.compare(o1.getEstimatedEntriesCount(), o2.getEstimatedEntriesCount()));
				}
			}
		}
	}

	/**
	 * 
	 * @param source
	 * @param otherContextsIdx
	 * @return
	 */
	public synchronized List<Pair<Integer, Double>> getSimilarityScores(Context source,
			List<Integer> otherContextsIdx) {

		List<Pair<Integer, Double>> out = new ArrayList<>();
		Map<ContextDescriptor, List<Context>> theContexts = new HashMap<>(getContexts());

		List<Context> contexts = theContexts.entrySet().stream().flatMap(e -> e.getValue().stream())
				.filter(e -> otherContextsIdx.contains(e.getFinalDataIdx()) && source.size() == e.size())
				.collect(Collectors.toList());

		if (contexts.isEmpty()) {
			getLogger().trace("[" + getAgentName() + "] WARNING: no contexts found at indexes: "
					+ otherContextsIdx.stream().map(x -> Integer.toString(x)).collect(Collectors.joining(", ")));
			return out;
		}

		Optional<Context> sourceContext = contexts.stream().filter(x -> x.getFinalDataIdx() == source.getFinalDataIdx())
				.findFirst();

		for (Context c : contexts) {
			Double comparison = sourceContext.isPresent() ? sourceContext.get().compare(c)
					: contexts.get(contexts.size() - 1).compare(c);
			out.add(Pair.of(c.getFinalDataIdx(), comparison));
		}

		return out;
	}

	public ContextEstimationStrategy getImputationStrategy() {
		return estimationStrategy.orElseThrow(() -> new IllegalArgumentException("No imputation strategy provided"));
	}

	public synchronized List<Context> getAllContexts() {
		return new ArrayList<>(getContexts().values().stream().flatMap(List::stream).collect(Collectors.toList()));
	}

	/**
	 * Set estimation strategy to be used by this agent
	 * 
	 * @param strategy
	 */
	public void setEstimationStrategy(ContextEstimationStrategy strategy) {
		this.estimationStrategy = Optional.of(strategy);
	}

	/**
	 * Return {@code true} if this agent is associated to a real device that can
	 * perceive the physical environment. Otherwise, return {@code false}. In the
	 * second case when not associated to a real sensor, the agent behave as a
	 * virtual sensor, thus it uses the data from neighbor agents (when possible) to
	 * provide an accurate estimation
	 * 
	 * @return
	 */
	public boolean isRealSensor() {
		return dataSensor.isPresent() && dataSensor.get() instanceof FileDataSensor;
	}

	public DataSensor getSensor() {
		return dataSensor.orElseThrow(() -> new IllegalAccessError("no sensor present"));
	}

	public Optional<Context> getLastContext() {
		synchronized (contextMapLock) {
			if (lastContext.isPresent()) {
				return Optional.of(new Context(lastContext.get()));
			}
		}
		return Optional.empty();
	}

	public void setSensorFailureHandler(DataSensorFailureHandler h) {
		sensorFailureHandler = Optional.of(h);
	}

	public ConfidenceZone getConfidenceZone() {
		return confidenceZone.orElseThrow(() -> new IllegalArgumentException("no confidence zone provided"));
	}

	/**
	 * Get all the data collected among the contexts as a double array. Data are
	 * sorted according to the perception instant
	 * 
	 * @param info
	 * @return
	 */
	public double[] getDataAsDouble() {
		return getPerceptions().stream().mapToDouble(x -> x.getValue()).toArray();
	}

	public double lastPerception() {
		double[] data = getDataAsDouble();
		return data[data.length - 1];
	}

	public AgentState getAgentState() {
		AgentState s;
		synchronized (stateLock) {
			s = state;
		}
		return s;
	}

	public ContextInfo getSupportedInfo() {
		return supportedInfo;
	}

	public OptimalContextFinder getContextFinder() {
		return contextFinder;
	}

	protected Map<ContextDescriptor, List<Context>> getContexts() {
		synchronized (contextMapLock) {
			return new HashMap<>(contextMap);
		}
	}

	public Optional<Context> getContextAt(int idx) {
		synchronized (contextMapLock) {
			for (ContextDescriptor cd : contextMap.keySet()) {
				Optional<Context> cc = contextMap.get(cd).stream().filter(x -> x.getFinalDataIdx() == idx).findFirst();

				if (cc.isPresent()) {
					return Optional.of(new Context(cc.get()));
				}
			}
		}
		return Optional.empty();
	}

	public String getLongAgentName() {
		return toString();
	}

	@Override
	public void setPosition(Vector2D position) {
		super.setPosition(position);

		if (confidenceZone.isPresent()) {

			double deltaX = position.getX() - confidenceZone.get().centroid().getX();
			double deltaY = position.getY() - confidenceZone.get().centroid().getY();

			confidenceZone.get().translate(deltaX, deltaY);
		}

	}

	@Override
	protected double computeCriticality() {
		try {
			getLastPerceivedValue();
		}catch(NoSuchElementException e) {
			return Double.NEGATIVE_INFINITY;
		}
		
		
		double lastPerception = getLastPerceivedValue();
		ContextEntry otherACAsEstimation = estimateByContextualDataCooperationTEST(getSensor().getCurrentSampleIdx(),
				lastContext.get(), getSupportedInfo());

		double errorPerc = (Math.abs(otherACAsEstimation.getValue() - lastPerception) / lastPerception) * 100;

		return errorPerc;

		// return Double.NEGATIVE_INFINITY;
	}

	/* ********************************************* */
	/* CRITICALITY TEST */
	/* ********************************************* */

	private ContextEntry estimateByContextualDataCooperationTEST(int idx, Context lastContext,
			ContextInfo missingInfo) {
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

			// get the VIRTUAL agents
			List<AmbientContextAgent> neighbors = Environment.get().getAmbientContextAgents().stream()
					.filter(x -> x.getType() == AgentType.AMBIENT_CONTEXT_AGENT).map(x -> (AmbientContextAgent) x)
					.filter(x -> !x.isRealSensor() && x.isActive() && !x.isPaused()
							&& !x.getAgentName().equals(this.getAgentName()))
					.collect(Collectors.toList());

			neighborEstimations = estimateByCooperation(neighbors, lastContext, getSensor().getCurrentSampleIdx(),
					missingInfo, myContextIdxs);
			defEstimation = evaluateEstimation(neighborEstimations, myEstimationWeight);
		}

		defEstimation.setIsEstimation(true);

		if (listener.isPresent()) {
			listener.get().imputed(this, lastContext, defEstimation, cx, dataSensor.get().getCurrentData());
		}

		// Return the estimated entry
		return defEstimation;
	}

	/* ********************************************* */
	/* CRITICALITY TEST END */
	/* ********************************************* */

	private double getLastPerceivedValue() {
		return getLastContext().get().asDoubleArray()[getLastContext().get().asDoubleArray().length - 1];

		// return getSensor().getCurrentData();
	}

	@Override
	public String toString() {
		return "[(" + Double.toString(getHeight().orElse(0.)) + ")" + getAgentName() + "/" + supportedInfo.toString()
				+ "]";
	}
}
