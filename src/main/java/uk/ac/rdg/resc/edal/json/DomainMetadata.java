package uk.ac.rdg.resc.edal.json;

import java.util.Collection;
import java.util.List;

import org.geotoolkit.referencing.crs.DefaultGeographicCRS;
import org.joda.time.DateTime;
import org.opengis.metadata.extent.GeographicBoundingBox;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.domain.GridDomain;
import uk.ac.rdg.resc.edal.feature.Feature;
import uk.ac.rdg.resc.edal.feature.GridFeature;
import uk.ac.rdg.resc.edal.feature.ProfileFeature;
import uk.ac.rdg.resc.edal.geometry.BoundingBox;
import uk.ac.rdg.resc.edal.geometry.BoundingBoxImpl;
import uk.ac.rdg.resc.edal.position.HorizontalPosition;
import uk.ac.rdg.resc.edal.position.VerticalCrs;
import uk.ac.rdg.resc.edal.util.Extents;

/**
 * Derives metadata for horizontal, time and vertical axes from a given
 * Feature or the whole Dataset.
 * 
 * @author Maik
 *
 */
public class DomainMetadata {
	private BoundingBox bbox;
	private Extent<DateTime> timeExtent;
	private Extent<Double> verticalExtent;
	private VerticalCrs verticalCrs;

	public DomainMetadata(Feature<?> feature) {
		extractMetadata(feature);
	}
	
	/**
	 * Merge all metadatas into a new combined metadata object.
	 * This only works when the CRS of each axis is identical for all given objects.
	 *  
	 * @param metadatas
	 */
	public DomainMetadata(Collection<DomainMetadata> metadatas) {
		unionMetadata(metadatas);		
	}
	
	private void unionMetadata(Collection<DomainMetadata> metadatas) {
		DomainMetadata first = metadatas.iterator().next();
		DateTime timeStart = null;
		DateTime timeEnd = null;
		Double verticalMin = null;
		Double verticalMax = null;
		verticalCrs = first.verticalCrs;
		
		for (DomainMetadata meta : metadatas) {
			assert verticalCrs.equals(meta.verticalCrs);
			
			// TODO do we have to check for equal calendars here?
			if (meta.timeExtent != null) {
				if (timeStart == null) {
					timeStart = meta.timeExtent.getLow();
					timeEnd = meta.timeExtent.getHigh();
				} else {
					if (meta.timeExtent.getLow().isBefore(timeStart)) {
						timeStart = meta.timeExtent.getLow();
					}
					if (meta.timeExtent.getHigh().isAfter(timeEnd)) {
						timeEnd = meta.timeExtent.getHigh();
					}
				}
			}
			
			if (meta.verticalExtent != null) {
				if (verticalMin == null) {
					verticalMin = meta.verticalExtent.getLow();
					verticalMax = meta.verticalExtent.getHigh();
				} else {
					if (meta.verticalExtent.getLow() < verticalMin) {
						verticalMin = meta.verticalExtent.getLow();
					}
					if (meta.verticalExtent.getHigh() > verticalMax) {
						verticalMax = meta.verticalExtent.getHigh();
					}
				}
			}	
		}
		
		// TODO works for CRS84 only
		List<DatelineBoundingBox> geoBboxes = Utils.mapList(metadatas, m -> new DatelineBoundingBox(m.bbox));
		DatelineBoundingBox geoBbox = DatelineBoundingBox.smallestBoundingBox(geoBboxes);
		bbox = new BoundingBoxImpl(geoBbox.getWestBoundLongitude(), geoBbox.getSouthBoundLatitude(), 
				geoBbox.getUnwrappedEastBoundLongitude(), geoBbox.getNorthBoundLatitude(),
				DefaultGeographicCRS.WGS84);
		
		if (timeStart != null) {
			timeExtent = Extents.newExtent(timeStart, timeEnd);
		}
		if (verticalMin != null) {
			verticalExtent = Extents.newExtent(verticalMin, verticalMax);
		}		
	}

	private void extractMetadata(Feature<?> feature) {
		if (feature instanceof GridFeature) {
			GridFeature feat = (GridFeature) feature;
			GridDomain domain = feat.getDomain();
			bbox = domain.getHorizontalGrid().getBoundingBox();
			timeExtent = domain.getTimeAxis() == null ? null : 
				         domain.getTimeAxis().getCoordinateExtent();
			verticalExtent = domain.getVerticalAxis() == null ? null : 
				             domain.getVerticalAxis().getCoordinateExtent();
			verticalCrs = domain.getVerticalAxis() == null ? null :
				          domain.getVerticalAxis().getVerticalCrs();
		} else if (feature instanceof ProfileFeature) {
			ProfileFeature feat = (ProfileFeature) feature;
			HorizontalPosition pos = feat.getHorizontalPosition();
			bbox = new BoundingBoxImpl(pos.getX(), pos.getY(), pos.getX(),
					pos.getY(), pos.getCoordinateReferenceSystem());
			timeExtent = feat.getTime() == null ? null :
		         Extents.newExtent(feat.getTime(), feat.getTime());
			verticalExtent = feat.getDomain().getCoordinateExtent();
			verticalCrs = feat.getDomain().getVerticalCrs();
		} else {
			throw new UnsupportedOperationException("implement me, "
					+ feature.getClass().toString());
		}
	}

	public BoundingBox getBoundingBox() {
		return bbox;
	}

	/**
	 * 
	 * @return null if no time axis
	 */
	public Extent<DateTime> getTimeExtent() {
		return timeExtent;
	}

	/**
	 * 
	 * @return null if no vertical axis
	 */
	public Extent<Double> getVerticalExtent() {
		return verticalExtent;
	}
	
	public VerticalCrs getVerticalCrs() {
		return verticalCrs;
	}

}
