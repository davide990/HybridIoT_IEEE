package context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MissingInformationException extends Exception {

	private static final long serialVersionUID = 1L;
	private final ContextInfo[] info;
	private final List<ContextEntry> entries;

	public MissingInformationException(String message, List<ContextEntry> entries, ContextInfo... info) {
		super(message);
		this.info = info;
		this.entries = entries;
	}

	public MissingInformationException(String message, ContextEntry entry) {
		super(message);
		entries = new ArrayList<ContextEntry>();
		entries.add(entry);
		info = new ContextInfo[] { entry.getInfo() };
	}

	public ContextInfo[] getInfo() {
		return info;
	}

	public List<ContextEntry> getEntries() {
		return Collections.unmodifiableList(entries);
	}

}
