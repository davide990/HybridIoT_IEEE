package agent.contactSensor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

import org.apache.commons.lang3.tuple.Pair;

import agent.base.Agent;
import agent.environment.Environment;

public class ContactSensorTestBench {

	// Indoor/outdoor paired
	private static List<Pair<String, String>> pairs = Arrays.asList(Pair.of("6.I1", "6.O1"), Pair.of("6.I2", "6.O2"),
			Pair.of("6.I3", "6.O3"), Pair.of("6.I4", "6.O4"), Pair.of("6.I5", "6.O5"), Pair.of("6.106", "6.105"),
			Pair.of("6.106", "6.107"), Pair.of("6.103", "6.111"), Pair.of("6.111", "6.115"));

	// Indoor with indoor
	// outdoor with outdoor
	private static List<Pair<String, String>> pairsCoherent = Arrays.asList(Pair.of("6.I1", "6.I5"),
			Pair.of("6.I3", "6.I4"), Pair.of("6.I4", "6.I2"));

	private static List<Pair<String, String>> pairs3Indoor = Arrays.asList(Pair.of("6.106", "6.105"),
			Pair.of("6.106", "6.107"), Pair.of("6.103", "6.111"), Pair.of("6.111", "6.115"));

	public static List<Pair<Agent, Agent>> getAgents() {
		List<Pair<Agent, Agent>> aa = new ArrayList<>();

		for (Pair<String, String> p : pairs3Indoor) {
			try {
				Agent A1 = Environment.get().getAgents().stream().filter(x -> x.getAgentName().equals(p.getLeft()))
						.findFirst().get();
				Agent B1 = Environment.get().getAgents().stream().filter(x -> x.getAgentName().equals(p.getRight()))
						.findFirst().get();
				aa.add(Pair.of(A1, B1));
			} catch (NoSuchElementException e) {
				System.err.println("Pair [" + p.getLeft() + ";" + p.getRight() + "] not found");
			}
		}

		return aa;

	}

}
