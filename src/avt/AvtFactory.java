package avt;

import fr.irit.smac.util.avt.AVT;
import fr.irit.smac.util.avt.AVTBuilder;

public class AvtFactory {

	public final static double MIN_CONFIDENCE = 0;
	public final static double MAX_CONFIDENCE = 1;
	public final static double PRECISION_MAX = .002;
	public final static double PRECISION_MIN = .002;
	public final static double START_CONFIDENCE = .0;

	public static AVT getNew() {

		AVT nn = new AVTBuilder().lowerBound(MIN_CONFIDENCE).upperBound(MAX_CONFIDENCE).deltaMin(PRECISION_MIN)
				.deltaMax(PRECISION_MAX).deltaIncreaseDelay(2).deltaDecreaseDelay(1).startValue(START_CONFIDENCE)
				.build();
		return nn;
	}

	public static AVT getNewForContactSensor() {

		AVT nn = new AVTBuilder().lowerBound(.0).upperBound(1.).deltaMin(.05)
				.deltaMax(.05).startValue(0.5).build();//.deltaIncreaseDelay(1).deltaDecreaseDelay(1).startValue(0.5).build();
		return nn;
	}

	public static AVT getNewForContactSensor2() {

		AVT nn = new AVTBuilder().lowerBound(.0).upperBound(1.).deltaMin(.02)
				.deltaMax(.03).startValue(0).build();
		return nn;
	}

}
