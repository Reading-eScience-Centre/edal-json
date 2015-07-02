package uk.ac.rdg.resc.edal.json;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import org.opengis.metadata.extent.GeographicBoundingBox;

import uk.ac.rdg.resc.edal.geometry.BoundingBox;
import uk.ac.rdg.resc.edal.geometry.BoundingBoxImpl;
import uk.ac.rdg.resc.edal.position.HorizontalPosition;
import uk.ac.rdg.resc.edal.util.GISUtils;

/**
 * Geographic bounding box which supports spanning the dateline.
 * 
 * TODO BoundingBox also supports the dateline however longitudes are always unwrapped there
 *  -> should unify both
 *  
 * @author Maik
 *
 */
public class DatelineBoundingBox implements GeographicBoundingBox {

	private final double lonWest;
	private final double latSouth;
	private final double lonEast;
	private final double latNorth;
	private final boolean containsDiscontinuity;

	/**
	 * Longitudes will be wrapped to [-180,180].
	 */
	public DatelineBoundingBox(double lonWest, double latSouth, double lonEast, double latNorth) {
		assert -90 <= latSouth && latSouth <= latNorth && latNorth <= 90;
		this.lonWest = wrapLongitude(lonWest);
		this.latSouth = latSouth;
		this.lonEast = wrapLongitude(lonEast);
		this.latNorth = latNorth;
		containsDiscontinuity = this.lonWest > this.lonEast;
	}
		
	public DatelineBoundingBox(BoundingBox bb) {
		this(bb.getMinX(), bb.getMinY(), bb.getMaxX(), bb.getMaxY());
		assert GISUtils.isWgs84LonLat(bb.getCoordinateReferenceSystem());
	}

	private static double wrapLongitude(double lon) {
		return lon == 180 ? lon : ((lon + 180) % 360 + 360) % 360 - 180;
	}
	
	/**
	 * If the bounding box spans the -180/180 discontinuity,
	 * then this method returns two bounding boxes within [-180,180]
	 * that do not span the discontinuity. Otherwise,
	 * only this bounding box is returned.
	 */
	public List<GeographicBoundingBox> getSplitBoxes() {
		List<GeographicBoundingBox> res = new LinkedList<>();
		if (!containsDiscontinuity) {
			res.add(this);
		} else {
			res.add(new DatelineBoundingBox(lonWest, latSouth, 180, latNorth));
			res.add(new DatelineBoundingBox(-180, latSouth, lonEast, latNorth));
		}
		return res;
	}
	
	@Override
	public Boolean getInclusion() {
		return true;
	}

	@Override
	public double getWestBoundLongitude() {
		return lonWest;
	}

	@Override
	public double getEastBoundLongitude() {
		return lonEast;
	}
	
	public double getUnwrappedEastBoundLongitude() {
		return lonEast < lonWest ? lonEast + 360 : lonEast;
	}

	@Override
	public double getSouthBoundLatitude() {
		return latSouth;
	}

	@Override
	public double getNorthBoundLatitude() {
		return latNorth;
	}
	
	public boolean intersects(GeographicBoundingBox other) {
		List<GeographicBoundingBox> bboxes = getSplitBoxes();
		if (other instanceof DatelineBoundingBox) {
			bboxes.addAll(((DatelineBoundingBox) other).getSplitBoxes());
		} else {
			bboxes.add(other);
		}
		for (GeographicBoundingBox b1 : bboxes) {
			for (GeographicBoundingBox b2 : bboxes) {
				if (b1 == b2) continue;
				if (b1.getWestBoundLongitude() < b2.getEastBoundLongitude() &&
				    b1.getEastBoundLongitude() > b2.getWestBoundLongitude() &&
				    b1.getSouthBoundLatitude() < b2.getNorthBoundLatitude() &&
				    b1.getNorthBoundLatitude() > b2.getSouthBoundLatitude()) {
					return true;
				}
			}
		}
		return false;
	}
	
	public boolean contains(HorizontalPosition pos) {
		BoundingBox bb = new BoundingBoxImpl(pos.getX(), pos.getY(), pos.getX(),
				pos.getY(), pos.getCoordinateReferenceSystem());
		DatelineBoundingBox bb1 = new DatelineBoundingBox(bb);
		return intersects(bb1);
	}
	
	/**
	 * Return the smallest bounding box that contains all given bounding boxes.
	 * Note that this is not simply the min/max of longitude!
	 * 
	 * @see https://github.com/esa/auromat/blob/master/auromat/mapping/mapping.py#L232
	 * @see http://gis.stackexchange.com/q/17788
	 */
	public static DatelineBoundingBox smallestBoundingBox(Collection<DatelineBoundingBox> bbs) {
		double latSouth = bbs.stream().mapToDouble(bb -> bb.getSouthBoundLatitude()).min().getAsDouble();
		double latNorth = bbs.stream().mapToDouble(bb -> bb.getNorthBoundLatitude()).max().getAsDouble();
		
		// TODO write unit tests
		
		// longitude parts of bounding boxes
		List<Double> lons = new ArrayList<>(bbs.size()*2); // will be bigger in case of discontinuity
		for (DatelineBoundingBox bb : bbs) {
			if (bb.containsDiscontinuity) {
				// add both representations so that later checking is easier
				lons.add(bb.getWestBoundLongitude());
				lons.add(bb.getUnwrappedEastBoundLongitude());
				lons.add(bb.getWestBoundLongitude() - 360);
				lons.add(bb.getEastBoundLongitude());
			} else {
				lons.add(bb.getWestBoundLongitude());
				lons.add(bb.getEastBoundLongitude());
			}
		}
		
		// sorted intervals
		List<Double> xs = new ArrayList<>(lons.size()+1);
		for (DatelineBoundingBox bb : bbs) {
			xs.add(bb.getWestBoundLongitude());
			xs.add(bb.getEastBoundLongitude());
		}
		Collections.sort(xs);
		xs.add(xs.get(0) + 360);
		
		// whether an interval is covered by any bounding box
		boolean[] isCovered = new boolean[xs.size()-1];
		for (int i=1; i < xs.size(); i++) {
			for (int j=0; j < lons.size(); j += 2) {
				if (lons.get(j) <= xs.get(i-1) && xs.get(i) <= lons.get(j+1)) {
					isCovered[i-1] = true;
					break;
				}
			}
		}
		
		// find index of biggest gap
		double maxGapLength = 0;
		int biggestGapIdx = -1;
		for (int i=0; i < xs.size()-1; i++) {
			if (isCovered[i]) continue;
			double gapLength = xs.get(i+1) - xs.get(i);
			if (gapLength > maxGapLength) {
				maxGapLength = gapLength;
				biggestGapIdx = i;
			}
		}
		
		double lonWest = wrapLongitude(xs.get(biggestGapIdx+1));
		double lonEast = wrapLongitude(xs.get(biggestGapIdx));
		
		return new DatelineBoundingBox(lonWest, latSouth, lonEast, latNorth);
	}

}
