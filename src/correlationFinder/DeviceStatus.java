package correlationFinder;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

public class DeviceStatus {
	private final int sampleIndex;
	private double response;
	private double boundary;
	private double maxBoundary;

	static DecimalFormat df = new DecimalFormat("#####0.00");
	static DecimalFormatSymbols dfs = df.getDecimalFormatSymbols();
	static {
		dfs.setDecimalSeparator(',');
		df.setDecimalFormatSymbols(dfs);
	}

	public DeviceStatus(int sampleIndex, double response) {
		this.sampleIndex = sampleIndex;
		this.response = response;
	}

	public int getSampleIndex() {
		return sampleIndex;
	}

	public double getResponse() {
		return response;
	}

	public void setResponse(double response) {
		this.response = response;
	}

	public void setMaxBoundary(double maxBoundary) {
		this.maxBoundary = maxBoundary;
	}

	public void setBoundary(double minBoundary) {
		this.boundary = minBoundary;
	}

	public double getMaxBoundary() {
		return maxBoundary;
	}

	public double getBoundary() {
		return boundary;
	}

	@Override
	public String toString() {
		return Integer.toString(sampleIndex) + "\t" + df.format(response) + '\t' + df.format(boundary);
	}

}
