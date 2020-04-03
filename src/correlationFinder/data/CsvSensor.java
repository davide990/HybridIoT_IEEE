package correlationFinder.data;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

public class CsvSensor {

	private List<PySensorData> dataList;

	private CsvSensor() {
		dataList = new ArrayList<>();
	}

	public static CsvSensor getNew(String fname) {
		CsvSensor s = new CsvSensor();
		s.parse(fname);
		return s;
	}

	private void parse(String fname) {
		try (Reader reader = Files.newBufferedReader(Paths.get(fname));
				CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader());) {
			for (CSVRecord csvRecord : csvParser) {
				// Accessing Values by Column Index
				String altitude = csvRecord.get(0);
				String pressure = csvRecord.get(1);
				String temperature = csvRecord.get(2);
				String humidity = csvRecord.get(3);
				String light = csvRecord.get(4);
				String acceleration = csvRecord.get(5);
				String battery_voltage = csvRecord.get(6);

				double altitudeDouble = Double.parseDouble(altitude);
				double pressureDouble = Double.parseDouble(pressure);
				double temperatureDouble = Double.parseDouble(temperature);
				double humidityDouble = Double.parseDouble(humidity);
				Pair<Double, Double> lightPair = Pair.of(Double.parseDouble(light.split(";")[0]),
						Double.parseDouble(light.split(";")[1]));
				Triple<Double, Double, Double> accelerationTriple = Triple.of(
						Double.parseDouble(acceleration.split(";")[0]), Double.parseDouble(acceleration.split(";")[1]),
						Double.parseDouble(acceleration.split(";")[2]));
				double batteryDouble = Double.parseDouble(battery_voltage);

				PySensorData data = new PySensorData(altitudeDouble, pressureDouble, temperatureDouble, humidityDouble,
						lightPair, accelerationTriple, batteryDouble);

				dataList.add(data);

			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
