package uk.ac.rdg.resc.edal.json;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.restlet.data.Reference;
import org.restlet.ext.jackson.JacksonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

import uk.ac.rdg.resc.edal.dataset.Dataset;
import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.geometry.BoundingBox;
import uk.ac.rdg.resc.edal.metadata.Parameter;
import uk.ac.rdg.resc.edal.util.GISUtils;

public class DatasetResource extends ServerResource {
	
	// cache with datasetId as key
	private static Map<String,DatasetMetadata> datasetMetadataCache = new HashMap<>();

	public static Builder getDatasetJson(String datasetId, String rootUri) throws IOException {
		return getDatasetJson(datasetId, rootUri, false);
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Builder getDatasetJson(String datasetId, String rootUri, boolean skipDetails) throws IOException {
		Dataset dataset = Utils.getDataset(datasetId);
		String datasetUrl = rootUri + "/datasets/" + dataset.getId();
		
		List jsonParams = new LinkedList();
		if (!skipDetails) {
			for (String paramId : dataset.getVariableIds()) {
				Parameter param = dataset.getVariableMetadata(paramId).getParameter();
				Map m = ParameterResource.getParamJson(dataset.getId(), param).build();
				jsonParams.add(m);
			}
		}
		
		DatasetMetadata datasetMeta = DatasetResource.getDatasetMetadata(datasetId);
		DomainMetadata domainMeta = datasetMeta.getDomainMetadata();
		BoundingBox bb = domainMeta.getBoundingBox();
		
		long count = datasetMeta.getFeatureTypes().stream().mapToLong(datasetMeta::getFeatureCount).sum();		
		List<String> jsonFeatureTypes = Utils.mapList(datasetMeta.getFeatureTypes(), FeatureTypes::getName);
		
		Builder b = ImmutableMap.builder()
				.put("id", datasetUrl)
				.put("type", "Dataset")
				.put("title", "N/A (datasets in EDAL don't have a title, only at WMS level)")
				.put("license", "http://creativecommons.org/licenses/by/3.0/");
		
		// see GeoDCAT-AP for spatial and temporal spec
		b.put("spatial", ImmutableMap.of(
				"type", "Location",
				"geometry", "POLYGON((" + 
						bb.getMinX() + " " + bb.getMinY() + "," +
						bb.getMaxX() + " " + bb.getMinY() + "," +
						bb.getMaxX() + " " + bb.getMaxY() + "," +
						bb.getMinX() + " " + bb.getMaxY() + "," +
						bb.getMinX() + " " + bb.getMinY() + "))"
				));
		
		if (domainMeta.getTimeExtent() != null) {
			Extent<DateTime> ex = domainMeta.getTimeExtent();
			// TODO CRS if non-default
			//  -> does EDAL currently support other calendars etc.?
			
			// GeoDCAT
			b.put("temporal", ImmutableMap.of(
					"type", "Interval",
					"start", ex.getLow().toString(),
					"end", ex.getHigh().toString()
					));
		}
		
		if (!skipDetails) {
				b.put("distributions", ImmutableList.of(ImmutableMap.of(
							"url", datasetUrl + "/features.covjson",
							"mediaType", "application/cov+json"
							),
					ImmutableMap.of(
							"url", datasetUrl + "/features.covjsonb",
							"mediaType", "application/cov+json;encoding=cbor"
							),
					ImmutableMap.of(
							"url", datasetUrl + "/features.geojson",
							"mediaType", "application/vnd.geo+json"
							)));
		}
		
		// non-standard metadata
		
		b.put("features", datasetUrl + "/features")
		 .put("featureCount", count)
		 .put("featureTypes", jsonFeatureTypes);
		
		if (!skipDetails) {
			b.put("parameters", jsonParams);
		}
		/*
		 * In addition to the GeoDCAT bounding box we include a direct array-based
		 * bounding box as well for convenience of web clients.
		 * This is not exposed as RDF.
		 */
		// TODO CRS if non-default
		b.put("bbox", ImmutableList.of(bb.getMinX(), bb.getMinY(), bb.getMaxX(), bb.getMaxY()));
		
		if (!GISUtils.isWgs84LonLat(bb.getCoordinateReferenceSystem())) {
			b.put("horizontalCrs", Utils.getCrsUri(bb.getCoordinateReferenceSystem()));
		}
		
		if (domainMeta.getVerticalExtent() != null) {
			Extent<Double> ex = domainMeta.getVerticalExtent();
			b.put("verticalExtent", ImmutableList.of(ex.getLow(), ex.getHigh()));
			b.put("verticalCrs", "TODO vertical/crs/uri/");
		}
		
		return b;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Get("jsonld")
	public Representation json() throws EdalException, IOException {
		String datasetId = Reference.decode(getAttribute("datasetId"));
		Dataset dataset = Utils.getDataset(datasetId);
		
		Builder ldContext = ImmutableMap.builder();
		for (String paramId : dataset.getVariableIds()) {
			ldContext.put(paramId, ParameterResource.getParamUrl(datasetId, paramId, getRootRef().toString()));
		}
		
		String rootUri = getRootRef().toString();
		Map j = getDatasetJson(datasetId, rootUri)
					// TODO how to get URL of other static Application?
					.put("@context", ImmutableList.of("/static/contexts/Dataset.jsonld", ldContext.build()))
					.build();
		
		JacksonRepresentation r = new JacksonRepresentation(j);
		r.setMediaType(App.JSONLD);
		if (!App.acceptsJSON(this)) {
			r.getObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
		}
		return r;
	}

	/**
	 * cache
	 */
	public static DatasetMetadata getDatasetMetadata(String datasetId) throws IOException, EdalException {
		DatasetMetadata datasetMeta = DatasetResource.datasetMetadataCache.get(datasetId);
		if (datasetMeta == null) {
			datasetMeta = new DatasetMetadata(datasetId);
			DatasetResource.datasetMetadataCache.put(datasetId, datasetMeta);
		}
		return datasetMeta;
	}


}