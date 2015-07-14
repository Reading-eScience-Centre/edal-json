package uk.ac.rdg.resc.edal.json;

import java.util.Map;

import org.restlet.data.Disposition;
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
import com.google.common.collect.ImmutableMap.Builder;

@SuppressWarnings({ "unchecked", "rawtypes" })
public class ParamResource extends ServerResource {
	
	public static String getParamUrl(String datasetId, String variableId, String rootUri) {
		return rootUri + "/datasets/" + datasetId + "/params/" + variableId;
	}
	
	public static Builder getParamJson(Dataset dataset, Parameter param, String rootUri) {
		String paramUrl = getParamUrl(dataset.getId(), param.getVariableId(), rootUri);
		Builder j = ImmutableMap.builder()
				.put("id", paramUrl)
				.put("type", "mel:Parameter")
				.put("localId", param.getVariableId())
				.put("title", param.getTitle())
				.put("description", param.getDescription())
				.put("uom", param.getUnits());
			
		if (param.getStandardName() != null) {
			// TODO translate into URI
			j.put("observedProperty", param.getStandardName());
		}
		return j;
	}

	@Get("jsonld")
	public Representation json() throws VariableNotFoundException {
		String datasetId = Reference.decode(getAttribute("datasetId"));
		String paramId = Reference.decode(getAttribute("paramId"));
		Dataset dataset = Utils.getDataset(datasetId);
		Parameter param = dataset.getVariableMetadata(paramId).getParameter();
		
		Map j = getParamJson(dataset, param, getRootRef().toString())
					.put("@context", "/static/contexts/Dataset.jsonld")
					.build();
		
		JacksonRepresentation r = new JacksonRepresentation(j);
		r.setMediaType(App.JSONLD);
		if (!App.acceptsJSON(this)) {
			r.getObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
		}
		return r;
	}


}