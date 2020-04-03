package agent.contactSensor.interfaces;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

import agent.AmbientContextAgent;
import agent.ContactSensorAgent;

public interface CouplingStrategy {
	public final static int CORRELATION_WINDOW_SIZE = 64;

	Map<Pair<AmbientContextAgent, AmbientContextAgent>, Double> act(ContactSensorAgent self, List<AmbientContextAgent> neighbors, int idx);

	Map<Pair<AmbientContextAgent, AmbientContextAgent>, Double> act(ContactSensorAgent self, List<AmbientContextAgent> neighbors,
			int pairsCount, int idx);

}
