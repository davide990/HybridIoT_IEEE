package agent.contactSensor.interfaces;

import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

import agent.AmbientContextAgent;
import agent.ContactSensorAgent;

public interface SensorPairsAction {
	void act(ContactSensorAgent self, double threshold, Map<Pair<AmbientContextAgent, AmbientContextAgent>, Double> correlations);

}
