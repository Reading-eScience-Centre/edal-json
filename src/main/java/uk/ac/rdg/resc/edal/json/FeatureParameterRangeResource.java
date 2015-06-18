package uk.ac.rdg.resc.edal.json;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;

import org.restlet.data.MediaType;
import org.restlet.data.Reference;
import org.restlet.ext.jackson.JacksonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

import uk.ac.rdg.resc.edal.dataset.Dataset;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.feature.DiscreteFeature;
import uk.ac.rdg.resc.edal.metadata.Parameter;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.collect.ImmutableMap;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class FeatureParameterRangeResource extends ServerResource {
		
	private Map rangeData() throws IOException, EdalException {
		String datasetId = Reference.decode(getAttribute("datasetId"));
		String featureId = Reference.decode(getAttribute("featureId"));
		String parameterId = Reference.decode(getAttribute("parameterId"));
		
		Dataset dataset = Utils.getDataset(datasetId);
		DiscreteFeature feature;
		try {
			feature = (DiscreteFeature) dataset.readFeature(featureId);
		} catch (ClassCastException e) {
			throw new IllegalArgumentException("Only discrete features are supported");
		}
		Parameter param = feature.getParameter(parameterId);
		
		String parameterRangeUrl = getRootRef().toString() + "/datasets/" + dataset.getId() +
				"/features/" + feature.getId() + "/range/" + param.getId();
		
		Map j = ImmutableMap.of(
				"id", parameterRangeUrl,
				"values", FeatureResource.getValues(feature.getValues(param.getId()))
				);
		return j;
	}

	@Get("json")
	public Representation json() throws IOException, EdalException {
		Map j = rangeData();
		
		JacksonRepresentation r = new JacksonRepresentation(j);
		if (!App.acceptsJSON(getClientInfo())) {
			r.getObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
		}
		
		// TODO think about caching strategy
		Date exp = Date.from(LocalDate.now().plusDays(1).atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
		r.setExpirationDate(exp);
		return r;
	}
	
	@Get("msgpack")
	public Representation msgpack() throws IOException, EdalException {
		Map j = rangeData();
		Representation r = new MessagePackRepresentation(j);
		
		// TODO think about caching strategy
		Date exp = Date.from(LocalDate.now().plusDays(1).atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
		r.setExpirationDate(exp);
		return r;
	}
		
}
