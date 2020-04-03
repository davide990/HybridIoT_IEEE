package agent.crossValidation;

import java.io.EOFException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;

import agent.AmbientContextAgent;
import agent.base.Agent;
import agent.base.AgentType;
import agent.environment.Environment;
import configuration.MASConfig;
import context.Context;
import context.ContextInfo;
import context.descriptor.ContextDescriptor;
import context.dynamicContext.VarWidthContextFinder;
import sensors.DataSensor;
import sensors.DataSensorFailureHandler;
import sensors.FileDataSensor;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;
import ui.ContextSetPlotter;

public class CrossValidationAgent extends AmbientContextAgent {

	private static final boolean SHOW_CONTEXT_SIZE_PLOT = true;

	/**
	 * An <Int,Int> map where the key is a length of a context, the value is the
	 * number of contexts of the length [key] that have been found by this agent
	 */
	private static Object CONTEXT_SIZE_MAP_LOCK = new Object();
	public static Map<Integer, Integer> CONTEXT_SIZE_MAP = new HashMap<>();

	/**
	 * A map containing the estimation arrays for every {@code CrossValidationAgent}
	 * instance
	 */
	public static Map<String, double[]> ESTIMATION_MAP = new HashMap<>();

	/**
	 * A boolean variable that indicates whether {@code ESTIMATION_MAP} array has
	 * been initialized for a given agent and a given information
	 */
	private static List<String> SET_LIST = new ArrayList<>();

	/**
	 * Lock object for {@code ESTIMATION_MAP} array
	 */
	private static Object ESTIMATION_MAP_LOCK = new Object();

	public static Object FOLD_FINISHED_LOCK = new Object();
	public static int FOLD_FINISHED = 0;

	static boolean plotterInvoked = false;
	static Object plotterInvokedLock = new Object();

	/**
	 * The start index of the fold this agent is responsible for testing
	 */
	private int foldStartIndex;

	/**
	 * The end index of the fold this agent is responsible for testing
	 */
	private int foldEndIndex;

	/**
	 * The size of the fold
	 */
	private int foldSize;

	/**
	 * the index of the fold this agent is responsible for estimating
	 */
	private int foldID;

	/**
	 * The operation mode of this agent (training/testing)
	 */
	private CrossValidationMode mode;

	/**
	 * An integer array to be used for cross-validation. A 0 value depicts a
	 * training sample, whereas 1 depicts a test sample
	 */
	private Boolean[] testSetIDX;

	private boolean trainOnly;

	private boolean notifiedFinished;

	/**
	 * Constructor for the {@code CrossValidationAgent} class.
	 * 
	 * @param dr
	 * @param info
	 */
	protected CrossValidationAgent(String agName, DataSensor dr, ContextInfo info) {
		super(agName, dr, info);
		mode = CrossValidationMode.TRAINING;
		notifiedFinished = false;
	}

	public static CrossValidationAgent getNew(Vector2D position, DataSensor dr, String agName, int numFolds, int foldID,
			boolean trainOnly, ContextInfo info) {
		CrossValidationAgent ag = getNew(dr, agName, numFolds, foldID, trainOnly, info);
		ag.setPosition(position);
		return ag;
	}

	/**
	 * Static factory method for {@code CrossValidationAgent} class. By using this
	 * constructor, no Confidence zone is associated to the agent.
	 * 
	 * @param dr
	 * @param numFolds
	 * @param foldID
	 * @param trainOnly
	 * @param info
	 * @return
	 */
	public static CrossValidationAgent getNew(DataSensor dr, String agName, int numFolds, int foldID, boolean trainOnly,
			ContextInfo info) {
		// Create the agent
		CrossValidationAgent ag = new CrossValidationAgent(agName, dr, info);
		ag.initAgent(Vector2D.NaN);
		ag.trainOnly = trainOnly;

		if (!trainOnly) {
			synchronized (ESTIMATION_MAP_LOCK) {
				if (!SET_LIST.contains(ag.getAgentName())) {
					ESTIMATION_MAP.put(ag.getAgentName(), new double[dr.getSamplesCount()]);
					SET_LIST.add(ag.getAgentName());
				}
			}
			// Set the test set indexes
			ag.testSetIDX = getTestSetIDX(ag, numFolds, foldID, dr.getSamplesCount(), trainOnly);
		}

		// Set a new handler for sensor failure
		ag.setSensorFailureHandler(new DataSensorFailureHandler() {

			@Override
			public void handleSensorFailure(Agent a, Exception e, DataSensor s) {
				CrossValidationAgent agent = (CrossValidationAgent) a;

				if (agent.isTrainOnly()) {
					if (!agent.notifiedFinished) {
						agent.notifiedFinished = true;
						a.getListener().terminated(a);
					}
					return;
				}

				if (e instanceof EOFException && agent.getCrossValidationMode() == CrossValidationMode.TRAINING) {
					agent.getSensor().resetDataIdx();

					((FileDataSensor) s).setTestSetIDX(ag.testSetIDX);

					agent.observedInfoBuffer.clear();

					// Switch to TESTING mode
					agent.setCrossValidationMode(CrossValidationMode.TESTING);

					if (SHOW_CONTEXT_SIZE_PLOT && agent.isTrainOnly()) {
						/*
						 * List<Context> contexts = ag.getAllContexts(); synchronized
						 * (plotterInvokedLock) { if (!plotterInvoked) { plotterInvoked = true;
						 * ContextSetPlotter.run(contexts, agent.getAgentName()); } }
						 */
					}

					//	System.err.println("[" + agent.getAgentName() + "] Finished training. Switching to test mode");
					agent.getLogger().trace("Finished training. Switching to test mode");
				} else if (e instanceof EOFException && agent.getCrossValidationMode() == CrossValidationMode.TESTING) {
					//System.err.println("here");

					agent.getSensor().resetDataIdx();

					((FileDataSensor) s).setTestSetIDX(ag.testSetIDX);

					agent.observedInfoBuffer.clear();

					agent.setCrossValidationMode(CrossValidationMode.TESTING_WITH_BEST_AGENTS);

				//	System.err.println(
					//			"[" + agent.getAgentName() + "] Finished training. Switching to test with best agent mode");
					agent.getLogger().trace("Finished training. Switching to test with best agent mode");
				} else {
					// Take all the values perceived by the agent (both estimated and reals)
					double[] vals = agent.getDataAsDouble();

					// Take the source and destination indexes where the values have to be saved in
					// the final array
					int srcIDX = agent.foldStartIndex;
					int destPos = agent.foldEndIndex;

					// If the first fold has to be saved
					if (agent.foldID == 0) {
						// save also the data at the first positions
						srcIDX = 0;
					}

					synchronized (ESTIMATION_MAP_LOCK) {
						// copy from srcIDX to srcIDX+len-1 from array vals to the same start position
						// in ESTIMATION
						for (int i = srcIDX; i <= destPos; i++) {
							ESTIMATION_MAP.get(agent.getAgentName())[i] = vals[i];
						}
					}

					if (agent.getContextFinder() instanceof VarWidthContextFinder) {

						synchronized (CONTEXT_SIZE_MAP_LOCK) {
							CONTEXT_SIZE_MAP.clear();
							for (ContextDescriptor cd : agent.getContexts().keySet()) {

								for (Context c : ag.getContexts().get(cd)) {
									if (CONTEXT_SIZE_MAP.containsKey(c.size())) {
										CONTEXT_SIZE_MAP.put(c.size(), CONTEXT_SIZE_MAP.get(c.size()) + 1);
									} else {
										CONTEXT_SIZE_MAP.put(c.size(), 0);
									}
								}
							}
						}
					}

					// System.err.println("More confident agents: ");
					// agent.printMoreConfidentAgent();

					agent.getListener().terminated(agent);
					agent.stopAgent();
				}
			}
		});
		return ag;
	}

	@Override
	protected List<AmbientContextAgent> getNeighborsWithWhichCooperate() {
		if (getCrossValidationMode() != CrossValidationMode.TESTING_WITH_BEST_AGENTS) {
			return super.getNeighborsWithWhichCooperate();
		}

		if (MASConfig.BEST_AGENT_NEIGHBORS_STRATEGY == BestNeighborhoodStrategy.MOST_CONFIDENT_AGENTS) {
			return getMostConfidentAgentsToWhichCooperate(MASConfig.PERCENTAGE_AGENT_TO_WHICH_COOPERATE);
		} else if (MASConfig.BEST_AGENT_NEIGHBORS_STRATEGY == BestNeighborhoodStrategy.NEAREST_AGENTS) {
			return getNearestAgentsToWhichCooperate(MASConfig.PERCENTAGE_AGENT_TO_WHICH_COOPERATE);
		}

		throw new IllegalArgumentException("Invalid neighborhood strategy.");
	}

	@Override
	protected void modifyNeighborsConfidence(Map<Agent, Double> estimations, double binMin, double binMax) {
		if (getCrossValidationMode() == CrossValidationMode.TESTING_WITH_BEST_AGENTS) {
			return;
		}

		super.modifyNeighborsConfidence(estimations, binMin, binMax);
	}

	/**
	 * 
	 * @param perc percentage
	 */
	private List<AmbientContextAgent> getNearestAgentsToWhichCooperate(int perc) {

		List<AmbientContextAgent> neighbors = Environment.get().getAmbientContextAgents().stream()
				.filter(x -> x.getType() == AgentType.AMBIENT_CONTEXT_AGENT).map(x -> (AmbientContextAgent) x)
				.filter(x -> x.isRealSensor() && x.isActive() && !x.isPaused()
						&& !x.getAgentName().equals(this.getAgentName()))
				.sorted((a1, a2) -> Double.compare(Vector2D.distance(getPosition(), a1.getPosition()),
						Vector2D.distance(getPosition(), a2.getPosition())))
				.collect(Collectors.toList());

		double numAgents = (double) neighbors.size() * ((double) MASConfig.PERCENTAGE_AGENT_TO_WHICH_COOPERATE / 100.);

		return IntStream.range(0, (int) numAgents).mapToObj(x -> neighbors.get(x)).collect(Collectors.toList());
	}

	/**
	 * 
	 * @param perc percentage
	 */
	private List<AmbientContextAgent> getMostConfidentAgentsToWhichCooperate(int perc) {

		List<AmbientContextAgent> neighbors = Environment.get().getAmbientContextAgents().stream()
				.filter(x -> x.getType() == AgentType.AMBIENT_CONTEXT_AGENT).map(x -> (AmbientContextAgent) x)
				.filter(x -> x.isRealSensor() && x.isActive() && !x.isPaused()
						&& !x.getAgentName().equals(this.getAgentName()))
				.sorted((a1, a2) -> Double.compare(getConfidence(a1), getConfidence(a2))).collect(Collectors.toList());

		double numAgents = (double) neighbors.size() * ((double) MASConfig.PERCENTAGE_AGENT_TO_WHICH_COOPERATE / 100.);

		return IntStream.range(0, (int) numAgents).mapToObj(x -> neighbors.get(x)).collect(Collectors.toList());
	}

	@Override
	public void perceive() {
		super.perceive();

		/*
		 * if (getSensor().getCurrentSampleIdx() >= getSensor().getSamplesCount() + 1 &&
		 * mode == CrossValidationMode.TESTING) {
		 */
		if (getSensor().getCurrentSampleIdx() >= getSensor().getSamplesCount() + 1
				&& mode == CrossValidationMode.TESTING_WITH_BEST_AGENTS) {
			stopAgent();
		}
	}

	@Override
	public void decideAndAct() {
		if (trainOnly && getSensor().getCurrentSampleIdx() >= getSensor().getSamplesCount() + 1) {
			return;
		}
		super.decideAndAct();
	}

	/**
	 * Evaluate the array of indexes to be used for evaluating the training/test set
	 * 
	 * @param numFolds
	 * @param foldID
	 * @param dataSamples the size of the data set
	 * @return
	 */
	private static Boolean[] getTestSetIDX(CrossValidationAgent ag, int numFolds, int foldID, int dataSamples,
			boolean trainOnly) {

		if (trainOnly || foldID == -1) {
			Boolean[] testSetIdx = new Boolean[dataSamples];

			// start by filling all the test set array to false
			Arrays.fill(testSetIdx, false);
			return testSetIdx;
		}

		// The ID for the fold must be lower of the number of folds
		if (foldID >= numFolds) {
			throw new IllegalArgumentException("fold ID over the number of folds");
		}

		// Size of each fold
		int foldSize = new Double(Math.ceil((double) dataSamples / (double) numFolds)).intValue();

		// Index of the first element of the fold
		int foldIDXstart = (foldID * foldSize);

		// Index of the last element of the fold
		int foldIDXend = Math.min(foldIDXstart + foldSize, dataSamples) - 1;

		// If the fold is the first, keep the data from 0 to the context size in order
		// to the agent to have a reliable value when starting the estimation step
		if (foldIDXstart < MIN_NUMBER_SAMPLE_OBSERVED) {
			foldIDXstart += MIN_NUMBER_SAMPLE_OBSERVED + 1;
		}

		ag.getLogger().trace("-> fold id: " + Integer.toString(foldID));
		ag.getLogger().trace("-> fold size: " + Integer.toString(foldSize));
		ag.getLogger().trace("-> samples count: " + Integer.toString(dataSamples));

		ag.getLogger().trace("[" + ag.toString() + "]--------- FOLD START: " + Integer.toString(foldIDXstart));
		ag.getLogger().trace("[" + ag.toString() + "]--------- FOLD END: " + Integer.toString(foldIDXend));

		ag.foldStartIndex = foldIDXstart;
		ag.foldEndIndex = foldIDXend;
		ag.foldSize = foldSize;
		ag.foldID = foldID;

		// data in interval [foldIDXstart, foldIDend] are used as TEST SET(=1), the
		// other as TRAINING set(=0)
		Boolean[] testSetIdx = new Boolean[dataSamples];

		// start by filling all the test set array to false
		Arrays.fill(testSetIdx, false);
		if (!trainOnly) {
			// Fill the test set index array with true in the range
			// [foldIDXstart,foldIDXend]
			for (int i = foldIDXstart; i <= foldIDXend; i++) {
				testSetIdx[i] = true;
			}

		}

		// Assign the test set vector to the sensor
		return testSetIdx;
	}

	private void printMoreConfidentAgent() {

		List<Agent> neighbors = Environment.get().getAmbientContextAgents().stream()
				.filter(x -> x.getType() == AgentType.AMBIENT_CONTEXT_AGENT).map(x -> (AmbientContextAgent) x)
				.filter(x -> x.isRealSensor() && x.isActive() && !x.isPaused()
						&& !x.getAgentName().equals(this.getAgentName()))
				.collect(Collectors.toList());

		double numAgents = (double) neighbors.size() * ((double) MASConfig.PERCENTAGE_AGENT_TO_WHICH_COOPERATE / 100.);

		neighbors.sort((c1, c2) -> Double.compare(getConfidence(c1), getConfidence(c2)));
		System.err.println(IntStream.range(0, (int) numAgents).mapToObj(x -> neighbors.get(x).getAgentName())
				.collect(Collectors.joining(";")));

	}

	public CrossValidationMode getCrossValidationMode() {
		return mode;
	}

	private void setCrossValidationMode(CrossValidationMode mode) {
		this.mode = mode;
	}

	public int getFoldStartIndex() {
		return foldStartIndex;
	}

	public int getFoldEndIndex() {
		return foldEndIndex;
	}

	public int getFoldSize() {
		return foldSize;
	}

	public static double[] getEstimationArray(CrossValidationAgent ag) {
		synchronized (ESTIMATION_MAP_LOCK) {
			return ESTIMATION_MAP.get(ag.getAgentName());
		}
	}

	public boolean isTrainOnly() {
		return trainOnly;
	}

	public void setTrainOnly(boolean trainOnly) {
		this.trainOnly = trainOnly;
	}
}
