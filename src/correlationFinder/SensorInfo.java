package correlationFinder;

import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;

import context.ContextInfo;

public class SensorInfo {
	private final String name;
	private final ContextInfo info;
	private Vector2D position;

	public SensorInfo(String name, ContextInfo info) {
		this.name = name;
		this.info = info;
	}

	public String getName() {
		return name;
	}

	public ContextInfo getInfo() {
		return info;
	}

	@Override
	public String toString() {
		return name + ";" + info.toString();
	}

	public Vector2D getPosition() {
		return position;
	}

	public void setPosition(Vector2D position) {
		this.position = position;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((info == null) ? 0 : info.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
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
		SensorInfo other = (SensorInfo) obj;
		if (info != other.info)
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

}
