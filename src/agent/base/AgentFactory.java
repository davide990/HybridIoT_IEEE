package agent.base;

import java.util.Collections;
import java.util.stream.Collectors;

import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;

import agent.AmbientContextAgent;
import agent.crossValidation.CrossValidationAgent;
import context.ContextInfo;
import sensors.DataSensor;
import sensors.FileDataSensor;
import sensors.VirtualDataSensor;

public class AgentFactory {

	public static AmbientContextAgent getRealAgent(String fname, String agentName, String columnSeparator,
			ContextInfo info) throws Exception {
		return getRealAgent(Vector2D.NaN, fname, agentName, columnSeparator, info);
	}

	public static AmbientContextAgent getRealAgent(Vector2D position, String fname, String agentName,
			String columnSeparator, ContextInfo info) throws Exception {

		// Create the sensor
		DataSensor dr = null;
		try {
			dr = FileDataSensor.getNew(info, fname, agentName, columnSeparator);
		} catch (Exception e) {
			System.err.println(
					"Unable to create real device for agent [" + agentName + "]. Creating virtual sensor. Error: ");
			e.printStackTrace();
			dr = new VirtualDataSensor(agentName, info);
		}

		// Create and return a new agent
		return AmbientContextAgent.getNew(agentName, dr, position, info);
	}

	/**
	 * Create a new agent for cross-validating a given dataset (as CSV file). By
	 * using this method, no confidence zone is associated to the agent.
	 * 
	 * @param fname           the dataset to be used
	 * @param agentName       the ID of the sensor to use (it corresponds to the row
	 *                        of the dataset to be read)
	 * @param columnSeparator the separator character used by the CSV file
	 * @param numFolds        the number of folds
	 * @param foldID          the ID of the fold to treat ([0..{@code numFolds}))
	 * @param info
	 * @return
	 * @throws Exception
	 */
	public static AmbientContextAgent getCrossValidationAgent(String fname, String agentName, String columnSeparator,
			int numFolds, int foldID, boolean trainOnly, ContextInfo info) throws Exception {

		// Create the sensor
		DataSensor dr = FileDataSensor.getNew(info, fname, agentName, columnSeparator);

		// Create the agent
		AmbientContextAgent ag = CrossValidationAgent.getNew(dr, agentName, numFolds, foldID, trainOnly, info);

		ag.addPerceptions(Collections.nCopies(dr.getSamplesCount() + 1, .0).stream().map(x -> new Perception(x, false))
				.collect(Collectors.toList()));

		// ag.addPerceptions(Collections.nCopies(dr.getSamplesCount() + 1, .0));

		// return the new agent
		return ag;
	}

	public static AmbientContextAgent getCrossValidationAgent(Vector2D position, String fname, String agentName,
			String columnSeparator, int numFolds, int foldID, boolean trainOnly, ContextInfo info) throws Exception {

		// Create the sensor
		DataSensor dr = FileDataSensor.getNew(info, fname, agentName, columnSeparator);

		// Create the agent
		AmbientContextAgent ag = CrossValidationAgent.getNew(position, dr, agentName, numFolds, foldID, trainOnly,
				info);

		ag.addPerceptions(Collections.nCopies(dr.getSamplesCount() + 1, .0).stream().map(x -> new Perception(x, false))
				.collect(Collectors.toList()));

		// ag.addPerceptions(Collections.nCopies(dr.getSamplesCount() + 1, .0));

		// return the new agent
		return ag;

	}

}
