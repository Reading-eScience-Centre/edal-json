package uk.ac.rdg.resc.edal.json;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.restlet.ext.jackson.JacksonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.collect.ImmutableMap;

import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.geometry.BoundingBox;
import uk.ac.rdg.resc.edal.util.GISUtils;

public class DatasetsResource extends ServerResource {
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Get("jsonld")
	public Representation json() throws EdalException, IOException {

		FilterConstraint filter = new FilterConstraint(getQueryValue("filter"));
		String rootUri = getRootRef().toString();
		String datasetsUrl = rootUri + "/datasets";
		
		// scan /datasets folder and fetch metadata
		
		List datasetsJson = new ArrayList();
		
		List<String> datasetIds = IOUtils.readLines(DatasetsResource.class.getClassLoader()
		        .getResourceAsStream(Utils.DATASETS_FOLDER.substring(1)), Charsets.UTF_8);
		for (String datasetId : datasetIds) {
			DatasetMetadata meta = DatasetResource.getDatasetMetadata(datasetId);
			DomainMetadata domainMeta = meta.getDomainMetadata();
			
			// filtering
			// TODO remove code duplication with FeaturesResource
			if (filter.bbox.isPresent()) {
				// TODO we cannot use GeographicBoundingBox here as
				//  this cannot span the discontinuity...
				BoundingBox bb = domainMeta.getBoundingBox();
				
				if (!GISUtils.isWgs84LonLat(bb.getCoordinateReferenceSystem())) {
					throw new RuntimeException("only WGS84 supported currently");
				}
				DatelineBoundingBox geoBB = new DatelineBoundingBox(bb);
				if (!geoBB.intersects(filter.bbox.get())) {
					continue;
				}
			}
			
			if (filter.timeExtent.getLow() == null && filter.timeExtent.getHigh() == null) {
				// include
			} else {
				Extent<DateTime> t = domainMeta.getTimeExtent();
				if (t == null || !t.intersects(filter.timeExtent)) {
					continue;
				}
			}
			
			if (filter.verticalExtent.getLow() == null && filter.verticalExtent.getHigh() == null) {
				// include
			} else {
				Extent<Double> v = domainMeta.getVerticalExtent();
				if (v == null || !v.intersects(filter.verticalExtent)) {
					continue;
				}
			}
			
			datasetsJson.add(DatasetResource.getDatasetJson(datasetId, rootUri).build());			
		}
		
		
		Map j = ImmutableMap.builder()
				.put("@context", "https://rawgit.com/ec-melodies/wp02-dcat/master/context.jsonld")
				.put("@type", "Catalog")
				.put("@id", datasetsUrl)
				.put("title", "Catalogue 1")
				.put("description", "This is my first catalogue.")
				.put("publisher", ImmutableMap.of(
						"type", "Organisation",
						"name", "University of Reading"
						))
				.put("license", "http://creativecommons.org/licenses/by/4.0/")
				.put("homepage", "http://rdg.ac.uk/demo/datasets")
				.put("datasets", datasetsJson)
				.build();		
		
		JacksonRepresentation r = new JacksonRepresentation(j);
		r.setMediaType(App.JSONLD);
		if (!App.acceptsJSON(this)) {
			r.getObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
		}
		return r;
	}

}