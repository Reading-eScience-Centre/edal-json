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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

@SuppressWarnings({ "unchecked", "rawtypes" })
public class ParameterResource extends ServerResource {
	
	public static String getParamUrl(String datasetId, String variableId, String rootUri) {
		return rootUri + "/datasets/" + datasetId + "/params/" + variableId;
	}
	
	public static Builder getParamJson(String datasetId, Parameter param, String rootUri) {
		Builder j = ImmutableMap.builder()
				.put("id", getParamUrl(datasetId, param.getVariableId(), rootUri))
				.put("type", "Parameter")
				.put("description", ImmutableMap.of("en", param.getDescription()))
				// TODO translate EDAL units to qudt terms
				.put("unit", ImmutableMap.of(
						"id", "TODOhttp://qudt.org/vocab/unit#DegreeCelsius",
						// TODO read unit label from qudt ontology
						"symbol", param.getUnits()
						));
			
		String observedPropertyUri = null;
		if (param.getStandardName() != null) {
			observedPropertyUri = "http://vocab.nerc.ac.uk/standard_name/" + param.getStandardName();
		}	
		Builder obsProp = ImmutableMap.builder()
				.put("label", ImmutableMap.of("en", param.getTitle()));
		if (observedPropertyUri != null) {
			obsProp.put("id", observedPropertyUri);
		}
		j.put("observedProperty", obsProp.build());
		return j;
	}

	@Get("jsonld")
	public Representation json() throws VariableNotFoundException {
		String datasetId = Reference.decode(getAttribute("datasetId"));
		String paramId = Reference.decode(getAttribute("paramId"));
		Dataset dataset = Utils.getDataset(datasetId);
		Parameter param = dataset.getVariableMetadata(paramId).getParameter();
		
		Map j = getParamJson(dataset.getId(), param, getRootRef().toString())
					.put("@context", ImmutableList.of(
							Constants.CoverageJSONContext,
							ImmutableMap.of(param.getVariableId(), ImmutableMap.of(
									"@id", getParamUrl(datasetId, param.getVariableId(), getRootRef().toString()),
									"@type", "@id"
									))
									
							))
					.build();
		
		JacksonRepresentation r = new JacksonRepresentation(j);
		r.setMediaType(App.JSONLD);
		if (!App.acceptsJSON(this)) {
			r.getObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
		}
		return r;
	}


}