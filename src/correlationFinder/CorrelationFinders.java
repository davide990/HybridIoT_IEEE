package correlationFinder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.commons.math3.stat.descriptive.rank.Median;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;

import agent.ContactSensorAgent;
import agent.base.Agent;
import agent.base.AgentFactory;
import agent.contactSensor.impl.CouplingStrategyImpl;
import agent.contactSensor.impl.PearsonCorrelationStrategy;
import agent.contactSensor.impl.SensorsPairsActionImpl;
import agent.contactSensor.impl.ThresholdEvaluationImpl;
import configuration.MASConfig;
import context.ContextInfo;
import correlationFinder.data.CsvSensor;
import fr.irit.smac.util.avt.AVT;
import sensors.FileDataSensor;
import smile.wavelet.DaubechiesWavelet;
import smile.wavelet.WaveletShrinkage;

public class CorrelationFinders {

	/*
	 * private static String[] SENSORS = { "6.110", "6.110", "6.110", "6.110",
	 * "6.199A", "6.199A", "6.199A", "6.199A", "6.110C", "6.110C", "6.110C",
	 * "6.110C", "6.199B", "6.103", "6.103", "6.103", "6.199B", "6.199B", "6.103",
	 * "6.199B", "6.107", "6.105", "6.105", "6.109", "6.108", "6.105", "6.109",
	 * "6.108", "6.105", "6.108", "6.107", "6.107", "6.109", "6.107", "6.109",
	 * "6.199C", "6.199C", "6.108", "6.199C", "6.199C", "6.111", "6.110B", "6.110B",
	 * "6.110B", "6.103B", "6.110B", "6.111", "6.103B", "6.103B", "6.103B", "6.111",
	 * "6.111", "6.199D", "6.199D", "6.104", "6.199D", "6.104", "6.104", "6.104",
	 * "6.199D", "6.102", "6.102", "6.102", "6.102", "6.106", "6.106", "6.106",
	 * "6.106", "6.112", "6.112", "6.112", "6.115", "6.115", "6.115", "6.115",
	 * "6.112", "6.103A", "6.103A", "6.103A", "6.103A", "6.O3", "6.I3", "6.O2",
	 * "6.I2", "6.O4", "6.I4" };
	 */

	private static String[] SENSORS = { "6.102", "6.103B", "6.104", "6.105", "6.106", "6.107", "6.109", "6.111",
			"6.114", "6.115", "6.199C", "6.199D", "S1", "S2", "S3", "S4", "S5", "S7", "S8", "S9" };

	private static final ContextInfo[] INFOS = { ContextInfo.TEMP };

	private static final int CONTEXT_SIZE = 50;
	private static final int THRESHOLD_WINDOW_SIZE = 150;

	private static final String fname = "C:\\Users\\dguastel\\ownCloud\\travail-sync\\UOW\\passageTest\\realData\\testCorrelation\\datasetWithIndoorOutdoor.csv";
	private static final String configFile = "C:\\Users\\dguastel\\ownCloud\\travail-sync\\UOW\\passageTest\\realData\\testCorrelation\\level1_sensors.xml";

	private static final String colSep = ";";

	private static Map<Pair<SensorInfo, SensorInfo>, AVT> confidences = new HashMap<>();

	private static Map<String, Vector2D> agentPositions = new HashMap<>();

	private static Map<SensorInfo, List<Number>> values = new HashMap<>();
	private static Set<Pair<SensorInfo, SensorInfo>> correlatedSensors = new HashSet<>();

	private static int maxSamples = Integer.MAX_VALUE;

	/**
	 * Read the data from file and add it to the {@code values} data structure.
	 * 
	 * @throws Exception
	 */
	public static void getDataFromFile(String[] sensorsName) throws Exception {

		for (int i = 0; i < sensorsName.length; i++) {
			for (int j = 0; j < INFOS.length; j++) {
				SensorInfo agName = new SensorInfo(sensorsName[i], INFOS[j]);

				// skip this agent if the specified XML configuration file doesn't contain a
				// position
/*
				if (!agentPositions.containsKey(sensorsName[i])) {
					continue;
				}*/

				//agName.setPosition(agentPositions.get(sensorsName[i]));

				FileDataSensor fd = FileDataSensor.getNew(INFOS[j], fname, sensorsName[i], colSep);

				if (fd.getSamplesCount() <= 0) {
					continue;
				}

				if (!values.containsKey(agName)) {
					values.put(agName, new ArrayList<>());
				}

				for (int k = 0; k < fd.getSamplesCount(); k++) {
					values.get(agName).add(fd.getCurrentData());
					fd.nextSample();
				}

				if (fd.getSamplesCount() < maxSamples) {
					maxSamples = fd.getSamplesCount();
				}

			}

		}

	}

	private static void readAgentPositionFromXMLFile(String configFile) {

		MASConfig config = new MASConfig(configFile, new String[] {}) {

			@Override
			public Optional<Agent> createContactSensorAgent(Vector2D vector2d, String agName) {
				Agent cs = new ContactSensorAgent(agName, new SensorsPairsActionImpl(),
						new CouplingStrategyImpl(new PearsonCorrelationStrategy()), new ThresholdEvaluationImpl());

				System.err.println("ok " + cs.getAgentName());
				return Optional.of(cs);
			}

			@Override
			public Optional<Agent> createAgent(Vector2D position, String dataFile, String agName, String colSep,
					ContextInfo info, Optional<Integer> foldID, boolean onlyTraining) {

				Optional<Agent> ag = Optional.empty();
				try {
					ag = Optional.ofNullable(AgentFactory.getCrossValidationAgent(position, dataFile, agName, colSep,
							MASConfig.NUM_FOLDS, foldID.orElse(-1), true, info));
				} catch (Exception e) {
					e.printStackTrace();
				}

				ag.ifPresent(a -> {
					a.setAgentName(agName);
					//agentPositions.put(agName, position);
				});

				return ag;

			}
		};

		config.parse();
	}

	private static PearsonsCorrelation PEARSON_CORR = new PearsonsCorrelation();

	private static double getCorrelationsByEvolution(Pair<SensorInfo, SensorInfo> pair, int i) {

		SensorInfo sa = pair.getLeft();
		SensorInfo sb = pair.getRight();
		double[] contextA = values.get(sa).subList(Math.max(0, i - CONTEXT_SIZE), i + 1).stream()
				.mapToDouble(x -> (double) x).toArray();
		double[] cA_a = ArrayUtils.subarray(contextA, 0, contextA.length - 1);
		double[] cA_b = ArrayUtils.subarray(contextA, 1, contextA.length);
		double[] trendA = IntStream.range(0, cA_a.length - 1).mapToDouble(x -> cA_b[x] - cA_a[x]).toArray();

		double[] contextB = values.get(sb).subList(Math.max(0, i - CONTEXT_SIZE), i + 1).stream()
				.mapToDouble(x -> (double) x).toArray();

		double[] cB_a = ArrayUtils.subarray(contextB, 0, contextB.length - 1);
		double[] cB_b = ArrayUtils.subarray(contextB, 1, contextB.length);
		double[] trendB = IntStream.range(0, cA_a.length - 1).mapToDouble(x -> cB_b[x] - cB_a[x]).toArray();

		// double r = PEARSON_CORR.correlation(trendA, trendB);
		// double r = new KendallsCorrelation().correlation(contextA, contextB);
		double r = PEARSON_CORR.correlation(trendA, trendB);

		// double r = PEARSON_CORR.correlation(contextA, contextB);

		return r;
	}

	/**
	 * NUOVA VERSIONE NUOVA VERSIONE NUOVA VERSIONE NUOVA VERSIONE NUOVA VERSIONE
	 */
	private static Map<Pair<SensorInfo, SensorInfo>, List<DeviceStatus>> doContactSensorTest_2(Vector2D csPosition,
			Optional<List<Pair<SensorInfo, SensorInfo>>> sensorsPairs) {

		final int startIdx = 3;

		List<Double> responsesBuffer = new ArrayList<>();
		Map<Pair<SensorInfo, SensorInfo>, List<DeviceStatus>> couplesStatus = new HashMap<>();

		DeviceStatus first = new DeviceStatus(0, 0);
		responsesBuffer.add(.0);
		first.setBoundary(0);

		// Loop each pair of sensors
		// for (Pair<SensorInfo, SensorInfo> pair : sensorsPairs) {
		int observedSamples = 0, observedSample2 = 0;
		double boundary = 0;

		int i = startIdx;

		// iterate each sample for the current pair of sensors
		for (; i < maxSamples - CONTEXT_SIZE; i++, observedSample2++, observedSamples++) {
			if (!sensorsPairs.isPresent()) {
				sensorsPairs = Optional.of(getSensorsPairsByContextSimilarity(i));
			}

			for (Pair<SensorInfo, SensorInfo> pair : sensorsPairs.get()) {
				if (!couplesStatus.containsKey(pair)) {
					couplesStatus.put(pair, new ArrayList<>());
				}
				// Evaluate the correlation of the current sensors pair
				double correlation = getCorrelationsByEvolution(pair, i);

				// Calculate the response of the pair at i as the average of the previous
				// responses at [i-CONTEXT_SIZE,i]
				DeviceStatus ds = new DeviceStatus(i, correlation);
				ds.setBoundary(boundary);
				ds.setMaxBoundary(0);
				couplesStatus.get(pair).add(ds);
			}

			// CORRELATION EVALUATION
			if (observedSample2 >= CONTEXT_SIZE) {
				setCorrelation(couplesStatus);
				observedSample2 = 0;
			}

			// THRESHOLD EVALUATION
			if (observedSamples >= THRESHOLD_WINDOW_SIZE) {
				boundary = setThreshold(couplesStatus);
				observedSamples = 0;
			}
		}

		// evaluate correlation and threshold for the final sample
		setCorrelation(couplesStatus);
		// Set the boundary for the remaining samples
		boundary = setThreshold(couplesStatus);

		return couplesStatus;
	}

	private static void setCorrelation(Map<Pair<SensorInfo, SensorInfo>, List<DeviceStatus>> couplesStatus// ,
	/* Pair<SensorInfo, SensorInfo> pair */) {

		for (Pair<SensorInfo, SensorInfo> pair : couplesStatus.keySet()) {
			int startIdx = Math.max(0, couplesStatus.get(pair).size() - CONTEXT_SIZE);

			double[] correlationValues = couplesStatus.get(pair).subList(startIdx, couplesStatus.get(pair).size())
					.stream().mapToDouble(x -> x.getResponse()).toArray();

			// TEST - per avere un dominio [0,1] cambio i numeri negativi in 1-|q|, dove q è
			// una correlazione negativa. IN questo modo i valori vicini a 1 saranno i
			// correlati, mentre a 0 non correlati

			for (int i = 0; i < correlationValues.length; i++) {
				if (correlationValues[i] < 0) {
					correlationValues[i] = 1 - Math.abs(correlationValues[i]);
				}
			}

			double avg = new Median().evaluate(correlationValues);

			for (int j = startIdx; j < couplesStatus.get(pair).size(); j++) {
				couplesStatus.get(pair).get(j).setResponse(avg);
			}
		}
	}

	/**
	 * Calculate the threshold according to the last correlation values related to
	 * the given pair. Than set this value to the related pairs of sensors
	 * 
	 * @param couplesStatus
	 * @param pair
	 * @return
	 */
	private static double setThreshold(
			Map<Pair<SensorInfo, SensorInfo>, List<DeviceStatus>> couplesStatus /* ,Pair<SensorInfo, SensorInfo> pair */) {

		double boundary = 0;
		for (Pair<SensorInfo, SensorInfo> pair : couplesStatus.keySet()) {
			int startIdx = Math.max(0, couplesStatus.get(pair).size() - THRESHOLD_WINDOW_SIZE);
			double[] correlationsDouble = couplesStatus.get(pair).subList(startIdx, couplesStatus.get(pair).size())
					.stream().mapToDouble(x -> x.getResponse()).toArray();

			// double boundary = evaluateThresholdByClusters(correlationsDouble);
			boundary = evaluateThresholdByThreeClusters(correlationsDouble);
			// double boundary = evaluateThresholdByPercentile(correlationsDouble);

			// Set the threshold for the observed data
			for (int j = startIdx; j < couplesStatus.get(pair).size(); j++) {
				couplesStatus.get(pair).get(j).setBoundary(boundary);
			}
		}

		return boundary;
	}

	/**
	 * Method for evaluating the threshold by separating classes of correlated and
	 * uncorrelated responses
	 * 
	 * @param values
	 * @return
	 */
	private static double evaluateThresholdByThreeClusters(double[] values) {

		List<Double> low = new ArrayList<Double>();
		// List<Double> zero = new ArrayList<Double>();
		List<Double> high = new ArrayList<Double>();

		// I take the max and min values as the pivots for the two sets
		////// double maxValue = Arrays.stream(values).max().getAsDouble();
		// double minValue = Arrays.stream(values).min().getAsDouble();
		////// double minValue = Arrays.stream(values).map(x ->
		// Math.abs(x)).min().getAsDouble();

		double maxValue = new Percentile().evaluate(values, 75);
		double minValue = new Percentile().evaluate(values, 25);

		low.add(minValue);
		high.add(maxValue);

		// for each correlation values
		for (Double d : values) {
			// if already considered, skip this value
			if (low.contains(d) || high.contains(d)) {
				continue;
			}

			// creates two test lists
			List<Double> lowTest = new ArrayList<Double>(low);
			List<Double> highTest = new ArrayList<Double>(high);

			lowTest.add(d);
			highTest.add(d);

			// evaluate the variance in the two groups where the sample is added
			double newLowVariance = new StandardDeviation().evaluate(lowTest.stream().mapToDouble(x -> x).toArray());
			double newHighVariance = new StandardDeviation().evaluate(highTest.stream().mapToDouble(x -> x).toArray());

			double previousLowVariance = new StandardDeviation().evaluate(low.stream().mapToDouble(x -> x).toArray());
			double previousHighVariance = new StandardDeviation().evaluate(high.stream().mapToDouble(x -> x).toArray());

			double lowDiff = Math.abs(newLowVariance - previousLowVariance);
			double highDiff = Math.abs(newHighVariance - previousHighVariance);

			if (lowDiff <= highDiff) {
				low.add(d);
			} else {
				high.add(d);

			}

		}

		double thLow = low.stream().mapToDouble(x -> x).sorted().max().getAsDouble();
		double thHigh = high.stream().mapToDouble(x -> x).sorted().min().getAsDouble();

		return (thLow + thHigh) * .5;
		// System.err.println();
	}

	private static void writeCorrelatedPairsToCSV(String fname) {
		File outFile = new File(fname);
		boolean isFileUnlocked = false;
		try {
			FileUtils.touch(outFile);
			isFileUnlocked = true;
		} catch (IOException e) {
			isFileUnlocked = false;
		}

		if (!isFileUnlocked) {
			System.err.println("[ERROR] file \"" + fname + ".csv\" is already opened. Cannot write results.");
			return;
		}

		DecimalFormat df = new DecimalFormat("#####0.00");
		DecimalFormatSymbols dfs = df.getDecimalFormatSymbols();
		dfs.setDecimalSeparator(',');
		df.setDecimalFormatSymbols(dfs);

		String colSep = ";";
		StringBuilder sb = new StringBuilder();
		for (Pair<SensorInfo, SensorInfo> correlatedPair : correlatedSensors) {

			String s1 = correlatedPair.getLeft().getName() + colSep
					+ df.format(confidences.get(correlatedPair).getValue()) + colSep
					+ correlatedPair.getLeft().getInfo().toString() + colSep + values.get(correlatedPair.getLeft())
							.stream().map(x -> df.format((double) x)).collect(Collectors.joining(colSep));

			String s2 = correlatedPair.getRight().getName() + colSep
					+ df.format(confidences.get(correlatedPair).getValue()) + colSep
					+ correlatedPair.getRight().getInfo().toString() + colSep + values.get(correlatedPair.getRight())
							.stream().map(x -> df.format((double) x)).collect(Collectors.joining(colSep));

			String oo = s1 + "\n" + s2 + "\n\n";
			sb.append(oo);

		}

		OutputStream os = null;
		try {
			// Write the data to file
			os = new FileOutputStream(outFile);
			os.write(sb.toString().getBytes());
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				os.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	static List<Double> filterOutliers(List<Double> values) {
		double medianDataField = StatUtils.percentile(values.stream().mapToDouble(d -> d).toArray(), 50);

		List<Double> sortedValues = new ArrayList<>(values);

		sortedValues.sort((c1, c2) -> Double.compare(Math.abs(medianDataField - c1), Math.abs(medianDataField - c2)));

		double refValue = values.get(0);
		double dist = Math.abs(refValue - sortedValues.get(2));

		double factor = .5;

		List<Double> filtered = new ArrayList<>();

		for (Double e : sortedValues) {
			if (e <= refValue - factor * dist || e >= refValue + factor * dist) {
				filtered.add(e);
			}
		}
		return filtered;
	}

	/**
	 * Evaluate the contact sensor status for each time instant
	 * 
	 * @param deviceStatus
	 * @return
	 */
	private static List<Double> evaluateContactSensorStatus_RIGHT(
			Map<Pair<SensorInfo, SensorInfo>, List<DeviceStatus>> deviceStatus, Vector2D contactSensorPos) {

		List<Double> status = new ArrayList<>();

		// Loop each sample
		for (int i = 0; i < maxSamples - CONTEXT_SIZE - 3; i++) {
			// get the threshold to be used to determine the status of the device
			List<Double> responses = new ArrayList<>();

			double num = 0, den = 0;

			double maxDistance = Double.MIN_NORMAL;
			for (Pair<SensorInfo, SensorInfo> p : deviceStatus.keySet()) {
				double midX = (p.getLeft().getPosition().getX() + p.getRight().getPosition().getX()) / 2;
				double midY = (p.getLeft().getPosition().getY() + p.getRight().getPosition().getY()) / 2;
				Vector2D midPoint = new Vector2D(midX, midY);

				if (Vector2D.distance(midPoint, contactSensorPos) > maxDistance) {
					maxDistance = Vector2D.distance(midPoint, contactSensorPos);
				}
			}

			// for each pair of sensors...
			for (Pair<SensorInfo, SensorInfo> p : deviceStatus.keySet()) {
				// get the correlation at time i
				double correlation = deviceStatus.get(p).get(i).getResponse();
				// get the threshold level
				double threshold = deviceStatus.get(p).get(i).getBoundary();

				// add the difference between correlation and threshold for the current sensors
				// pair

				double midX = (p.getLeft().getPosition().getX() + p.getRight().getPosition().getX()) / 2;
				double midY = (p.getLeft().getPosition().getY() + p.getRight().getPosition().getY()) / 2;
				Vector2D midPoint = new Vector2D(midX, midY);

				double w = maxDistance - Vector2D.distance(midPoint, contactSensorPos);
				num += (correlation - threshold) * w;
				den += w;

				responses.add(correlation - threshold);
			}
			// filters the responses that are considered as outliers
			responses = filterOutliers(responses);

			// evaluate the status of the contact sensor at time i as the average of the
			// calculated responses

			status.add(num / den);

			// status.add(responses.stream().mapToDouble(x -> x).average().orElse(0));
		}
		return status;
	}

	private final static double MADSCALEFACTOR = 1.4826;

	public static double medianAbsoluteDeviation(List<Double> data) {
		double[] dataArray = data.stream().mapToDouble(x -> x).toArray();
		double median = new Median().evaluate(dataArray);
		double[] dataSub = data.stream().mapToDouble(x -> Math.abs(x - median)).toArray();
		double median2 = new Median().evaluate(dataSub);
		double mad = MADSCALEFACTOR * median2;
		return mad;
	}

	public static List<Pair<SensorInfo, SensorInfo>> getSensorsPairsByContextSimilarity(int i) {
		Set<SensorInfo> sensors = values.keySet();
		List<Pair<SensorInfo, SensorInfo>> sensorsPairs = new ArrayList<>();
		List<SensorInfo> sensorsAdded = new ArrayList<>();

		for (SensorInfo s1 : sensors) {
			if (sensorsAdded.contains(s1)) {
				continue;
			}

			List<Number> s1Context = values.get(s1).subList(Math.max(0, i - CONTEXT_SIZE), i + 1);
			Optional<SensorInfo> bestSensor = Optional.empty();
			double score = Double.MAX_VALUE;

			for (SensorInfo s2 : sensors) {
				if (s2.getName().equals(s1.getName()) || sensorsAdded.contains(s2)) {
					continue;
				}

				List<Number> s2Context = values.get(s2).subList(Math.max(0, i - CONTEXT_SIZE), i + 1);

				if (contextDistance(s1Context, s2Context) < score) {
					bestSensor = Optional.of(s2);
				}
			}

			if (bestSensor.isPresent()) {
				sensorsAdded.add(s1);
				sensorsPairs.add(Pair.of(s1, bestSensor.get()));
				sensorsAdded.add(bestSensor.get());
			}
		}

		return sensorsPairs;
	}

	public static double contextDistance(List<Number> s1, List<Number> s2) {
		return IntStream.range(1, s1.size()).mapToDouble(i -> Math.abs((Double) s1.get(i) - (Double) s2.get(i))).sum()
				/ s1.size();
	}

	// private static List<Pair<String, String>> pairs =
	// Arrays.asList(Pair.of("6.106", "6.105"), Pair.of("6.106", "6.107"),
	// Pair.of("6.103", "6.111"));

	// private static List<Pair<String, String>> pairs =
	// Arrays.asList(Pair.of("6.O3", "6.O2"), Pair.of("6.O3", "6.O4"));// ,
	// Pair.of("6.I2",

	private static List<Pair<String, String>> pairs = Arrays.asList(Pair.of("6.I2", "6.O2"), Pair.of("6.I3", "6.O3"),
			Pair.of("6.I4", "6.O4"));// , Pair.of("6.I2",
										// "6.O2"),
	// Pair.of("6.I4", "6.O4"));

	public static void main(String[] args) throws Exception {

		// CsvSensor sens = CsvSensor.getNew("C:\\Users\\dguastel\\I1_pysensee.csv");

		// remove duplicate sensors strings
		SENSORS = Arrays.stream(SENSORS).distinct().toArray(String[]::new);

		readAgentPositionFromXMLFile(configFile);

		// Extract the name of sensors to be used from the pairs
		String[] sensorsName = pairs.stream().flatMap(x -> Arrays.stream(new String[] { x.getLeft(), x.getRight() }))
				.distinct().toArray(String[]::new);

		// get the data from the CSV file
		getDataFromFile(sensorsName);

		maxSamples = 800;

		// map List<Pair<String,String>> to List<Pair<SensorInfo,SensorInfo>>
		// map the pairs to a list of pairs of SensorInfo instances

		// ######################################
		// STEP 1 -> DETERMINE PAIRS OF SENSORS
		// ######################################
		List<Pair<SensorInfo, SensorInfo>> sensors = pairs.stream()
				.map(x -> Pair.of(
						values.keySet().stream().filter(xx -> xx.getName().equals(x.getLeft())).findFirst().get(),
						values.keySet().stream().filter(xx -> xx.getName().equals(x.getRight())).findFirst().get()))
				.collect(Collectors.toList());

		// ######################################################
		// STEP 2 -> Evaluate the correlation between each pair of sensors
		// ######################################################
		Map<Pair<SensorInfo, SensorInfo>, List<DeviceStatus>> pairsResponses = doContactSensorTest_2(
				new Vector2D(442, 883), /* Optional.of(sensors) */Optional.empty());

		// ######################################################
		// STEP 3 -> Data visualization
		// ######################################################

		// Prepare data to be printed
		int numRows = pairsResponses.keySet().size();
		double[][] matrix = new double[numRows * 2][maxSamples];
		int j = 0;
		String[] pairsName = new String[numRows * 2];
		for (Pair<SensorInfo, SensorInfo> pair : pairsResponses.keySet()) {
			double[] correlations = pairsResponses.get(pair).stream().mapToDouble(x -> x.getResponse()).toArray();
			double[] thresholds = pairsResponses.get(pair).stream().mapToDouble(x -> x.getBoundary()).toArray();
			pairsName[j] = pair.toString();
			pairsName[j + 1] = pair.toString() + "_TH";
			System.arraycopy(correlations, 0, matrix[j], 0, correlations.length);
			System.arraycopy(thresholds, 0, matrix[j + 1], 0, thresholds.length);

			j += 2;
		}

		// useful to format numbers with comme for excel plotting
		DecimalFormat df = new DecimalFormat("#####0.00");
		DecimalFormatSymbols dfs = df.getDecimalFormatSymbols();
		dfs.setDecimalSeparator(',');
		df.setDecimalFormatSymbols(dfs);
		for (int i = 0; i < numRows * 2; i++) {
			System.err.print(pairsName[i] + "\t");
		}

		// Print the correlations and the thresholds for each pair of sensors
		System.err.print("\n");
		for (int i = 0; i < maxSamples; i++) {
			for (int k = 0; k < 2 * numRows; k++) {
				System.err.print(df.format(matrix[k][i]));
				System.err.print("\t");
			}
			System.err.print("\n");
		}

		// ######################################################
		// STEP 4 -> Evaluation of the contact sensor status
		// ######################################################

		Vector2D contactSensorPos = new Vector2D(444, 899);

		// Evaluate the contact sensor status
		List<Double> rr = evaluateContactSensorStatus_RIGHT(pairsResponses, contactSensorPos);
		for (int i = 0; i < rr.size(); i++) {
			System.out.println(df.format(rr.get(i)));

		}
//		System.err.println("i'm here");

	}

	public static void mainTest(String[] args) throws Exception {
		/*
		 * GaussianProcessRegression<Double> gpr = new
		 * GaussianProcessRegression<Double>(new Double[] {}, new Double[] {}, new
		 * GaussianKernel(47.02), 0.1);
		 */
	}

	public static void main2(String[] args) throws Exception {
		RandomDataGenerator rg = new RandomDataGenerator();
		double[] data = IntStream.range(0, 32).mapToDouble(x -> rg.nextGaussian(.5, .5)).toArray();
		Arrays.stream(data).boxed().forEach(x -> System.err.print(Double.toString(x) + ";"));
		// System.err.println();
		WaveletShrinkage.denoise(data, new DaubechiesWavelet(10), true);
		Arrays.stream(data).boxed().forEach(x -> System.err.print(Double.toString(x) + ";"));
	}

}
