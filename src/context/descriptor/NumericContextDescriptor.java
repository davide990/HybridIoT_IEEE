package context.descriptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.commons.math3.util.Pair;

import context.Context;
import context.ContextInfo;

public class NumericContextDescriptor implements ContextDescriptor {

	private Map<ContextInfo, Pair<Double, Double>> descriptors;

	public NumericContextDescriptor(Context c) {
		descriptors = new HashMap<>();
		ContextInfo inf = c.getSupportedInfo();
		double[] situation = c.asDoubleArray();
		double min = Arrays.stream(situation).min().getAsDouble();
		double max = Arrays.stream(situation).max().getAsDouble();
		descriptors.put(inf, new Pair<>(min, max));
	}

	/**
	 * Return true if the input {@code ContextDescriptor} has a maximum/minimum
	 * range values that fall in the same range as this descriptor
	 */
	@Override
	public boolean includedIn(ContextDescriptor other) {

		if (!descriptors.keySet().containsAll(other.getDescriptors().keySet()))
			return false;

		if (!(other instanceof NumericContextDescriptor)) {
			return false;
		}

		NumericContextDescriptor theOther = (NumericContextDescriptor) other;
		for (ContextInfo inf : descriptors.keySet()) {
			boolean low = theOther.getDescriptors().get(inf).getFirst() <= descriptors.get(inf).getFirst();
			boolean high = theOther.getDescriptors().get(inf).getSecond() >= descriptors.get(inf).getSecond();

			if (!low || !high) {
				return false;
			}
		}

		return true;
	}

	@Override
	public double distance(ContextDescriptor other) {

		if (!descriptors.keySet().containsAll(other.getDescriptors().keySet()))
			return Double.NaN;

		if (!(other instanceof NumericContextDescriptor)) {
			return Double.NaN;
		}

		//int count = 0;
		double meanDist = 0;
		NumericContextDescriptor theOther = (NumericContextDescriptor) other;
		
		for (Iterator<ContextInfo> iterator = descriptors.keySet().iterator(); iterator.hasNext();) {
			
			ContextInfo inf = iterator.next();
			
			/*
			double low = (theOther.getDescriptors().get(inf).getFirst() + descriptors.get(inf).getFirst()) / 2;
			double high = (theOther.getDescriptors().get(inf).getSecond() + descriptors.get(inf).getSecond()) / 2;
			meanDist += (low + high) / 2;
			count++;*/
			
			Vector2D v1 = new Vector2D(theOther.getDescriptors().get(inf).getFirst(), theOther.getDescriptors().get(inf).getSecond());
			Vector2D v2 = new Vector2D(descriptors.get(inf).getFirst(), descriptors.get(inf).getSecond());
			
			meanDist += Vector2D.distance(v1, v2);
			//count++;
			
		}

		return meanDist / descriptors.keySet().size();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((descriptors == null) ? 0 : descriptors.hashCode());
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

		NumericContextDescriptor other = (NumericContextDescriptor) obj;
		if (descriptors == null) {
			if (other.descriptors != null)
				return false;
		} else if (!descriptors.equals(other.descriptors))
			return false;
		return true;
	}

	@Override
	public Map<ContextInfo, Pair<Double, Double>> getDescriptors() {
		return Collections.unmodifiableMap(descriptors);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (ContextInfo inf : descriptors.keySet()) {
			sb.append(inf.toString() + "->" + descriptors.get(inf).toString());
		}

		return sb.toString();
	}

}