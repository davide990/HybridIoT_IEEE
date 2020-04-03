package agent.behaviors;

public interface BehaviorListener {

	void changedState(Behavior b, BehaviorState previousState, BehaviorState newState);
}
