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
import org.restlet.data.Status;
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
import uk.ac.rdg.resc.edal.geometry.BoundingBox;
import uk.ac.rdg.resc.edal.json.CoverageResource.Embed;
import uk.ac.rdg.resc.edal.json.CoverageResource.FeatureMetadata;
import uk.ac.rdg.resc.edal.metadata.Parameter;
import uk.ac.rdg.resc.edal.util.GISUtils;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class CoverageCollectionResource extends ServerResource {
	
	class Paging {
		int currentPage = 1;
		int defaultElementsPerPage;
		int elementsPerPage;
		int totalElements;
		int totalPages;
		int beginOffset;
		int endOffset;
		
		Reference redirect;
		String previous;
		String next;
		String first;
		String last;
		
		Paging(int defaultPerPage, int maximumPerPage) {
			this.defaultElementsPerPage = defaultPerPage;
			String page = getQueryValue("page");
			if (page != null) {
				this.currentPage = Integer.parseInt(page);
				if (this.currentPage < 1) {
					throw new IllegalArgumentException("page must be >= 1");
				}
			}
			String perPage = getQueryValue("num");
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
			createURLs();
			
			Form form = getReference().getQueryAsForm();
			if (this.totalPages > 1 && this.currentPage == 1 && (form.getFirstValue("page") == null || form.getFirstValue("num") == null)) {
				redirect = getReference().addQueryParameter("page", "1").addQueryParameter("num", String.valueOf(this.elementsPerPage));
			}
		}
		
		private void createURLs() {
			String datasetId = Reference.decode(getAttribute("datasetId"));
			String baseUrl = getRootRef().toString() + "/datasets/" + datasetId + "/coverages";
			
			Map<String,String> params = new HashMap<>();
			if (this.defaultElementsPerPage != this.elementsPerPage) {
				params.put("num", String.valueOf(this.elementsPerPage));
			}
								
			if (this.currentPage < this.totalPages) {
				params.put("page", String.valueOf(this.currentPage+1));
				next = baseUrl + getQueryString(params);
			} else if (this.currentPage > 1) {
				if (this.currentPage != 2) {
					params.put("page", String.valueOf(this.currentPage-1));
				}
				previous = baseUrl + getQueryString(params);
			}
			if (this.totalPages > 1) {
				params.put("page", "1");
				first = baseUrl + getQueryString(params);
				
				params.put("page", String.valueOf(this.totalPages));
				last = baseUrl + getQueryString(params);
			}
		}
		
		private String getQueryString(Map<String,String> params) {
			Form q = new Form(getQuery().getQueryString());
			for (Entry<String,String> entry : params.entrySet()) {
				q.set(entry.getKey(), entry.getValue());
			}
			return q.isEmpty() ? "" : "?" + q.getQueryString();
		}
	}
	
	private Builder getFeaturesJson(boolean asGeojson, Paging paging, FilterConstraint filter, SubsetConstraint subset)
			throws IOException, EdalException {
		final String datasetId = Reference.decode(getAttribute("datasetId"));
		Embed defaultEmbed = new Embed(false, false);
		Embed embed = Embed.from(getRequest().getHeaders(), defaultEmbed);
		
		DatasetMetadata datasetMeta = DatasetResource.getDatasetMetadata(datasetId);
		
		String collectionUrl = getRootRef().toString() + "/datasets/" + datasetId + "/coverages";
		
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
				 feature = CoverageOutlinesResource.getOutlinesAsGeoJson(dataset, meta, getRootRef().toString(), 
						subset).build();
			} else {
				feature = CoverageResource.getCoverageAsCovJson(dataset, meta, getRootRef().toString(), 
						embed, subset, true).build();
			}
			jsonFeatures.add(feature);
		}
		
		
		Builder j = ImmutableMap.builder();
		if (asGeojson) {
			j.put("@context", ImmutableList.of(
					Constants.HydraContext,
					Constants.GeoJSONLDContext,
					ImmutableMap.of(
							Constants.CovAPIPrefix, Constants.CovAPINamespace,
							"api", Constants.CovAPIPrefix + ":api",
							"opensearchgeo", Constants.OpenSearchGeoNamespace,
							"opensearchtime", Constants.OpenSearchTimeNamespace,
							"subsetOf", Constants.CovAPIPrefix + ":subsetOf"
							)
					))
			 .put("type", "FeatureCollection")
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
					// hydra comes first since it defines some generic terms like "title" under the hydra: ns
					// but we want to use our own "title" definition from the coveragejson context (overriding hydra's)
					Constants.HydraContext,
					Constants.CoverageJSONContext,
					ldContext
						.put("qudt", "http://qudt.org/1.1/schema/qudt#")
						.put("unit", "qudt:unit")
						.put("symbol", "qudt:symbol")
						.put(Constants.CovAPIPrefix, Constants.CovAPINamespace)
						.put("api", Constants.CovAPIPrefix + ":api")
						.put("subsetOf", Constants.CovAPIPrefix + ":subsetOf")
						.put("opensearchgeo", Constants.OpenSearchGeoNamespace)
						.put("opensearchtime", Constants.OpenSearchTimeNamespace)
						.build()
					))
			 .put("id", collectionUrl + subset.getCanonicalSubsetQueryString())
			 .put("type", "CoverageCollection")
			 .put("parameters", jsonParams.build())
			 .put("coverages", jsonFeatures);
		}
		
		if (subset.isConstrained) {
			j.put("subsetOf", ImmutableMap.of(
						"id", collectionUrl,
						"type", "CoverageCollection"
						));
		}
		
		if (paging.totalPages > 1) {
			j.put("totalItems", paging.totalElements);
			Builder pagination = ImmutableMap.builder()
					.put("id", getReference().toString())
					.put("type", "PartialCollectionView")
					.put("itemsPerPage", paging.elementsPerPage);
			if (paging.first != null) {
				pagination.put("first", paging.first);
			}
			if (paging.previous != null) {
				pagination.put("previous", paging.previous);
			}
			if (paging.next != null) {
				pagination.put("next", paging.next);
			}
			if (paging.last != null) {
				pagination.put("last", paging.last);
			}
			
			j.put("view", ImmutableMap.of(
					"id", "#pagination",
					// cannot be made nicer currently
					// see https://github.com/json-ld/json-ld.org/issues/398
					"@graph", pagination.build()
					));
		}

		j.put("api", Hydra.getApiIriTemplate(collectionUrl, true, true));
		
		return j;
	}
	
	void setPagingHeaders(Paging paging) {		
		Map<String,String> links = new HashMap<>();
				
		if (paging.next != null) {
			links.put("next", paging.next);
		}
		if (paging.previous != null) {
			links.put("prev", paging.previous);
		}
		if (paging.first != null) {
			links.put("first", paging.first);
		}
		if (paging.last != null) {
			links.put("last", paging.last);
		}
		
		Series<Header> headers = this.getResponse().getHeaders();
		for (String rel : links.keySet()) {
			String url = links.get(rel);
			headers.add(new Header("Link", "<" + url + ">; rel=\"" + rel + "\""));
		}
	}
	
	@Get("html")
	public Representation html() throws IOException, EdalException {
		// FIXME cheap hack, rather separate geojson out into separate resource (like CoverageOutlinesResource)
		if (getReference().toString().contains("/outlines")) {
			getResponse().redirectSeeOther(Constants.CoverageCollectionOutlinesHTMLUrlPrefix + getReference());
		} else {
			getResponse().redirectSeeOther(Constants.CoverageCollectionHTMLUrlPrefix + getReference());
		}
		return null;
	}
		
	@Get("covjson|covcbor|covmsgpack")
	public Representation covjson() throws IOException, EdalException {
		// FIXME cheap hack, rather separate geojson out into separate resource (like CoverageOutlinesResource)
		if (getReference().toString().contains("/outlines")) {
			return geojson();
		}
		
		// FIXME add Vary: Prefer, see https://github.com/restlet/restlet-framework-java/issues/187
		
		Series<Header> headers = this.getResponse().getHeaders();
		headers.add(new Header("Link", "<" + Constants.Domain + ">; rel=\"" + Constants.CanIncludeURI + "\""));
		headers.add(new Header("Link", "<" + Constants.Range + ">; rel=\"" + Constants.CanIncludeURI + "\""));
		
		Paging paging;
		SubsetConstraint subset;
		FilterConstraint filter;
		Map j;
		try {
			paging = new Paging(Constants.DEFAULT_COVERAGES_PER_PAGE, Constants.MAXIMUM_COVERAGES_PER_PAGE);
			subset = new SubsetConstraint(getQuery());
			filter = new FilterConstraint(getQuery(), subset);
			j = getFeaturesJson(false, paging, filter, subset).build();
		} catch (IllegalArgumentException e) {
			setStatus(Status.CLIENT_ERROR_NOT_FOUND);
			return App.getErrorRepresentation(e);
		}
		
		if (filter.isConstrained || subset.isConstrained) {
			final String datasetId = Reference.decode(getAttribute("datasetId"));
			String collectionUrl = getRootRef().toString() + "/datasets/" + datasetId + "/coverages";
			headers.add(new Header("Link", "<" + collectionUrl + subset.getCanonicalSubsetQueryString() + ">; rel=\"canonical\""));
		}
		
		if (paging.redirect != null) {
			getResponse().redirectSeeOther(paging.redirect);
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
		// Note: paging is disabled for geojson by having very high default items per page
		// reason: general geojson clients couldn't handle it anyway
		Paging paging;
		SubsetConstraint subset;
		FilterConstraint filter;
		Map j;
		
		try {
			paging = new Paging(Constants.DEFAULT_GEOJSON_FEATURES_PER_PAGE, Constants.MAXIMUM_GEOJSON_FEATURES_PER_PAGE);
			subset = new SubsetConstraint(getQuery());
			filter = new FilterConstraint(getQuery(), subset);
			
			// TODO cheap hack, rather separate geojson out into separate resource (like CoverageOutlinesResource)
			if (!getReference().toString().contains("/outlines")) {
				final String datasetId = Reference.decode(getAttribute("datasetId"));
				String outlinesUrl = getRootRef().toString() + "/datasets/" + datasetId + "/outlines" ;
				
				getResponse().redirectSeeOther(outlinesUrl + subset.getCanonicalSubsetQueryString());
				return null;
			}
			
			j = getFeaturesJson(true, paging, filter, subset).build();
		} catch (IllegalArgumentException e) {
			setStatus(Status.CLIENT_ERROR_NOT_FOUND);
			return App.getErrorRepresentation(e);
		}
		
		// TODO remove duplication with covjson()
		Series<Header> headers = this.getResponse().getHeaders();
		if (filter.isConstrained || subset.isConstrained) {
			final String datasetId = Reference.decode(getAttribute("datasetId"));
			String collectionUrl = getRootRef().toString() + "/datasets/" + datasetId + "/coverages";
			headers.add(new Header("Link", "<" + collectionUrl + subset.getCanonicalSubsetQueryString() + ">; rel=\"canonical\""));
		}
		
		if (paging.redirect != null) {
			getResponse().redirectSeeOther(paging.redirect);
			return null;
		} else {
			// if paged, this would be type=PartialCollectionView or similar and not the CoverageCollection itself!
			// therefore we skip the Link header and only include that information in the data itself
			setPagingHeaders(paging);
			
			JacksonRepresentation r = new JacksonRepresentation(j);
			r.setMediaType(App.GeoJSON);
			if (!App.acceptsJSON(this)) {
				r.getObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
			}
			return r;
		}
	}

}