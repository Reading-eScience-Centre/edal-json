package uk.ac.rdg.resc.edal.json;

import java.io.IOException;
import java.util.HashMap;
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
import uk.ac.rdg.resc.edal.feature.DiscreteFeature;
import uk.ac.rdg.resc.edal.feature.Feature;
import uk.ac.rdg.resc.edal.geometry.BoundingBox;
import uk.ac.rdg.resc.edal.json.FeatureResource.Details;
import uk.ac.rdg.resc.edal.json.FeatureResource.FeatureMetadata;
import uk.ac.rdg.resc.edal.util.GISUtils;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class FeaturesResource extends ServerResource {
	
	static class DatasetMetadata {
		private final Map<String,FeatureMetadata> featureMetadata;
		private final String datasetId;
		public DatasetMetadata(String datasetId, Map<String,FeatureMetadata> featureMetadata) {
			this.datasetId = datasetId;
			this.featureMetadata = featureMetadata;
		}
		public Set<String> getFeatureIds() {
			return featureMetadata.keySet();
		}
		public FeatureMetadata getFeatureMetadata(String featureId) {
			return featureMetadata.get(featureId);
		}
		public Supplier<Dataset> getLazyDataset() {
			return Suppliers.memoize(() -> Utils.getDataset(datasetId));
		}
	}
	
	// cache with datasetId as key
	private static Map<String,DatasetMetadata> datasetMetadataCache = new HashMap<>();
	
	/**
	 * cache
	 */
	public static DatasetMetadata getDatasetMetadata(String datasetId) throws IOException, EdalException {
		DatasetMetadata datasetMeta = datasetMetadataCache.get(datasetId);
		if (datasetMeta == null) {
			Dataset dataset = Utils.getDataset(datasetId);
			
			Map<String,FeatureMetadata> featureMetadata = new HashMap<>();
			for (String featureId : dataset.getFeatureIds()) {
				Feature<?> feature = dataset.readFeature(featureId);
				if (!(feature instanceof DiscreteFeature)) {
					continue;
				}
				featureMetadata.put(featureId, new FeatureMetadata(
							datasetId,
							feature));
			}
			datasetMeta = new DatasetMetadata(datasetId, featureMetadata);
			datasetMetadataCache.put(datasetId, datasetMeta);
		}
		return datasetMeta;
	}

	private Map getFeaturesJson() throws IOException, EdalException {
		final String datasetId = Reference.decode(getAttribute("datasetId"));
		Details fallback = new Details(false, false, false);
		Details details = Details.from(getQueryValue("details"), fallback);
		Constraint filter = new Constraint(getQueryValue("filter"));
		Constraint subset = new Constraint(getQueryValue("subset"));
		
		DatasetMetadata datasetMeta = getDatasetMetadata(datasetId);
		
		String datasetUrl = getRootRef().toString() + "/datasets/" + datasetId;
		
		List jsonFeatures = new LinkedList();
				
		Supplier<Dataset> dataset = datasetMeta.getLazyDataset();
		
		for (String featureId : datasetMeta.getFeatureIds()) {
			FeatureMetadata meta = datasetMeta.getFeatureMetadata(featureId);
			DomainMetadata domainMeta = meta.domainMeta;
			RangeMetadata rangeMeta = meta.rangeMeta;
			
			if (filter.params != null) {
				Set<String> params = new HashSet<>(rangeMeta.getParameterIds());
				params.retainAll(filter.params);
				if (params.isEmpty()) continue;
			}
						
			if (filter.bbox != null) {
				// TODO we cannot use GeographicBoundingBox here as
				//  this cannot span the discontinuity...
				BoundingBox bb = domainMeta.getBoundingBox();
				
				if (!GISUtils.isWgs84LonLat(bb.getCoordinateReferenceSystem())) {
					throw new RuntimeException("only WGS84 supported currently");
				}
				DatelineBoundingBox geoBB = new DatelineBoundingBox(bb);
				if (!geoBB.intersects(filter.bbox)) {
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
			
			jsonFeatures.add(FeatureResource.getFeatureJson(dataset, meta, getRootRef().toString(), 
					details, subset));
		}
		
		Map j = ImmutableMap.of(
				"@context", "/static/FeatureCollection.jsonld",
				"id", datasetUrl + "/features",
				"type", "oml:ObservationCollectionTODO",
				"features", jsonFeatures
				);
		return j;
	}
	
	 
	@Get("json")
	public Representation json() throws IOException, EdalException {
		Map j = getFeaturesJson();
		
		JacksonRepresentation r = new JacksonRepresentation(j);
		if (!App.acceptsJSON(getClientInfo())) {
			r.getObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
		}
		return r;
	}
	
	@Get("msgpack")
	public Representation msgpack() throws IOException, EdalException {
		return new MessagePackRepresentation(getFeaturesJson());
	}


}