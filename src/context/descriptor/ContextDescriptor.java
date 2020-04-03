package context.descriptor;

import java.util.Map;
import java.util.function.Supplier;

import context.Context;
import context.ContextInfo;

/**
 * A descriptor related to a unique context. It describes the data range to
 * which a context it refers, for each information supported by the context
 * 
 * @author Davide Andrea Guastella (SMAC-IRIT)
 */
public interface ContextDescriptor {

	Map<ContextInfo, ?> getDescriptors();

	boolean includedIn(ContextDescriptor other);

	double distance(ContextDescriptor other);

	static ContextDescriptor create(Supplier<? extends ContextDescriptor> factory) {
		return factory.get();
	}

	public static ContextDescriptor getNumericContextDescriptor(Context c) {
		Supplier<ContextDescriptor> numericDescriptorFactory = () -> new NumericContextDescriptor(c);
		return create(numericDescriptorFactory);
	}

}
