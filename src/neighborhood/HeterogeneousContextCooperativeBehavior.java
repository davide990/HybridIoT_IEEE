package neighborhood;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import agent.AmbientContextAgent;
import agent.base.Agent;
import context.Context;

public class HeterogeneousContextCooperativeBehavior<T extends List<Pair<Integer, Double>>>
		implements CooperativeBehavior<T> {

	/**
	 * In questo caso il contesto in parametro è quello di un agente che non sa
	 * stimare una informazione (sourceAgent). L'agente che riceve questo contesto
	 * prende una finestra di medesima lunghezza a partire dall'indice in parametro
	 * (all'indietro)
	 */
	@Override
	// public Object[] act(Agent source, Agent dest, int currentDataIdx, Context
	// sourceContext, Object... args) {
	public T act(Agent source, Agent dest, int currentDataIdx, Context sourceContext, Object... args) {

		AmbientContextAgent destAg = (AmbientContextAgent) dest;

		if (args.length == 0) {
			throw new IllegalArgumentException("no indexes provided.");
		}

		if (!(args[0] instanceof List<?>)) {
			throw new IllegalArgumentException("indexes must be provided as List<Integer>.");
		}

		@SuppressWarnings("unchecked")
		List<Integer> idxWhereToFind = (List<Integer>) args[0];

		return (T) destAg.getSimilarityScores(sourceContext, idxWhereToFind);
	}

}
