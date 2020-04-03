package agent.contactSensor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import agent.base.Agent;
import context.Context;

public class ContactSensorEstimation {

	private List<Pair<Agent, Agent>> agents;
	private Context contextA;
	private Context contextB;
	private int sampleIndex;
	private double response;
	private double threshold;

	List<Pair<Agent, Agent>> outliers = new ArrayList<>();
	List<Pair<Agent, Agent>> inliers = new ArrayList<>();

	public ContactSensorEstimation() {

		this.agents = new ArrayList<>();

		// this.agents.addAll(agents);
		/*
		 * this.contextA = contextA; this.contextB = contextB; this.response = response;
		 */
		sampleIndex = 0;
	}

	public List<Pair<Agent, Agent>> getAgents() {
		return Collections.unmodifiableList(agents);
	}

	public void addAgentPair(Pair<Agent, Agent> p) {
		agents.add(p);
	}

	public Context getContextA() {
		return contextA;
	}

	public Context getContextB() {
		return contextB;
	}

	public double getResponse() {
		return response;
	}

	public void setContextA(Context contextA) {
		this.contextA = contextA;
	}

	public void setContextB(Context contextB) {
		this.contextB = contextB;
	}

	public void setResponse(double response) {
		this.response = response;
	}

	public void setSampleIndex(int sampleIndex) {
		this.sampleIndex = sampleIndex;
	}

	public int getSampleIndex() {
		return sampleIndex;
	}

	public double getThreshold() {
		return threshold;
	}

	public void setThreshold(double threshold) {
		this.threshold = threshold;
	}

	public void addOutlier(Pair<Agent,Agent> a) {
		outliers.add(a);
	}

	public void addInlier(Pair<Agent,Agent> a) {
		inliers.add(a);
	}

	public List<Pair<Agent, Agent>> getOutliers() {
		return outliers;
	}

	public List<Pair<Agent, Agent>> getInliers() {
		return inliers;
	}

}