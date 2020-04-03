package agent;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.descriptive.rank.Median;

import agent.base.Agent;
import agent.base.AgentType;
import agent.contactSensor.impl.PearsonCorrelationStrategy;
import agent.contactSensor.impl.PearsonThresholdEvaluation;
import agent.contactSensor.interfaces.CorrelationStrategy;
import agent.contactSensor.interfaces.CouplingStrategy;
import agent.contactSensor.interfaces.SensorPairsAction;
import agent.contactSensor.interfaces.ThresholdEvaluation;
import agent.environment.Environment;
import agent.listener.ContactSensorAgentListener;

public class ContactSensorAgent extends Agent {

	public final static Class<? extends CorrelationStrategy> DEFAULT_CORRELATION_METHOD = PearsonCorrelationStrategy.class;
	public final static Class<? extends ThresholdEvaluation> DEFAULT_THRESHOLD_METHOD = PearsonThresholdEvaluation.class;

	public final static boolean USE_CSA_COOPERATION = false;

	private static final boolean LIMIT_PAIRS_COUNT = false;

	private static final int MAXIMUM_PAIRS_ALLOWED = 4;

	private static int UPDATE_RATE = 2000;

	private int i = 0;

	private int sensorsCount;

	/**
	 * The list of neighbors agents (only real and virtual agents)
	 */
	private List<AmbientContextAgent> neighbors;

	/**
	 * The action to accomplish once the pairs of sensors have been assembled
	 */
	private final SensorPairsAction pairAction;

	/**
	 * The strategy used to pair sensors (real/virtual)
	 */
	private final CouplingStrategy couplingStrategy;

	/**
	 * The strategy used to evaluate the threshold over the correlations
	 */
	private final ThresholdEvaluation thresholdEvaluation;

	private Optional<ContactSensorAgentListener> listener;

	/**
	 * Correlation measured for each time instant by the pairs of ACAs
	 */
	private Map<Pair<AmbientContextAgent, AmbientContextAgent>, List<Double>> punctualCorrelationHistory;

	/**
	 * Correlation measured <i>per window</i> which has size of CONTEXT_WINDOW (see
	 * coupling strategy implementation class)
	 */
	private Map<Pair<AmbientContextAgent, AmbientContextAgent>, List<Double>> correlationHistory;

	private List<Double> contactSensorState;

	private Map<Pair<AmbientContextAgent, AmbientContextAgent>, Double[]> threshold;

	boolean setUpdateRate = false;

	public ContactSensorAgent(String name, SensorPairsAction pairAction, CouplingStrategy couplingStrategy,
			ThresholdEvaluation thresholdEvaluation) {
		super(name, AgentType.CONTACT);

		this.pairAction = pairAction;
		this.couplingStrategy = couplingStrategy;
		this.thresholdEvaluation = thresholdEvaluation;
		neighbors = new ArrayList<>();
		punctualCorrelationHistory = new HashMap<>();
		correlationHistory = new HashMap<>();
		listener = Optional.empty();
		contactSensorState = new ArrayList<>();
		threshold = new HashMap<>();
		sensorsCount = 0;
	}

	@Override
	protected void perceive() {

		// System.err.println("CurrentIDX: " + Integer.toString(currentIdx));
		// perceive neighbors
		// update neighbors list
		// System.err.println("[" + getAgentName() + "] perceive");

		neighbors = Environment.get().getAmbientContextAgents().stream().map(x -> (AmbientContextAgent) x)
				.collect(Collectors.toList());
	}

	@Override
	protected void decideAndAct() {

		if (neighbors.size() <= 1) {
			// nothing to do with 1 agent
			return;
		}

		// Evaluate the correlation values between the pairs at the current time instant
		Map<Pair<AmbientContextAgent, AmbientContextAgent>, Double> punctualCorrelations = evaluatePunctualCorrelation();

		if (!setUpdateRate) {
			setUpdateRate();
			setUpdateRate = true;
		}

		if (i > 0 && i % CouplingStrategy.CORRELATION_WINDOW_SIZE == 0) {
			// Evaluate the correlation window using the previous puntual correlation values
			calculateCorrelationBetweenPairs(punctualCorrelations);
		}

		if (i > 0 && i % UPDATE_RATE == 0) {
			// Evaluate the contact sensor state
			evaluateContactSensorState();
		}

		if (i >= UPDATE_RATE) {
			printContactSensorsState();
		}

		listener.ifPresent(x -> x.perceived(this, i));

		// System.out.println("i: "+Integer.toString(i)+" of
		// "+Integer.toString(UPDATE_RATE));
		i++;
	}

	private void printContactSensorsState() {

		DecimalFormat df = new DecimalFormat("#####0.00");
		DecimalFormatSymbols dfs = df.getDecimalFormatSymbols();
		dfs.setDecimalSeparator(',');
		df.setDecimalFormatSymbols(dfs);

		String agName = getAgentName();

		for (Double centroid : getThresholdEvaluation().getCentroids()) {
			double[] ll = new double[contactSensorState.size()];
			Arrays.fill(ll, centroid);
			String cc = Arrays.stream(ll).boxed().map(x -> Double.toString(x)).collect(Collectors.joining(","));
			System.out.println(agName + "(ths)," + cc);
		}

		for (Pair<AmbientContextAgent, AmbientContextAgent> pp : correlationHistory.keySet()) {

			String corrHist = correlationHistory.get(pp).stream().map(x -> Double.toString(x))
					.collect(Collectors.joining(","));
			System.out.println(agName + "(corr)," + corrHist);

			for (Pair<AmbientContextAgent, AmbientContextAgent> thePair : correlationHistory.keySet()) {

				/*
				 * String leftAg = thePair.getLeft().getPerceptions().stream().map(x ->
				 * Double.toString(x.getValue())) .collect(Collectors.joining(",")); String
				 * rightAg = thePair.getRight().getPerceptions().stream().map(x ->
				 * Double.toString(x.getValue())) .collect(Collectors.joining(","));
				 */
				// System.err.println(thePair.getLeft().getAgentName() + "(values)," + leftAg);
				// System.err.println(thePair.getRight().getAgentName() + "(values)," +
				// rightAg);

			}

		}

		List<Double> st = contactSensorState;
		String values = st.stream().map(x -> Integer.toString(new Double(x).intValue()))
				.collect(Collectors.joining(","));
		System.out.println(agName + "(state)," + values);

		System.out.println("---------------");

	}

	/**
	 * Evaluate the pairs of sensors to be used. Then evaluate the correlation
	 * between them
	 */
	void calculateCorrelationBetweenPairs(
			Map<Pair<AmbientContextAgent, AmbientContextAgent>, Double> punctualCorrelations) {

		for (Pair<AmbientContextAgent, AmbientContextAgent> pair : punctualCorrelations.keySet()) {
			List<Double> window = new ArrayList<>();

			int limit = punctualCorrelationHistory.get(pair).size() - 1;

			for (int j = limit; j > Math.max(0, j - CouplingStrategy.CORRELATION_WINDOW_SIZE); j--) {
				window.add(punctualCorrelationHistory.get(pair).get(j));
			}

			// calculate the mean of the observed correlation window
			double medianValue = new Median().evaluate(window.stream().mapToDouble(x -> x).toArray());
			// double meanWindow = window.stream().mapToDouble(x -> x).average().orElse(0.);

			if (!correlationHistory.containsKey(pair)) {
				correlationHistory.put(pair, new ArrayList<>());
			}

			for (int j = limit; j > Math.max(0, j - CouplingStrategy.CORRELATION_WINDOW_SIZE); j--) {
				correlationHistory.get(pair).add(medianValue);
			}
		}
	}

	private Map<Pair<AmbientContextAgent, AmbientContextAgent>, Double> evaluatePunctualCorrelation() {
		Map<Pair<AmbientContextAgent, AmbientContextAgent>, Double> correlations = new HashMap<>();

		if (LIMIT_PAIRS_COUNT) {
			correlations = couplingStrategy.act(this, neighbors, MAXIMUM_PAIRS_ALLOWED, i);
		} else {
			correlations = couplingStrategy.act(this, neighbors, i);
		}

		sensorsCount = correlations.keySet().size();

		// PUNTUAL CORRELATION

		// Add the observed correlations to the correlations history map
		for (Pair<AmbientContextAgent, AmbientContextAgent> pair : correlations.keySet()) {
			// if no correlation value has been observed for the pair, add a new list to the
			// map
			if (!punctualCorrelationHistory.containsKey(pair)) {
				punctualCorrelationHistory.put(pair, new ArrayList<>());
			}
			double corr = correlations.get(pair);

			if (Double.isFinite(corr)) {
				punctualCorrelationHistory.get(pair).add(corr);
			}
		}

		return correlations;
	}

	private void setUpdateRate() {
		int update_rate = Integer.MAX_VALUE;
		for (Pair<AmbientContextAgent, AmbientContextAgent> pair : punctualCorrelationHistory.keySet()) {
			int sl = pair.getLeft().getSensor().getSamplesCount() - 1;// pair.getLeft().getPerceptions().size() - 1;
			int sr = pair.getRight().getSensor().getSamplesCount() - 1;// .getPerceptions().size() - 1;

			int ur = Math.min(sl, sr);
			if (ur < update_rate) {
				update_rate = ur;
			}
		}

		UPDATE_RATE = Math.min(UPDATE_RATE, update_rate);

	}

	/**
	 * Evaluate the state of this contact sensor (open/closed) using the calculated
	 * correlations values. A threshold is calculated here and evaluate among the
	 * correlation values in order to deduce the state of the environment
	 */
	void evaluateContactSensorState() {

//		if (currentIdx == UPDATE_RATE - 1) {
		// Evaluate the threshold(s) for each pair of sensors
		for (Pair<AmbientContextAgent, AmbientContextAgent> pair : punctualCorrelationHistory.keySet()) {
			Double[] thresholds = thresholdEvaluation.calculate(this, pair, correlationHistory);
			getThreshold().put(pair, thresholds);
		}
		// return;
		// }

		// Loop over all the correlation values
		for (int i = 0; i < getObservedCorrelationsCount(); i++) {
			List<Double> classes = new ArrayList<>();

			// loop on each pair of sensors this CSA considers
			for (Pair<AmbientContextAgent, AmbientContextAgent> pair : correlationHistory.keySet()) {
				// get the correlation between the sensors of the current pair
				double cr = correlationHistory.get(pair).get(i);

				// evaluate the class (so the environments state) according to the pair
				classes.add(getClass(cr, getThreshold().get(pair)));

				// cooperative process among CSAs
				if (USE_CSA_COOPERATION) {
					Map<Agent, Double> CSAReponses = evaluateCooperativeContactSensorState(cr);

					for (Agent a : CSAReponses.keySet()) {
						classes.add(CSAReponses.get(a));
					}

				}

			}

			// la moda è il valore della classe, ovvero il valore di classe piu frequente
			contactSensorState.add(StatUtils.mode(classes.stream().mapToDouble(x -> x).toArray())[0]);
		}

		// currentIdx = 0;

	}

	/**
	 * Cooperate with agents that have a criticality lower than the one of this
	 * agent in order to calculate more accurately the state of this contact sensor
	 * agent
	 */
	private Map<Agent, Double> evaluateCooperativeContactSensorState(double myCorrelation) {

		Map<Agent, Double> suggestions = new HashMap<>();

		List<ContactSensorAgent> csa = Environment.get().getAgents().stream()
				.filter(x -> x.getType() == AgentType.CONTACT && !x.getAgentName().equals(getAgentName())
						&& x.getCriticality() <= getCriticality())
				.map(x -> (ContactSensorAgent) x).collect(Collectors.toList());

		for (ContactSensorAgent c : csa) {
			List<Double> classes = new ArrayList<>();
			for (Pair<AmbientContextAgent, AmbientContextAgent> pp : c.getThreshold().keySet()) {
				double theClass = c.getClass(myCorrelation, c.getThreshold().get(pp));
				classes.add(theClass);
			}

			if (classes.size() > 0) {

				double suggestion = StatUtils.mode(classes.stream().mapToDouble(x -> x).toArray())[0];

				if (suggestion != thresholdEvaluation.getUndeterminedClass()) {
					suggestions.put(c, suggestion);
				}

			}
		}

		return suggestions;
	}

	private int getObservedCorrelationsCount() {

		OptionalInt s = correlationHistory.values().stream().mapToInt(x -> x.size()).min();

		// OptionalInt s = correlationHistory.values().stream().flatMapToInt(x ->
		// IntStream.of(x.size())).min();
		return s.orElse(0);
	}

	private Double getClass(double corr, Double[] ths) {

		return new Integer(thresholdEvaluation.getClass(corr, ths)).doubleValue();
	}

	public CouplingStrategy getCouplingStrategy() {
		return couplingStrategy;
	}

	public SensorPairsAction getPairAction() {
		return pairAction;
	}

	public ThresholdEvaluation getThresholdEvaluation() {
		return thresholdEvaluation;
	}

	/**
	 * Return the type of this agent (override from {@code Agent}
	 * 
	 * @return
	 */
	@Override
	public AgentType getType() {
		return AgentType.CONTACT;
	}

	@Override
	protected double computeCriticality() {
		return 1 / new Double(sensorsCount);
	}

	public void setContactSensorListener(ContactSensorAgentListener listener) {
		this.listener = Optional.of(listener);
	}

	private Map<Pair<AmbientContextAgent, AmbientContextAgent>, Double[]> getThreshold() {

		synchronized (this) {
			return threshold;
		}

	}

}
