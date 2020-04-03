package agent.contactSensor.interfaces;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

import agent.AmbientContextAgent;
import agent.ContactSensorAgent;

public interface ThresholdEvaluation {

	Double[] calculate(ContactSensorAgent self, Pair<AmbientContextAgent, AmbientContextAgent> thePair, Map<Pair<AmbientContextAgent, AmbientContextAgent>, List<Double>> correlationsMap);

	boolean hasThreshold();
	
	int getClass(double x, Double[] ths);
	
	double getFuzzyClass(double corr, Double[] ths);
	
	List<Double> getCentroids();
	
	double getUndeterminedClass();

}
