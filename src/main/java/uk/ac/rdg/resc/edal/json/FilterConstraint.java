package uk.ac.rdg.resc.edal.json;

import java.util.Optional;


public class FilterConstraint extends Constraint {

	public Optional<Class<?>> type;
	
	public FilterConstraint(String urlParam) {
		this(urlParam, null);
	}
	
	/**
	 * Uses the given SubsetConstraint as filter constraints
	 * in cases where a particular filter field is not specified.
	 * 
	 * @param urlParam
	 * @param subsetConstraint 
	 */
	public FilterConstraint(String urlParam, SubsetConstraint subsetConstraint) {
		super(urlParam);
		
		String val = getParams(urlParam).get("type");
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

}
