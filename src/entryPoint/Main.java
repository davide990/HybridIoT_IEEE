package entryPoint;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;

import agent.AmbientContextAgent;
import agent.base.Agent;
import agent.base.AgentFactory;
import agent.base.Perception;
import agent.crossValidation.BestNeighborhoodStrategy;
import agent.crossValidation.CrossValidationAgent;
import agent.environment.Environment;
import agent.listener.AgentListener;
import configuration.MASConfig;
import context.Context;
import context.ContextEntry;
import context.ContextInfo;

public class Main {
	private static Object finishedTrainingAgentLock = new Object();
	private static int finishedTrainingAgent = 0;

	public static Object finishedTestingAgentLock = new Object();
	public static int finishedTestingAgent = 0;

	public static Object finishedContactSensorAgentLock = new Object();
	public static int finishedContactSensorAgent = 0;

	public static Object statMapsLock = new Object();
	public static Map<String, Double> meanErrorMap = new HashMap<>();
	public static Map<String, Double> stdMap = new HashMap<>();
	public static Map<String, Double> rmseMap = new HashMap<>();
	public static Map<String, Double> maeMap = new HashMap<>();

	/**
	 * ENTRY POINT
	 * 
	 * @param args
	 */
	public static void main(String[] args) {

		// get the XML config file as the first parameter
		String configFname = args[0];

		// create a new configuration
		MASConfig config = openMASConfiguration(configFname, args);

		// parse the configuration file
		if (!config.parse()) {
			System.err.println("Unable to parse file name [" + configFname + "].");
			return;
		}

		parseArgs(args);

		// setup the task responsible for showing mean/std to screen once the execution
		// is terminated
		setupShutdownTask();
		/*
		 * System.err.println("best neighbors strategy: " +
		 * MASConfig.BEST_AGENT_NEIGHBORS_STRATEGY);
		 * System.err.println("percentage agents to which cooperate: " +
		 * Integer.toString(MASConfig.PERCENTAGE_AGENT_TO_WHICH_COOPERATE));
		 * 
		 * System.err.println("~~~ TRAINING ~~~");
		 */
		trainModel(config);

		// System.err.println("~~~ TESTING ~~~");
		testModel(config);

		// Stop all the agents
		Environment.get().getAgents().forEach(x -> x.stopAgent());
	}

	/**
	 * Read args from command line. This function override some parameters that
	 * could be specified in the xml configuration file
	 * 
	 * @param args
	 */
	static void parseArgs(String[] args) {
		if (args.length < 3) {
			return;
		}

		if (args.length >= 3) {
			MASConfig.STAT_MAP_OUT_FILE = args[2];
		}

		if (args.length >= 4) {
			MASConfig.PERCENTAGE_AGENT_TO_WHICH_COOPERATE = Integer.parseInt(args[3]);
		}

		if (args.length >= 5) {
			MASConfig.BEST_AGENT_NEIGHBORS_STRATEGY = BestNeighborhoodStrategy.valueOf(args[4]);
		}
		
		if (args.length >= 6) {
			MASConfig.NUM_FOLDS = Integer.parseInt(args[5]);
		}
		
		if (args.length >= 7) {
			MASConfig.USE_COOPERATION_FOR_ESTIMATION = Boolean.parseBoolean(args[6]);
		}
		
		if (args.length >= 8) {
			MASConfig.TEST_INFO = ContextInfo.fromString(args[7]);
		}
		
		if (args.length >= 9) {
			MASConfig.NEIGHBORS_INFO = ContextInfo.fromString(args[8]);
		}
		
	}

	/**
	 * Train the model. In this phase the training agents assemble the contexts by
	 * perceiving the environment
	 * 
	 * @param config
	 */
	private static void trainModel(MASConfig config) {
		startTrainingAgents(config);
		int numTrainingAgentFinished = 0;

		int numTrainingAgent = (int) config.getAmbientContextAgents().stream()
				.filter(x -> ((CrossValidationAgent) x).isTrainOnly()).count();

		// Wait for training agents to finish and assemble their contexts
		while (numTrainingAgentFinished < numTrainingAgent) {
			synchronized (finishedTrainingAgentLock) {
				numTrainingAgentFinished = finishedTrainingAgent;
			}
		}
	}

	/**
	 * Test the model. Once the training phase has finished. The test agent do a
	 * k-fold cross validation estimating missing information on each fold, and
	 * cooperating with the training agents
	 * 
	 * @param config
	 */
	private static void testModel(MASConfig config) {
		startTestAgents(config);
		int numTestAgent = (int) config.getAmbientContextAgents().stream().map(x -> (CrossValidationAgent) x)
				.filter(x -> !x.isTrainOnly()).count();

		// Wait for the test agents to finish
		int numTestingAgentFinished = 0;
		while (numTestingAgentFinished < numTestAgent) {
			synchronized (finishedTestingAgentLock) {
				numTestingAgentFinished = finishedTestingAgent;
			}
		}
	}

	/**
	 * Set up a task to be executed once all the agents have finished their
	 * execution. The tast will show some statistics about the estimations (mean
	 * err, standard deviation...)
	 */
	private static void setupShutdownTask() {
		Thread shutDownTask = new Thread() {
			@Override
			public void run() {

				// System.err.println("~~~ TERMINATING ~~~");

				for (String id : Main.meanErrorMap.keySet()) {
					if (!isTestAgent(id)) {
						continue;
					}
					System.err.println(
							id + "\tmean error\t" + Main.meanErrorMap.get(id) + "\tstd\t" + Main.stdMap.get(id) + "");

					appendStatsToFile(MASConfig.STAT_MAP_OUT_FILE, id, Main.meanErrorMap.get(id), Main.rmseMap.get(id),
							Main.maeMap.get(id), Main.stdMap.get(id));

				}
				/*
				 * System.err.println(CrossValidationAgent.CONTEXT_SIZE_MAP.entrySet().stream()
				 * .map(x -> Integer.toString(x.getKey()) + "\t" +
				 * Integer.toString(x.getValue())) .collect(Collectors.joining("\t")));
				 */
			}

		};

		// add shutdown hook
		Runtime.getRuntime().addShutdownHook(shutDownTask);
	}

	private static void appendStatsToFile(String statMapOutFile, String agent, Double meanErr, Double RMSE, Double MAE,
			Double std) {
		DecimalFormat df = new DecimalFormat("#####0.00");
		DecimalFormatSymbols dfs = df.getDecimalFormatSymbols();
		dfs.setDecimalSeparator(',');
		df.setDecimalFormatSymbols(dfs);

		File file = new File(statMapOutFile);
		try {
			FileWriter fr = new FileWriter(file, true);
			fr.write("\nAGENT;ME;STD;RMSE;MAE\n");
			fr.write(agent + ";" + df.format(meanErr) + ";" + df.format(std) + ";" + df.format(RMSE) + ";"
					+ df.format(MAE) + "\n");
			fr.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		System.err.println("Stats appended to [" + MASConfig.STAT_MAP_OUT_FILE + "]");

	}

	private static boolean isTestAgent(String s) {
		List<CrossValidationAgent> cgs = Environment.get().getAmbientContextAgents().stream()
				.filter(x -> x instanceof CrossValidationAgent).map(x -> (CrossValidationAgent) x)
				.filter(x -> x.getAgentName().compareTo(s) == 0 && !x.isTrainOnly()).collect(Collectors.toList());

		return !cgs.isEmpty();
	}

	/**
	 * Open the XML configuration file that is used to setup all the agents
	 * 
	 * @param configFname
	 * @return
	 */
	private static MASConfig openMASConfiguration(String configFname, String[] args) {
		MASConfig config = new MASConfig(configFname, args) {
			@Override
			public Optional<Agent> createContactSensorAgent(Vector2D vector2d, String agName) {
				return Optional.empty();
			}

			@Override
			public Optional<Agent> createAgent(Vector2D position, String dataFile, String agName, String colSep,
					ContextInfo info, Optional<Integer> foldID, boolean onlyTraining) {
				AmbientContextAgent ag = null;
				try {
					ag = AgentFactory.getCrossValidationAgent(position, dataFile, agName, colSep, MASConfig.NUM_FOLDS,
							foldID.orElse(-1), onlyTraining, info);
					ag.setAgentName(agName);
				} catch (Exception e) {
					e.printStackTrace();
				}

				return Optional.of(ag);
			}
		};
		return config;
	}

	/**
	 * Start the training phase. Training agents will read all the data using their
	 * sensors and assemble the necessary contexts used by the test agent for
	 * estimating missing information
	 * 
	 * @param config
	 */
	public static void startTrainingAgents(MASConfig config) {
		config.getAmbientContextAgents().stream().filter(x -> ((CrossValidationAgent) x).isTrainOnly()).forEach(ag -> {
			// Set the listener
			ag.setListener(new AgentListener() {

				@Override
				public void terminated(Agent ag) {
					synchronized (finishedTrainingAgentLock) {
						++finishedTrainingAgent;
					}
				}

				@Override
				public void perceived(String agName, List<ContextEntry> entries, int currentSampleIdx) {
				}

				@Override
				public void imputed(Agent ag, Context buildingContext, ContextEntry imputedEntry,
						Map<Agent, List<Context>> usedContext, double realValue) {
				}

				@Override
				public void addedContext(Agent ag, Context context) {
				}

				@Override
				public void perceived(Agent ag, Perception p, int currentSampleIdx) {
				}
			});

			/*
			 * statMap.put(ag.getAgentName(), Pair.of(-1.0, -1.0));
			 * meanErrorMap.put(ag.getAgentName(), -1.); stdMap.put(ag.getAgentName(), -1.);
			 * rmseMap.put(ag.getAgentName(), -1.);
			 */
			// Start the agent
			ag.start();

			// System.err.println("[TRAINING] agent [" + ag.getAgentName() + "] started");
		});
	}

	/**
	 * Start the testing agent.
	 * 
	 * @param config
	 */
	public static void startTestAgents(MASConfig config) {
		config.getAmbientContextAgents().stream().filter(x -> !((CrossValidationAgent) x).isTrainOnly()).forEach(ag -> {
			// Set the listener

			ag.setListener(
					AgentListener.getCrossValidationAgentListener(MASConfig.OUT_ESTIMATION_FOLDER + File.separator
							+ ag.getAgentName() + "_" + ((AmbientContextAgent) ag).getSupportedInfo().toString()));

			meanErrorMap.put(ag.getAgentName(), -1.);
			stdMap.put(ag.getAgentName(), -1.);
			rmseMap.put(ag.getAgentName(), -1.);
			maeMap.put(ag.getAgentName(), -1.);

			// Start the agent
			ag.start();

			// System.err.println("agent [" + ag.getAgentName() + "] started");
		});

	}

}
