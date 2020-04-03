package context;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import agent.base.Agent;
import context.comparison.ContextComparatorStrategy;

public class Context implements Cloneable {

	/**
	 * The owner of this context
	 */
	private final Agent owner;

	/**
	 * The list of entries of this context. Each entry depicts a unique type of
	 * environmental information
	 */
	private List<ContextEntry> entries;

	private ContextInfo info;

	/**
	 * The index of the last information contained in this context. This value
	 * represents the identifier of this context.
	 */
	private int finalDataIdx;

	/**
	 * The strategy used to compare contexts
	 */
	private static Optional<ContextComparatorStrategy> comparisonStrategy = Optional.empty();

	/**
	 * Create a new instance of {@code Context}
	 * 
	 * @param info the types of information supported by this context
	 */
	public Context(Agent owner, ContextInfo info) {
		this.owner = owner;
		this.info = info;
		entries = new ArrayList<>();
		finalDataIdx = 0;
	}

	public Context(Context c) {
		owner = c.owner;
		info = c.info;
		entries = new ArrayList<>();
		for (ContextEntry e : c.getEntries()) {
			entries.add(e);
		}
		finalDataIdx = c.finalDataIdx;
	}

	public static void setContextComparisonStrategy(ContextComparatorStrategy s) {
		comparisonStrategy = Optional.of(s);
	}

	public boolean isEmpty() {
		return entries.isEmpty();

	}

	public int size() {
		return entries.size();
	}

	/**
	 * Clone this instance of {@code Context}
	 */
	@Override
	public Object clone() throws CloneNotSupportedException {
		Context copy = new Context(owner, info);

		for (ContextEntry e : entries) {
			copy.entries.add(new ContextEntry(e));
		}
		return copy;
	}

	/**
	 * Set the index in the observed data flow of the agent
	 * 
	 * @param finalDataIdx
	 */
	public void setFinalDataIdx(int finalDataIdx) {
		this.finalDataIdx = finalDataIdx;
	}

	public int getFinalDataIdx() {
		return finalDataIdx;
	}

	/**
	 * Add a data sequence to this context. For each registered data type supported
	 * by this context, a value must be provided.
	 * 
	 * @param seq
	 * @throws MissingInformationException
	 */
	public void addEntries(List<ContextEntry> seq) throws MissingInformationException {

		entries.addAll(seq);

		// check if some information has not been provided, thus it has to be estimated
		List<ContextEntry> missingEntries = seq.stream().filter(x -> x.isEmpty()).collect(Collectors.toList());

		if (!missingEntries.isEmpty()) {
			String missingInfoStr = missingEntries.stream().map(x -> x.getInfo().toString())
					.collect(Collectors.joining("; "));

			throw new MissingInformationException("Missing information(s): " + missingInfoStr, seq,
					missingEntries.stream().map(x -> x.getInfo()).toArray(ContextInfo[]::new));
		}
	}

	public void addEntry(ContextEntry e) throws MissingInformationException {

		entries.add(e);

		if (e.isEmpty()) {
			throw new MissingInformationException("Missing information: " + e.getInfo().toString(), e);
		}
	}

	/**
	 * Return {@code true} if all the (context) entries are not NaN
	 * 
	 * @return
	 */
	public boolean isValid() {
		return entries.stream().filter(x -> x.isEmpty()).count() == 0;
	}

	/**
	 * Force the context to add the sequence of entries in input and ignore those
	 * who contain empty data
	 * 
	 * @param seq
	 */
	public void forceAddEntries(List<ContextEntry> seq) {

		entries.addAll(seq);
	}

	public void forceAddFirst(List<ContextEntry> seq) {
		// add the information to this context
		for (ContextEntry entry : seq) {
			// Add the entry, even if empty
			entries.add(0, entry);
		}

	}

	public ContextEntry[] getEntries() {
		return entries.stream().toArray(ContextEntry[]::new);
	}

	public int getEstimatedEntriesCount() {
		return entries.stream().filter(x -> x.isEstimation()).mapToInt(x -> 1).sum();
	}

	public double[] asDoubleArray() {
		return entries.stream().mapToDouble(x -> x.getValue()).toArray();
	}

	public ContextInfo getSupportedInfo() {
		return info;
	}

	public Agent getOwner() {
		return owner;
	}

	public Context getTrendContext() {
		Context copy = new Context(owner, info);

		for (int i = 1; i < entries.size(); i++) {
			double dt = entries.get(i).getValue() - entries.get(i - 1).getValue();
			copy.entries.add(new ContextEntry(info, Instant.now(), dt));
		}

		return copy;
	}
	
	
	public double getMovingAverage() {
		double ma = 0;
		for (int i = 1; i < entries.size(); i++) {
			ma += entries.get(i).getValue() - entries.get(i - 1).getValue();
			
		}
		return  ma / entries.size();
	}

	/**
	 * Compare this context to the input context. The data sequences of the same
	 * type are compared using the Dynamic Time Warping (DTW).
	 * 
	 * @param c the context to compare to
	 * @return the mean of the DTW between the sequences of the contexts
	 */
	public double compare(Context c) {

		ContextComparatorStrategy s = comparisonStrategy
				.orElseThrow(() -> new IllegalAccessError("No context comparator provided"));

		return s.compare(this, c);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((entries == null) ? 0 : entries.hashCode());
		result = prime * result + finalDataIdx;
		result = prime * result + ((info == null) ? 0 : info.hashCode());
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
		Context other = (Context) obj;
		if (entries == null) {
			if (other.entries != null)
				return false;
		} else if (!entries.equals(other.entries))
			return false;
		if (finalDataIdx != other.finalDataIdx)
			return false;
		if (info != other.info)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "[" + Integer.toString(getFinalDataIdx()) + ";" + Integer.toString(getEstimatedEntriesCount()) + "] "
				+ info.toString() + " -> " + entries.stream().map(x -> x.toString()).collect(Collectors.joining("; "));
	}
}
