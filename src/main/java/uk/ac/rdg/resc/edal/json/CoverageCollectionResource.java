package uk.ac.rdg.resc.edal.json;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.joda.time.DateTime;
import org.restlet.data.Form;
import org.restlet.data.Header;
import org.restlet.data.Reference;
import org.restlet.ext.jackson.JacksonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.restlet.util.Series;

import uk.ac.rdg.resc.edal.dataset.Dataset;
import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.geometry.BoundingBox;
import uk.ac.rdg.resc.edal.json.CoverageResource.Embed;
import uk.ac.rdg.resc.edal.json.CoverageResource.FeatureMetadata;
import uk.ac.rdg.resc.edal.metadata.Parameter;
import uk.ac.rdg.resc.edal.util.GISUtils;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class CoverageCollectionResource extends ServerResource {
	
	public static final int DEFAULT_COVERAGES_PER_PAGE = 100;
	public static final int DEFAULT_GEOJSON_FEATURES_PER_PAGE = 1000;
	
	// TODO this should depend on coverage size and whether range/domain is embedded or not
	public static final int MAXIMUM_COVERAGES_PER_PAGE = 10000;
	public static final int MAXIMUM_GEOJSON_FEATURES_PER_PAGE = 100000;
	
	public static final String FilterByCoordinateURI = "http://coverageapi.org/def#filterByCoordinate";
	
	class Paging {
		int currentPage = 1;
		int defaultElementsPerPage;
		int elementsPerPage;
		int totalElements;
		int totalPages;
		int beginOffset;
		int endOffset;
		Paging(int defaultPerPage, int maximumPerPage) {
			this.defaultElementsPerPage = defaultPerPage;
			String page = CoverageCollectionResource.this.getQueryValue("page");
			if (page != null) {
				this.currentPage = Integer.parseInt(page);
				if (this.currentPage < 1) {
					throw new IllegalArgumentException("page must be >= 1");
				}
			}
			String perPage = CoverageCollectionResource.this.getQueryValue("num");
			if (perPage != null) {
				this.elementsPerPage = Integer.parseInt(perPage);
				if (this.elementsPerPage < 1) {
					throw new IllegalArgumentException("num must be >= 1");
				}
				if (this.elementsPerPage > maximumPerPage) {
					throw new IllegalArgumentException("num must be <= " + maximumPerPage);
				}
			} else {
				this.elementsPerPage = defaultPerPage;
			}
			this.beginOffset = (this.currentPage-1) * this.elementsPerPage;
			this.endOffset = this.currentPage * this.elementsPerPage - 1;
		}
		
		void setTotalElements(int total) {
			this.totalElements = total;
			if (this.totalElements == 0) {
				this.totalPages = 1;
			} else {
				this.totalPages = (int) Math.ceil((double)this.totalElements / this.elementsPerPage);
			}
			if (this.currentPage > this.totalPages) {
				throw new IllegalArgumentException("page must be <= " + this.totalPages);
			}
			if (this.endOffset > this.totalElements-1) {
				this.endOffset = this.totalElements-1;
			}
		}
	}
	
	private Builder getFeaturesJson(boolean asGeojson, Paging paging) throws IOException, EdalException {
		final String datasetId = Reference.decode(getAttribute("datasetId"));
		Embed defaultEmbed = new Embed(false, false, false);
		Embed embed = Embed.from(getQueryValue("embed"), defaultEmbed);
		SubsetConstraint subset = new SubsetConstraint(getQueryValue("subsetByCoordinate"));
		FilterConstraint filter = new FilterConstraint(getQueryValue("filterByCoordinate"), subset);
		
		DatasetMetadata datasetMeta = DatasetResource.getDatasetMetadata(datasetId);
		
		String datasetUrl = getRootRef().toString() + "/datasets/" + datasetId;
		
		List jsonFeatures = new LinkedList();
				
		Supplier<Dataset> dataset = datasetMeta.getLazyDataset();
				
		int totalCount = 0;
		List<String> featureIdsInPage = new LinkedList<>();
		for (String featureId : datasetMeta.getFeatureIds()) {
			FeatureMetadata meta = datasetMeta.getFeatureMetadata(featureId);
			DomainMetadata domainMeta = meta.domainMeta;
			RangeMetadata rangeMeta = meta.rangeMeta;
			
			if (filter.type.isPresent()) {
				if (!filter.type.get().isAssignableFrom(meta.type)) continue;
			}
			
			if (filter.params.isPresent()) {
				Set<String> params = new HashSet<>(rangeMeta.getParameterIds());
				params.retainAll(filter.params.get());
				if (params.isEmpty()) continue;
			}
						
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
			
			/*
			 * Time filtering rules:
			 * 1. Feature has no time information
			 * 1a. Time filter is given -> ??? probably skip feature
			 * 1b. Time filter not given -> include
			 * 2. Feature has time information
			 * 2a. Time filter is given -> check intersection
			 * 2b. Time filter not given -> include
			 */
			if (filter.timeExtent.getLow() == null && filter.timeExtent.getHigh() == null) {
				// include
			} else {
				Extent<DateTime> t = domainMeta.getTimeExtent();
				if (t == null || !t.intersects(filter.timeExtent)) {
					continue;
				}
			}
			
			/*
			 * Vertical filtering rules:
			 * 1. Feature has no vertical information
			 * 1a. Vertical filter is given -> skip feature
			 * 1b. Vertical filter not given -> include
			 * 2. Feature has vertical information
			 * 2a. Vertical filter is given -> check intersection
			 * 2b. Vertical filter not given -> include
			 */
			if (filter.verticalExtent.getLow() == null && filter.verticalExtent.getHigh() == null) {
				// include
			} else {
				Extent<Double> v = domainMeta.getVerticalExtent();
				if (v == null || !v.intersects(filter.verticalExtent)) {
					continue;
				}
			}
			
			++totalCount;
			
			// check if in current page, otherwise skip
			if (paging.beginOffset <= totalCount-1 && totalCount-1 <= paging.endOffset) {
				featureIdsInPage.add(featureId);
			}
		}
		paging.setTotalElements(totalCount);
		
		for (String featureId : featureIdsInPage) {		
			Map feature;
			FeatureMetadata meta = datasetMeta.getFeatureMetadata(featureId);
			if (asGeojson) {
				 feature = CoverageResource.getCoverageGeoJson(dataset, meta, getRootRef().toString(), 
						embed, subset).build();
			} else {
				feature = CoverageResource.getCoverageCovJson(dataset, meta, getRootRef().toString(), 
						embed, subset).build();
			}
			jsonFeatures.add(feature);
		}
		
		
		Builder j = ImmutableMap.builder();
		if (asGeojson) {
			j.put("type", "FeatureCollection")
			 .put("features", jsonFeatures);	
		} else {
			Builder ldContext = ImmutableMap.builder();
			
			Dataset dataset_ = dataset.get();
			Builder jsonParams = ImmutableMap.builder();
			for (String paramId : dataset_.getVariableIds()) {
				Parameter param = dataset_.getVariableMetadata(paramId).getParameter();
				Map m = ParameterResource.getParamJson(dataset_.getId(), param, getRootRef().toString()).build();
				jsonParams.put(paramId, m);
				ldContext.put(paramId, ImmutableMap.of(
						"@id", ParameterResource.getParamUrl(datasetId, paramId, getRootRef().toString()),
						"@type", "@id"
						));
			}
			
			j.put("@context", ImmutableList.of(
					"https://rawgit.com/reading-escience-centre/coveragejson/master/contexts/coveragejson-base.jsonld",
					ldContext
						.put("qudt", "http://qudt.org/1.1/schema/qudt#")
						.put("unit", "qudt:unit")
						.put("symbol", "qudt:symbol")
						.build()
					))
			 .put("id", datasetUrl + "/coverages")
			 .put("type", "CoverageCollection")
			 .put("parameters", jsonParams.build())
			 .put("coverages", jsonFeatures);
		}
		
		// TODO how to put metadata in non-default JSON-LD graph?
		// see http://ruben.verborgh.org/blog/2015/10/06/turtles-all-the-way-down/
		j.put("totalCount", totalCount);
		j.put("perPage", paging.elementsPerPage);
		
		return j;
	}
	
	String getQueryString(Map<String,String> params) {
		Form q = new Form(getQuery().getQueryString());
		for (Entry<String,String> entry : params.entrySet()) {
			q.set(entry.getKey(), entry.getValue());
		}
		return q.isEmpty() ? "" : "?" + q.getQueryString();
	}
	
	void setPagingHeaders(Paging paging) {
		Series<Header> headers = this.getResponse().getHeaders();
		
		String datasetId = Reference.decode(getAttribute("datasetId"));
		String baseUrl = getRootRef().toString() + "/datasets/" + datasetId + "/coverages";
		
		Map<String,String> params = new HashMap<>();
		if (paging.defaultElementsPerPage != paging.elementsPerPage) {
			params.put("num", String.valueOf(paging.elementsPerPage));
		}
		
		Map<String,String> links = new HashMap<>();
				
		if (paging.currentPage < paging.totalPages) {
			params.put("page", String.valueOf(paging.currentPage+1));
			links.put("next", baseUrl + getQueryString(params));
		} else if (paging.currentPage > 1) {
			if (paging.currentPage != 2) {
				params.put("page", String.valueOf(paging.currentPage-1));
			}
			links.put("prev", baseUrl + getQueryString(params));
		}
		if (paging.totalPages > 1) {
			params.put("page", "1");
			links.put("first", baseUrl + getQueryString(params));
			
			params.put("page", String.valueOf(paging.totalPages));
			links.put("last", baseUrl + getQueryString(params));
		}
		
		for (String rel : links.keySet()) {
			String url = links.get(rel);
			headers.add(new Header("Link", "<" + url + ">; rel=\"" + rel + "\""));
		}
	}
		
	@Get("covjson|covcbor|covmsgpack")
	public Representation covjson() throws IOException, EdalException {
		/*
		 * Note that type=CoverageCollection is not included as a Link header since this
		 * is not easily possible with paged collections. A page /coverages?page=2 is a partial view
		 * and not the collection itself (the "id" inside the JSON also refers to /coverages only).
		 * Similarly, this applies to /coverages?num=30 where it is not clear what type this should be.
		 * Both of those redirect to their ?page=n version.
		 */		
		
		Series<Header> headers = this.getResponse().getHeaders();
		// FIXME check if this is semantically correct; use hydra IRITemplate in JSON and see if it matches
		headers.add(new Header("Link", "<" + FilterByCoordinateURI + ">; rel=\"" + CoverageResource.CapabilityURI + "\""));
		headers.add(new Header("Link", "<" + CoverageResource.SubsetByCoordinateURI + ">; rel=\"" + CoverageResource.CapabilityURI + "\""));
		headers.add(new Header("Link", "<" + CoverageResource.EmbedURI + ">; rel=\"" + CoverageResource.CapabilityURI + "\""));
		
		Paging paging = new Paging(DEFAULT_COVERAGES_PER_PAGE, MAXIMUM_COVERAGES_PER_PAGE);
		Map j = getFeaturesJson(false, paging).build();
		
		if (paging.totalPages > 1 && paging.currentPage == 1 && getReference().getQueryAsForm().getFirstValue("page") == null) {
			getResponse().redirectPermanent(getReference().addQueryParameter("page", "1"));
			return null;
		} else {
			// if paged, this would be type=PartialCollectionView or similar and not the CoverageCollection itself!
			// therefore we skip the Link header and only include that information in the data itself
			setPagingHeaders(paging);
			return App.getCovJsonRepresentation(this, j);
		}
	}
		
	@Get("geojson")
	public Representation geojson() throws IOException, EdalException {
		Paging paging = new Paging(DEFAULT_GEOJSON_FEATURES_PER_PAGE, MAXIMUM_GEOJSON_FEATURES_PER_PAGE);
		Map j = getFeaturesJson(true, paging)
				.put("@context", CoverageResource.GeoJSONLDContext).build();
		setPagingHeaders(paging);
		
		JacksonRepresentation r = new JacksonRepresentation(j);
		r.setMediaType(App.GeoJSON);
		if (!App.acceptsJSON(this)) {
			r.getObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
		}
		return r;
	}

}