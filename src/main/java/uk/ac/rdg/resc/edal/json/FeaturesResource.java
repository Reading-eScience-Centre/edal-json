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

public class FeaturesResource extends ServerResource {

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Get("json")
	public Representation json() throws IOException, EdalException {
		String datasetId = Reference.decode(getAttribute("datasetId"));
		FeatureResource.Details details = FeatureResource.Details.from(getQueryValue("details"));
		Dataset dataset = Utils.getDataset(datasetId);
		
		String datasetUrl = getRootRef().toString() + "/datasets/" + dataset.getId();
		
		List jsonFeatures = new LinkedList();
		
		for (String featureId : dataset.getFeatureIds()) {
			Feature feature;
			try {
				// TODO how resource intensive is this operation?
				feature = dataset.readFeature(featureId);
			} catch (DataReadingException | VariableNotFoundException e) {
				e.printStackTrace();
				continue;
			}
			if (!(feature instanceof DiscreteFeature)) {
				continue;
			}
			DiscreteFeature discreteFeat = (DiscreteFeature) feature;
			
			// TODO apply search filter
			
			
			jsonFeatures.add(FeatureResource.getFeatureJson(dataset, discreteFeat, getRootRef().toString(), 
					details));
		}
		
		Map j = ImmutableMap.of(
				"id", datasetUrl + "/features",
				"features", jsonFeatures
				);
		
		JsonRepresentation r = new JsonRepresentation(j);
		r.setIndenting(true);
		return r;
	}


}