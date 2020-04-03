package sensors;

import agent.base.Agent;

public interface DataSensorFailureHandler {

	/**
	 * This method defines how an agent has to handle the failure of the related
	 * sensor.
	 * 
	 * @param a an agent
	 * @param e an exception generated by the sensor
	 * @param s the sensor related to {@code a}
	 */
	void handleSensorFailure(Agent a, Exception e, DataSensor s);
}