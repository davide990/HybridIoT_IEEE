package agent.contactSensor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.MutableTriple;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.rank.Median;

import agent.AmbientContextAgent;
import agent.base.Agent;
import agent.base.Perception;
import agent.environment.Environment;
import avt.AvtFactory;
import context.Context;
import context.ContextEntry;
import fr.irit.smac.util.avt.AVT;
import fr.irit.smac.util.avt.Feedback;

/**
 * TODO, actually, this is a kind of CrossValidation ContactSensorAgent
 * 
 * @author Davide Andrea Guastella (SMAC-IRIT)
 */
@Deprecated
public class OldContactSensorAgent{

	private final static boolean USE_BENCHMARK_PAIRS = true;

	@Deprecated
	private List<Pair<Double, Feedback>> history;

	public final static double PASSAGE_THRESHOLD = 0.5;
	public final static double PASSAGE_OPEN = 1;
	public final static double PASSAGE_CLOSED = 0;
	public final static double PASSAGE_UND = Double.NaN;

	private int sampleIndex;

	private static final PearsonsCorrelation PEARSON_CORRELATION = new PearsonsCorrelation();

	private List<ContactSensorEstimation> estimations;

	private int maxSamples = 0;

	private List<Double> correlationBuffer;
	private static final int CORRELATION_BUFFER_SIZE = 5;

	private AVT avt;

	private Map<Pair<Agent, Agent>, List<Double>> pairsHistory;

	/**
	 * Constructor for the {@code ContactSensorAgent} class
	 * 
	 * @param agName   the name of the agent
	 * @param position the XY position of the agent
	 */
	public OldContactSensorAgent(String agName, Vector2D position) {
		//super(agName, position, AgentType.CONTACT);
		estimations = new ArrayList<>();

		maxSamples = new Double(Environment.get().getAmbientContextAgents().stream().map(x -> (AmbientContextAgent) x)
				.mapToDouble(x -> x.getSensor().getSamplesCount()).max().getAsDouble()).intValue();

		sampleIndex = AmbientContextAgent.MIN_NUMBER_SAMPLE_OBSERVED + 1;

		avt = AvtFactory.getNewForContactSensor();

		history = new ArrayList<>();
		pairsHistory = new HashMap<>();
		correlationBuffer = new ArrayList<>();
	}

	double lastValue = 0;
	double currentValue = 0;
	double smoothFactor = 0.002;

	List<Double> theAvtBuffer = new ArrayList<>();

	
	protected void perceive() {
		if (sampleIndex >= maxSamples) {
			//getListener().terminated(this);
			return;
		}

		// Get the list of learner agents only
		List<Agent> learnerAgents = Environment.get().getAmbientContextAgents().stream().map(x -> x)
				.collect(Collectors.toList());

		List<Pair<Agent, Agent>> collinearSensorPairs = new ArrayList<>();
		if (!USE_BENCHMARK_PAIRS) {
			collinearSensorPairs.addAll(getAgentPairsByDistance(learnerAgents));
		} else {
			collinearSensorPairs.addAll(ContactSensorTestBench.getAgents());
		}

		// the double value is the r index (Pearson correlation index) between the last
		// contexts of the corresponding pair
		List<MutableTriple<Agent, Agent, Double>> responses = evaluateCorr(collinearSensorPairs);

		if (responses.isEmpty()) {
			++sampleIndex;
			return;
		}

		responses.sort((e1, e2) -> Double.compare(e1.getRight(), e2.getRight()));

		for (int i = 0; i < responses.size(); i++) {
			responses.get(i).setRight(responses.get(i).getRight() / (i + 1));
		}

		double xHat = new Median().evaluate(responses.stream().mapToDouble(x -> x.getRight()).toArray());

		double MAD = new Median()
				.evaluate(responses.stream().mapToDouble(x -> Math.abs(x.getRight() - xHat)).toArray());

		double thMinus = xHat - 1.5 * MAD;
		double thPlus = xHat + 1.5 * MAD;

		// double th1 = responses.get(0).getRight();
		// double th2 = responses.get(3).getRight();

		double avg = responses.stream().map(x -> x.getRight()).filter(x -> thMinus < x && x < thPlus)
				.mapToDouble(x -> x).average().orElse(0.5);

		// double avg = responses.stream().map(x->x.getRight()).filter(x-> x >= th1 && x
		// <= th2).mapToDouble(x->x).average().getAsDouble();

		if (Math.abs(avg) > Math.abs(lastValue)) {
			avt.adjustValue(Feedback.GREATER);
		} else if (Math.abs(avg) == Math.abs(lastValue)) {
			avt.adjustValue(Feedback.GOOD);
		} else {
			avt.adjustValue(Feedback.LOWER);
		}
		lastValue = avg;
		// provare a togliere gli outlier con MAD

		// double avg = responses.stream().mapToDouble(x ->
		// x.getRight()).average().getAsDouble();
		/*
		 * List<Pair<Feedback, Double>> fd = responses.stream() .map(pp ->
		 * evaluatePassageStatus(pp.left, pp.middle, pp.right)).map(x -> x)
		 * .collect(Collectors.toList());
		 * 
		 * // fino a ora ho pesato i valori di correlazione in modo da dare piu
		 * importanza // alle coppie che sono piu correlate e meno importanza alle altre
		 * coppie
		 * 
		 * double greater = 0; double less = 0;
		 * 
		 * for (Feedback f : fd.stream().map(x ->
		 * x.getLeft()).collect(Collectors.toList())) { if (f == Feedback.GREATER) {
		 * greater++; }
		 * 
		 * if (f == Feedback.LOWER) { less++; } }
		 * 
		 * if (greater == less) { // indeciso avt.adjustValue(Feedback.GOOD);
		 * history.add(Pair.of(Double.NaN, Feedback.GOOD)); } else if (greater > less) {
		 * // molto correlate, tende a 0, PASSAGGIO APERTO
		 * avt.adjustValue(Feedback.GREATER); history.add(Pair.of(avt.getValue(),
		 * Feedback.GREATER)); } else { // non correlate, PASSAGGIO CHIUSO
		 * avt.adjustValue(Feedback.LOWER); history.add(Pair.of(avt.getValue(),
		 * Feedback.LOWER)); }
		 */

		theAvtBuffer.add(avt.getValue());

		while (theAvtBuffer.size() > 10) {
			theAvtBuffer.remove(0);
		}

		double vv;

		if (theAvtBuffer.size() < 4) {
			vv = avg;
		} else {

			vv = PEARSON_CORRELATION.correlation(
					theAvtBuffer.subList(0, theAvtBuffer.size() - 2).stream().mapToDouble(x -> x).toArray(),
					theAvtBuffer.subList(1, theAvtBuffer.size() - 1).stream().mapToDouble(x -> x).toArray());

			// vv = theAvtBuffer.stream().mapToDouble(x -> x).average().getAsDouble();

		}

		ContactSensorEstimation es = new ContactSensorEstimation();
		es.setSampleIndex(sampleIndex);
		es.setResponse(vv);
		// es.setResponse(avt.getValue());
		// es.setResponse(med);

		estimations.add(es);

		Perception p = new Perception(currentValue, true);

		// Notice the listener and increase the index of the current sample for the next
		// lifecycle
		/*if (listener.isPresent()) {
			listener.get().perceived(this, p, sampleIndex++);
		}*/
	}

	// stream().skip(Math.max(0, all.size() - n))
	List<Double> buffer = new ArrayList<Double>();
	int bufferSize = 5;

	/**
	 * 
	 * @param a
	 * @param b
	 * @param r Pearson correlation index
	 * @return
	 */
	private Pair<Feedback, Double> evaluatePassageStatus(Agent a, Agent b, double r) {
		Pair<Agent, Agent> p = Pair.of(a, b);
		if (!pairsHistory.containsKey(p)) {
			List<Double> ll = new ArrayList<>();
			ll.add(r);
			pairsHistory.put(Pair.of(a, b), ll);
		}

		double lastMeanCorrelation = new Mean().evaluate(pairsHistory.get(p).stream().mapToDouble(x -> x).toArray());

		pairsHistory.get(p).add(r);

		while (pairsHistory.get(p).size() >= CORRELATION_BUFFER_SIZE) {
			pairsHistory.get(p).remove(0);
		}

		double currentMeanCorrelation = new Mean().evaluate(pairsHistory.get(p).stream().mapToDouble(x -> x).toArray());

		if (Math.abs(currentMeanCorrelation) < Math.abs(lastMeanCorrelation)) {
			// meno correlate, ci si avvicina verso le zero, quindi il passaggio risulta
			// chiuso
			return Pair.of(Feedback.LOWER, currentMeanCorrelation);
		}
		if (Math.abs(currentMeanCorrelation) == Math.abs(lastMeanCorrelation)) {
			return Pair.of(Feedback.GOOD, currentMeanCorrelation);
		} else {
			return Pair.of(Feedback.GREATER, currentMeanCorrelation);
		}
	}

	
	protected void decideAndAct() {

	}

	/**
	 * 
	 * @param a
	 * @param b
	 * @param ac
	 * @param bc
	 * @return
	 */
	private Pair<Context, Context> equalizeContexts(AmbientContextAgent a, AmbientContextAgent b, Context ac, Context bc) {
		// Make a copy of the contexts
		Context acl = null, bcl = null;
		try {
			acl = (Context) ac.clone();
			bcl = (Context) bc.clone();
		} catch (CloneNotSupportedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		int na = ac.size();
		int nb = bc.size();

		if (nb < na) {

			int ii = sampleIndex - nb;

			while (bcl.size() < acl.size()) {
				// Optional<Context> prev = b.getContextAt(ii--);
				// if(prev.isPresent()) {
				List<ContextEntry> l = new ArrayList<>();

				// l.add(prev.get().getLastSample());
				l.add(new ContextEntry(bcl.getSupportedInfo(), Instant.now(), b.getPerceptions().get(ii--)));

				bcl.forceAddFirst(l);
				// }
			}

		}

		if (na < nb) {
			int ii = sampleIndex - na;

			while (acl.size() < bcl.size()) {
				// Optional<Context> prev = a.getContextAt(ii--);
				// if(prev.isPresent()) {
				List<ContextEntry> l = new ArrayList<>();
				// l.add(prev.get().getLastSample());

				// b.getPerceptions().get(ii).

				l.add(new ContextEntry(acl.getSupportedInfo(), Instant.now(), a.getPerceptions().get(ii--)));

				acl.forceAddFirst(l);
				// }
			}

		}

		if (acl.size() == bcl.size() && acl.size() < 3) {
			int idx = sampleIndex - acl.size();

			List<ContextEntry> l = new ArrayList<>();
			l.add(new ContextEntry(acl.getSupportedInfo(), Instant.now(), a.getPerceptions().get(idx)));
			acl.forceAddFirst(l);

			l.clear();
			l.add(new ContextEntry(bcl.getSupportedInfo(), Instant.now(), b.getPerceptions().get(idx)));
			bcl.forceAddFirst(l);

		}

		return Pair.of(acl, bcl);
	}

	private List<MutableTriple<Agent, Agent, Double>> evaluateCorr(List<Pair<Agent, Agent>> sensorPairs) {

		List<MutableTriple<Agent, Agent, Double>> scores = new ArrayList<>();

		// for each sensors pair
		for (Pair<Agent, Agent> pair : sensorPairs) {
			// take their last contexts
			AmbientContextAgent a0 = (AmbientContextAgent) pair.getLeft();
			AmbientContextAgent a1 = (AmbientContextAgent) pair.getRight();

			// get the context at the current index/time instant
			Optional<Context> ac = a0.getContextAt(sampleIndex);
			Optional<Context> bc = a1.getContextAt(sampleIndex);

			// continue only if both agent have a context at the current index
			if (ac.isPresent() && bc.isPresent()) {
				Pair<Context, Context> eqContexts = equalizeContexts(a0, a1, ac.get(), bc.get());

				// Evaluate the trend contexts, that is, the first derivative of the contexts
				Context trendC0 = eqContexts.getLeft();// .getTrendContext();
				Context trendC1 = eqContexts.getRight();// .getTrendContext();

				// Evaluate the pearson correlation index between the trend contexts
				// double r = PEARSON_CORRELATION.correlation(trendC0.getSituationAsDouble(),
				// trendC1.getSituationAsDouble());

				// double r = new
				// SpearmansCorrelation().correlation(trendC0.getSituationAsDouble(),
				// trendC1.getSituationAsDouble());

				double r = ac.get().compare(bc.get());

				double mm = r;// / similarity;

				if (Double.isNaN(mm)) {
					mm = 0;
					System.err.println();
				}

				// System.err.println("r: " + Double.toString(r) + ";\t sim: " +
				// Double.toString(similarity) + ";\t mm: " + Double.toString(mm));

				// Add the score to the triple
				scores.add(MutableTriple.of(a0, a1, mm));
			}
		}

		return scores;
	}

	private List<Pair<Agent, Agent>> getAgentPairsByDistance(List<Agent> allAgents) {
		List<Pair<Agent, Agent>> pairs = new ArrayList<>();

		Set<Double> heihtsOpt = allAgents.stream().map(x -> x.getHeight().get()).filter(x -> x != Double.NaN)
				.collect(Collectors.toSet());

		// Loop through the available heights/level
		for (Double height : heihtsOpt) {
			/*
			List<Agent> floorAgents = allAgents.stream()
					.filter(x -> Double.compare(x.getHeight(), height) == 0 && x.getPosition() != Vector2D.NaN)
					.sorted((a1, a2) -> Double.compare(this.getPosition().distance(a1.getPosition()),
							this.getPosition().distance(a2.getPosition())))
					.collect(Collectors.toList());
*/
			// Create a temp copy of the agent list
			List<Agent> temp;// = new ArrayList<>(floorAgents);
/*
			while (!temp.isEmpty()) {
				LearnerAgent from = (LearnerAgent) temp.remove(0);

				if (!temp.isEmpty()) {
					LearnerAgent to = (LearnerAgent) temp.remove(0);
					pairs.add(Pair.of(from, to));
				}
			}*/
		}

		return pairs;
	}

	public List<ContactSensorEstimation> getEstimations() {
		return estimations;
	}

	public int getSampleIndex() {
		return sampleIndex;
	}

}
