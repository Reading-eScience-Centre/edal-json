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

import uk.ac.rdg.resc.edal.dataset.Dataset;
import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.geometry.BoundingBox;
import uk.ac.rdg.resc.edal.metadata.Parameter;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

public class DatasetResource extends ServerResource {
	
	// cache with datasetId as key
	private static Map<String,DatasetMetadata> datasetMetadataCache = new HashMap<>();

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Get("json")
	public Representation json() throws EdalException, IOException {
		String datasetId = Reference.decode(getAttribute("datasetId"));
		Dataset dataset = Utils.getDataset(datasetId);

		String rootUri = getRootRef().toString();
		String datasetUrl = rootUri + "/datasets/" + dataset.getId();
		
		List jsonParams = new LinkedList();
		for (String paramId : dataset.getVariableIds()) {
			Parameter param = dataset.getVariableMetadata(paramId).getParameter();
			Map m = ParamResource.getParamJson(dataset, param, getRootRef().toString());
			jsonParams.add(m);
		}
		
		DatasetMetadata datasetMeta = DatasetResource.getDatasetMetadata(datasetId);
		DomainMetadata domainMeta = datasetMeta.getDomainMetadata();
		BoundingBox bb = domainMeta.getBoundingBox();
		
		Map jsonCounts = new HashMap();
		for (Class<?> type : datasetMeta.getFeatureTypes()) {
			String name = FeatureTypes.getName(type);
			jsonCounts.put(name, datasetMeta.getFeatureCount(type));
		}
		
		Map jsonFeatureParams = new HashMap<>();
		for (Class<?> type : datasetMeta.getFeatureTypes()) {
			String name = FeatureTypes.getName(type);
			List<String> uris = Utils.mapList(datasetMeta.getFeatureParameters(type), param -> 
				ParamResource.getParamUrl(dataset.getId(), param.getVariableId(), rootUri));
			jsonFeatureParams.put(name, uris);
		}
		
		Builder b = ImmutableMap.builder()
				// TODO how to get URL of other static Application?
				.put("@context", "/static/dataset.jsonld")
				.put("id", datasetUrl)
				.put("type", ImmutableList.of("dcat:Dataset"))
				.put("title", "TODO: where to get this from EDAL?")
				.put("parameters", jsonParams)
				.put("features", datasetUrl + "/features")
				.put("featureCounts", jsonCounts)
				.put("featureParameters", jsonFeatureParams)
				// TODO CRS if non-default
				.put("bbox", ImmutableList.of(bb.getMinX(), bb.getMinY(), bb.getMaxX(), bb.getMaxY()));
		if (domainMeta.getVerticalExtent() != null) {
			Extent<Double> ex = domainMeta.getVerticalExtent();
			b.put("verticalExtent", ImmutableList.of(ex.getLow(), ex.getHigh()));
			// TODO CRS if non-default
		}
		if (domainMeta.getTimeExtent() != null) {
			Extent<DateTime> ex = domainMeta.getTimeExtent();
			b.put("timeExtent", ImmutableList.of(ex.getLow().toString(), ex.getHigh().toString()));
			// TODO CRS if non-default
		}		
		
		Map j = b.build();
		
		JacksonRepresentation r = new JacksonRepresentation(j);
		if (!App.acceptsJSON(getClientInfo())) {
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