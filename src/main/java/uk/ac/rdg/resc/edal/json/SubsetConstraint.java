package uk.ac.rdg.resc.edal.json;

import java.util.Optional;

public class SubsetConstraint extends Constraint {

	/**
	 * If given, it restrict the vertical axis to exactly the element
	 * which is closest to verticalTarget.
	 */
	public Optional<Double> verticalTarget;
	
	public SubsetConstraint(String urlParam) {
		super(urlParam);

		String val = getParams(urlParam).get("verticalTarget");
		verticalTarget = val == null ? Optional.empty() : Optional.of(Double.parseDouble(val));
	}

}
