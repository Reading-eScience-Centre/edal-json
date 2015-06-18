package uk.ac.rdg.resc.edal.json;

import java.util.Map;

import org.restlet.data.Reference;
import org.restlet.ext.jackson.JacksonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

import uk.ac.rdg.resc.edal.dataset.Dataset;
import uk.ac.rdg.resc.edal.exceptions.VariableNotFoundException;
import uk.ac.rdg.resc.edal.metadata.Parameter;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.collect.ImmutableMap;

public class ParamResource extends ServerResource {
	
	public static Map getParamJson(Dataset dataset, Parameter param, String rootUri) {
		String paramUrl = rootUri + "/datasets/" + dataset.getId() + "/params/" + param.getVariableId();
		Map j = ImmutableMap.of(
				"id", paramUrl,
				"type", "mel:Parameter",
				"title", param.getTitle(),
				"description", param.getDescription(),
				"uom", param.getUnits()
				);
			
		if (param.getStandardName() != null) {
			// TODO translate into URI
			j = ImmutableMap.builder()
					.putAll(j)
				    .put("observedProperty", param.getStandardName())
				    .build();
		}
		return j;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Get("json")
	public Representation json() throws VariableNotFoundException {
		String datasetId = Reference.decode(getAttribute("datasetId"));
		String paramId = Reference.decode(getAttribute("paramId"));
		Dataset dataset = Utils.getDataset(datasetId);
		Parameter param = dataset.getVariableMetadata(paramId).getParameter();
		
		Map j = getParamJson(dataset, param, getRootRef().toString());
		j.put("@context", "/static/Dataset.jsonld");
		
		JacksonRepresentation r = new JacksonRepresentation(j);
		if (!App.acceptsJSON(getClientInfo())) {
			r.getObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
		}
		return r;
	}


}