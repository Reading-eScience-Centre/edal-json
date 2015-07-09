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
import uk.ac.rdg.resc.edal.util.GISUtils;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class FeaturesResource extends ServerResource {
	
	private Map getFeaturesJson() throws IOException, EdalException {
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
			
			jsonFeatures.add(FeatureResource.getFeatureJson(dataset, meta, getRootRef().toString(), 
					details, subset));
		}
		
		Map j = ImmutableMap.of(
				"@context", "/static/contexts/FeatureCollection.jsonld",
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