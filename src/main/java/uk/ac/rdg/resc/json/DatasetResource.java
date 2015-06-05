package uk.ac.rdg.resc.json;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.restlet.data.Reference;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import uk.ac.rdg.resc.edal.dataset.Dataset;
import uk.ac.rdg.resc.edal.dataset.DatasetFactory;
import uk.ac.rdg.resc.edal.dataset.cdm.CdmGridDatasetFactory;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.feature.Feature;

public class DatasetResource extends ServerResource {

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Get("json")
	public Representation json() throws IOException, EdalException {
		String datasetId = Reference.decode(getAttribute("datasetId"));
		Dataset dataset = Utils.getDataset(datasetId);
		
		String baseUrl = "http://foo/datasets/";
		
		List jsonFeatures = new LinkedList();
		for (String featureId : dataset.getFeatureIds()) {
			Feature feature = dataset.readFeature(featureId);
			jsonFeatures.add(ImmutableMap.of(
				"id", baseUrl + dataset.getId() + "/features/" + feature.getId(),
				"title", feature.getName()
				));
		}
		
		Map j = ImmutableMap.of(
				"id", baseUrl + dataset.getId(),
				"title", "...",
				"features", jsonFeatures
				);
		
		JsonRepresentation r = new JsonRepresentation(j);
		r.setIndenting(true);
		return r;
	}


}