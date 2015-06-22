package uk.ac.rdg.resc.edal.json;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import uk.ac.rdg.resc.edal.feature.DiscreteFeature;
import uk.ac.rdg.resc.edal.feature.Feature;
import uk.ac.rdg.resc.edal.metadata.Parameter;
import uk.ac.rdg.resc.edal.util.Array;

public class RangeMetadata {
	
	private Map<String,Parameter> params;
	private Map<Parameter,Double> minValues = new HashMap<>();
	private Map<Parameter,Double> maxValues = new HashMap<>();
	
	public RangeMetadata(Feature<?> feature) {
		extractMetadata(feature);
	}

	private void extractMetadata(Feature<?> feature) {
		params = feature.getParameterMap();
		
		if (feature instanceof DiscreteFeature) {
			DiscreteFeature<?,?> discreteFeature = (DiscreteFeature<?,?>) feature;
			for (Parameter param : params.values()) {
				Array<Number> vals = discreteFeature.getValues(param.getVariableId());
				double min = Double.POSITIVE_INFINITY;
				double max = Double.NEGATIVE_INFINITY;
				for (Number val : vals) {
					if (val == null) continue;
					double v = val.doubleValue();
					if (v < min) {
						min = v;
					} 
					if (v > max) {
						max = v;
					}
				}
				minValues.put(param, min);
				maxValues.put(param, max);
			}
		}
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
	
	public double getMinValue(Parameter param) {
		return minValues.get(param);
	}
	
	public double getMaxValue(Parameter param) {
		return maxValues.get(param);
	}

}
