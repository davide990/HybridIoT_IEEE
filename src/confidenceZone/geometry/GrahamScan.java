package confidenceZone.geometry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;

import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;

/**
 *
 */
public final class GrahamScan {

	/**
	 * An enum denoting a directional-turn between 3 points (vectors).
	 */
	protected static enum Turn {
		CLOCKWISE, COUNTER_CLOCKWISE, COLLINEAR
	}

	/**
	 * Returns true iff all points in <code>points</code> are collinear.
	 *
	 * @param points the list of points.
	 * @return true iff all points in <code>points</code> are collinear.
	 */
	protected static boolean areAllCollinear(List<Vector2D> points) {

		if (points.size() < 2) {
			return true;
		}

		final Vector2D a = points.get(0);
		final Vector2D b = points.get(1);

		for (int i = 2; i < points.size(); i++) {

			Vector2D c = points.get(i);

			if (getTurn(a, b, c) != Turn.COLLINEAR) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Returns the convex hull of the points created from <code>xs</code> and
	 * <code>ys</code>. Note that the first and last point in the returned
	 * <code>List&lt;java.awt.Vector2D&gt;</code> are the same point.
	 *
	 * @param xs the x coordinates.
	 * @param ys the y coordinates.
	 * @return the convex hull of the points created from <code>xs</code> and
	 *         <code>ys</code>.
	 * @throws IllegalArgumentException if <code>xs</code> and <code>ys</code> don't
	 *                                  have the same size, if all points are
	 *                                  collinear or if there are less than 3 unique
	 *                                  points present.
	 */
	public static List<Vector2D> getConvexHull(double[] xs, double[] ys) throws IllegalArgumentException {

		if (xs.length != ys.length) {
			throw new IllegalArgumentException("xs and ys don't have the same size");
		}

		List<Vector2D> points = new ArrayList<Vector2D>();

		for (int i = 0; i < xs.length; i++) {
			points.add(new Vector2D(xs[i], ys[i]));
		}

		return getConvexHull(points);
	}

	/**
	 * Returns the convex hull of the points created from the list
	 * <code>points</code>. Note that the first and last point in the returned
	 * <code>List&lt;java.awt.Vector2D&gt;</code> are the same point.
	 *
	 * @param points the list of points.
	 * @return the convex hull of the points created from the list
	 *         <code>points</code>.
	 * @throws IllegalArgumentException if all points are collinear or if there are
	 *                                  less than 3 unique points present.
	 */
	public static List<Vector2D> getConvexHull(List<Vector2D> points) throws IllegalArgumentException {

		List<Vector2D> sorted = new ArrayList<Vector2D>(getSortedPointSet(points));

		if (sorted.size() < 3) {
			throw new IllegalArgumentException("can only create a convex hull of 3 or more unique points");
		}

		if (areAllCollinear(sorted)) {
			throw new IllegalArgumentException("cannot create a convex hull from collinear points");
		}

		Stack<Vector2D> stack = new Stack<Vector2D>();
		stack.push(sorted.get(0));
		stack.push(sorted.get(1));

		for (int i = 2; i < sorted.size(); i++) {

			Vector2D head = sorted.get(i);
			Vector2D middle = stack.pop();
			Vector2D tail = stack.peek();

			Turn turn = getTurn(tail, middle, head);

			switch (turn) {
			case COUNTER_CLOCKWISE:
				stack.push(middle);
				stack.push(head);
				break;
			case CLOCKWISE:
				i--;
				break;
			case COLLINEAR:
				stack.push(head);
				break;
			}
		}

		// close the hull
		stack.push(sorted.get(0));

		return new ArrayList<>(stack);
	}

	/**
	 * Returns the points with the lowest y coordinate. In case more than 1 such
	 * point exists, the one with the lowest x coordinate is returned.
	 *
	 * @param points the list of points to return the lowest point from.
	 * @return the points with the lowest y coordinate. In case more than 1 such
	 *         point exists, the one with the lowest x coordinate is returned.
	 */
	protected static Vector2D getLowestPoint(List<Vector2D> points) {

		Vector2D lowest = points.get(0);

		for (int i = 1; i < points.size(); i++) {

			Vector2D temp = points.get(i);

			if (temp.getY() < lowest.getY() || (temp.getY() == lowest.getY() && temp.getX() < lowest.getX())) {
				lowest = temp;
			}
		}

		return lowest;
	}

	/**
	 * Returns a sorted set of points from the list <code>points</code>. The set of
	 * points are sorted in increasing order of the angle they and the lowest point
	 * <tt>P</tt> make with the x-axis. If tow (or more) points form the same angle
	 * towards <tt>P</tt>, the one closest to <tt>P</tt> comes first.
	 *
	 * @param points the list of points to sort.
	 * @return a sorted set of points from the list <code>points</code>.
	 * @see GrahamScan#getLowestPoint(java.util.List)
	 */
	protected static Set<Vector2D> getSortedPointSet(List<Vector2D> points) {

		final Vector2D lowest = getLowestPoint(points);

		TreeSet<Vector2D> set = new TreeSet<Vector2D>(new Comparator<Vector2D>() {
			@Override
			public int compare(Vector2D a, Vector2D b) {

				if (a == b || a.equals(b)) {
					return 0;
				}

				// use longs to guard against int-underflow
				double thetaA = Math.atan2((long) a.getY() - lowest.getY(), (long) a.getX() - lowest.getX());
				double thetaB = Math.atan2((long) b.getY() - lowest.getY(), (long) b.getX() - lowest.getX());

				if (thetaA < thetaB) {
					return -1;
				} else if (thetaA > thetaB) {
					return 1;
				} else {
					// collinear with the 'lowest' point, let the point closest to it come first

					// use longs to guard against int-over/underflow
					double distanceA = Math.sqrt((((long) lowest.getX() - a.getX()) * ((long) lowest.getX() - a.getX()))
							+ (((long) lowest.getY() - a.getY()) * ((long) lowest.getY() - a.getY())));
					double distanceB = Math.sqrt((((long) lowest.getX() - b.getX()) * ((long) lowest.getX() - b.getX()))
							+ (((long) lowest.getY() - b.getY()) * ((long) lowest.getY() - b.getY())));

					if (distanceA < distanceB) {
						return -1;
					} else {
						return 1;
					}
				}
			}
		});

		set.addAll(points);

		return set;
	}

	/**
	 * Returns the GrahamScan#Turn formed by traversing through the ordered points
	 * <code>a</code>, <code>b</code> and <code>c</code>. More specifically, the
	 * cross product <tt>C</tt> between the 3 points (vectors) is calculated:
	 *
	 * <tt>(b.getX()-a.getX() * c.getY()-a.getY()) - (b.getY()-a.getY() * c.getX()-a.getX())</tt>
	 *
	 * and if <tt>C</tt> is less than 0, the turn is CLOCKWISE, if <tt>C</tt> is
	 * more than 0, the turn is COUNTER_CLOCKWISE, else the three points are
	 * COLLINEAR.
	 *
	 * @param a the starting point.
	 * @param b the second point.
	 * @param c the end point.
	 * @return the GrahamScan#Turn formed by traversing through the ordered points
	 *         <code>a</code>, <code>b</code> and <code>c</code>.
	 */
	protected static Turn getTurn(Vector2D a, Vector2D b, Vector2D c) {

		// use longs to guard against int-over/underflow
		double crossProduct = (((long) b.getX() - a.getX()) * ((long) c.getY() - a.getY()))
				- (((long) b.getY() - a.getY()) * ((long) c.getX() - a.getX()));

		if (crossProduct > 0) {
			return Turn.COUNTER_CLOCKWISE;
		} else if (crossProduct < 0) {
			return Turn.CLOCKWISE;
		} else {
			return Turn.COLLINEAR;
		}
	}
}