package confidenceZone.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;

import javafx.geometry.Point2D;

/**
 * A polygon used to model the {@code ConfidenceZone}
 * 
 * @author Davide A. Guastella <a href=
 *         "mailto:davide.guastella@irit.fr">davide.guastella@irit.fr</a>
 */
public class Polygon {
	private final List<Vector2D> points;
	private Vector2D centroid;

	public static int MIN_WIDTH = 50;
	public static int MAX_WIDTH = 50;

	public static int MIN_HEIGHT = 800;
	public static int MAX_HEIGHT = 800;

	/**
	 * The minimum allowed radius of this polygon
	 */
	private int minRadius;

	/**
	 * Creates an empty polygon.
	 * 
	 * @param sideLength
	 * @param minRadius
	 * 
	 */
	public Polygon(int minRadius) {
		this.minRadius = minRadius;
		points = new ArrayList<>();
	}

	public Polygon(double[] xPoints, double[] yPoints) {
		points = new ArrayList<>();
		for (int i = 0; i < yPoints.length; i++) {
			points.add(new Vector2D(xPoints[i], yPoints[i]));
		}
	}

	/**
	 * Static factory method. It returns a new polygon with n sides of side length
	 * specified.
	 * 
	 * @param sides
	 * @param sideLength
	 * @return
	 */
	public static Polygon getRegularPolygon(int sides, int minRadius, int sideLength) {
		return getRegularPolygon(0, 0, minRadius, sides, sideLength);
	}

	/**
	 * Static factory method. It returns a new polygon with {@code sides} sides of
	 * side length {@code sideLength}. specified.
	 * 
	 * @param sides      the number of sides of this polygon
	 * @param sideLength the inner side length between the polygon and the center
	 * @return
	 */
	public static Polygon getRegularPolygon(double xCenter, double yCenter, int minRadius, int sides, int sideLength) {
		Polygon p = new Polygon(minRadius);
		p.centroid = new Vector2D(xCenter, yCenter);

		double angle = 2 * Math.PI;
		double angleSeparation = angle / sides;
		List<Double> angles = new ArrayList<>();
		double ca = 0;

		while (ca + angleSeparation <= angle) {
			ca += angleSeparation;
			angles.add(ca);
		}

		for (int i = 0; i < sides; i++) {
			double x = xCenter + (sideLength * Math.cos(angles.get(i)));
			double y = yCenter + (sideLength * Math.sin(angles.get(i)));
			p.addPoint(x, y);
		}

		return p;
	}

	/**
	 * Inflate (or deflate) this polygon towards the given point according to a
	 * specified weight. If negative, the polygon is deflated.
	 * 
	 * @param interestPoint
	 * @param weight
	 */
	public void inflateTowardsPoint(Vector2D interestPoint, double weight) {

		for (int i = 0; i < points.size(); i++) {
			Vector2D d = points.get(i);
			Point2D dp = new Point2D(d.getX(), d.getY());
			Point2D vsap = new Point2D(centroid.getX(), centroid.getY());
			Point2D rsap = new Point2D(interestPoint.getX(), interestPoint.getY());

			double a = vsap.angle(dp, rsap);
			if (a >= 60) {
				continue;
			}
			Point2D nnp = rsap.subtract(vsap).normalize();
			double newX = d.getX() + nnp.getX() * Math.signum(weight);
			double newY = d.getY() + nnp.getY() * Math.signum(weight);

			Vector2D newP = new Vector2D(newX, newY);
			if (Vector2D.distance(newP, centroid) >= minRadius) {
				points.set(i, new Vector2D(newX, newY));
			}
		}
	}

	/**
	 * Returns the area of this {@code Polygon}.
	 * 
	 * @return
	 */
	public double area() {
		double sum = 0.0;
		for (int i = 0; i < points.size() - 1; i++) {
			double vX = points.get(i).getX();
			double vX_1 = points.get(i + 1).getX();

			double vY = points.get(i).getY();
			double vY_1 = points.get(i + 1).getY();

			sum = sum + (vX * vY_1) - (vY * vX_1);
		}
		return 0.5 * sum;
	}

	/**
	 * Returns a read-only list containing the points in this {@code Polygon}.
	 * 
	 * @return a list of points
	 */
	public List<Vector2D> getPoints() {
		return Collections.unmodifiableList(this.points);
	}

	/**
	 * Translates the vertices of the {@code Polygon} by {@code deltaX} along the x
	 * axis and by {@code deltaY} along the y axis.
	 * 
	 * @param deltaX the amount to translate along the X axis
	 * @param deltaY the amount to translate along the Y axis
	 * @since 1.1
	 */
	public void translate(double deltaX, double deltaY) {
		centroid = new Vector2D(centroid.getX() + deltaX, centroid.getY() + deltaY);
		for (int i = 0; i < points.size(); i++) {
			points.set(i, new Vector2D(points.get(i).getX() + deltaX, points.get(i).getY() + deltaY));
		}
	}

	/**
	 * Appends the specified coordinates to this {@code Polygon}.
	 * 
	 * @param x the specified X coordinate
	 * @param y the specified Y coordinate
	 */
	public void addPoint(double x, double y) {
		points.add(new Vector2D(x, y));
	}

	public double[] getXPoints() {
		return points.stream().mapToDouble(d -> d.getX()).toArray();
	}

	public double[] getYPoints() {
		return points.stream().mapToDouble(d -> d.getY()).toArray();
	}

	/**
	 * Determines whether the specified coordinates are inside this {@code Polygon}.
	 *
	 * @param x the specified X coordinate to be tested
	 * @param y the specified Y coordinate to be tested
	 * @return {@code true} if this {@code Polygon} contains the specified
	 *         coordinates {@code (x,y)}; {@code false} otherwise.
	 * 
	 * 
	 *         copyright ORACLE, from java.awt.Polygon class
	 */
	public boolean contains(double x, double y) {
		int hits = 0;
		int npoints = points.size();
		double lastx = getXPoints()[npoints - 1];
		double lasty = getYPoints()[npoints - 1];
		double curx, cury;

		// Walk the edges of the polygon
		for (int i = 0; i < npoints; lastx = curx, lasty = cury, i++) {
			curx = getXPoints()[i];
			cury = getYPoints()[i];

			if (cury == lasty) {
				continue;
			}

			double leftx;
			if (curx < lastx) {
				if (x >= lastx) {
					continue;
				}
				leftx = curx;
			} else {
				if (x >= curx) {
					continue;
				}
				leftx = lastx;
			}

			double test1, test2;
			if (cury < lasty) {
				if (y < cury || y >= lasty) {
					continue;
				}
				if (x < leftx) {
					hits++;
					continue;
				}
				test1 = x - curx;
				test2 = y - cury;
			} else {
				if (y < lasty || y >= cury) {
					continue;
				}
				if (x < leftx) {
					hits++;
					continue;
				}
				test1 = x - lastx;
				test2 = y - lasty;
			}

			if (test1 < (test2 / (lasty - cury) * (lastx - curx))) {
				hits++;
			}
		}

		return ((hits & 1) != 0);
	}

	public static Polygon union(Polygon poly1, Polygon poly2) {

		List<Double> xp = new ArrayList<>();
		List<Double> yp = new ArrayList<>();

		for (Double p : poly1.getXPoints()) {
			xp.add(p);
		}

		for (Double p : poly1.getYPoints()) {
			yp.add(p);
		}

		for (Double p : poly2.getXPoints()) {
			xp.add(p);
		}

		for (Double p : poly2.getYPoints()) {
			yp.add(p);
		}

		List<Vector2D> convexHull = GrahamScan.getConvexHull(xp.stream().mapToDouble(x -> x).toArray(),
				yp.stream().mapToDouble(x -> x).toArray());

		double[] xpp = convexHull.stream().mapToDouble(x -> x.getX()).toArray();
		double[] ypp = convexHull.stream().mapToDouble(y -> y.getY()).toArray();

		return new Polygon(xpp, ypp);
	}

	/**
	 * Get the number of points that make up this polygon
	 * 
	 * @return
	 */
	public int getNpoints() {
		return points.size();
	}

	public Vector2D getCentroid() {
		return centroid;
	}

}
