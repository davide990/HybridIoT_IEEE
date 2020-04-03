package agent.listener;

import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

import agent.AmbientContextAgent;
import agent.ContactSensorAgent;

public interface ContactSensorAgentListener {
	void contactSensorUpdate(ContactSensorAgent contactSensorAgent, double threshold,
			Map<Pair<AmbientContextAgent, AmbientContextAgent>, Double> correlations);
	
	void perceived(ContactSensorAgent contactSensorAgent, int i);
}