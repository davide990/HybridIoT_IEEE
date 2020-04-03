package confidenceZone;

import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;

import confidenceZone.geometry.Polygon;

/**
 * Confidence zone of a Virtual Sensor Agent class
 * 
 * @author Davide A. Guastella <a href=
 *         "mailto:davide.guastella@irit.fr">davide.guastella@irit.fr</a>
 */
public class ConfidenceZone {

	/**
	 * This value is set to true if the shape of the confidence zones must not be
	 * updated, false otherwise.
	 */
	public static boolean LOCK_CONFIDENCE_ZONE = false;

	/**
	 * The default number of sides that make up a polygon
	 */
	private static final int NUM_POLYGON_SIDES = 8;

	/**
	 * The minimum radius of the related polygon. Polygon points must have at least
	 * this value of distance from the center of the polygon
	 */
	private static final int MIN_RADIUS = 50;

	// for Emilia Romagna
	// private static final int POLYGON_SIDE_LENGTH = 500;

	// private static final int POLYGON_SIDE_LENGTH = 800;

	// private static final int POLYGON_SIDE_LENGTH = 280;

	// For neOCampus
	//private static final int POLYGON_SIDE_LENGTH = 500;// 1200;
	private static final int POLYGON_SIDE_LENGTH = 250;
	/**
	 * the polygon that describes this confidence zone
	 */
	private final Polygon polygon;

	/**
	 * Create a confidence zone centered in the specified position.
	 * 
	 * @param position
	 */
	public ConfidenceZone(Vector2D position) {
		// Create a new polygon
		polygon = Polygon.getRegularPolygon(position.getX(), position.getY(), MIN_RADIUS, NUM_POLYGON_SIDES,
				POLYGON_SIDE_LENGTH);
	}

	/**
	 * Translate the entire confidence by a specified delta
	 * 
	 * @param deltaX
	 * @param deltaY
	 */
	public void translate(Double deltaX, Double deltaY) {
		polygon.translate(deltaX, deltaY);
	}

	/**
	 * Return the area of this polygon.
	 * 
	 * @return
	 */
	public double area() {
		return polygon.area();
	}

	/**
	 * Update this confidence zone according to a given RSA which has to be included
	 * or excluded. The points of the polygon are moved by a certain weight given in
	 * input
	 * 
	 * @param agentPosition
	 * @param weight
	 * @param include
	 */
	public void update(Vector2D agentPosition, double weight, boolean include) {
		if (LOCK_CONFIDENCE_ZONE) {
			return;
		}

		weight = include ? weight : -weight;
		polygon.inflateTowardsPoint(agentPosition, weight);
	}

	/**
	 * Return the minimum distance between a point p in input and this confidence
	 * zone
	 * 
	 * @param p
	 * @return
	 */
	public double minDistance(Vector2D p) {
		return polygon.getPoints().stream().mapToDouble(p1 -> Vector2D.distance(p, p1)).sorted().findFirst()
				.getAsDouble();
	}

	/**
	 * Return {@code true} if this polygon contains the specified point,
	 * {@code false} otherwise.
	 * 
	 * @param x
	 * @param y
	 * @return
	 */
	public boolean contains(double x, double y) {
		return polygon.contains(x, y);
	}

	public Vector2D centroid() {
		return polygon.getCentroid();
	}

	public boolean contains(Vector2D pos) {
		return polygon.contains(pos.getX(), pos.getY());
	}

	public double[] getXPoints() {
		return polygon.getXPoints();
	}

	public double[] getYPoints() {
		return polygon.getYPoints();
	}

	public Polygon getPolygon() {
		return polygon;
	}

}
