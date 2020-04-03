package context.estimation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import agent.base.Agent;
import context.Context;
import context.ContextInfo;

public interface ContextEstimationStrategy {
	String getName();

	/**
	 * Impute an information to {@code lastObservedContext} using the agents
	 * contexts {@code agentContext}.
	 * 
	 * @param self
	 *            the agent that required the estimation
	 * @param agentContext
	 *            a map where a key is an agent and its value is the list of
	 *            contexts observed by the agent
	 * @param lastObservedContext
	 *            the last context observed by the agent that has to impute a
	 *            missing information
	 * 
	 * @param missingInfo
	 *            the type of information to be imputed
	 * 
	 * @return
	 */
	Double impute(Agent self, Map<Agent, List<Context>> agentContext, Context lastObservedContext,
			ContextInfo missingInfo, Optional<List<Pair<Integer,Double>>> weights);
}
