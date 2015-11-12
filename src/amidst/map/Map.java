package amidst.map;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;

import amidst.map.layers.BiomeLayer;

public class Map {
	public static Map instance = null;
	private static final boolean START = true;
	private static final boolean END = false;
	private FragmentManager fragmentManager;

	private Fragment startNode = new Fragment();

	private double scale = 0.25;
	private Point2D.Double start = new Point2D.Double();

	public int tileWidth;
	public int tileHeight;
	public int width = 1;
	public int height = 1;

	private final Object resizeLock = new Object();
	private final Object drawLock = new Object();
	private AffineTransform mat = new AffineTransform();

	private boolean firstDraw = true;

	public Map(FragmentManager fragmentManager) {
		this.fragmentManager = fragmentManager;
		fragmentManager.setMap(this);

		addStart(0, 0);

		instance = this;
	}

	public void resetImageLayer(int id) {
		Fragment frag = startNode;
		while (frag.hasNext()) {
			frag = frag.getNext();
			fragmentManager.repaintFragmentLayer(frag, id);
		}
	}

	public void resetFragments() {
		Fragment frag = startNode;
		while (frag.hasNext()) {
			frag = frag.getNext();
			fragmentManager.repaintFragment(frag);
		}
	}

	public void draw(Graphics2D g, float time) {
		AffineTransform originalTransform = g.getTransform();
		if (firstDraw) {
			firstDraw = false;
			centerOn(0, 0);
		}

		synchronized (drawLock) {
			int size = (int) (Fragment.SIZE * scale);
			int w = width / size + 2;
			int h = height / size + 2;

			while (tileWidth < w) {
				addColumn(END);
			}
			while (tileWidth > w) {
				removeColumn(END);
			}
			while (tileHeight < h) {
				addRow(END);
			}
			while (tileHeight > h) {
				removeRow(END);
			}

			while (start.x > 0) {
				start.x -= size;
				addColumn(START);
				removeColumn(END);
			}
			while (start.x < -size) {
				start.x += size;
				addColumn(END);
				removeColumn(START);
			}
			while (start.y > 0) {
				start.y -= size;
				addRow(START);
				removeRow(END);
			}
			while (start.y < -size) {
				start.y += size;
				addRow(END);
				removeRow(START);
			}

			size = Fragment.SIZE;

			Fragment fragment = startNode;
			if (fragment.hasNext()) {
				initMat(originalTransform);
				while (fragment.hasNext()) {
					fragment = fragment.getNext();
					fragment.drawImageLayers(time, g, mat);
					mat.translate(size, 0);
					if (fragment.isEndOfLine()) {
						mat.translate(-size * w, size);
					}
				}
			}

			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
					RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			fragmentManager.updateAllLayers(time);

			fragment = startNode;
			if (fragment.hasNext()) {
				initMat(originalTransform);
				while (fragment.hasNext()) {
					fragment = fragment.getNext();
					fragment.drawLiveLayers(time, g, mat);
					mat.translate(size, 0);
					if (fragment.isEndOfLine()) {
						mat.translate(-size * w, size);
					}
				}
			}

			fragment = startNode;
			if (fragment.hasNext()) {
				initMat(originalTransform);
				while (fragment.hasNext()) {
					fragment = fragment.getNext();
					fragment.drawObjects(g, mat);
					mat.translate(size, 0);
					if (fragment.isEndOfLine()) {
						mat.translate(-size * w, size);
					}
				}
			}

			g.setTransform(originalTransform);
		}
	}

	private void initMat(AffineTransform originalTransform) {
		mat.setToIdentity();
		mat.concatenate(originalTransform);
		mat.translate(start.x, start.y);
		mat.scale(scale, scale);
	}

	public void addStart(int x, int y) {
		synchronized (resizeLock) {
			Fragment start = fragmentManager.requestFragment(x, y);
			start.setEndOfLine(true);
			startNode.setNext(start);
			tileWidth = 1;
			tileHeight = 1;
		}
	}

	public void addColumn(boolean start) {
		synchronized (resizeLock) {
			int x = 0;
			Fragment fragment = startNode;
			if (start) {
				x = fragment.getNext().getBlockX() - Fragment.SIZE;
				Fragment newFrag = fragmentManager.requestFragment(x, fragment
						.getNext().getBlockY());
				newFrag.setNext(startNode.getNext());
				startNode.setNext(newFrag);
			}
			while (fragment.hasNext()) {
				fragment = fragment.getNext();
				if (fragment.isEndOfLine()) {
					if (start) {
						if (fragment.hasNext()) {
							Fragment newFrag = fragmentManager.requestFragment(
									x, fragment.getBlockY() + Fragment.SIZE);
							newFrag.setNext(fragment.getNext());
							fragment.setNext(newFrag);
							fragment = newFrag;
						}
					} else {
						Fragment newFrag = fragmentManager.requestFragment(
								fragment.getBlockX() + Fragment.SIZE,
								fragment.getBlockY());

						if (fragment.hasNext()) {
							newFrag.setNext(fragment.getNext());
						}
						newFrag.setEndOfLine(true);
						fragment.setEndOfLine(false);
						fragment.setNext(newFrag);
						fragment = newFrag;
					}
				}
			}
			tileWidth++;
		}
	}

	public void removeRow(boolean start) {
		synchronized (resizeLock) {
			if (start) {
				for (int i = 0; i < tileWidth; i++) {
					Fragment frag = startNode.getNext();
					frag.remove();
					fragmentManager.recycleFragment(frag);
				}
			} else {
				Fragment fragment = startNode;
				while (fragment.hasNext()) {
					fragment = fragment.getNext();
				}
				for (int i = 0; i < tileWidth; i++) {
					fragment.remove();
					fragmentManager.recycleFragment(fragment);
					fragment = fragment.getPrevious();
				}
			}
			tileHeight--;
		}
	}

	public void addRow(boolean start) {
		synchronized (resizeLock) {
			Fragment fragment = startNode;
			int y;
			if (start) {
				fragment = startNode.getNext();
				y = fragment.getBlockY() - Fragment.SIZE;
			} else {
				while (fragment.hasNext()) {
					fragment = fragment.getNext();
				}
				y = fragment.getBlockY() + Fragment.SIZE;
			}

			tileHeight++;
			Fragment newFrag = fragmentManager.requestFragment(startNode
					.getNext().getBlockX(), y);
			Fragment chainFrag = newFrag;
			for (int i = 1; i < tileWidth; i++) {
				Fragment tempFrag = fragmentManager.requestFragment(
						chainFrag.getBlockX() + Fragment.SIZE,
						chainFrag.getBlockY());
				chainFrag.setNext(tempFrag);
				chainFrag = tempFrag;
				if (i == (tileWidth - 1)) {
					chainFrag.setEndOfLine(true);
				}
			}
			if (start) {
				chainFrag.setNext(fragment);
				startNode.setNext(newFrag);
			} else {
				fragment.setNext(newFrag);
			}
		}
	}

	public void removeColumn(boolean start) {
		synchronized (resizeLock) {
			Fragment fragment = startNode;
			if (start) {
				fragmentManager.recycleFragment(fragment.getNext());
				startNode.getNext().remove();
			}
			while (fragment.hasNext()) {
				fragment = fragment.getNext();
				if (fragment.isEndOfLine()) {
					if (start) {
						if (fragment.hasNext()) {
							Fragment tempFrag = fragment.getNext();
							tempFrag.remove();
							fragmentManager.recycleFragment(tempFrag);
						}
					} else {
						fragment.getPrevious().setEndOfLine(true);
						fragment.remove();
						fragmentManager.recycleFragment(fragment);
						fragment = fragment.getPrevious();
					}
				}
			}
			tileWidth--;
		}
	}

	public void moveBy(Point2D.Double speed) {
		moveBy(speed.x, speed.y);
	}

	public void moveBy(double x, double y) {
		start.x += x;
		start.y += y;
	}

	public void centerOn(long x, long y) {
		long fragOffsetX = x % Fragment.SIZE;
		long fragOffsetY = y % Fragment.SIZE;
		long startX = x - fragOffsetX;
		long startY = y - fragOffsetY;
		synchronized (drawLock) {
			while (tileHeight > 1) {
				removeRow(false);
			}
			while (tileWidth > 1) {
				removeColumn(false);
			}
			Fragment frag = startNode.getNext();
			frag.remove();
			fragmentManager.recycleFragment(frag);
			// TODO: Support longs?
			double offsetX = width >> 1;
			double offsetY = height >> 1;

			offsetX -= (fragOffsetX) * scale;
			offsetY -= (fragOffsetY) * scale;

			start.x = offsetX;
			start.y = offsetY;

			addStart((int) startX, (int) startY);
		}
	}

	public void setZoom(double scale) {
		this.scale = scale;
	}

	public double getZoom() {
		return scale;
	}

	public Point2D.Double getScaled(double oldScale, double newScale, Point p) {
		double baseX = p.x - start.x;
		double scaledX = baseX - (baseX / oldScale) * newScale;

		double baseY = p.y - start.y;
		double scaledY = baseY - (baseY / oldScale) * newScale;

		return new Point2D.Double(scaledX, scaledY);
	}

	public void dispose() {
		synchronized (drawLock) {
			fragmentManager.reset();
		}
	}

	public Fragment getFragmentAt(Point position) {
		Fragment frag = startNode;
		Point cornerPosition = new Point(position.x >> Fragment.SIZE_SHIFT,
				position.y >> Fragment.SIZE_SHIFT);
		Point fragmentPosition = new Point();
		while (frag.hasNext()) {
			frag = frag.getNext();
			fragmentPosition.x = frag.getFragmentX();
			fragmentPosition.y = frag.getFragmentY();
			if (cornerPosition.equals(fragmentPosition))
				return frag;
		}
		return null;
	}

	public MapObject getObjectAt(Point position, double maxRange) {
		double x = start.x;
		double y = start.y;
		MapObject closestObject = null;
		double closestDistance = maxRange;
		Fragment frag = startNode;
		int size = (int) (Fragment.SIZE * scale);
		while (frag.hasNext()) {
			frag = frag.getNext();
			for (int i = 0; i < frag.getObjectsLength(); i++) {
				if (frag.getObjects()[i].parentLayer.isVisible()) {
					Point objPosition = frag.getObjects()[i].getLocation();
					objPosition.x *= scale;
					objPosition.y *= scale;
					objPosition.x += x;
					objPosition.y += y;

					double distance = objPosition.distance(position);
					if (distance < closestDistance) {
						closestDistance = distance;
						closestObject = frag.getObjects()[i];
					}
				}
			}
			x += size;
			if (frag.isEndOfLine()) {
				x = start.x;
				y += size;
			}
		}
		return closestObject;
	}

	public Point screenToLocal(Point inPoint) {
		Point point = inPoint.getLocation();

		point.x -= start.x;
		point.y -= start.y;

		// TODO: int -> double -> int = bad?
		point.x /= scale;
		point.y /= scale;

		point.x += startNode.getNext().getBlockX();
		point.y += startNode.getNext().getBlockY();

		return point;
	}

	public String getBiomeNameAt(Point point) {
		Fragment frag = startNode;
		while (frag.hasNext()) {
			frag = frag.getNext();
			if ((frag.getBlockX() <= point.x) && (frag.getBlockY() <= point.y)
					&& (frag.getBlockX() + Fragment.SIZE > point.x)
					&& (frag.getBlockY() + Fragment.SIZE > point.y)) {
				int x = point.x - frag.getBlockX();
				int y = point.y - frag.getBlockY();

				return BiomeLayer.getBiomeNameForFragment(frag, x, y);
			}
		}
		return "Unknown";
	}

	public String getBiomeAliasAt(Point point) {
		Fragment frag = startNode;
		while (frag.hasNext()) {
			frag = frag.getNext();
			if ((frag.getBlockX() <= point.x) && (frag.getBlockY() <= point.y)
					&& (frag.getBlockX() + Fragment.SIZE > point.x)
					&& (frag.getBlockY() + Fragment.SIZE > point.y)) {
				int x = point.x - frag.getBlockX();
				int y = point.y - frag.getBlockY();

				return BiomeLayer.getBiomeAliasForFragment(frag, x, y);
			}
		}
		return "Unknown";
	}

}
