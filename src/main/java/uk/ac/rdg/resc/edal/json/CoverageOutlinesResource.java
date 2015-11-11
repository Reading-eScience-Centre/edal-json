package uk.ac.rdg.resc.edal.json;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.joda.time.DateTime;
import org.restlet.data.Header;
import org.restlet.data.Reference;
import org.restlet.ext.jackson.JacksonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.restlet.util.Series;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

import uk.ac.rdg.resc.edal.dataset.Dataset;
import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.feature.GridFeature;
import uk.ac.rdg.resc.edal.feature.ProfileFeature;
import uk.ac.rdg.resc.edal.geometry.BoundingBox;
import uk.ac.rdg.resc.edal.json.CoverageResource.FeatureMetadata;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class CoverageOutlinesResource extends ServerResource {
			
	public static Builder getOutlinesAsGeoJson(Supplier<Dataset> dataset, FeatureMetadata meta, String rootUri, 
			SubsetConstraint subset) throws EdalException {
		String coverageUrl = rootUri + "/datasets/" + meta.datasetId + "/coverages/" + meta.featureId;
		String outlinesUrl = coverageUrl + "/outlines";
		
		// TODO possibly have to convert to WGS84
		BoundingBox bb = meta.domainMeta.getBoundingBox();
		
		Map geometry;
		// Profile -> Point
		// Grid -> Bbox Polygon (could have actual outline, but for now just bbox)
		// Trajectory -> LineString (not supported in EDAL yet)
		String type;
		if (meta.type.isAssignableFrom(ProfileFeature.class)) {
			type = "Profile";
			geometry = ImmutableMap.of(
					"type", "Point",
					"coordinates", ImmutableList.of(bb.getMinX(), bb.getMinY())
					);
		} else if (meta.type.isAssignableFrom(GridFeature.class)) {
			type = "Grid";
			geometry = ImmutableMap.of(
					"type", "Polygon",
					"coordinates", ImmutableList.of(ImmutableList.of(
							// counter-clockwise as recommended in https://tools.ietf.org/html/draft-butler-geojson-05
							// to improve dateline handling
							ImmutableList.of(bb.getMinX(), bb.getMinY()),
							ImmutableList.of(bb.getMaxX(), bb.getMinY()),
							ImmutableList.of(bb.getMaxX(), bb.getMaxY()),
							ImmutableList.of(bb.getMinX(), bb.getMaxY()),
							ImmutableList.of(bb.getMinX(), bb.getMinY())
							))
					);
		} else {
			throw new IllegalStateException(meta.type.getName() + " not supported");
		}
		
		List<String> paramTitles = meta.rangeMeta.getParameters().stream().map(p -> p.getTitle()).collect(Collectors.toList());

		Builder props = ImmutableMap.builder()
				.put("domainType", type)
				.put("title", meta.name)
				.put("parameters", paramTitles)
				.put("coverage", coverageUrl + subset.getCanonicalSubsetQueryString());
		/*
		 * Vertical extent is included in properties in a lax way.
		 * It is not on the same level as "bbox" and "when" because
		 * the bbox already includes altitude if it is in meters above WGS84.
		 * We don't want to duplicate or extend this concept and claim some
		 * general format here. Therefore the extent lives in properties
		 * as informal additional information.
		 * TODO If the vertical CRS is height above WGS84 then the bbox
		 *      should be used instead. 
		 */
		Extent<Double> v = meta.domainMeta.getVerticalExtent();
		if (v != null) {
			// TODO ambiguous if increases downwards (negate value?)
			props.put("verticalExtent", ImmutableList.of(v.getLow(), v.getHigh()))
			     .put("verticalUnits", meta.domainMeta.getVerticalCrs().getUnits());
		}
		
		Builder j = ImmutableMap.builder()
				.put("type", "Feature")
				.put("id", outlinesUrl + subset.getCanonicalSubsetQueryString())
				.put("bbox", ImmutableList.of(bb.getMinX(), bb.getMinY(), bb.getMaxX(), bb.getMaxY()))
				.put("properties", props.build())
				.put("geometry", geometry);
				
		Extent<DateTime> dt = meta.domainMeta.getTimeExtent();
		if (dt != null) {
			Map jsonTime;
			if (dt.getLow() == dt.getHigh()) {
				jsonTime = ImmutableMap.of(
						"type", "Instant",
						"datetime", dt.getLow().toString()
						);
			} else {
				jsonTime = ImmutableMap.of(
						"type", "Interval",
						"start", dt.getLow().toString(),
						"stop", dt.getHigh().toString()
						);
			}
			j.put("when", jsonTime);
		}
		
		return j;
	}
			
	@Get("geojson")
	public Representation geojson() throws IOException, EdalException {
		addLinkHeaders();
		
		String datasetId = Reference.decode(getAttribute("datasetId"));
		String coverageId = Reference.decode(getAttribute("coverageId"));
		SubsetConstraint subset = new SubsetConstraint(getQuery());
		
		String rootUri = getRootRef().toString();
		String outlinesUrl = rootUri + "/datasets/" + datasetId + "/coverages/" + coverageId + "/outlines";
		
		DatasetMetadata meta = DatasetResource.getDatasetMetadata(datasetId);
		FeatureMetadata featureMeta = meta.getFeatureMetadata(coverageId);
		Builder geojsonBuilder = getOutlinesAsGeoJson(meta.getLazyDataset(),
				featureMeta, rootUri, subset)
				.put("@context", ImmutableList.of(
						Constants.HydraContext,
						Constants.GeoJSONLDContext,
						ImmutableMap.of(
								Constants.CovAPIPrefix, Constants.CovAPINamespace,
								"api", Constants.CovAPIPrefix + ":api",
								"opensearchgeo", Constants.OpenSearchGeoNamespace,
								"opensearchtime", Constants.OpenSearchTimeNamespace
								)
						));

		Map apiIriTemplate = Hydra.getApiIriTemplate(outlinesUrl, false, true);
		geojsonBuilder.put("api", apiIriTemplate);
		
		Map geojson = geojsonBuilder.build();
		JacksonRepresentation r = new JacksonRepresentation(geojson);
		r.setMediaType(App.GeoJSON);
		if (!App.acceptsJSON(this)) {
			r.getObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
		}
		return r;
	}
		
	void addLinkHeaders() {
		Series<Header> headers = this.getResponse().getHeaders();
		
		String datasetId = Reference.decode(getAttribute("datasetId"));
		String collectionUrl = getRootRef() + "/datasets/" + datasetId + "/outlines";
		
		SubsetConstraint subset = new SubsetConstraint(getQuery());
		headers.add(new Header("Link", "<" + collectionUrl + subset.getCanonicalSubsetQueryString() + ">; rel=\"collection\""));
	}


}
