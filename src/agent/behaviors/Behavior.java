package agent.behaviors;

import java.util.Optional;

import agent.base.Agent;

public abstract class Behavior {

	/**
	 * The current state of this behavior
	 */
	private BehaviorState state;

	/**
	 * An optional parent to which this behavior owns
	 */
	private Optional<Behavior> parent;

	private Optional<BehaviorListener> listener;

	/**
	 * The agent that owns this behavior
	 */
	private Agent agent;

	public Behavior(Agent owner) {
		parent = Optional.empty();
		listener = Optional.empty();
		state = BehaviorState.READY;
		agent = owner;
	}

	public void setParent(CompositeBehaviour cb) {
		parent = Optional.ofNullable(cb);
		if (parent.isPresent()) {
			agent = parent.get().agent;
			listener = Optional.of(agent);
		}
	}

	public void executeBehavior() {

		if (state == BehaviorState.RUNNING) {
			return;
		}

		if (listener.isPresent()) {
			listener.get().changedState(this, state, BehaviorState.RUNNING);
		}
		state = BehaviorState.RUNNING;

		onStart();
		action();
		onEnd();

		if (done() && listener.isPresent()) {
			listener.get().changedState(this, state, BehaviorState.READY);
		}

	}

	public BehaviorState getState() {
		return state;
	}
	
	
	protected Agent getAgent() {
		return agent;
	}

	/**
	 * This method is executed only once, before the execution of this behavior
	 */
	public void onStart() {
	}

	/**
	 * This method is executed only once, after the execution of this behavior
	 */
	public void onEnd() {
	}

	/**
	 * Runs this behavior
	 */
	public abstract void action();

	/**
	 * Check if this behaviour is done
	 * 
	 * @return
	 */
	public abstract boolean done();

}
