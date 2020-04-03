package context.dynamicContext;

import java.util.List;

import agent.AmbientContextAgent;
import agent.base.Perception;
import context.Context;
import sensors.DataSensor;

public interface OptimalContextFinder {
	/**
	 * Get the best context (in terms of width) that is able to estimate the last
	 * information perceived by the sensor given in parameter.
	 * 
	 * @param sensor the data sensor used by the agent {@code ag}
	 * @param ag     the agent
	 * 
	 * @return
	 */
	Context getContext(AmbientContextAgent ag, DataSensor sensor, List<Perception> agentPerceptions);
}
