package agent.listener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;

import agent.AmbientContextAgent;
import agent.ContactSensorAgent;
import agent.base.Agent;
import agent.base.AgentType;
import agent.base.Perception;
import agent.crossValidation.CrossValidationAgent;
import agent.environment.Environment;
import context.Context;
import context.ContextEntry;
import entryPoint.Main;
import smile.validation.RMSE;

public interface AgentListener {

	final static boolean PRINT_CONFIDENCE_TABLES = false;

	void addedContext(Agent ag, Context context);

	void perceived(String agName, List<ContextEntry> entries, int currentSampleIdx);

	void perceived(Agent ag, Perception p, int currentSampleIdx);

	void imputed(Agent ag, Context buildingContext, ContextEntry imputedEntry, Map<Agent, List<Context>> usedContext,
			double realValue);

	void terminated(Agent ag);

	/*****************************************/
	/**
	 * Default agent listener used for cross validation agents. The
	 * {@code terminated()} method is overridden to write the estimation array to
	 * the specified input file name
	 * 
	 * @param outEstimationFname
	 * @return
	 */
	public static AgentListener getCrossValidationAgentListener(String outEstimationFname) {
		return new AgentListener() {

			@Override
			public void terminated(Agent ag) {

				if (ag.getType() == AgentType.CONTACT) {
					ContactSensorAgent cag = (ContactSensorAgent) ag;
					processContactSensorAgentResult(cag, outEstimationFname);
					// terminates here
					return;
				}

				List<AmbientContextAgent> ags = Environment.get().getAmbientContextAgents().stream()
						.map(x -> (AmbientContextAgent) x).collect(Collectors.toList());

				List<String> agNames = Environment.get().getAmbientContextAgents().stream().map(x -> x.getAgentName())
						.collect(Collectors.toList());

				DecimalFormat df = new DecimalFormat("#####0.00");
				DecimalFormatSymbols dfs = df.getDecimalFormatSymbols();
				dfs.setDecimalSeparator(',');
				df.setDecimalFormatSymbols(dfs);
				if (PRINT_CONFIDENCE_TABLES) {
					System.err.println("\t" + agNames.stream().collect(Collectors.joining("\t")));

					for (int i = 0; i < agNames.size(); i++) {
						System.err.print(agNames.get(i) + "\t");

						for (int j = 0; j < agNames.size(); j++) {
							final String agIName = agNames.get(i);
							final String agJName = agNames.get(j);
							AmbientContextAgent agI = ags.stream().filter(x -> x.getAgentName().equals(agIName))
									.findFirst().get();
							AmbientContextAgent agJ = ags.stream().filter(x -> x.getAgentName().equals(agJName))
									.findFirst().get();

							if (agI.getConfidenceValues().containsKey(agJ)) {
								// System.err.print(
								// df.format(agI.getConfidenceValues().get(agJ).getValue())+"\t");
								System.err.print(df.format(agI.getConfidenceValues().get(agJ)) + "\t");
							} else {
								System.err.print("NaN\t");
							}
						}
						System.err.print("\n");
					}
				}
				File outFile = new File(outEstimationFname + ".csv");
				boolean isFileUnlocked = false;
				try {
					FileUtils.touch(outFile);
					isFileUnlocked = true;
				} catch (IOException e) {
					isFileUnlocked = false;
				}

				if (!isFileUnlocked) {
					System.err.println(
							"[ERROR] file \"" + outEstimationFname + ".csv\" is already opened. Cannot write results.");
					Environment.get().getAgents().forEach(x -> x.stopAgent());
					return;
				}

				// Format the estimation as a string that will be saved to the CSV output file
				double[] pmap = CrossValidationAgent.getEstimationArray((CrossValidationAgent) ag);
				String estimationString = ag.getAgentName() + " estimation;"
						+ Arrays.stream(pmap).boxed().map(x -> df.format(x)).collect(Collectors.joining(";"));

				// Do the same with the real data
				double[] data = ((AmbientContextAgent) ag).getSensor().getObservedData();
				String dataString = ag.getAgentName() + "_real;"
						+ Arrays.stream(data).boxed().map(x -> df.format(x)).collect(Collectors.joining(";"));

				double[] diff = IntStream.range(0, Math.min(data.length, pmap.length))
						.mapToDouble(i -> data[i] - pmap[i]).toArray();

				// evaluate mean error and standard deviation to print to the output console
				double meanErr = new Mean().evaluate(diff);
				double std = new StandardDeviation().evaluate(diff);
				double rmse = new RMSE().measure(data, pmap);
				
				synchronized (Main.statMapsLock) {
					Main.meanErrorMap.put(((AmbientContextAgent) ag).getSensor().getID(), meanErr);
					Main.stdMap.put(((AmbientContextAgent) ag).getSensor().getID(), std);
					Main.rmseMap.put(((AmbientContextAgent) ag).getSensor().getID(), rmse);
					Main.maeMap.put(((AmbientContextAgent) ag).getSensor().getID(), Math.abs(meanErr));
				}

				// Assemble data on two lines
				String r = estimationString + "\n" + dataString;
				OutputStream os = null;
				try {
					// Write the data to file
					os = new FileOutputStream(outFile);
					os.write(r.getBytes());
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					try {
						os.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

				synchronized (Main.finishedTestingAgentLock) {
					++Main.finishedTestingAgent;
				}
			}

			@Override
			public void perceived(String agName, List<ContextEntry> entries, int currentSampleIdx) {
			}

			@Override
			public void perceived(Agent ag, Perception p, int currentSampleIdx) {
				// TODO Auto-generated method stub

			}

			@Override
			public void addedContext(Agent ag, Context context) {
			}

			@Override
			public void imputed(Agent ag, Context buildingContext, ContextEntry imputedEntry,
					Map<Agent, List<Context>> usedContext, double realValue) {
				// TODO Auto-generated method stub

			}
		};
	}

	/**
	 * Save to a CSV file the results of the contact sensor agent {@code ag} in
	 * input.
	 * 
	 * @param ag
	 * @param outEstimationFname
	 */
	static void processContactSensorAgentResult(Agent ag, String outEstimationFname) {
		return;
	}

	static int countCoherentAgents(final List<Pair<Agent, Agent>> agents) {

		int counter = 0;

		for (Pair<Agent, Agent> p : agents) {
			int a1 = p.getLeft().getAgentName().endsWith("_OUT") ? 1 : 0;
			int a2 = p.getRight().getAgentName().endsWith("_OUT") ? 1 : 0;
			counter += a1 ^ a2;
		}

		return counter;
	}

}
