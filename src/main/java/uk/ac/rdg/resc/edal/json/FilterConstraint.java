package uk.ac.rdg.resc.edal.json;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import org.restlet.data.Form;


public class FilterConstraint extends Constraint {

	public Optional<Class<?>> type;
	
	public FilterConstraint(Form queryParams) {
		this(queryParams, null);
	}
	
	/**
	 * Uses the given SubsetConstraint as filter constraints
	 * in cases where a particular filter field is not specified.
	 * 
	 * @param urlParam
	 * @param subsetConstraint 
	 */
	public FilterConstraint(Form queryParams, SubsetConstraint subsetConstraint) {
		super(queryParams);
		
		String val = null; //getParams(queryParams).get("type");
		type = val == null ? Optional.empty() : Optional.of(FeatureTypes.getType(val));
		
		if (subsetConstraint == null) {
			return;
		}
		if (!params.isPresent()) {
			params = subsetConstraint.params;
		}
		if (!bbox.isPresent()) {
			bbox = subsetConstraint.bbox;
			longitudeExtent = subsetConstraint.longitudeExtent;
			latitudeExtent = subsetConstraint.latitudeExtent;
		}
		if (timeExtent.getLow() == null && timeExtent.getHigh() == null) {
			timeExtent = subsetConstraint.timeExtent;
		}
		if (verticalExtent.getLow() == null && verticalExtent.getHigh() == null) {
			verticalExtent = subsetConstraint.verticalExtent;
		}
	}
	
	public Form getCanonicalQueryParams() {
		Form form = new Form();
		
		if (timeExtent.getLow() != null) {
			String time = timeExtent.getLow().toString();
			form.add(TimeStart, time);
		}
		if (timeExtent.getHigh() != null) {
			String time = timeExtent.getHigh().toString();
			form.add(TimeEnd, time);
		}
		if (bbox.isPresent()) {
			DatelineBoundingBox bb = bbox.get();
			String box = bb.getWestBoundLongitude() + "," + bb.getSouthBoundLatitude() + "," + bb.getEastBoundLongitude() + "," +
			     bb.getNorthBoundLatitude();
			form.add(Bbox, box);
		}
		if (verticalExtent.getLow() != null) {
			String val = verticalExtent.getLow().toString();
			form.add(VerticalStart, val);
		}
		if (verticalExtent.getHigh() != null) {
			String val = verticalExtent.getHigh().toString();
			form.add(VerticalEnd, val);
		}
		if (params.isPresent()) {
			List<String> p = new LinkedList<>(params.get());
			Collections.sort(p);
			String val = String.join(",", p);
			form.add(Params, val);
		}
		return form;
	}
}
