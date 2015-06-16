package uk.ac.rdg.resc.edal.json;

import java.util.Set;

import org.joda.time.DateTime;
import org.opengis.metadata.extent.GeographicBoundingBox;

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
	public GeographicBoundingBox bbox;
	private Double verticalStart, verticalEnd;
	public Extent<Double> verticalExtent;
	public Set<String> params;
	
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
			case "params": params = Sets.newHashSet(val.split(",")); break;
			}
		}
		// FIXME implementation for DateTimeExtent doesn't seem to support open ends
		timeExtent = Extents.newExtent(timeStart, timeEnd);
		verticalExtent = Extents.newExtent(verticalStart, verticalEnd);
	}
}
