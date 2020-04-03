package agent.base;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import agent.behaviors.Behavior;
import agent.behaviors.BehaviorListener;
import agent.behaviors.BehaviorState;
import agent.environment.Environment;
import agent.listener.AgentListener;

public abstract class Agent extends Thread implements BehaviorListener {
	public static int MS_REASONING_CYCLE_INTERVAL = 1;
	public static int MS_BEFORE_PERCEIVE = 1;

	public static double CONFIDENCE_DELTA = .1;

	public static double MAX_CONFIDENCE = 1.;
	public static double MIN_CONFIDENCE = 0.;

	private final Optional<Logger> logger;

	/**
	 * An optional vector 2D that indicates the position of this agent in a
	 * Euclidean space
	 */
	private Optional<Vector2D> position;

	/**
	 * The (optional) height at which this agent is located. This has to be used in
	 * conjunction with {@code position}
	 */
	private Optional<Double> height;

	/**
	 * Last time instant in which this agent has made a reasoning cycle
	 */
	private Instant lastReasoningCycle;

	/**
	 * 
	 */
	private AgentReasoningProgress progress;

	/**
	 * The list of neighbors agents. To each agent is associated a double that
	 * represents the confidence that this agent has to the agent in this list.
	 */
	private Map<Agent, Double> neighbors;

	/**
	 * The name of this agent
	 */
	private String name;

	/**
	 * A boolean value that indicates whether this agent is paused
	 */
	private boolean paused;

	/**
	 * A boolean variable that indicates whether this agent is active or not.
	 */
	private boolean isActive;

	private boolean threadStarted;

	private Object confidenceValuesLock;
	private Map<Agent, Double> confidenceValues = new HashMap<>();

	/**
	 * An optional listener for this agent
	 */
	protected Optional<AgentListener> listener;

	/**
	 * The perception map of this agent
	 */
	// protected Map<ContextInfo, List<Double>> perceptionMap;
	private List<Perception> perceptions;
	private Object perceptionsLock = new Object();

	/**
	 * The type of this agent.
	 */
	protected AgentType type;

	private double criticality;

	/*
	 * protected Agent(String agName, Vector2D position) { this(agName);
	 * this.position = Optional.of(position); }
	 */

	protected Agent(String agName, Vector2D position, AgentType type) {
		this(agName, type);
		this.position = Optional.of(position);
		criticality = Double.NEGATIVE_INFINITY;
	}

	protected Agent(String agName, AgentType type) {
		neighbors = new HashMap<>();
		lastReasoningCycle = Instant.now();
		position = Optional.empty();
		progress = AgentReasoningProgress.IDLE;
		paused = false;
		isActive = false;
		name = agName;
		height = Optional.empty();
		perceptions = Collections.synchronizedList(new ArrayList<>());

		confidenceValuesLock = new Object();
		confidenceValues = Collections.synchronizedMap(new HashMap<>());

		logger = Optional.ofNullable(LoggerFactory.getLogger(Agent.class.getSimpleName()));

		threadStarted = false;

		// Set the neighbors to this agent
		Environment.get().getAgents().forEach(x -> addNeighbor(x));

		// Add this agent to the environment
		Environment.get().add(this);

		this.type = type;
	}

	/**
	 * Get the neighbors agents
	 * 
	 * @return
	 */
	public List<Agent> getNeighbors() {
		return Collections.unmodifiableList(neighbors.keySet().stream().collect(Collectors.toList()));
	}

	/**
	 * Add a neighbor and associate a confidence value of {@code 1}.
	 * 
	 * @param ag
	 * @return true if the agent is associated. False if the agent is already in the
	 *         neighbor list.
	 */
	public boolean addNeighbor(Agent ag) {
		if (ag.equals(this) || neighbors.containsKey(ag)) {
			return false;
		}

		neighbors.put(ag, 1.0);
		return true;
	}

	/**
	 * Dissociate from the specified agent.
	 * 
	 * @param ag
	 * @return the last confidence value between this agent the input agent.
	 *         Otherwise, return {@code NaN}.
	 */
	public double dissociateFrom(Agent ag) {
		if (!neighbors.containsKey(ag)) {
			return Double.NaN;
		}
		return neighbors.remove(ag);
	}

	public Map<Agent, Double> getConfidenceValues() {
		synchronized (confidenceValuesLock) {
			return neighbors;
		}
	}

	public double getConfidence(Agent a) {
		synchronized (confidenceValuesLock) {
			Optional<Double> dd = Optional.ofNullable(neighbors.get(a));
			
			
			
			return dd.orElse(0.0);
		}
	}
	/*
	 * public Map<Agent, Double> getConfidenceValuesForRead() { synchronized
	 * (confidenceValuesLock) { Map<Agent, Double> r = new
	 * HashMap<>(confidenceValues);
	 * 
	 * 
	 * return r.entrySet().stream().map(x -> new
	 * AbstractMap.SimpleEntry<>(x.getKey(), x.getValue().getValue()))
	 * .collect(Collectors.toMap(x -> x.getKey(), x -> x.getValue())); } }
	 */

	/**
	 * Modify the confidence value of this agent to the specified agent. The
	 * confidence is modified according to the delta value provided in input
	 * (positive or negative)
	 * 
	 * @param ag
	 * @param delta
	 * @return the previous confidence value associated with {@code ag}, or NaN if
	 *         this agent is not associated to {@code ag}.
	 */
	public double modifyConfidence(Agent ag, double delta) {
		if (!neighbors.containsKey(ag)) {
			
			neighbors.put(ag, 1.);
			return 1.;
			//return Double.NaN;
		}
		
		double oldConfidence = neighbors.get(ag);
		if(neighbors.get(ag) + delta > neighbors.get(ag)) {
			neighbors.put(ag, Math.min(neighbors.get(ag) + delta, 1.));
		}else {
			neighbors.put(ag, Math.max(neighbors.get(ag) - delta, 0.));
		}
		
		
		return oldConfidence;
	}

	@Override
	public final void run() {
		isActive = true;
		resumeAgent();
		reasoningCycleMain();
	}

	private final void reasoningCycleMain() {
		while (isActive) {
			try {
				sleep(MS_REASONING_CYCLE_INTERVAL);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			if (Duration.between(lastReasoningCycle, Instant.now()).toMillis() < MS_REASONING_CYCLE_INTERVAL) {
				continue;
			}

			if (progress == AgentReasoningProgress.IDLE) {
				lastReasoningCycle = Instant.now();
				getLogger().trace("Reasoning cycle. TS: " + lastReasoningCycle.toString());
				continue;
			}

			// If reasoning cycle was previously aborted, restore to READY state
			if (progress == AgentReasoningProgress.ABORT) {
				progress = AgentReasoningProgress.READY;
			}

			// if ready, perceive
			if (progress == AgentReasoningProgress.READY) {
				perceive();
				getLogger().trace("Perceive OK");
				// If not aborted, proceed
				if (progress == AgentReasoningProgress.ABORT) {
					lastReasoningCycle = Instant.now();
					continue;
				}
				progress = AgentReasoningProgress.PERCEIVE_OK;
			}

			// If perception OK
			if (progress == AgentReasoningProgress.PERCEIVE_OK) {

				// decide and act
				decideAndAct();

				getLogger().trace("Decide&Act OK");

				// check if aborted
				if (progress == AgentReasoningProgress.ABORT) {
					lastReasoningCycle = Instant.now();
					continue;
				}

				if (paused) {
					getLogger().trace("Paused");
					progress = AgentReasoningProgress.IDLE;
				} else {
					progress = AgentReasoningProgress.READY;
				}

			}
			lastReasoningCycle = Instant.now();
			// Let other agents go on
			Thread.yield();
		}
	}

	/**
	 * 
	 */
	protected abstract void perceive();

	/**
	 * 
	 */
	protected abstract void decideAndAct();

	protected final void abortReasoning() {
		progress = AgentReasoningProgress.ABORT;
		// TODO log abort
	}

	public void stopAgent() {
		isActive = false;

	}

	public void activate() {
		if (!threadStarted) {
			threadStarted = true;
			start();
			return;
		}

		if (isActive && !paused) {
			return;
		}

		isActive = true;
		paused = false;
		resumeAgent();
		reasoningCycleMain();
	}

	public boolean isActive() {
		return isActive;
	}

	public final synchronized void resumeAgent() {
		progress = AgentReasoningProgress.READY;
		paused = false;
	}

	public final synchronized boolean isPaused() {
		return paused;
	}

	public final synchronized void pauseAgent() {
		paused = true;
	}

	protected Logger getLogger() {
		return logger.orElseThrow(() -> new IllegalStateException("No logger found"));
	}

	@Override
	public String toString() {
		return "[" + getAgentName() + ")]";
	}

	public void setAgentName(String name) {
		this.name = name;
	}

	public String getAgentName() {
		return name;
	}

	public void setPosition(Vector2D position) {
		this.position = Optional.of(position);
	}

	/**
	 * Return a vector containing the position of this agent, otherwise (if no
	 * position has been set) return a vector (NaN,NaN)
	 * 
	 * @return
	 */
	public Vector2D getPosition() {
		return position.orElse(Vector2D.NaN);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Agent other = (Agent) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

	public List<Perception> getPerceptions() {
		synchronized (perceptionsLock) {
			return new ArrayList<>(perceptions);
		}
	}

	public void addPerceptions(List<Perception> ll) {
		synchronized (perceptionsLock) {
			perceptions.addAll(ll);
		}
	}

	public void addPerception(Perception l) {
		synchronized (perceptionsLock) {
			perceptions.add(l);
		}
	}

	public void setPerception(int idx, Perception l) {
		synchronized (perceptionsLock) {

			if (idx >= perceptions.size()) {
				addPerception(l);
				return;
			}

			perceptions.set(idx, l);
		}
	}

	/**
	 * This method must be overridden by the agents. This method shouldn't make any
	 * calls to internal representation an agent has on its environment because
	 * these information maybe outdated.
	 * 
	 * @return the criticality at a given moment
	 */
	protected abstract double computeCriticality();

	public double getCriticality() {
		return criticality;
	}

	public AgentListener getListener() {
		return listener.orElseThrow(() -> new IllegalAccessError("no listener provided"));
	}

	public void setListener(AgentListener listener) {
		this.listener = Optional.of(listener);
	}

	public AgentType getType() {
		return type;
	}

	public Optional<Double> getHeight() {
		return height;
	}

	public void setHeight(Optional<Double> height) {
		this.height = height;
	}
	
	@Override
	public void changedState(Behavior b, BehaviorState previousState, BehaviorState newState) {
		// TODO Auto-generated method stub
		
	}

}
