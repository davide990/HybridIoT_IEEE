package context;

import java.time.Instant;

import agent.base.Perception;

public class ContextEntry {
	/**
	 * The type of information of this entry
	 */
	private final ContextInfo info;

	/**
	 * the value of this entry
	 */
	private final Double value;

	/**
	 * The time instant when this information has been perceived
	 */
	private final Instant perceptionInstant;

	private boolean isEmpty;

	private boolean isEstimation;

	/**
	 * Create a new context entry
	 * 
	 * @param info
	 * @param perceptionInstant
	 * @param value
	 */
	public ContextEntry(ContextInfo info, Instant perceptionInstant, Double value) {
		this.info = info;
		this.value = value;
		this.perceptionInstant = perceptionInstant;

		isEmpty = !Double.isFinite(value);
		isEstimation = false;
	}

	public ContextEntry(ContextInfo info, Instant perceptionInstant, Perception value) {
		this.info = info;
		this.value = value.getValue();
		this.perceptionInstant = perceptionInstant;
		this.isEstimation = value.isEstimation();
		this.isEmpty = !Double.isFinite(value.getValue());
	}

	/**
	 * Copy constructor
	 * 
	 * @param e
	 */
	public ContextEntry(ContextEntry e) {
		this.info = e.info;
		this.value = e.value;
		this.perceptionInstant = e.perceptionInstant;
		this.isEstimation = e.isEstimation;
		this.isEmpty = !Double.isFinite(value);
	}

	/**
	 * Create a new empty context entry
	 * 
	 * @param info
	 * @return
	 */
	public static ContextEntry empty(ContextInfo info) {
		ContextEntry ce = new ContextEntry(info, Instant.now(), Double.NaN);
		//ce.isEmpty = true;
		//ce.isEstimation = false;
		return ce;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((info == null) ? 0 : info.hashCode());
		result = prime * result + ((value == null) ? 0 : value.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ContextEntry other = (ContextEntry) obj;
		if (info != other.info)
			return false;
		if (value == null) {
			if (other.value != null)
				return false;
		} else if (!value.equals(other.value))
			return false;
		return true;
	}

	/**
	 * Return {@code true} if this entry contains a NaN value or no value has been
	 * set yet, {@code false} otherwise.
	 * 
	 * @return
	 */
	public boolean isEmpty() {
		return isEmpty;
	}

	public ContextInfo getInfo() {
		return info;
	}

	public Double getValue() {
		return value;
	}

	public Instant getPerceptionInstant() {
		return perceptionInstant;
	}

	public boolean isEstimation() {
		return isEstimation;
	}

	public void setIsEstimation(boolean isEstimation) {
		this.isEstimation = isEstimation;
	}

	@Override
	public String toString() {
		return "(" + Double.toString(value) + ")";
	}

	public String toLongString() {
		return "(" + info + "=" + Double.toString(value) + ")";
	}

}
