package sensors;

import java.io.EOFException;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import agent.AmbientContextAgent;
import agent.environment.Environment;
import configuration.MASConfig;
import context.ContextEntry;
import context.ContextInfo;
import device.Device;

public class FileDataSensor implements DataSensor, Device {

	/**
	 * The complete path to the data set
	 */
	private String fname;

	/**
	 * The file object that points to the data set
	 */
	private File file;

	/**
	 * A boolean flag that indicates whether this sensor is active or not
	 */
	private boolean active;

	/**
	 * The current index at which the sensor is pointing. This has to be intended as
	 * the position, within the file this sensor is reading, at which the current is
	 * located.
	 */
	private int dataIdx;

	/**
	 * The number of samples of this data set
	 */
	private int dataSize;

	/**
	 * ID of the used sensors (name of city, station, sensor...)
	 */
	private String ID;

	/**
	 * The list of data
	 */
	private List<Double> data;

	/**
	 * The information provided by this sensor
	 */
	private ContextInfo supportedInfo;

	/**
	 * An optional binary vector that specify when this sensor works or not. If the
	 * value at the i-th position is {@code true}, this sensors simulate a
	 * malfunction, thus providing a null data. Otherwise, if {@code false}, the
	 * data is provided by the real sensor. <br/>
	 * <br/>
	 * 
	 * <b>Used ONLY when doing cross-validation.<b/>
	 */
	private Optional<Boolean[]> testSetIDX;

	/**
	 * Name of the agent related to this sensor
	 */
	private String agentID;

	/**
	 * Constructor for {@code FileDataReceiver} class
	 * 
	 * @param filename
	 */
	private FileDataSensor(String filename, String columnsSeparator) {
		data = new ArrayList<>();
		dataIdx = 0;

		fname = filename;
		file = new File(fname);
		ID = "";
		dataSize = 0;
		active = true;
		testSetIDX = Optional.empty();
	}

	/**
	 * Static factory method for {@code FileDataReceiver} class
	 * 
	 * @param filename
	 * @param agentID
	 * @return
	 * @throws Exception
	 */
	public static FileDataSensor getNew(ContextInfo info, String filename, String agentID, String columnSeparator)
			throws Exception {

		// Create a new sensor
		FileDataSensor fd = new FileDataSensor(filename, columnSeparator);

		// Assign properties
		fd.supportedInfo = info;
		fd.agentID = agentID;

		// Read all the lines from the file
		List<String> lines = Files.readAllLines(fd.file.toPath(), StandardCharsets.UTF_8);

		// Find out the available sensors
		Set<String> availableSensorsID = lines.stream().map(x -> x.split(columnSeparator)[0])
				.collect(Collectors.toSet());

		// If this list does not contain the sensor provided in input, throw an
		// exception
		if (!availableSensorsID.contains(agentID)) {
			throw new IllegalArgumentException(
					"Sensor [" + agentID + "] has not been found in file [" + filename + "]");
		}

		// Assign the ID
		fd.ID = agentID;

		// Loop through the dataset in order to find the line that contains the data
		// related to the sensor
		boolean foundLine = false;
		for (int lineIdx = 0; lineIdx < lines.size() && !foundLine; lineIdx++) {

			String currentLine = lines.get(lineIdx);
			String[] words = currentLine.split(columnSeparator);

			// Find the line associated to the specified sensor
			if (!words[0].trim().equals(agentID.trim())) {
				continue;
			}

			String[] dataStr = Arrays.copyOfRange(words, MASConfig.DATA_START_COLUMN, words.length);
			ContextInfo inf = ContextInfo.fromString(words[MASConfig.DATA_TYPE_NAME_COLUMN].trim());

			if (inf != info) {
				continue;
			}

			if (inf == ContextInfo.NULL) {
				System.err.println("[WARNING] Null context");
			}

			List<Double> dataDouble = Arrays.asList(dataStr).stream().map(x -> Double.parseDouble(x))
					.collect(Collectors.toList());
			fd.dataSize = dataDouble.size();
			fd.data = dataDouble;
			foundLine = true;
		}

		if (!foundLine) {
			throw new IllegalArgumentException("No data found for sensor [" + agentID + "/" + info + "]");
		}

		return fd;
	}

	@Override
	public int getSamplesCount() {
		return dataSize;
	}

	@Override
	public int getCurrentSampleIdx() {
		return dataIdx;
	}

	@Override
	public ContextEntry receiveData() throws EOFException {

		if (dataIdx >= dataSize) {
			throw new EOFException("Reached EOF.");
		}

		// If a test set is provided (for cross-validation), turn on/off
		if (testSetIDX.isPresent()) {
			if (testSetIDX.get()[dataIdx]) {
				turnOff();

				// If other agents have the same name, they belong to the same "sensor box".
				// Thus, turn off their sensor
				Environment.get().getAmbientContextAgents().stream().filter(x -> x.getAgentName().equals(this.agentID))
						.map(x -> (AmbientContextAgent) x).forEach(x -> ((Device) x.getSensor()).turnOff());

			} else {
				turnOn();
				Environment.get().getAmbientContextAgents().stream().filter(x -> x.getAgentName().equals(this.agentID))
						.map(x -> (AmbientContextAgent) x).forEach(x -> ((Device) x.getSensor()).turnOn());
			}
		}

		if (active) {
			ContextEntry c = new ContextEntry(getSupportedInfo(), Instant.now(), data.get(dataIdx));
			return c;
		}

		return ContextEntry.empty(getSupportedInfo());
	}

	public String getFname() {
		return fname;
	}

	@Override
	public void nextSample() {
		dataIdx++;
	}

	@Override
	public double getCurrentData() {
		return getCurrentData(0);
	}

	@Override
	public double getCurrentData(int offset) {
		return data.get(getCurrentSampleIdx() - offset);
	}

	/**
	 * Reset the data indexes to the first sample
	 */
	@Override
	public void resetDataIdx() {
		dataIdx = 0;
	}

	/**
	 * Get the ID of the used sensors (name of city, station, sensor...)
	 */
	@Override
	public String getID() {
		return ID;
	}

	@Override
	public double[] getObservedData() {
		return Arrays.copyOfRange(data.stream().mapToDouble(x -> x).toArray(), 0, getCurrentSampleIdx());
	}

	@Override
	public boolean isActive() {
		return active;
	}

	@Override
	public void turnOn() {
		active = true;
	}

	@Override
	public void turnOff() {
		active = false;
	}

	public Boolean[] getTestSetIDX() {
		return testSetIDX.orElseThrow(() -> new IllegalAccessError("No test set provided."));
	}

	public void setTestSetIDX(Boolean[] s) {
		testSetIDX = Optional.ofNullable(s);
	}

	@Override
	public ContextInfo getSupportedInfo() {
		return supportedInfo;
	}

}
