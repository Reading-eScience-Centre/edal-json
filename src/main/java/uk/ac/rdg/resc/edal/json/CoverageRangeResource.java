package uk.ac.rdg.resc.edal.json;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.restlet.data.Header;
import org.restlet.data.Reference;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.restlet.util.Series;

import com.google.common.collect.ImmutableMap;

import uk.ac.rdg.resc.edal.dataset.Dataset;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.feature.DiscreteFeature;
import uk.ac.rdg.resc.edal.feature.ProfileFeature;
import uk.ac.rdg.resc.edal.grid.ReferenceableAxis;
import uk.ac.rdg.resc.edal.grid.RegularAxisImpl;
import uk.ac.rdg.resc.edal.json.CoverageResource.FeatureMetadata;
import uk.ac.rdg.resc.edal.json.CoverageResource.UniformFeature;
import uk.ac.rdg.resc.edal.metadata.Parameter;
import uk.ac.rdg.resc.edal.util.Array;
import uk.ac.rdg.resc.edal.util.Array4D;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class CoverageRangeResource extends ServerResource {
		
	public static Map getRangeJson(String datasetId, String featureId, String paramId, SubsetConstraint subset) throws EdalException, IOException {		
		FeatureMetadata meta = DatasetResource.getDatasetMetadata(datasetId).getFeatureMetadata(featureId);
		Dataset dataset = Utils.getDataset(datasetId);
		DiscreteFeature feature;
		try {
			feature = (DiscreteFeature) dataset.readFeature(featureId);
		} catch (ClassCastException e) {
			throw new IllegalArgumentException("Only discrete features are supported");
		}
		Parameter param = feature.getParameter(paramId);
		
		UniformFeature uniFeature =	new UniformFeature(feature);
		
		boolean isCategorical = param.getCategories() != null;
		String dtype = isCategorical ? "integer" : "float";
		
		AxesIndices ind = getAxesIndices(uniFeature, subset);
		
		List<Integer> shape = new LinkedList<>();
		List<String> axisNames = new LinkedList<>();
		if (uniFeature.t != null) {
			axisNames.add("t");
			shape.add(ind.t.length);
		}
		if (uniFeature.z != null) {
			axisNames.add("z");
			shape.add(ind.z.length);
		}
		axisNames.add("y");
		shape.add(ind.y.length);
		axisNames.add("x");
		shape.add(ind.x.length);
		
		Map j = ImmutableMap.of(
				"type", "NdArray",
				"dataType", dtype,
				"axisNames", axisNames,
				"shape", shape,
				"values", getValues(feature.getValues(param.getVariableId()), uniFeature, ind, isCategorical)
				// TODO enable again when CBOR missing-value encoding is implemented and only output for CBOR
//				"validMin", meta.rangeMeta.getMinValue(param),
//				"validMax", meta.rangeMeta.getMaxValue(param)
				);
		return j;
	}
	
	private Map rangeData() throws IOException, EdalException {
		String datasetId = Reference.decode(getAttribute("datasetId"));
		String featureId = Reference.decode(getAttribute("coverageId"));
		String paramId = Reference.decode(getAttribute("parameterId"));
		SubsetConstraint subset = new SubsetConstraint(getQuery());
		
		return getRangeJson(datasetId, featureId, paramId, subset);
	}

	@Get("covjson|covcbor|covmsgpack")
	public Representation json() throws IOException, EdalException {
		Series<Header> headers = this.getResponse().getHeaders();
		
		// TODO add subsetOf rel if subsetted
		// TODO add link to coverage
		
		Map j = rangeData();
		return App.getCovJsonRepresentation(this, j);
	}
	
	static class AxesIndices {
		int[] x;
		int[] y;
		int[] z;
		int[] t;
	}
	
	private static AxesIndices getAxesIndices(UniformFeature uniFeature, SubsetConstraint subset) {
		int[] xIndices;
		int[] yIndices;
		
		if (uniFeature.rectgrid != null) {
			xIndices = CoverageDomainResource.getXAxisIndices(uniFeature.rectgrid.getXAxis(), subset).toArray();
			yIndices = CoverageDomainResource.getYAxisIndices(uniFeature.rectgrid.getYAxis(), subset).toArray();
		} else if (uniFeature.projgrid != null) {
			// FIXME the start and stop coordinates are wrong, but there's no way to access those via EDAL
			ReferenceableAxis<Double> xAxis = new RegularAxisImpl("x", 0, 1, uniFeature.projgrid.getXSize(), false);
			ReferenceableAxis<Double> yAxis = new RegularAxisImpl("y", 0, 1, uniFeature.projgrid.getYSize(), false);
			xIndices = CoverageDomainResource.getXAxisIndices(xAxis, subset).toArray();
			yIndices = CoverageDomainResource.getYAxisIndices(yAxis, subset).toArray();
		} else {
			throw new RuntimeException("Not implemented");
		}
		
		int[] zIndices = CoverageDomainResource.getVerticalAxisIndices(uniFeature.z, subset).toArray();
		int[] tIndices = CoverageDomainResource.getTimeAxisIndices(uniFeature.t, subset).toArray();
		
		AxesIndices ind = new AxesIndices();
		ind.t = tIndices;
		ind.x = xIndices;
		ind.y = yIndices;
		ind.z = zIndices;
		return ind;
	}
	
	public static List<Number> getValues(Array<Number> valsArr, UniformFeature uniFeature, 
			AxesIndices ind, boolean isCategorical) {
		if (valsArr.size() > Integer.MAX_VALUE) {
			throw new RuntimeException("Array too big, consider subsetting!");
		}
		
		int[] xIndices = ind.x;
		int[] yIndices = ind.y;
		int[] zIndices = ind.z;
		int[] tIndices = ind.t;		

		long size = xIndices.length * yIndices.length * zIndices.length * tIndices.length;
		if (size > 3600*7200) {
			// TODO implement streaming solution
			throw new RuntimeException("range too big, please subset");
		}
		
		// FIXME EN3 has 99999.0 as values which probably means missing
		//  -> shouldn't this be detected by EDAL and returned as null instead?
				
				
		Array4D<Number> vals4D;
		
		if (valsArr instanceof Array4D) {
			vals4D = (Array4D<Number>) valsArr;
		} else if (uniFeature.feature instanceof ProfileFeature) {
			// Array1D varying over vertical axis
			vals4D = new Array4D<Number>(1, zIndices.length, 1, 1) {
				@Override
				public Number get(int... coords) {
					return valsArr.get(coords[1]);
				}
				@Override
				public void set(Number value, int... coords) {
					throw new UnsupportedOperationException();
				}
			};
		} else {
			throw new RuntimeException("not supported: " + valsArr.getClass().getName());
		}
		
		Number[] vals = new Number[xIndices.length * yIndices.length * 
				zIndices.length * tIndices.length];
		
		int i = 0;
		for (int t : tIndices) {
			for (int z : zIndices) {
				for (int y : yIndices) {
					for (int x : xIndices) {
						Number val = vals4D.get(t, z, y, x);
						vals[i++] = isCategorical && val != null ? val.intValue() : val;
					}
				}
			}
		}
		
		return Arrays.asList(vals);
	}
	
	public static float[] getValues_(Array<Number> valsArr) {
		if (valsArr.size() > Integer.MAX_VALUE) {
			throw new RuntimeException("Array too big, consider subsetting!");
		}
		
		// TODO make this more clever, depending on input data
		float[] vals = new float[(int) valsArr.size()];
		
		Iterator<Number> it = valsArr.iterator();
		int i = 0;
		while (it.hasNext()) {
			Number v = it.next();
			if (v == null) {
				vals[i] = Integer.MIN_VALUE;
			} else {
				vals[i] = v.intValue();
			}
			i++;
		}
		return vals;
	}
}
