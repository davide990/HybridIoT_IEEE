package sensors;

import context.ContextEntry;
import context.ContextInfo;

public interface DataSensor {
	ContextEntry receiveData() throws Exception;

	int getSamplesCount();

	int getCurrentSampleIdx();

	String getID();

	/**
	 * Get the last data observed by this sensor.
	 * 
	 * @param info
	 * @return
	 */
	double getCurrentData();

	/**
	 * Get the last data observed by this sensor.
	 * 
	 * @param info
	 * @param offset the offset from the last observed element (if {@code i}, the
	 *               element at {@code i-offset} is returned
	 * @return
	 */
	double getCurrentData(int offset);

	/**
	 * Get all the data currently observed by this sensor
	 * 
	 * @param info
	 * @return
	 */
	double[] getObservedData();

	/**
	 * Reset the data index. The data index represents the number of samples
	 * currently observed by this sensor
	 */
	void resetDataIdx();

	/**
	 * Move to the next sample. If {@code FileDataSensor} is used, the index to the
	 * next data in the csv file is incremented
	 */
	void nextSample();

	ContextInfo getSupportedInfo();
}
