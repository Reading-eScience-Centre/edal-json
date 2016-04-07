package uk.ac.rdg.resc.edal.json;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import org.restlet.data.Form;

public class SubsetConstraint extends Constraint {

	private static final String PREFIX = "subset";
	private static final String VerticalTarget = "verticalTarget";
	
	/**
	 * If given, it restricts the vertical axis to exactly the element
	 * which is closest to verticalTarget.
	 */
	public Optional<Double> verticalTarget;
	
	public SubsetConstraint(Form queryParams) {
		super(queryParams, PREFIX);
		String val = queryParams.getFirstValue(PREFIX + upper(VerticalTarget));
		verticalTarget = val == null ? Optional.empty() : Optional.of(Double.parseDouble(val));
	}
	
	public Form getCanonicalQueryParams() {
		Form form = new Form();
		
		if (timeExtent.getLow() != null) {
			String time = timeExtent.getLow().toString();
			form.add(PREFIX + upper(TimeStart), time);
		}
		if (timeExtent.getHigh() != null) {
			String time = timeExtent.getHigh().toString();
			form.add(PREFIX + upper(TimeEnd), time);
		}
		if (bbox.isPresent()) {
			DatelineBoundingBox bb = bbox.get();
			String box = bb.getWestBoundLongitude() + "," + bb.getSouthBoundLatitude() + "," + bb.getEastBoundLongitude() + "," +
			     bb.getNorthBoundLatitude();
			form.add(PREFIX + upper(Bbox), box);
		}
		if (verticalExtent.getLow() != null) {
			String val = verticalExtent.getLow().toString();
			form.add(PREFIX + upper(VerticalStart), val);
		}
		if (verticalExtent.getHigh() != null) {
			String val = verticalExtent.getHigh().toString();
			form.add(PREFIX + upper(VerticalEnd), val);
		}
		if (verticalTarget.isPresent()) {
			String val = verticalTarget.get().toString();
			form.add(PREFIX + upper(VerticalTarget), val);
		}
		if (params.isPresent()) {
			List<String> p = new LinkedList<>(params.get());
			Collections.sort(p);
			String val = String.join(",", p);
			form.add(PREFIX + Params, val);
		}
		return form;
	}
	
	private static String upper(String name) {
		return name.substring(0, 1).toUpperCase() + name.substring(1);
	}
}
