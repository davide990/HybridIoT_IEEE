package configuration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import agent.AmbientContextAgent;
import agent.base.Agent;
import agent.base.AgentType;
import agent.crossValidation.BestNeighborhoodStrategy;
import context.Context;
import context.ContextInfo;
import context.comparison.ContextComparatorStrategy;
import context.dynamicContext.OptimalContextFinder;
import context.dynamicContext.VarWidthContextFinder;
import context.estimation.ContextEstimationStrategy;

public abstract class MASConfig {

	// --------------------------------------------------------
	// DEFAULT CONFIGURATION
	// --------------------------------------------------------
	public static final Class<? extends OptimalContextFinder> OPTIMAL_CONTEXT_STRATEGY = VarWidthContextFinder.class;

	private static final boolean log = false;

	public static final String OUT_ESTIMATION_FOLDER = "C:\\Users\\dguastel\\estimations";
	public static final String CSV_COL_SEPARATOR = ";";
	public static int NUM_FOLDS = 10;

	public static BestNeighborhoodStrategy BEST_AGENT_NEIGHBORS_STRATEGY = BestNeighborhoodStrategy.MOST_CONFIDENT_AGENTS;

	// default = true;
	public static boolean USE_COOPERATION_FOR_ESTIMATION = true;

	public static int PERCENTAGE_AGENT_TO_WHICH_COOPERATE = 25;

	/*
	 * DATA ARE STORED BY COLUMN --> ID, type, DATA1, DATA2, DATA3, ...
	 */
	// The column where to find the ID of the sensor/agent
	public static final int DATA_TYPE_NAME_COLUMN = 1;

	// The column where data start
	public static final int DATA_START_COLUMN = 2;

	public static ContextInfo TEST_INFO = ContextInfo.TEMP;
	public static ContextInfo NEIGHBORS_INFO = ContextInfo.TEMP;

	public static String STAT_MAP_OUT_FILE = "C:\\Users\\dguastel\\estimations\\estimation_results.csv";
	// public static String STAT_MAP_OUT_FILE = "estimation_results.csv";

	// --------------------------------------------------------
	// --------------------------------------------------------

	private String fname;
	private List<Agent> agents;
	private String[] cmdLineArgs;

	public MASConfig(String fname, String[] cmdLineArgs) {
		this.fname = fname;
		this.agents = new ArrayList<>();
		this.cmdLineArgs = cmdLineArgs;
	}

	public boolean parse() {

		try {
			// Input file
			File inputFile = new File(fname);

			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

			// Parse the input file
			Document doc = dBuilder.parse(inputFile);

			parseContextComparisonStrategy(doc);

			parsePercentageOfAgentsToWhichCooperate(doc);

			parseUseCooperation(doc);

			parseNeighborhoodStrategy(doc);

			parseAllAgents(doc);

		} catch (SAXException | IOException | ParserConfigurationException e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}

	private void parseNeighborhoodStrategy(Document doc) {
		NodeList nList = doc.getElementsByTagName("neighborhoodStrategy");

		if (nList.getLength() <= 0) {
			return;
		}

		if (nList.getLength() > 1) {
			throw new IllegalArgumentException("Only one value allowed");
		}
		Element agElement = (Element) nList.item(0);

		MASConfig.BEST_AGENT_NEIGHBORS_STRATEGY = BestNeighborhoodStrategy.valueOf(agElement.getAttribute("value"));

	}

	private void parsePercentageOfAgentsToWhichCooperate(Document doc) {
		NodeList nList = doc.getElementsByTagName("agentPercentage");

		if (nList.getLength() <= 0) {
			return;
		}

		if (nList.getLength() > 1) {
			throw new IllegalArgumentException("Only one value allowed");
		}
		Element agElement = (Element) nList.item(0);

		try {
			PERCENTAGE_AGENT_TO_WHICH_COOPERATE = Integer.parseInt(agElement.getAttribute("value"));
		} catch (NumberFormatException e) {
			e.printStackTrace();
		}
	}

	private void parseUseCooperation(Document doc) {
		NodeList nList = doc.getElementsByTagName("useCooperation");

		if (nList.getLength() > 1) {
			throw new IllegalArgumentException("Only one node allowed for property [useCooperation]");
		}

		Element useCooperationElem = (Element) nList.item(0);
		if (useCooperationElem == null) {
			if (log) {
				System.err.println("[warning] property [useCooperation] not specified. Default: "
						+ Boolean.toString(USE_COOPERATION_FOR_ESTIMATION));
			}

			return;
		}

		// Element useCooperationElem = (Element) nList.item(0);
		String useCooperationStr = useCooperationElem.getAttribute("value");

		Optional<Boolean> value = Optional.ofNullable(BooleanUtils.toBooleanObject(useCooperationStr));
		if (value.isPresent()) {
			USE_COOPERATION_FOR_ESTIMATION = value.get();
		} else {
			if (log) {
				System.err.println("[warning] property [useCooperation] not specified. Default: "
						+ Boolean.toString(USE_COOPERATION_FOR_ESTIMATION));
			}
			return;
		}

		if (log) {
			System.err.println("property [useCooperation] set to " + Boolean.toString(USE_COOPERATION_FOR_ESTIMATION));
		}
	}

	private void parseContextComparisonStrategy(Document doc) {
		NodeList nList = doc.getElementsByTagName("contextComparisonStrategy");

		if (nList.getLength() > 1) {
			throw new IllegalArgumentException("Only one comparison strategy allowed");
		}
		Element agElement = (Element) nList.item(0);
		String comparisonStrategyClass = agElement.getAttribute("value");
		if (log) {
			System.out.println("comparison strategy : " + comparisonStrategyClass);
		}

		try {
			Class<?> comparisonStrategy = Class.forName(comparisonStrategyClass);
			Context.setContextComparisonStrategy((ContextComparatorStrategy) comparisonStrategy.newInstance());
		} catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
			e.printStackTrace();
		}

	}

	private void parseAllAgents(Document doc) {
		NodeList nList = doc.getElementsByTagName("agents");

		Node nNode = nList.item(0);
		if (log) {
			System.out.println("\nCurrent Element :" + nNode.getNodeName());
		}

		for (int i = 0; i < nNode.getChildNodes().getLength(); i++) {
			parseAgent(nNode.getChildNodes().item(i));
		}

	}

	private void parseAgent(Node agentNode) {
		if (!agentNode.getNodeName().equals("agent") || agentNode.getNodeType() != Node.ELEMENT_NODE) {
			return;
		}
		if (log) {
			System.out.println("\nCurrent Element :" + agentNode.getNodeName());
		}

		Element agElement = (Element) agentNode;
		if (log) {
			System.out.println("name : " + agElement.getAttribute("name"));
		}

		String agType = agElement.getAttribute("type");

		if (agType.equals("CONTACT")) {
			parseContactSensorAgent(agElement);
		} else if (agType.equals("LEARNER")) {
			parseAmbientContextAgent(agElement);
		}

	}

	private void parseContactSensorAgent(final Element agElement) {
		String coordinateX = "";
		String coordinateY = "";
		String agName = agElement.getAttribute("name");
		for (int i = 0; i < agElement.getChildNodes().getLength(); i++) {
			Node agProperty = agElement.getChildNodes().item(i);
			String nodeName = agProperty.getNodeName();
			switch (nodeName) {
			case "coordinates":
				coordinateX = ((Element) agProperty).getAttribute("x");
				coordinateY = ((Element) agProperty).getAttribute("y");
				System.out.println("x:" + ((Element) agProperty).getAttribute("x"));
				System.out.println("y:" + ((Element) agProperty).getAttribute("y"));
				break;
			}
		}
		double xCoord = Double.NaN, yCoord = Double.NaN;
		try {
			xCoord = Double.parseDouble(coordinateX);
			yCoord = Double.parseDouble(coordinateY);
			if (log) {
				System.err.println("creating agent [" + agName + "] at " + new Vector2D(xCoord, yCoord).toString());
			}
		} catch (Exception e) {
			if (log) {
				System.err.println("Error while parsing position for contact sensor agent [" + agName + "]");
			}
			e.printStackTrace();
		}

		createContactSensorAgent(new Vector2D(xCoord, yCoord), agName);
	}

	private void parseAmbientContextAgent(final Element agElement) {
		String agName = agElement.getAttribute("name");
		String dataFile = "";
		String colSep = "";
		String coordinateX = "";
		String heightStr = "";
		String coordinateY = "";
		String imputationStrategyStr = "";
		String contextInfo = null;
		boolean onlyTraining = true;

		for (int i = 0; i < agElement.getChildNodes().getLength(); i++) {
			Node agProperty = agElement.getChildNodes().item(i);
			String nodeName = agProperty.getNodeName();
			switch (nodeName) {
			case "datafile":
				if (log) {
					System.out.println("dataFile:" + ((Element) agProperty).getAttribute("value"));
				}
				dataFile = ((Element) agProperty).getAttribute("value");
				break;

			case "columnSeparator":
				colSep = ((Element) agProperty).getAttribute("value");
				if (log) {
					System.out.println("columnSeparator:" + ((Element) agProperty).getAttribute("value"));
				}
				break;

			case "coordinates":
				coordinateX = ((Element) agProperty).getAttribute("x");
				coordinateY = ((Element) agProperty).getAttribute("y");
				if (log) {
					System.out.println("x:" + ((Element) agProperty).getAttribute("x"));
				}
				if (log) {
					System.out.println("y:" + ((Element) agProperty).getAttribute("y"));
				}
				break;

			case "supportedInfo":
				// contextInfo = parseContextInfo(agProperty);
				contextInfo = ((Element) agProperty).getAttribute("value");
				if (log) {
					System.out.println("info: " + contextInfo);
				}
				// System.out.println("info: " + contextInfo.stream().map(x ->
				// x).collect(Collectors.joining(",")));

				break;

			case "imputationStrategy":
				imputationStrategyStr = ((Element) agProperty).getAttribute("value");
				if (log) {
					System.out.println("info: " + imputationStrategyStr);
				}
				break;

			case "height":
				heightStr = ((Element) agProperty).getAttribute("value");
				if (log) {
					System.out.println("info: " + heightStr);
				}
				break;

			case "onlyTraining":
				Optional<Boolean> bv = Optional
						.ofNullable(BooleanUtils.toBooleanObject(((Element) agProperty).getAttribute("value")));

				if (bv.isPresent()) {
					onlyTraining = bv.get();
					if (log) {
						System.out.println("only training: " + onlyTraining);
					}
				}

				break;

			default:
				break;
			}
		}

		try {
			double xCoord = Double.NaN, yCoord = Double.NaN;
			try {
				xCoord = Double.parseDouble(coordinateX);
				yCoord = Double.parseDouble(coordinateY);
				if (log) {
					System.err.println("creating agent [" + agName + "] at " + new Vector2D(xCoord, yCoord).toString());
				}
			} catch (Exception e) {
				if (log) {
					System.err.println("Unable to parse XY coordinates for" + agName);
				}
			}

			if (cmdLineArgs.length >= 2) {
				if (cmdLineArgs[1].equals(agName)) {
					onlyTraining = false;
				}
			}

			if (!onlyTraining) {
				
				// Create N agent and assign each one to a specific fold
				for (int i = 0; i < MASConfig.NUM_FOLDS; i++) {

					ContextInfo info = MASConfig.TEST_INFO;
					
					
					// Create the agent
					Optional<Agent> ag = createAgent(new Vector2D(xCoord, yCoord), dataFile, agName, colSep,
							info/*ContextInfo.fromString(contextInfo)*/, Optional.of(i), onlyTraining);
					if (ag.isPresent()) {
						if (NumberUtils.isCreatable(heightStr)) {
							ag.get().setHeight(Optional.of(Double.parseDouble(heightStr)));
						}
						((AmbientContextAgent) ag.get()).setEstimationStrategy(
								(ContextEstimationStrategy) Class.forName(imputationStrategyStr).newInstance());
						agents.add(ag.get());

					}
				}
			} else {

				
				
				//ContextInfo info = MASConfig.NEIGHBORS_INFO;
				
				ContextInfo info = ContextInfo.fromString(contextInfo);
				
				
				Optional<Agent> ag = createAgent(new Vector2D(xCoord, yCoord), dataFile, agName, colSep,
						info/*ContextInfo.fromString(contextInfo)*/, Optional.empty(), onlyTraining);
				if (ag.isPresent()) {
					((AmbientContextAgent) ag.get()).setEstimationStrategy(
							(ContextEstimationStrategy) Class.forName(imputationStrategyStr).newInstance());
					if (NumberUtils.isCreatable(heightStr)) {
						ag.get().setHeight(Optional.of(Double.parseDouble(heightStr)));
					}

					agents.add(ag.get());
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public abstract Optional<Agent> createAgent(Vector2D vector2d, String dataFile, String agName, String colSep,
			ContextInfo fromString, Optional<Integer> foldID, boolean onlyTraining);

	public abstract Optional<Agent> createContactSensorAgent(Vector2D vector2d, String agName);

	public String getFname() {
		return fname;
	}

	public List<Agent> getAgents() {
		return agents;
	}

	public List<Agent> getAmbientContextAgents() {
		return agents.stream().filter(x -> x.getType() == AgentType.AMBIENT_CONTEXT_AGENT).collect(Collectors.toList());
	}

	public List<Agent> getContactSensorAgents() {
		return agents.stream().filter(x -> x.getType() == AgentType.CONTACT).collect(Collectors.toList());
	}

}
