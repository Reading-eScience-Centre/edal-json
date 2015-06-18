package uk.ac.rdg.resc.edal.json;

import static uk.ac.rdg.resc.edal.json.Utils.mapList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.joda.time.DateTime;
import org.restlet.data.Reference;
import org.restlet.ext.jackson.JacksonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

import uk.ac.rdg.resc.edal.dataset.Dataset;
import uk.ac.rdg.resc.edal.domain.Domain;
import uk.ac.rdg.resc.edal.domain.Extent;
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
import uk.ac.rdg.resc.edal.json.FeaturesResource.DatasetMetadata;
import uk.ac.rdg.resc.edal.metadata.Parameter;
import uk.ac.rdg.resc.edal.position.HorizontalPosition;
import uk.ac.rdg.resc.edal.util.Array;
import uk.ac.rdg.resc.edal.util.Array4D;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class FeatureResource extends ServerResource {
	
	static class FeatureMetadata {
		public final DomainMetadata domainMeta;
		public final RangeMetadata rangeMeta;
		public final String featureId;
		public final String datasetId;
		public final  String name;
		public FeatureMetadata(String datasetId, Feature feature) {
			this.datasetId = datasetId;
			this.featureId = feature.getId();
			this.domainMeta = new DomainMetadata(feature);
			this.rangeMeta = new RangeMetadata(feature);
			this.name = feature.getName();
		}
	}
	
	static class Details {
		final boolean domain, rangeMetadata, range;
		static Details from(String details, Details fallback) {
			if (details == null) return fallback;
			List<String> parts = Arrays.asList(details.split(","));
			return new Details(parts.contains("domain"), parts.contains("rangeMetadata"), 
					parts.contains("range"));
		}
		public Details(boolean domain, boolean rangeMetadata, boolean range) {
			this.domain = domain || range;
			this.rangeMetadata = rangeMetadata;
			this.range = range;
		}
	}
	
	public static Map getFeatureJson(Supplier<Dataset> dataset, FeatureMetadata meta, String rootUri, 
			Details details, Constraint subset) throws EdalException {
		String featureUrl = rootUri + "/datasets/" + meta.datasetId + "/features/" + meta.featureId;
				
		Map j = new HashMap(ImmutableMap.of(
				"id", featureUrl,
				"title", meta.name
				));
		
		if (details.domain || details.range || details.rangeMetadata) {
			Map result = new HashMap();
			Supplier<DiscreteFeature<?,?>> feature =
					Suppliers.memoize(() -> (DiscreteFeature<?, ?>) dataset.get().readFeature(meta.featureId));

			if (details.domain) {
				Map domainJson = getDomainJson(feature.get(), subset);
				result.put("domain", domainJson);
			}
			// TODO we may not need that at all!
			if (details.rangeMetadata) {
				result.put("rangeType", getParameterTypesJson(meta, rootUri));
			}
			result.put("range", getParameterValuesJson(meta, feature, details.range, subset, rootUri));
			j.put("result", result);
		}
		addPhenomenonTime(j, meta.domainMeta);
		
		return j;
	}
	
	private Map getFeatureJson() throws EdalException, IOException {
		String datasetId = Reference.decode(getAttribute("datasetId"));
		String featureId = Reference.decode(getAttribute("featureId"));
		Details fallback = new Details(true, true, false);
		Details details = Details.from(getQueryValue("details"), fallback);
		Constraint subset = new Constraint(getQueryValue("subset"));
		
		DatasetMetadata meta = FeaturesResource.getDatasetMetadata(datasetId);
		Map featureJson = getFeatureJson(meta.getLazyDataset(), 
				meta.getFeatureMetadata(featureId), getRootRef().toString(), details, subset);
		return featureJson;
	}
	
	@Get("json")
	public Representation json() throws IOException, EdalException {
		Map featureJson = getFeatureJson();
		JacksonRepresentation r = new JacksonRepresentation(featureJson);
		if (!App.acceptsJSON(getClientInfo())) {
			r.getObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
		}
		return r;
	}
	
	@Get("msgpack")
	public Representation msgpack() throws IOException, EdalException {
		return new MessagePackRepresentation(getFeatureJson());
	}
	
	private static void addPhenomenonTime(Map featureJson, DomainMetadata meta) {
		// a time range or a single point in time
		Extent<DateTime> time = meta.getTimeExtent();
		if (time == null) return;

		featureJson.put("phenomenonTime", time.getLow() == time.getHigh() ? time.getLow().toString() :
			ImmutableMap.of(
					"start", time.getLow().toString(),
					"end", time.getHigh().toString()
					)); 
	}
	
	private static Map getDomainJson(Feature<?> feature, Constraint subset) {
		Map domainJson;
		
		// FIXME feature types should be interfaces
		if (feature instanceof GridFeature) {
			GridFeature gridFeature = (GridFeature) feature;
			GridDomain grid = gridFeature.getDomain();
			if (grid.getHorizontalGrid() instanceof RectilinearGrid) {
				RectilinearGrid rectgrid = (RectilinearGrid) grid.getHorizontalGrid();
				TimeAxis t = grid.getTimeAxis();
				VerticalAxis z = grid.getVerticalAxis();
				
				// TODO add subset by bounding box
				
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
				addVerticalAxis(z, subset, domainJson);
				addTimeAxis(t, subset, domainJson);

			} else {
				domainJson = unsupportedDomain(feature.getDomain(), grid.getHorizontalGrid().getClass().getName());
			}
		} else if (feature instanceof ProfileFeature) {
			ProfileFeature profile = (ProfileFeature) feature;
			VerticalAxis z = profile.getDomain();
			DateTime t = profile.getTime();
			HorizontalPosition pos = profile.getHorizontalPosition();
			
			if (!subset.timeExtent.contains(t)) {
				// TODO design a JSON error document
				throw new RuntimeException("subsetting must not produce an empty domain");
			}
			if (subset.bbox != null && !subset.bbox.contains(pos)) {
				throw new RuntimeException("subsetting must not produce an empty domain");
			}
			
			domainJson = new HashMap(ImmutableMap.of(
					"type", "Profile",
				    "crs", Utils.getCrsUri(pos.getCoordinateReferenceSystem()),
				    "bbox", ImmutableList.of(pos.getX(), pos.getY(), pos.getX(), pos.getY()),
				    "x", ImmutableList.of(pos.getX()),
				    "y", ImmutableList.of(pos.getY())
					));
			
			addVerticalAxis(z, subset, domainJson);
			addTime(t, domainJson);
			
		} else {
			// TODO should probably say unsupported feature
			domainJson = unsupportedDomain(feature.getDomain());
		}
		
		 
		return domainJson;
	}
	
	private static void addVerticalAxis(VerticalAxis z, Constraint subset, Map domainJson) {
		if (z == null) {
			return;
		}
		List<Double> v = z.getCoordinateValues().stream()
				.filter(subset.verticalExtent::contains)
				.collect(Collectors.toList());
		
		domainJson.put("vertical", v);
		
		// TODO are there no standards for vertical CRS, with codes etc.?
		domainJson.put("verticalCrs", ImmutableMap.of(
				"uom", z.getVerticalCrs().getUnits(),
				"positiveUpwards", z.getVerticalCrs().isPositiveUpwards(),
				"dimensionless", z.getVerticalCrs().isDimensionless(),
				"pressure", z.getVerticalCrs().isPressure()
				));
		
	}
		
	private static void addTimeAxis(TimeAxis t, Constraint subset, Map domainJson) {
		if (t == null) {
			return;
			// TODO why is time null? shouldn't there be always a time?
		} 
		List<String> times = t.getCoordinateValues().stream()
				.filter(subset.timeExtent::contains)
				.map(time -> time.toString())
				.collect(Collectors.toList());
		domainJson.put("time", times);
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
		
	private static Map getParameterTypesJson(FeatureMetadata meta, String rootUri) {
		String root = rootUri + "/datasets/" + meta.datasetId + "/params/";
		
		Builder types = ImmutableMap.builder();
		for (Parameter param : meta.rangeMeta.getParameters()) {
			types.put(root + param.getVariableId(), ImmutableMap.of(
					"title", param.getTitle(),
					"description", param.getDescription(),
					"observedProperty", param.getStandardName() == null ? "UNKNOWN" : param.getStandardName(),
					"uom", param.getUnits()
					));
		}
		return types.build();
	}
		
	private static Map getParameterValuesJson(FeatureMetadata meta, Supplier<DiscreteFeature<?,?>> feature, boolean includeValues,
			Constraint subset, String rootUri) {
		String root = rootUri + "/datasets/" + meta.datasetId;
		Builder values = ImmutableMap.builder();

		for (String paramId : meta.rangeMeta.getParameterIds()) {
			
			Map rangeParam = ImmutableMap.of(
					"id", root + "/features/" + meta.featureId + "/range/" + paramId
					);
			
			if (includeValues) {
				// TODO how do we know which axis order the array has?!
				Array<Number> valsArr = feature.get().getValues(paramId);
				
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
		valsArr.forEach(vals::add);
		
		// TODO subset!
		
		return vals;
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
