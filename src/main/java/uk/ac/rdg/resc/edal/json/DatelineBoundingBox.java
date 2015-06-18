package uk.ac.rdg.resc.edal.json;

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

}
