package context.dynamicContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import agent.AmbientContextAgent;
import agent.base.Perception;
import context.Context;
import context.ContextEntry;
import context.ContextInfo;
import sensors.DataSensor;

public class FixedWidthContextFinder implements OptimalContextFinder {
	public static int CONTEXT_SIZE = 10;

	@Override
	public Context getContext(AmbientContextAgent ag, DataSensor sensor, List<Perception> agentPerceptions) {

		int currentIdx = sensor.getCurrentSampleIdx();
		ContextInfo infoType = ag.getSupportedInfo();
		// Push back
		// note: at least two information are necessary to impute an information
		Context testContext = new Context(ag, infoType);

		for (int i = 0; testContext.size() < CONTEXT_SIZE && currentIdx - i > 0; i++) {

			// k-i
			ContextEntry entry = new ContextEntry(infoType, Instant.now(), sensor.getCurrentData(i));

			// Push back
			List<ContextEntry> entries = new ArrayList<>();
			entries.add(entry);
			testContext.forceAddFirst(entries);
		}

		return testContext;
	}

}
