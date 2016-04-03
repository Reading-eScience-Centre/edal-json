package uk.ac.rdg.resc.edal.json;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import org.joda.time.DateTime;
import org.restlet.data.Form;
import org.restlet.data.Parameter;

import com.google.common.collect.Sets;

import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.util.Extents;

/**
 * Base class used for filter and subset constraints.
 *
 */
public abstract class Constraint {
	
	protected static final String TimeStart = "timeStart";
	protected static final String TimeEnd = "timeEnd";
	protected static final String Bbox = "bbox";
	protected static final String VerticalStart = "verticalStart";
	protected static final String VerticalEnd = "verticalEnd";
	protected static final String Params = "params";
	
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
	
	// TODO send Content-Location header with canonical URL (e.g. for repeated parameters or different ordering)
	
	private static DateTime merge (DateTime oldDate, DateTime newDate, boolean isStart) {
		if (oldDate == null) {
			return newDate;
		}
		if (isStart) {
			return newDate.isAfter(oldDate) ? newDate : oldDate;
		} else {
			return newDate.isBefore(oldDate) ? newDate : oldDate;
		}
	}
	
	private static DatelineBoundingBox merge(DatelineBoundingBox oldBbox, DatelineBoundingBox newBbox) {
		if (oldBbox == null) {
			return newBbox;
		}
		return oldBbox.intersect(newBbox);
	}
	
	private static Double merge(Double oldVal, Double newVal, boolean isStart) {
		if (oldVal == null) {
			return newVal;
		}
		if (isStart) {
			return newVal > oldVal ? newVal : oldVal;
		} else {
			return newVal < oldVal ? newVal : oldVal;
		}
	}
	
	public Constraint(Form queryParams, String prefix) {
		this.queryParams = queryParams;
		DateTime timeStart = null, timeEnd = null;
		Double verticalStart = null, verticalEnd = null;
		DatelineBoundingBox bbox = null;
		
		for (Parameter param : queryParams) {
			String name = param.getName();
			String val = param.getValue();
			if (!name.startsWith(prefix)) continue;
			
			name = name.substring(prefix.length());
			if (!prefix.isEmpty()) {
				// TimeStart -> timeStart
				name = name.substring(0, 1).toLowerCase() + name.substring(1);
			}
			
			switch (name) {
			case TimeStart: timeStart = merge(timeStart, DateTime.parse(val), true); break;
			case TimeEnd: timeEnd = merge(timeEnd, DateTime.parse(val), false); break;
			case Bbox:
				String[] bb = val.split(",");
				bbox = merge(bbox, new DatelineBoundingBox(
						Double.parseDouble(bb[0]),
						Double.parseDouble(bb[1]), 
						Double.parseDouble(bb[2]), 
						Double.parseDouble(bb[3]))); break;
			case VerticalStart: verticalStart = merge(verticalStart, Double.parseDouble(val), true); break;
			case VerticalEnd: verticalEnd = merge(verticalEnd, Double.parseDouble(val), false); break;
			case Params: params = Optional.of(Sets.newHashSet(val.split(","))); break;
			}
		}
		timeExtent = Extents.newExtent(timeStart, timeEnd);
		verticalExtent = Extents.newExtent(verticalStart, verticalEnd);
		if (bbox == null) {
			this.bbox = Optional.empty();
			longitudeExtent = Extents.newExtent(null, null);
			latitudeExtent = Extents.newExtent(null, null);
		} else {
			this.bbox = Optional.of(bbox);
			longitudeExtent = Extents.newExtent(bbox.getWestBoundLongitude(), bbox.getUnwrappedEastBoundLongitude());
			latitudeExtent = Extents.newExtent(bbox.getSouthBoundLatitude(), bbox.getNorthBoundLatitude());
		}
		if (params == null) {
			params = Optional.empty();
		}
		
		if (this.bbox.isPresent() || longitudeExtent.getLow() != null || longitudeExtent.getHigh() != null ||
				latitudeExtent.getLow() != null || latitudeExtent.getHigh() != null ||
				timeExtent.getLow() != null || timeExtent.getHigh() != null ||
				verticalExtent.getLow() != null || verticalExtent.getHigh() != null ||
				params.isPresent()) {
			isConstrained = true;
		}
	}
	
	public static String getQueryString(Form... forms) {
		Form all = new Form();
		for (Form form : forms) {
			all.addAll(form);
		}
		String queryString = all.getQueryString();
		return queryString.isEmpty() ? "" : "?" + queryString;
	}
	
	abstract public Form getCanonicalQueryParams();
		
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
