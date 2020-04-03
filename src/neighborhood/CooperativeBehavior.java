package neighborhood;

import agent.base.Agent;
import context.Context;

public interface CooperativeBehavior<T> {

	T act(Agent source, Agent dest, int currentDataIdx, Context context, Object... args);

}
