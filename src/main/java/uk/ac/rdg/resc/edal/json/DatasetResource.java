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

public class DatasetResource extends ServerResource {
	
	// cache with datasetId as key
	private static Map<String,DatasetMetadata> datasetMetadataCache = new HashMap<>();
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Builder getDatasetJson(String datasetId, String rootUri) throws IOException {
		Dataset dataset = Utils.getDataset(datasetId);
		String datasetUrl = rootUri + "/datasets/" + dataset.getId();
		
		DatasetMetadata datasetMeta = DatasetResource.getDatasetMetadata(datasetId);
		DomainMetadata domainMeta = datasetMeta.getDomainMetadata();
		BoundingBox bb = domainMeta.getBoundingBox();
		
		Builder b = ImmutableMap.builder()
				.put("@id", datasetUrl)
				.put("@type", "Dataset")
				// datasets in EDAL don't have a title, only at WMS level
				.put("title", dataset.getId())
				.put("license", "http://creativecommons.org/licenses/by/4.0/");
		
		// see GeoDCAT-AP for spatial and temporal spec
		// TODO should always be WGS84, convert if necessary
		b.put("spatial", ImmutableMap.of(
				"@type", "Location",
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
					"@type", "Interval",
					"startDate", ex.getLow().toString(),
					"endDate", ex.getHigh().toString()
					));
		}
		
		b.put("distributions", ImmutableList.of(ImmutableMap.of(
					"accessURL", datasetUrl + "/coverages",
					"mediaType", "application/prs.coverage+json"
					),
			ImmutableMap.of(
					"accessURL", datasetUrl + "/coverages",
					"mediaType", "application/prs.coverage+cbor"
					),
			ImmutableMap.of(
					"accessURL", datasetUrl + "/coverages",
					"mediaType", "application/vnd.geo+json"
					)));
		
		// non-standard metadata
		List jsonParams = new LinkedList<>();
		for (String paramId : dataset.getVariableIds()) {
			Parameter param = dataset.getVariableMetadata(paramId).getParameter();
			Object m = ParameterResource.getParamJson(dataset.getId(), param, rootUri).build().get("observedProperty");
			jsonParams.add(m);
		}
		b.put("observedProperties", jsonParams);
		
		return b;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Get("jsonld")
	public Representation json() throws EdalException, IOException {
		String datasetId = Reference.decode(getAttribute("datasetId"));

		String rootUri = getRootRef().toString();
		Map j = getDatasetJson(datasetId, rootUri)
					.put("@context", "https://rawgit.com/ec-melodies/wp02-dcat/master/context.jsonld")
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