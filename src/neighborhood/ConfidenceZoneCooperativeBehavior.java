package neighborhood;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.math3.geometry.euclidean.twod.Line;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.util.Pair;

import agent.AmbientContextAgent;
import agent.base.Agent;
import agent.environment.Environment;
import confidenceZone.ConfidenceZone;
import context.Context;

public class ConfidenceZoneCooperativeBehavior<T extends Number> implements CooperativeBehavior<T> {

	/**
	 * The default spatial increment of the confidence zone
	 */
	private static final int CZ_UPDATE_DELTA = 220;

	private ConfidenceZone confidenceZone;
	private Agent self;

	public ConfidenceZoneCooperativeBehavior(Agent a) {
		confidenceZone = ((AmbientContextAgent) a).getConfidenceZone();
		self = a;
	}

	@Override
	public T act(Agent source, Agent dest, int currentDataIdx, Context context, Object... args) {

		List<Agent> ags = checkRealSensorsInConfidenceZone();

		List<Pair<Agent, Agent>> pairs = evaluateCollinearSensors(ags);

		if (pairs.size() < 3) {
			return (T) new Double(Double.NaN);
		}

		Map<Pair<Agent, Agent>, Double> df = evaluateDataField(pairs);

		Map<Pair<Agent, Agent>, Double> weights = evaluatePairsWeights(pairs);

		updateConfidenceZone(df, weights);

		double est = evaluateEstimation(df, weights);
		return (T) new Double(est);
	}

	private void updateConfidenceZone(Map<Pair<Agent, Agent>, Double> dataFieldsMap,
			Map<Pair<Agent, Agent>, Double> weights) {

		List<Pair<Agent, Agent>> outliers = getOutliersPairs(dataFieldsMap);
		List<Pair<Agent, Agent>> toInclude = dataFieldsMap.keySet().stream().filter(x -> !outliers.contains(x))
				.collect(Collectors.toList());

		for (Pair<Agent, Agent> ag : outliers) {
			confidenceZone.update(ag.getFirst().getPosition(), CZ_UPDATE_DELTA, false);
			confidenceZone.update(ag.getSecond().getPosition(), CZ_UPDATE_DELTA, false);
		}

		for (Pair<Agent, Agent> ag : toInclude) {
			confidenceZone.update(ag.getFirst().getPosition(), CZ_UPDATE_DELTA / 2, true);
			confidenceZone.update(ag.getSecond().getPosition(), CZ_UPDATE_DELTA / 2, true);
		}
	}

	private List<Pair<Agent, Agent>> getOutliersPairs(Map<Pair<Agent, Agent>, Double> dataFieldsMap) {

		double medianDataField = StatUtils.percentile(dataFieldsMap.values().stream().mapToDouble(d -> d).toArray(),
				50);

		// Ordino la mappa secondo la distanza dei valori dal mediano precedente
		Stream<Entry<Pair<Agent, Agent>, Double>> sortedDataFieldStream = dataFieldsMap.entrySet().stream()
				.sorted((e1, e2) -> Double.compare(Math.abs(medianDataField - e1.getValue()),
						Math.abs(medianDataField - e2.getValue())));

		List<Entry<Pair<Agent, Agent>, Double>> sortedDataField = sortedDataFieldStream.collect(Collectors.toList());

		// take the data value of the first pair
		double dataFieldRef = sortedDataField.get(0).getValue();
		// take the distance between the first pair and the third pair of RSAs
		double dist = Math.abs(dataFieldRef - sortedDataField.get(2).getValue());

		// AVT??
		// double factor = 2.5;
		double factor = .1;
		// double factor = .5; // --> WOLLONGONG

		List<Pair<Agent, Agent>> outliers = new ArrayList<>();

		for (Entry<Pair<Agent, Agent>, Double> e : sortedDataField) {
			if (e.getValue() <= dataFieldRef - factor * dist || e.getValue() >= dataFieldRef + factor * dist) {
				outliers.add(e.getKey());
			}
		}

		return outliers;
	}

	private Map<Pair<Agent, Agent>, Double> evaluatePairsWeights(List<Pair<Agent, Agent>> pairs) {
		Map<Pair<Agent, Agent>, Double> weights = new HashMap<>();
		for (Pair<Agent, Agent> pair : pairs) {
			Line line = new Line(pair.getFirst().getPosition(), pair.getSecond().getPosition(), 0);
			weights.put(pair, line.distance(self.getPosition()));
		}

		return weights;
	}

	private List<Agent> checkRealSensorsInConfidenceZone() {
		return Environment.get().getAmbientContextAgents().stream()
				.filter(x -> x.getPosition().distance(self.getPosition()) != 0
						&& confidenceZone.contains(x.getPosition()) && x.isActive() && !x.isPaused())
				.collect(Collectors.toList());
	}

	/**
	 * Evaluate a list of agent pairs that are aligned with respect to the VSA/Agent
	 * this class is related to
	 * 
	 * @param agents
	 * @return
	 */
	private List<Pair<Agent, Agent>> evaluateCollinearSensors(List<Agent> agents) {
		List<Pair<Agent, Agent>> pairs = new ArrayList<>();

		// Sort according to the distance from the sensor
		agents.sort((a1, a2) -> Double.compare(self.getPosition().distance(a1.getPosition()),
				self.getPosition().distance(a2.getPosition())));

		// Create a temp copy of the agent list
		List<Agent> temp = new ArrayList<>(agents);

		// Remove one by one agents in order to make up the pairs
		while (!temp.isEmpty()) {
			Agent from = temp.remove(0);

			if (!temp.isEmpty()) {
				Agent to = getMostAlignedAgent(from, temp);
				temp.remove(to);
				pairs.add(new Pair<>(from, to));
			}

		}
		return pairs;
	}

	private Agent getMostAlignedAgent(Agent from, List<Agent> agList) {
		Optional<Agent> a = agList.stream()
				.sorted((a1, a2) -> Double.compare(collinearity(from, a1), collinearity(from, a1))).findFirst();

		return a.get();
	}

	private double collinearity(Agent agA, Agent agB) {
		Line line = new Line(agA.getPosition(), agB.getPosition(), 0);
		return line.distance(self.getPosition());
	}

	private Map<Pair<Agent, Agent>, Double> evaluateDataField(List<Pair<Agent, Agent>> collinearPairs) {
		Map<Pair<Agent, Agent>, Double> dataFields = new HashMap<>();

		for (Pair<Agent, Agent> pair : collinearPairs) {
			AmbientContextAgent from = (AmbientContextAgent) pair.getFirst();
			AmbientContextAgent to = (AmbientContextAgent) pair.getSecond();

			double fromData = from.lastPerception();
			double toData = to.lastPerception();

			double d_a = self.getPosition().distance(from.getPosition());
			double d_c = from.getPosition().distance(to.getPosition());
			double angleA = calculateAngle(from.getPosition(), to.getPosition());
			double cosAngle = Math.cos(Math.toRadians(angleA));

			double vab = fromData + (toData - fromData) * ((d_a / d_c) * cosAngle);

			dataFields.put(pair, vab);
		}
		return dataFields;
	}

	private double evaluateEstimation(Map<Pair<Agent, Agent>, Double> dataFieldsMap,
			Map<Pair<Agent, Agent>, Double> weights) {

		if (dataFieldsMap.isEmpty()) {
			return Double.NaN;
		}

		// Sum of weights multiplied by the values of data fields
		final double numerator = dataFieldsMap.entrySet().stream()
				.mapToDouble(e -> e.getValue() * weights.get(e.getKey())).sum();

		// Sum of weights
		final double denominator = weights.values().stream().mapToDouble(d -> d).sum();

		// Estimation
		double est = numerator / denominator;

		return est;
	}

	private double calculateAngle(Vector2D from, Vector2D to) {
		javafx.geometry.Point2D p1 = new javafx.geometry.Point2D(from.getX(), from.getY());
		javafx.geometry.Point2D p2 = new javafx.geometry.Point2D(to.getX(), to.getY());
		javafx.geometry.Point2D p3 = new javafx.geometry.Point2D(self.getPosition().getX(), self.getPosition().getY());
		return p2.angle(p1, p3);
	}

}
