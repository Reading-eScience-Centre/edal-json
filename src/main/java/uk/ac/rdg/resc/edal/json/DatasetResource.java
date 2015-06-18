package uk.ac.rdg.resc.edal.json;

import java.util.LinkedList;
import java.util.List;
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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class DatasetResource extends ServerResource {

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Get("json")
	public Representation json() throws VariableNotFoundException {
		String datasetId = Reference.decode(getAttribute("datasetId"));
		Dataset dataset = Utils.getDataset(datasetId);

		String datasetUrl = getRootRef().toString() + "/datasets/" + dataset.getId();
		
		List jsonParams = new LinkedList();
		// TODO check what the relation between variable and parameter is
		for (String paramId : dataset.getVariableIds()) {
			Parameter param = dataset.getVariableMetadata(paramId).getParameter();
			Map m = ParamResource.getParamJson(dataset, param, getRootRef().toString());
			jsonParams.add(m);
		}
		
		// TODO add spatiotemporal extent
		Map j = ImmutableMap.builder()
				// TODO how to get URL of other static Application?
				.put("@context", "/static/dataset.jsonld")
				.put("id", datasetUrl)
				.put("type", ImmutableList.of("dcat:Dataset"))
				.put("title", "TODO: where to get this from EDAL?")
				.put("parameters", jsonParams)
				.put("features", datasetUrl + "/features")
				.build();		
		
		JacksonRepresentation r = new JacksonRepresentation(j);
		if (!App.acceptsJSON(getClientInfo())) {
			r.getObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
		}
		return r;
	}


}