package context;

import java.util.Arrays;
import java.util.Objects;

public enum ContextInfo {
	HUMIDITY("humidity"), TEMP("temperature"), CO2("co2"), VOLTAGE("voltage"), RADIATION("radiation"), PRESSURE("pressure"), LIGHT("light"),
	WINDSPEED("windSpeed"), NULL("");

	private final String[] name;

	private ContextInfo(String... name) {
		this.name = Objects.requireNonNull(name);
	}

	@Override
	public String toString() {
		return String.join(";", name);
	}

	public static ContextInfo fromString(String s) {

		for (ContextInfo instance : ContextInfo.values()) {
			for (String str : instance.name) {

				boolean match = Arrays.asList(instance.name).stream().anyMatch(x -> x.contains(s));
				
				if (!str.toUpperCase().equals(s.trim().toUpperCase()) && !match) {
					continue;
				}
				return instance;
			}
		}
		throw new IllegalArgumentException("no info corresponding to specified string: \"" + s + "\"");
	}

}
