package uk.ac.rdg.resc.edal.json;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import uk.ac.rdg.resc.edal.feature.Feature;
import uk.ac.rdg.resc.edal.metadata.Parameter;

public class RangeMetadata {
	
	private Map<String,Parameter> params;
	
	public RangeMetadata(Feature<?> feature) {
		extractMetadata(feature);
	}

	private void extractMetadata(Feature<?> feature) {
		params = feature.getParameterMap();
	}

	public Set<String> getParameterIds() {
		return params.keySet();
	}
	
	public Parameter getParameter(String paramId) {
		return params.get(paramId);
	}
	
	public Collection<Parameter> getParameters() {
		return params.values();
	}

}
