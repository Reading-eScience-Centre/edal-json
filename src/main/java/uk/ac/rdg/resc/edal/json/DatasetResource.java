package uk.ac.rdg.resc.edal.json;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.restlet.data.Reference;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

import uk.ac.rdg.resc.edal.dataset.Dataset;
import uk.ac.rdg.resc.edal.exceptions.DataReadingException;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.exceptions.VariableNotFoundException;
import uk.ac.rdg.resc.edal.feature.DiscreteFeature;
import uk.ac.rdg.resc.edal.feature.Feature;
import uk.ac.rdg.resc.edal.metadata.Parameter;
import uk.ac.rdg.resc.edal.metadata.VariableMetadata;

import com.google.common.collect.ImmutableMap;

public class DatasetResource extends ServerResource {

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Get("json")
	public Representation json() throws IOException, EdalException {
		String datasetId = Reference.decode(getAttribute("datasetId"));
		Dataset dataset = Utils.getDataset(datasetId);
		
		String datasetUrl = getReference().toString();
		
		List jsonFeatures = new LinkedList();
		
		for (String featureId : dataset.getFeatureIds()) {
			Feature feature;
			try {
				feature = dataset.readFeature(featureId);
			} catch (DataReadingException | VariableNotFoundException e) {
				e.printStackTrace();
				continue;
			}
			if (!(feature instanceof DiscreteFeature)) {
				continue;
			}
			
			jsonFeatures.add(ImmutableMap.of(
				"id", datasetUrl + "/features/" + feature.getId(),
				"title", feature.getName()
				));
		}
		
		List jsonParams = new LinkedList();
		// TODO check what the relation between variable and parameter is
		for (String paramId : dataset.getVariableIds()) {
			Parameter param = dataset.getVariableMetadata(paramId).getParameter();
			Map m = ImmutableMap.of(
					"id", datasetUrl + "/params/" + param.getId(),
					"title", param.getTitle(),
					"description", param.getDescription(),
					"uom", param.getUnits()
					);
			
			if (param.getStandardName() != null) {
				// TODO translate into URI
				m = ImmutableMap.builder()
						.putAll(m)
					    .put("observedProperty", param.getStandardName())
					    .build(); 
			}
			jsonParams.add(m);
		}
		
		Map j = ImmutableMap.of(
				"id", datasetUrl,
				"title", "...",
				"features", jsonFeatures,
				"parameters", jsonParams
				);
		
		JsonRepresentation r = new JsonRepresentation(j);
		r.setIndenting(true);
		return r;
	}


}