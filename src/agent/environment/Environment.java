package agent.environment;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import agent.base.Agent;
import agent.base.AgentType;

public class Environment {

	private static Optional<Environment> instance = Optional.empty();

	private Object agLock;
	private Set<Agent> agents;

	private Environment() {
		agLock = new Object();
		agents = Collections.synchronizedSet(new HashSet<Agent>());
	}

	/**
	 * Get the environment
	 * 
	 * @return
	 */
	public static Environment get() {
		if (!instance.isPresent()) {
			instance = Optional.of(new Environment());
		}
		return instance.get();
	}

	public Set<Agent> getAgents() {
		synchronized (agLock) {
			return Collections.unmodifiableSet(agents);
		}
	}

	/**
	 * Add an agent to this environment
	 * 
	 * @param a
	 */
	public void add(Agent a) {
		synchronized (agLock) {
			agents.add(a);
		}

	}

	public Set<Agent> getAmbientContextAgents() {
		synchronized (agLock) {
			return Collections.unmodifiableSet(
					agents.stream().filter(x -> x.getType() == AgentType.AMBIENT_CONTEXT_AGENT).collect(Collectors.toSet()));
		}
	}
	
	public Set<Agent> getContactSensorAgents() {
		synchronized (agLock) {
			return Collections.unmodifiableSet(
					agents.stream().filter(x -> x.getType() == AgentType.CONTACT).collect(Collectors.toSet()));
		}
	}

}
