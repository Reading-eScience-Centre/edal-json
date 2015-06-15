package uk.ac.rdg.resc.edal.json;

import static uk.ac.rdg.resc.edal.json.Utils.mapList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
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
import uk.ac.rdg.resc.edal.feature.GridFeature;
import uk.ac.rdg.resc.edal.feature.ProfileFeature;
import uk.ac.rdg.resc.edal.geometry.BoundingBox;
import uk.ac.rdg.resc.edal.grid.RectilinearGrid;
import uk.ac.rdg.resc.edal.grid.RegularGrid;
import uk.ac.rdg.resc.edal.grid.TimeAxis;
import uk.ac.rdg.resc.edal.grid.VerticalAxis;
import uk.ac.rdg.resc.edal.metadata.Parameter;
import uk.ac.rdg.resc.edal.position.HorizontalPosition;
import uk.ac.rdg.resc.edal.util.Array;
import uk.ac.rdg.resc.edal.util.Array4D;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class FeatureResource extends ServerResource {
	
	static class Details {
		final boolean domain, rangeMetadata, range;
		static Details from(String details) {
			if (details == null) return new Details(false, true, false);
			List<String> parts = Arrays.asList(details.split(","));
			return new Details(parts.contains("domain"), parts.contains("rangeMetadata"), 
					parts.contains("range"));
		}
		public Details(boolean domain, boolean rangeMetadata, boolean range) {
			this.domain = domain;
			this.rangeMetadata = rangeMetadata;
			this.range = range;
		}
	}
	
	public static Map getFeatureJson(Dataset dataset, String featureId, String rootUri, Details details) throws EdalException {
		DiscreteFeature feature;
		try {
			feature = (DiscreteFeature) dataset.readFeature(featureId);
		} catch (ClassCastException e) {
			throw new IllegalArgumentException("Only discrete features are supported");
		}
		return getFeatureJson(dataset, feature, rootUri, details);
	}
	
	public static Map getFeatureJson(Dataset dataset, DiscreteFeature feature, String rootUri, Details details) throws EdalException {
		String featureUrl = rootUri + "/datasets/" + dataset.getId() + "/features/" + feature.getId();
				
		Map j = new HashMap(ImmutableMap.of(
				"id", featureUrl,
				"title", feature.getName()
				));
		Map domainJson = getDomainJson(feature);
		if (details.domain || details.range || details.rangeMetadata) {
			Map result = new HashMap();
			if (details.domain) {
				result.put("domain", domainJson);
			}
			if (details.rangeMetadata) {
				result.put("rangeType", getParameterTypesJson(feature, dataset, rootUri));
			}
			result.put("range", getParameterValuesJson(feature, dataset, details.range, rootUri));
			j.put("result", result);
		}
		addPhenomenonTime(domainJson, j);
		
		return j;
	}
	
	@Get("json")
	public Representation json() throws IOException, EdalException {
		String datasetId = Reference.decode(getAttribute("datasetId"));
		String featureId = Reference.decode(getAttribute("featureId"));
		Details details = Details.from(getQueryValue("details"));
		Dataset dataset = Utils.getDataset(datasetId);
		Map featureJson = getFeatureJson(dataset, featureId, getRootRef().toString(), details);
		JsonRepresentation r = new JsonRepresentation(featureJson);
		r.setIndenting(true);
		return r;
	}
	
	private static void addPhenomenonTime(Map domainJson, Map featureJson) {
		// a time range or a single point in time
		List time = (List) domainJson.get("time");
		if (time == null) return;

		featureJson.put("phenomenonTime", time.size() == 1 ? time.get(0) :
			ImmutableMap.of(
					"start", time.get(0),
					"end", time.get(time.size()-1)
					)); 
	}
	
	private static Map getDomainJson(Feature<?> feature) {
		Map domainJson;
		
		// FIXME feature types should be interfaces
		if (feature instanceof GridFeature) {
			GridFeature gridFeature = (GridFeature) feature;
			GridDomain grid = gridFeature.getDomain();
			if (grid.getHorizontalGrid() instanceof RectilinearGrid) {
				RectilinearGrid rectgrid = (RectilinearGrid) grid.getHorizontalGrid();
				TimeAxis t = grid.getTimeAxis();
				VerticalAxis z = grid.getVerticalAxis();
				// TODO vertical CRS?
				
				BoundingBox bb = rectgrid.getBoundingBox();
				domainJson = new HashMap(ImmutableMap.of(
						"type", "RectilinearGrid",
					    "crs", Utils.getCrsUri(rectgrid.getCoordinateReferenceSystem()),
					    "bbox", ImmutableList.of(bb.getMinX(), bb.getMinY(), bb.getMaxX(), bb.getMaxY()),
					    "x", rectgrid.getXAxis().getCoordinateValues(),
						"y", rectgrid.getYAxis().getCoordinateValues()
						));
				if (rectgrid instanceof RegularGrid) {
					RegularGrid reggrid = (RegularGrid) rectgrid;
					domainJson.putAll(ImmutableMap.of(
							"type", "RegularGrid",
							"delta", ImmutableList.of(
						    		reggrid.getXAxis().getCoordinateSpacing(),
						    		reggrid.getYAxis().getCoordinateSpacing()
						    		)						    
						    ));
				}
				
				// TODO should we name the type "RegularGrid" even if z or t is irregular?
				addVerticalAxis(z, domainJson);
				addTimeAxis(t, domainJson);

			} else {
				domainJson = unsupportedDomain(feature.getDomain(), grid.getHorizontalGrid().getClass().getName());
			}
		} else if (feature instanceof ProfileFeature) {
			ProfileFeature profile = (ProfileFeature) feature;
			VerticalAxis z = profile.getDomain();
			DateTime t = profile.getTime();
			HorizontalPosition pos = profile.getHorizontalPosition();
			
			domainJson = new HashMap(ImmutableMap.of(
					"type", "Profile",
				    "crs", Utils.getCrsUri(pos.getCoordinateReferenceSystem()),
				    "bbox", ImmutableList.of(pos.getX(), pos.getY(), pos.getX(), pos.getY()),
				    "x", ImmutableList.of(pos.getX()),
				    "y", ImmutableList.of(pos.getY())
					));
			
			addVerticalAxis(z, domainJson);
			addTime(t, domainJson);
			
		} else {
			// TODO should probably say unsupported feature
			domainJson = unsupportedDomain(feature.getDomain());
		}
		
		 
		return domainJson;
	}
	
	private static void addVerticalAxis(VerticalAxis z, Map domainJson) {
		if (z != null) {
			domainJson.put("vertical", z.getCoordinateValues());
			// TODO are there no standards for vertical CRS, with codes etc.?
			domainJson.put("verticalCrs", ImmutableMap.of(
					"uom", z.getVerticalCrs().getUnits(),
					"positiveUpwards", z.getVerticalCrs().isPositiveUpwards(),
					"dimensionless", z.getVerticalCrs().isDimensionless(),
					"pressure", z.getVerticalCrs().isPressure()
					));
		}		
	}
		
	private static void addTimeAxis(TimeAxis t, Map domainJson) {
		if (t == null) {
			return;
			// TODO why is time null? shouldn't there be always a time?
		} 
		domainJson.put("time", mapList(t.getCoordinateValues(), time -> time.toString()));
	}
	
	private static void addTime(DateTime t, Map domainJson) {
		// TODO profile should have a timeaxis with a single element
		// this would avoid special handling here
		if (t != null) {
			domainJson.put("time", ImmutableList.of(t.toString()));
		}
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
		
	private static Map getParameterTypesJson(DiscreteFeature<?,?> feature, Dataset dataset, String rootUri) {
		String root = rootUri + "/datasets/" + dataset.getId() + "/params/";
		
		Builder types = ImmutableMap.builder();
		for (String paramId : feature.getParameterIds()) {
			Parameter param = feature.getParameter(paramId);
			types.put(root + paramId, ImmutableMap.of(
					"title", param.getTitle(),
					"description", param.getDescription(),
					"observedProperty", param.getStandardName() == null ? "UNKNOWN" : param.getStandardName(),
					"uom", param.getUnits()
					));
		}
		return types.build();
	}
		
	private static Map getParameterValuesJson(DiscreteFeature<?,?> feature, Dataset dataset, boolean includeValues, String rootUri) {
		String root = rootUri + "/datasets/" + dataset.getId();
		Builder values = ImmutableMap.builder();

		for (String paramId : feature.getParameterIds()) {
			
			Map rangeParam = ImmutableMap.of(
					"id", root + "/features/" + feature.getId() + "/range/" + paramId
					);
			
			if (includeValues) {
				// TODO how do we know which axis order the array has?!
				Array<Number> valsArr = feature.getValues(paramId);
				
				rangeParam = ImmutableMap.builder()
						.putAll(rangeParam)
						.put("values", getValues(valsArr))
						.build();
			}
			
			values.put(root + "/params/" + paramId, rangeParam);
		}
		
		return values.build();
	}
	
	public static List<Number> getValues(Array<Number> valsArr) {
		if (valsArr.size() > Integer.MAX_VALUE) {
			throw new RuntimeException("Array too big, consider subsetting!");
		}
		
		List<Number> vals = new ArrayList<Number>((int) valsArr.size());
		
		if (valsArr instanceof Array4D) {
			// workaround because CdmGridDataSource->WrappedArray broken...
			iterator((Array4D<Number>) valsArr).forEachRemaining(vals::add);
		} else {
			valsArr.forEach(vals::add);
		}
		return vals;
	}
	
	public static int[] getValues_(Array<Number> valsArr) {
		if (valsArr.size() > Integer.MAX_VALUE) {
			throw new RuntimeException("Array too big, consider subsetting!");
		}
		
		// TODO make this more clever, depending on input data
		int[] vals = new int[(int) valsArr.size()];
		
		Iterator<Number> it;
		if (valsArr instanceof Array4D) {
			// workaround because CdmGridDataSource->WrappedArray broken...
			it = iterator((Array4D<Number>) valsArr);
		} else {
			it = valsArr.iterator();
		}
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
