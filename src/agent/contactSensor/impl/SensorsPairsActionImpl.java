package agent.contactSensor.impl;

import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

import agent.AmbientContextAgent;
import agent.ContactSensorAgent;
import agent.contactSensor.interfaces.SensorPairsAction;

public class SensorsPairsActionImpl implements SensorPairsAction {

	private final double CZ_UPDATE_RATE = 5;

	@Override
	public void act(ContactSensorAgent self, double threshold,
			Map<Pair<AmbientContextAgent, AmbientContextAgent>, Double> correlations) {

		// For each pair (Ci,Ck) of context
		// - get the correlation value r
		// - get the related agent Ai, Ak
		// - if over threshold
		// - - push away Ci and Ck from this agent
		// - else
		// - - approach Ci and Ck towards this agent

		for (Pair<AmbientContextAgent, AmbientContextAgent> pair : correlations.keySet()) {

			double correlation = correlations.get(pair);

			AmbientContextAgent a1 = pair.getLeft();
			AmbientContextAgent a2 = pair.getRight();

			if (correlation <= threshold) {
				a1.getConfidenceZone().update(self.getPosition(), CZ_UPDATE_RATE, true);
				a2.getConfidenceZone().update(self.getPosition(), CZ_UPDATE_RATE, true);
			} else {
				a1.getConfidenceZone().update(self.getPosition(), CZ_UPDATE_RATE, false);
				a2.getConfidenceZone().update(self.getPosition(), CZ_UPDATE_RATE, false);
			}

		}

	}
}