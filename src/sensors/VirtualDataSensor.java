package sensors;

import java.util.ArrayList;
import java.util.List;

import context.ContextEntry;
import context.ContextInfo;
import device.Device;

public class VirtualDataSensor implements DataSensor, Device {

	private int observedSampleID;
	private List<Double> data;
	private ContextInfo supportedInfo;

	/**
	 * Name of the agent related to this sensor
	 */
	private String agentID;

	public VirtualDataSensor(String ID, ContextInfo info) {
		observedSampleID = 0;
		data = new ArrayList<>();
	}

	@Override
	public ContextEntry receiveData() throws Exception {
		data.add(Double.POSITIVE_INFINITY);
		return ContextEntry.empty(getSupportedInfo());
	}

	@Override
	public int getSamplesCount() {
		return Integer.MAX_VALUE;
	}

	@Override
	public int getCurrentSampleIdx() {
		//return Math.max(data.size()-1, 0);
		return observedSampleID;
	}

	@Override
	public String getID() {
		return agentID;
	}

	@Override
	public double getCurrentData() {
		return data.get(data.size() - 1);
	}

	@Override
	public double getCurrentData(int offset) {
		return data.get(getCurrentSampleIdx() - offset);
	}
	
	
	public void setEstimation(Double v, int index) {
		data.set(index, v);
	}

	@Override
	public double[] getObservedData() {

		return data.stream().mapToDouble(x -> x).toArray();
	}

	@Override
	public void resetDataIdx() {
		observedSampleID = 0;

	}

	@Override
	public void nextSample() {
		observedSampleID++;
	}

	@Override
	public ContextInfo getSupportedInfo() {
		return supportedInfo;
	}

	@Override
	public boolean isActive() {
		return true;
	}

	@Override
	public void turnOn() {

	}

	@Override
	public void turnOff() {

	}

}
