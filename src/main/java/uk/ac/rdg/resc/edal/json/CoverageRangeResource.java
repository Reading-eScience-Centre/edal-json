package uk.ac.rdg.resc.edal.json;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
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
import uk.ac.rdg.resc.edal.json.CoverageResource.FeatureMetadata;
import uk.ac.rdg.resc.edal.json.CoverageResource.UniformFeature;
import uk.ac.rdg.resc.edal.metadata.Parameter;
import uk.ac.rdg.resc.edal.util.Array;
import uk.ac.rdg.resc.edal.util.Array4D;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class CoverageRangeResource extends ServerResource {
		
	private Map rangeData() throws IOException, EdalException {
		String datasetId = Reference.decode(getAttribute("datasetId"));
		String featureId = Reference.decode(getAttribute("coverageId"));
		String parameterId = Reference.decode(getAttribute("parameterId"));
		SubsetConstraint subset = new SubsetConstraint(getQueryValue("subsetByCoordinate"));
		
		FeatureMetadata meta = DatasetResource.getDatasetMetadata(datasetId).getFeatureMetadata(featureId);
		Dataset dataset = Utils.getDataset(datasetId);
		DiscreteFeature feature;
		try {
			feature = (DiscreteFeature) dataset.readFeature(featureId);
		} catch (ClassCastException e) {
			throw new IllegalArgumentException("Only discrete features are supported");
		}
		Parameter param = feature.getParameter(parameterId);
		
		String parameterRangeUrl = getRootRef().toString() + "/datasets/" + dataset.getId() +
				"/coverages/" + feature.getId() + "/range/" + param.getVariableId();
		
		// TODO remove duplication with FeatureResource
		
		Map j = ImmutableMap.of(
				"type", "Range",
				"values", getValues(feature.getValues(param.getVariableId()), feature, subset),
				"validMin", meta.rangeMeta.getMinValue(param),
				"validMax", meta.rangeMeta.getMaxValue(param)
				);
		return j;
	}

	@Get("covjson|covcbor|covmsgpack")
	public Representation json() throws IOException, EdalException {
		Series<Header> headers = this.getResponse().getHeaders();
		headers.add(new Header("Link", "<http://coveragejson.org/def#Range>; rel=\"type\""));
		// TODO add as soon as subsetting by index is supported
		//headers.add(new Header("Link", "<" + CoverageResource.SubsetByIndexURI + ">; rel=\"" + CoverageResource.CapabilityURI + "\""));
		headers.add(new Header("Link", "<" + CoverageResource.SubsetByCoordinateURI + ">; rel=\"" + CoverageResource.CapabilityURI + "\""));
		
		// TODO add subsetOf rel if subsetted
		// TODO add link to coverage
		
		Map j = rangeData();
		Representation r = App.getCovJsonRepresentation(this, j);
		
		// TODO think about caching strategy
		Date exp = Date.from(LocalDate.now().plusDays(1).atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
		r.setExpirationDate(exp);
		
		return r;
	}
		
	public static List<Number> getValues(Array<Number> valsArr, DiscreteFeature<?,?> feature, 
			SubsetConstraint subset) {
		return getValues(valsArr, new UniformFeature(feature), subset);
	}
	
	public static List<Number> getValues(Array<Number> valsArr, UniformFeature uniFeature, 
			SubsetConstraint subset) {
		if (valsArr.size() > Integer.MAX_VALUE) {
			throw new RuntimeException("Array too big, consider subsetting!");
		}
		
		int[] xIndices = CoverageDomainResource.getXAxisIndices(uniFeature.rectgrid.getXAxis(), subset).toArray();
		int[] yIndices = CoverageDomainResource.getYAxisIndices(uniFeature.rectgrid.getYAxis(), subset).toArray();
		int[] zIndices = CoverageDomainResource.getVerticalAxisIndices(uniFeature.z, subset).toArray();
		int[] tIndices = CoverageDomainResource.getTimeAxisIndices(uniFeature.t, subset).toArray();
		
		// FIXME EN3 has 99999.0 as values which probably means missing
		//  -> shouldn't this be detected by EDAL and returned as null instead?
		
//		valsArr.forEach(vals::add);
		
				
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
						vals[i++] = vals4D.get(t, z, y, x);
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
