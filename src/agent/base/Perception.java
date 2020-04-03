package agent.base;

public class Perception {
	private final double value;
	private final boolean isEstimation;

	public Perception(double value, boolean isEstimation) {
		this.value = value;
		this.isEstimation = isEstimation;
	}

	public static Perception empty() {
		return new Perception(Double.NaN, false);
	}

	public double getValue() {
		return value;
	}

	public boolean isEstimation() {
		return isEstimation;
	}

	@Override
	public String toString() {
		return "[value=" + value + ", isEstimation=" + isEstimation + "]";
	}

}
