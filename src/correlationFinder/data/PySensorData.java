package correlationFinder.data;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

public class PySensorData {
	private final Double altitude;
	private final Double pressure;
	private final Double temperature;
	private final Double humidity;
	private final Pair<Double, Double> light;
	private final Triple<Double, Double, Double> acceleration;
	private final Double battery_voltage;

	public PySensorData(Double altitude, Double pressure, Double temperature, Double humidity,
			Pair<Double, Double> light, Triple<Double, Double, Double> acceleration, Double battery_voltage) {
		this.altitude = altitude;
		this.pressure = pressure;
		this.temperature = temperature;
		this.humidity = humidity;
		this.light = light;
		this.acceleration = acceleration;
		this.battery_voltage = battery_voltage;
	}

	public Double getAltitude() {
		return altitude;
	}

	public Double getPressure() {
		return pressure;
	}

	public Double getTemperature() {
		return temperature;
	}

	public Double getHumidity() {
		return humidity;
	}

	public Pair<Double, Double> getLight() {
		return light;
	}

	public Triple<Double, Double, Double> getAcceleration() {
		return acceleration;
	}

	public Double getBattery_voltage() {
		return battery_voltage;
	}

	@Override
	public String toString() {
		return "PySensorData [altitude=" + altitude + ", pressure=" + pressure + ", temperature=" + temperature
				+ ", humidity=" + humidity + ", light=" + light + ", acceleration=" + acceleration
				+ ", battery_voltage=" + battery_voltage + "]";
	}

}
