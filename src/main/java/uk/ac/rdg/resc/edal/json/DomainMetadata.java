package uk.ac.rdg.resc.edal.json;

import org.joda.time.DateTime;

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
 * Feature.
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

	public Extent<DateTime> getTimeExtent() {
		return timeExtent;
	}

	public Extent<Double> getVerticalExtent() {
		return verticalExtent;
	}
	
	public VerticalCrs getVerticalCrs() {
		return verticalCrs;
	}

}
