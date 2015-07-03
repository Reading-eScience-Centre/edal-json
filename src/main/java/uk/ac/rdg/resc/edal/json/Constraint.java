package uk.ac.rdg.resc.edal.json;

import java.util.Set;

import org.joda.time.DateTime;

import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.util.Extents;

import com.google.common.collect.Sets;

/**
 * Used for filter and subset constraints.
 *
 */
public final class Constraint {
	
	private DateTime timeStart, timeEnd;
	public Extent<DateTime> timeExtent;
	public DatelineBoundingBox bbox;
	public Extent<Double> longitudeExtent, latitudeExtent;
	private Double verticalStart, verticalEnd;
	public Extent<Double> verticalExtent;
	public Set<String> params;
	
	/**
	 * type is only usable for filtering.
	 */
	public Class<?> type;
	
	/**
	 * verticalTarget is only usable for subsetting.
	 * If given, it restrict the vertical axis to exactly the element
	 * which is closest to verticalTarget.
	 */
	public Double verticalTarget;
	
	public Constraint(String urlParam) {
		if (urlParam == null) urlParam = "";
		String[] parts = urlParam.split(";");
		for (String part : parts) {
			String[] kv = part.split("=");
			if (kv.length != 2) continue;
			String key = kv[0];
			String val = kv[1];
			
			switch (key) {
			case "timeStart": timeStart = DateTime.parse(val); break;
			case "timeEnd": timeEnd = DateTime.parse(val); break;
			case "bbox":
				String[] bb = val.split(",");
				bbox = new DatelineBoundingBox(
						Double.parseDouble(bb[0]),
						Double.parseDouble(bb[1]), 
						Double.parseDouble(bb[2]), 
						Double.parseDouble(bb[3])); break;
			case "verticalStart": verticalStart = Double.parseDouble(val); break;
			case "verticalEnd": verticalEnd = Double.parseDouble(val); break;
			case "verticalTarget": verticalTarget = Double.parseDouble(val); break;
			case "params": params = Sets.newHashSet(val.split(",")); break;
			case "type": type = FeatureTypes.getType(val); break;
			}
		}
		timeExtent = Extents.newExtent(timeStart, timeEnd);
		verticalExtent = Extents.newExtent(verticalStart, verticalEnd);
		if (bbox != null) {
			longitudeExtent = Extents.newExtent(bbox.getWestBoundLongitude(), bbox.getUnwrappedEastBoundLongitude());
			latitudeExtent = Extents.newExtent(bbox.getSouthBoundLatitude(), bbox.getNorthBoundLatitude());
		} else {
			longitudeExtent = Extents.newExtent(null, null);
			latitudeExtent = Extents.newExtent(null, null);
		}
	}
}
