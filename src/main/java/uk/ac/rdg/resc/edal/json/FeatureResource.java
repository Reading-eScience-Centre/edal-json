package uk.ac.rdg.resc.edal.json;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.opengis.metadata.extent.GeographicBoundingBox;
import org.restlet.data.Reference;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

import uk.ac.rdg.resc.edal.dataset.Dataset;
import uk.ac.rdg.resc.edal.domain.Domain;
import uk.ac.rdg.resc.edal.domain.GridDomain;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.feature.DiscreteFeature;
import uk.ac.rdg.resc.edal.feature.Feature;
import uk.ac.rdg.resc.edal.geometry.BoundingBox;
import uk.ac.rdg.resc.edal.grid.RegularGrid;
import uk.ac.rdg.resc.edal.grid.TimeAxis;
import uk.ac.rdg.resc.edal.grid.VerticalAxis;
import uk.ac.rdg.resc.edal.metadata.Parameter;
import uk.ac.rdg.resc.edal.util.Array;
import uk.ac.rdg.resc.edal.util.Array4D;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class FeatureResource extends ServerResource {

	@Get("json")
	public Representation json() throws IOException, EdalException {
		String datasetId = Reference.decode(getAttribute("datasetId"));
		String featureId = Reference.decode(getAttribute("featureId"));
		Dataset dataset = Utils.getDataset(datasetId);
		DiscreteFeature feature;
		try {
			feature = (DiscreteFeature) dataset.readFeature(featureId);
		} catch (ClassCastException e) {
			throw new IllegalArgumentException("Only disrete features are supported");
		}
		
		String featureUrl = getReference().toString();
		
		Object range;
		if (feature.getDomain() instanceof GridDomain) {
			// grids are big, so we don't supply the parameter values with the feature itself
			range = getParameterValuesJson(feature, false, featureUrl);
		} else {
			range = getParameterValuesJson(feature, true, featureUrl);
		}
		
		Map j = ImmutableMap.of(
				"id", featureUrl,
				"title", feature.getName(),
				"phenomenonTime", "?",
				"result", ImmutableMap.of(
						"domain", getDomainJson(feature),
						"rangeType", ImmutableMap.of(
								"fields", getParameterTypesJson(feature)
								),
						"range", range
						)
				);
		
		JsonRepresentation r = new JsonRepresentation(j);
		r.setIndenting(true);
		return r;
	}
	
	private Map getDomainJson(Feature<?> feature) {
		Domain<?> domain = feature.getDomain();
		Map domainJson;
		
		if (domain instanceof GridDomain) {
			GridDomain grid = (GridDomain) domain;
			if (grid.getHorizontalGrid() instanceof RegularGrid) {
				RegularGrid reggrid = (RegularGrid) grid.getHorizontalGrid();
				TimeAxis t = grid.getTimeAxis();
				VerticalAxis z = grid.getVerticalAxis();
				
				BoundingBox bb = reggrid.getBoundingBox();
				domainJson = new HashMap(ImmutableMap.of(
						"type", "RegularGrid",
					    "crs", Utils.getCrsUri(reggrid.getCoordinateReferenceSystem()),
					    "bbox", ImmutableList.of(bb.getMinX(), bb.getMinY(), bb.getMaxX(), bb.getMaxY()),
					    "delta", ImmutableList.of(
					    		reggrid.getXAxis().getCoordinateSpacing(),
					    		reggrid.getYAxis().getCoordinateSpacing()
					    		)
						));
				domainJson.putAll(ImmutableMap.of(
						"x", reggrid.getXAxis().getCoordinateValues(),
						"y", reggrid.getYAxis().getCoordinateValues()
						));
				
				// TODO should we name the type "RegularGrid" even if z is irregular?
				if (z != null) {
					domainJson.put("vertical", z.getCoordinateValues());
					domainJson.put("verticalUom", z.getVerticalCrs().getUnits());
					domainJson.put("verticalPositiveUpwards", z.getVerticalCrs().isPositiveUpwards());
				}
				if (t == null) {
					// TODO why is time null? shouldn't there be always a time?
				} else if (t.size() > 1) {
					domainJson.putAll(ImmutableMap.of(
							"type", "RegularGridSeries",
							"time", t.getCoordinateValues()
							));
				} else {
					domainJson.put("time", t.getCoordinateValues().get(0));
				}
			} else {
				domainJson = unsupportedDomain(domain, grid.getHorizontalGrid().getClass().getName());
			}
		} else {
			domainJson = unsupportedDomain(domain);
		}
		
		 
		return domainJson;
	}
	
	private static Map unsupportedDomain(Domain<?> domain) {
		return unsupportedDomain(domain, "");
	}
	
	private static Map unsupportedDomain(Domain<?> domain, String info) {
		return ImmutableMap.of(
				"type", domain.getClass().getName(),
				"info", info,
				"ERROR", "UNSUPPORTED"
				);
	}
		
	private List getParameterTypesJson(DiscreteFeature<?,?> feature) {
		List types = new LinkedList();
		for (String paramId : feature.getParameterIds()) {
			Parameter param = feature.getParameter(paramId);
			
			// TODO how do we get the range extents from EDAL?
			
			
			types.add(ImmutableMap.of(
					"name", param.getId(),
					"title", param.getTitle(),
					"description", param.getDescription(),
					"observedProperty", param.getStandardName() == null ? "UNKNOWN" : param.getStandardName(),
					"uom", param.getUnits()
					));
		}
		return types;
	}
		
	private List getParameterValuesJson(DiscreteFeature<?,?> feature, boolean includeValues, String featureUrl) {
		List values = new LinkedList();

		for (String paramId : feature.getParameterIds()) {
			

			Map rangeParam = ImmutableMap.of(
					"id", featureUrl + "/range/" + paramId, 
					"name", paramId);
			
			if (includeValues) {
				// TODO how do we know which axis order the array has?!
				Array<Number> valsArr = feature.getValues(paramId);
				
				Builder rangeParamWithValues = ImmutableMap.builder();
				rangeParamWithValues.putAll(rangeParam);
				rangeParamWithValues.put("values", getValues(valsArr));
				rangeParam = rangeParamWithValues.build();
			}
			
			values.add(rangeParam);
		}
		
		return values;
	}
	
	public static List<Number> getValues(Array<Number> valsArr) {
		if (valsArr.size() > Integer.MAX_VALUE) {
			throw new RuntimeException("Array too big, consider subsetting!");
		}
		
		// TODO this function should not be needed...
		// the values should be delivered in a defined order by the Array<Number> iterator
		// but on the other side we may need to manually fetch values anyway for subsetting
		List<Number> vals = new ArrayList<Number>((int) valsArr.size());
		
		if (valsArr instanceof Array4D) {
			// workaround because CdmGridDataSource->WrappedArray broken...
			iterator((Array4D<Number>) valsArr).forEachRemaining(vals::add);
		} else {
			valsArr.forEach(vals::add);
		}
		return vals;
	}
	
    private static <T> Iterator<T> iterator(Array4D<T> arr) {
    	// FIXME remove this once CdmGridDataSource::WrappedArray is fixed
        final int X_IND = 3;
        final int Y_IND = 2;
        final int Z_IND = 1;
        final int T_IND = 0;
    	
        return new Iterator<T>() {
            private int xCounter = 0;
            private int yCounter = 0;
            private int zCounter = 0;
            private int tCounter = 0;

            boolean done = false;

            @Override
            public boolean hasNext() {
                return (!done);
            }

            @Override
            public T next() {
                T value = arr.get(tCounter, zCounter, yCounter, xCounter);
                /*
                 * Increment the counters, resetting to zero if necessary
                 */
                if (++xCounter >= arr.getShape()[X_IND]) {
                    xCounter = 0;
                    if (++yCounter >= arr.getShape()[Y_IND]) {
                        yCounter = 0;
                        if (++zCounter >= arr.getShape()[Z_IND]) {
                            zCounter = 0;
                            if (++tCounter >= arr.getShape()[T_IND]) {
                                tCounter = 0;
                                done = true;
                            }
                        }
                    }
                }
                return value;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("Remove is not supported for this iterator");
            }
        };
    }

}
