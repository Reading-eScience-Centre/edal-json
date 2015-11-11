package uk.ac.rdg.resc.edal.json;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import org.restlet.data.Form;

public class SubsetConstraint extends Constraint {

	private static final String PREFIX = "subset";
	
	/**
	 * If given, it restricts the vertical axis to exactly the element
	 * which is closest to verticalTarget.
	 */
	public Optional<Double> verticalTarget;
	
	public SubsetConstraint(Form queryParams) {
		super(queryParams, PREFIX);

		String val = null; //getParams(urlParam).get("verticalTarget");
		verticalTarget = val == null ? Optional.empty() : Optional.of(Double.parseDouble(val));
	}

	/**
	 * Return a query string that only contains the subset* parameters
	 * in alphabetical order. Includes "?" if query string not empty.
	 */
	public String getCanonicalSubsetQueryString() {
		List<String> names = new LinkedList<>();
		for (String name : this.queryParams.getNames()) {
			if (name.startsWith(PREFIX)) {
				names.add(name);
			}
		}
		names.sort(Comparator.naturalOrder());
		Form form = new Form();
		for (String name : names) {
			form.add(name, this.queryParams.getFirstValue(name));
		}
		String queryString = form.getQueryString();
		return queryString.isEmpty() ? "" : "?" + queryString;
	}
}
