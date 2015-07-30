package uk.ac.rdg.resc.edal.json;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;
import org.restlet.data.Reference;
import org.restlet.ext.jackson.JacksonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

import uk.ac.rdg.resc.edal.dataset.Dataset;
import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.geometry.BoundingBox;
import uk.ac.rdg.resc.edal.json.FeatureResource.Details;
import uk.ac.rdg.resc.edal.json.FeatureResource.FeatureMetadata;
import uk.ac.rdg.resc.edal.metadata.Parameter;
import uk.ac.rdg.resc.edal.util.GISUtils;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class FeaturesResource extends ServerResource {
	
	private Builder getFeaturesJson(boolean asGeojson) throws IOException, EdalException {
		final String datasetId = Reference.decode(getAttribute("datasetId"));
		Details fallback = new Details(false, false, false);
		Details details = Details.from(getQueryValue("details"), fallback);
		SubsetConstraint subset = new SubsetConstraint(getQueryValue("subset"));
		FilterConstraint filter = new FilterConstraint(getQueryValue("filter"), subset);
		
		DatasetMetadata datasetMeta = DatasetResource.getDatasetMetadata(datasetId);
		
		String datasetUrl = getRootRef().toString() + "/datasets/" + datasetId;
		
		List jsonFeatures = new LinkedList();
				
		Supplier<Dataset> dataset = datasetMeta.getLazyDataset();
		
		for (String featureId : datasetMeta.getFeatureIds()) {
			FeatureMetadata meta = datasetMeta.getFeatureMetadata(featureId);
			DomainMetadata domainMeta = meta.domainMeta;
			RangeMetadata rangeMeta = meta.rangeMeta;
			
			if (filter.type.isPresent()) {
				if (!filter.type.get().isAssignableFrom(meta.type)) continue;
			}
			
			if (filter.params.isPresent()) {
				Set<String> params = new HashSet<>(rangeMeta.getParameterIds());
				params.retainAll(filter.params.get());
				if (params.isEmpty()) continue;
			}
						
			if (filter.bbox.isPresent()) {
				// TODO we cannot use GeographicBoundingBox here as
				//  this cannot span the discontinuity...
				BoundingBox bb = domainMeta.getBoundingBox();
				
				if (!GISUtils.isWgs84LonLat(bb.getCoordinateReferenceSystem())) {
					throw new RuntimeException("only WGS84 supported currently");
				}
				DatelineBoundingBox geoBB = new DatelineBoundingBox(bb);
				if (!geoBB.intersects(filter.bbox.get())) {
					continue;
				}
			}
			
			/*
			 * Time filtering rules:
			 * 1. Feature has no time information
			 * 1a. Time filter is given -> ??? probably skip feature
			 * 1b. Time filter not given -> include
			 * 2. Feature has time information
			 * 2a. Time filter is given -> check intersection
			 * 2b. Time filter not given -> include
			 */
			if (filter.timeExtent.getLow() == null && filter.timeExtent.getHigh() == null) {
				// include
			} else {
				Extent<DateTime> t = domainMeta.getTimeExtent();
				if (t == null || !t.intersects(filter.timeExtent)) {
					continue;
				}
			}
			
			/*
			 * Vertical filtering rules:
			 * 1. Feature has no vertical information
			 * 1a. Vertical filter is given -> skip feature
			 * 1b. Vertical filter not given -> include
			 * 2. Feature has vertical information
			 * 2a. Vertical filter is given -> check intersection
			 * 2b. Vertical filter not given -> include
			 */
			if (filter.verticalExtent.getLow() == null && filter.verticalExtent.getHigh() == null) {
				// include
			} else {
				Extent<Double> v = domainMeta.getVerticalExtent();
				if (v == null || !v.intersects(filter.verticalExtent)) {
					continue;
				}
			}
			
			Map feature;
			if (asGeojson) {
				 feature = FeatureResource.getFeatureGeoJson(dataset, meta, getRootRef().toString(), 
						details, subset).build();
			} else {
				feature = FeatureResource.getFeatureCovJson(dataset, meta, getRootRef().toString(), 
						details, subset).build();
			}
			jsonFeatures.add(feature);
		}
		
		Builder j = ImmutableMap.builder();
		if (asGeojson) {
			j.put("type", "FeatureCollection")
			 .put("features", jsonFeatures);	
		} else {
			Builder ldContext = ImmutableMap.builder();
			
			Dataset dataset_ = dataset.get();
			List jsonParams = new LinkedList();
			for (String paramId : dataset_.getVariableIds()) {
				Parameter param = dataset_.getVariableMetadata(paramId).getParameter();
				Map m = ParameterResource.getParamJson(dataset_.getId(), param).build();
				jsonParams.add(m);
				ldContext.put(paramId, ImmutableMap.of(
						"@id", ParameterResource.getParamUrl(datasetId, paramId, getRootRef().toString()),
						"@type", "@id"
						));
			}
			
			j.put("@context", ImmutableList.of(
					"https://rawgit.com/neothemachine/coveragejson/master/contexts/coveragejson-base.jsonld",
					ldContext
						.put("qudt", "http://qudt.org/1.1/schema/qudt#")
						.put("unit", "qudt:unit")
						.put("symbol", "qudt:symbol")
						.build()
					))
			 .put("id", datasetUrl + "/features")
			 .put("type", "CoverageCollection")
			 .put("parameters", jsonParams)
			 .put("coverages", jsonFeatures);
		}
		
		return j;
	}
	
	@Get("covjson|covcbor|covmsgpack")
	public Representation covjson() throws IOException, EdalException {
		Map j = getFeaturesJson(false).build();
		return App.getCovJsonRepresentation(this, j);
	}
		
	@Get("geojson")
	public Representation geojson() throws IOException, EdalException {
		Map j = getFeaturesJson(true)
				.put("@context", FeatureResource.GeoJSONLDContext).build();
		
		JacksonRepresentation r = new JacksonRepresentation(j);
		r.setMediaType(App.GeoJSON);
		if (!App.acceptsJSON(this)) {
			r.getObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
		}
		return r;
	}

}