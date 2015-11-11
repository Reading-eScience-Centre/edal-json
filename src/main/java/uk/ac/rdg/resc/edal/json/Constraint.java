package uk.ac.rdg.resc.edal.json;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import org.joda.time.DateTime;
import org.restlet.data.Form;

import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.util.Extents;

import com.google.common.collect.Sets;

/**
 * Base class used for filter and subset constraints.
 *
 */
public abstract class Constraint {
	Form queryParams;
	public Extent<DateTime> timeExtent;
	public Optional<DatelineBoundingBox> bbox;
	public Extent<Double> longitudeExtent, latitudeExtent;
	public Extent<Double> verticalExtent;
	public Optional<Set<String>> params;
	public boolean isConstrained = false;
	
	public Constraint(Form queryParams) {
		this(queryParams, "");
	}
	
	public Constraint(Form queryParams, String prefix) {
		this.queryParams = queryParams;
		DateTime timeStart = null, timeEnd = null;
		Double verticalStart = null, verticalEnd = null;
		
		for (Entry<String,String> kv : queryParams.getValuesMap().entrySet()) {
			if (!kv.getKey().startsWith(prefix)) continue;
			
			String val = kv.getValue();
			String name = kv.getKey().substring(prefix.length());
			if (!prefix.isEmpty()) {
				// TimeStart -> timeStart
				name = name.substring(0, 1).toLowerCase() + name.substring(1);
			}
			
			switch (name) {
			case "timeStart": timeStart = DateTime.parse(val); break;
			case "timeEnd": timeEnd = DateTime.parse(val); break;
			case "bbox":
				String[] bb = val.split(",");
				bbox = Optional.of(new DatelineBoundingBox(
						Double.parseDouble(bb[0]),
						Double.parseDouble(bb[1]), 
						Double.parseDouble(bb[2]), 
						Double.parseDouble(bb[3]))); break;
			case "verticalStart": verticalStart = Double.parseDouble(val); break;
			case "verticalEnd": verticalEnd = Double.parseDouble(val); break;
			case "params": params = Optional.of(Sets.newHashSet(val.split(","))); break;
			}
		}
		timeExtent = Extents.newExtent(timeStart, timeEnd);
		verticalExtent = Extents.newExtent(verticalStart, verticalEnd);
		if (bbox == null) {
			bbox = Optional.empty();
			longitudeExtent = Extents.newExtent(null, null);
			latitudeExtent = Extents.newExtent(null, null);
		} else {
			DatelineBoundingBox bb = bbox.get();
			longitudeExtent = Extents.newExtent(bb.getWestBoundLongitude(), bb.getUnwrappedEastBoundLongitude());
			latitudeExtent = Extents.newExtent(bb.getSouthBoundLatitude(), bb.getNorthBoundLatitude());
		}
		if (params == null) {
			params = Optional.empty();
		}
		
		if (bbox.isPresent() || longitudeExtent.getLow() != null || longitudeExtent.getHigh() != null ||
				latitudeExtent.getLow() != null || latitudeExtent.getHigh() != null ||
				timeExtent.getLow() != null || timeExtent.getHigh() != null ||
				verticalExtent.getLow() != null || verticalExtent.getHigh() != null ||
				params.isPresent()) {
			isConstrained = true;
		}
	}
	
	protected Map<String, String> getParams(String urlParam) {
		Map<String, String> params = new HashMap<>();
		if (urlParam == null) {
			urlParam = "";
		}
		String[] parts = urlParam.split(";");
		for (String part : parts) {
			String[] kv = part.split("=");
			if (kv.length != 2) continue;
			params.put(kv[0], kv[1]);
		}
		return params;
	}
}
