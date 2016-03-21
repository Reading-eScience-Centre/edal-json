package uk.ac.rdg.resc.edal.json;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.opengis.referencing.cs.CoordinateSystem;
import org.opengis.referencing.cs.RangeMeaning;
import org.restlet.data.Header;
import org.restlet.data.Reference;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.restlet.util.Series;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

import uk.ac.rdg.resc.edal.dataset.Dataset;
import uk.ac.rdg.resc.edal.domain.Domain;
import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.domain.GridDomain;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.feature.DiscreteFeature;
import uk.ac.rdg.resc.edal.feature.Feature;
import uk.ac.rdg.resc.edal.feature.GridFeature;
import uk.ac.rdg.resc.edal.feature.ProfileFeature;
import uk.ac.rdg.resc.edal.grid.AbstractTransformedGrid;
import uk.ac.rdg.resc.edal.grid.RectilinearGrid;
import uk.ac.rdg.resc.edal.grid.RectilinearGridImpl;
import uk.ac.rdg.resc.edal.grid.ReferenceableAxisImpl;
import uk.ac.rdg.resc.edal.grid.TimeAxis;
import uk.ac.rdg.resc.edal.grid.TimeAxisImpl;
import uk.ac.rdg.resc.edal.grid.VerticalAxis;
import uk.ac.rdg.resc.edal.json.PreferParser.Preference;
import uk.ac.rdg.resc.edal.metadata.Parameter;
import uk.ac.rdg.resc.edal.position.HorizontalPosition;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class CoverageResource extends ServerResource {
	
	static class FeatureMetadata {
		public final DomainMetadata domainMeta;
		public final RangeMetadata rangeMeta;
		public final String featureId;
		public final String datasetId;
		public final String name;
		public final Class<?> type;
		public FeatureMetadata(String datasetId, Feature feature) {
			this.datasetId = datasetId;
			this.featureId = feature.getId();
			this.domainMeta = new DomainMetadata(feature);
			this.rangeMeta = new RangeMetadata(feature);
			this.name = feature.getName();
			this.type = feature.getClass();
		}
	}
	
	// FIXME send Vary: Prefer header
	// see https://github.com/restlet/restlet-framework-java/issues/1202
	static class Embed {
		boolean domain, range;
		/**
		 * Inspects the following header:
		 * 
		 * Prefer: return=representation; include="http://coveragejson.org/ns#Domain http://coveragejson.org/ns#Range"
		 */
		static Embed from(Series<Header> headers, Embed merge) {
			Map<String,Preference> prefs = PreferParser.parse(Arrays.asList(headers.getValuesArray("Prefer")));
			Preference pref = prefs.get("return");
			if (pref == null) return merge;
			String val = pref.getParameters().getOrDefault("include", "");
			return new Embed(val.contains(Constants.Domain) || merge.domain, val.contains(Constants.Range) || merge.range);
		}
		/**
		 * 
		 * @param details query parameter string value of the form "domain,range"
		 * @deprecated Use Prefer header instead (see other constructor)
		 */
		static Embed from(String details, Embed merge) {
			if (details == null) return merge;
			List<String> parts = Arrays.asList(details.split(","));
			return new Embed(parts.contains("domain") || merge.domain, parts.contains("range") || merge.range);
		}
		public Embed(boolean domain, boolean range) {
			this.domain = domain || range;
			this.range = range;
		}
	}
		
	public static Builder getCoverageAsCovJson(Supplier<Dataset> dataset, FeatureMetadata meta, String rootUri, 
			Embed details, SubsetConstraint subset, boolean skipParameters) throws EdalException {
		String coverageUrl = rootUri + "/datasets/" + meta.datasetId + "/coverages/" + meta.featureId;
		
		Builder j = ImmutableMap.builder()
				.put("type", "Coverage")
				.put("profile", meta.domainMeta.getType() + "Coverage")
				.put("id", coverageUrl + subset.getCanonicalSubsetQueryString())
				.put("title", ImmutableMap.of("en", meta.name));
				
		Supplier<UniformFeature> feature =
				Suppliers.memoize(() -> new UniformFeature((DiscreteFeature)dataset.get().readFeature(meta.featureId)));

		try {
			if (details.domain) {
				Map domainJson = CoverageDomainResource.getDomainJson(feature.get(), subset, coverageUrl);
				j.put("domain", domainJson);
			} else {
				j.put("domain", coverageUrl + "/domain" + subset.getCanonicalSubsetQueryString());
				j.put("domainProfile", meta.domainMeta.getType());
			}
			if (!skipParameters) {
				j.put("parameters", getParametersJson(meta, rootUri));
			}
			j.put("ranges", getRangesJson(meta, feature, details.range, subset, rootUri));
		
		} catch (UnsupportedDomainException e) {
			// FIXME return proper error doc
			j.put("domain", unsupportedDomain(e.domain, e.info));
		}

		addPhenomenonTime(j, meta.domainMeta);
		
		return j;
	}
	
	@Get("html")
	public Representation html() throws IOException, EdalException {
		getResponse().redirectSeeOther(Constants.CoverageHTMLUrlPrefix + getReference());
		return null;
	}
		
	@Get("geojson")
	public Representation geojson() throws IOException, EdalException {
		String datasetId = Reference.decode(getAttribute("datasetId"));
		String coverageId = Reference.decode(getAttribute("coverageId"));
		SubsetConstraint subset = new SubsetConstraint(getQuery());
		
		String coverageUrl = getRootRef() + "/datasets/" + datasetId + "/coverages" + "/" + coverageId;		
		
		getResponse().redirectSeeOther(coverageUrl + "/outlines" + subset.getCanonicalSubsetQueryString());
		return null;
	}
	
	@Get("covjson|covcbor|covmsgpack")
	public Representation covjson() throws IOException, EdalException {
		addLinkHeaders();
		Series<Header> headers = this.getResponse().getHeaders();
		
		headers.add(new Header("Link", "<" + Constants.Domain + ">; rel=\"" + Constants.CanIncludeURI + "\""));
		headers.add(new Header("Link", "<" + Constants.Range + ">; rel=\"" + Constants.CanIncludeURI + "\""));
		
		
		String datasetId = Reference.decode(getAttribute("datasetId"));
		String coverageId = Reference.decode(getAttribute("coverageId"));
		Embed defaultEmbed = new Embed(true, false);
		Embed embed = Embed.from(getRequest().getHeaders(), defaultEmbed);
		SubsetConstraint subset = new SubsetConstraint(getQuery());
		
		String rootUri = getRootRef().toString();
		String collectionUrl = rootUri + "/datasets/" + datasetId + "/coverages";
		String coverageUrl = collectionUrl + "/" + coverageId;
		
		DatasetMetadata meta = DatasetResource.getDatasetMetadata(datasetId);
		FeatureMetadata featureMeta = meta.getFeatureMetadata(coverageId);
		
		Builder ldContext = ImmutableMap.builder();
		for (String paramId : featureMeta.rangeMeta.getParameterIds()) {
			ldContext.put(paramId, ImmutableMap.of(
					"@id", ParameterResource.getParamUrl(datasetId, paramId, rootUri),
					"@type", "@id"
					));
		}
		
		Builder coverageJson = getCoverageAsCovJson(meta.getLazyDataset(), 
				meta.getFeatureMetadata(coverageId), getRootRef().toString(), embed, subset, false)
				.put("@context", ImmutableList.of(
						Constants.CoverageJSONContext,
						ldContext
							.put(Constants.RdfsPrefix, Constants.RdfsNamespace)
							.put(Constants.CovAPIPrefix, Constants.CovAPINamespace)
							.put(Constants.HydraPrefix, Constants.HydraNamespace)
							.put("comment", Constants.Comment)
							.put("derivedFrom", Constants.DctNS + "source")
							.put("api", Constants.CovAPIPrefix + ":api")
							.put(Constants.OpenSearchGeoPrefix, Constants.OpenSearchGeoNamespace)
							.put(Constants.OpenSearchTimePrefix, Constants.OpenSearchTimeNamespace)
							.put("inCollection", ImmutableMap.of("@reverse", "hydra:member"))
							.build()
						));
		
		Builder coll = ImmutableMap.builder()
				.put("id", collectionUrl + subset.getCanonicalSubsetQueryString())
				.put("type", "CoverageCollection");
		if (subset.isConstrained) {
			coll.put("derivedFrom", ImmutableMap.of(
					"id", collectionUrl,
					"type", "CoverageCollection"
					));
		}
		coverageJson.put("inCollection", coll.build());
		
		
		Map apiIriTemplate = Hydra.getApiIriTemplate(coverageUrl, false, true);
		if (subset.isConstrained) {
			coverageJson.put("derivedFrom", ImmutableMap.of(
					"id", coverageUrl,
					"type", "Coverage",
					"api", apiIriTemplate
					));
		} else {
			coverageJson.put("api", apiIriTemplate);
		}
		
		Map json = coverageJson.build();
		
		return App.getCovJsonRepresentation(this, json);
	}
	
	void addLinkHeaders() {
		Series<Header> headers = this.getResponse().getHeaders();
		
		String datasetId = Reference.decode(getAttribute("datasetId"));
		String collectionUrl = getRootRef() + "/datasets/" + datasetId + "/coverages";
		
		SubsetConstraint subset = new SubsetConstraint(getQuery());
		headers.add(new Header("Link", "<" + collectionUrl + subset.getCanonicalSubsetQueryString() + ">; rel=\"collection\""));
	}
	
	private static void addPhenomenonTime(Builder featureJson, DomainMetadata meta) {
		// a time range or a single point in time
		Extent<DateTime> time = meta.getTimeExtent();
		if (time == null) return;

		featureJson.put("phenomenonTime", time.getLow() == time.getHigh() 
			? ImmutableMap.of(
					"type", "Instant",
					"dateTime", time.getLow().toString()
					)
			: ImmutableMap.of(
					"type", "Interval",
					"start", ImmutableMap.of(
							"type", "Instant",
							"dateTime", time.getLow().toString()
							),
					"end", ImmutableMap.of(
							"type", "Instant",
							"dateTime", time.getHigh().toString()
							)
					)); 
	}
	
	static class UniformFeature {
		DiscreteFeature<?,?> feature;
		RectilinearGrid rectgrid;
		AbstractTransformedGrid projgrid;
		TimeAxis t;
		VerticalAxis z;
		String type;
		public UniformFeature(DiscreteFeature<?,?> feature) {
			this.feature = feature;
			this.type = FeatureTypes.getName(feature.getClass());
						
			// FIXME feature types should be interfaces
			
			// the following piece of code checks and uniformizes different feature types 
			if (feature instanceof GridFeature) {
				GridFeature gridFeature = (GridFeature) feature;
				GridDomain grid = gridFeature.getDomain();
				t = grid.getTimeAxis();
				z = grid.getVerticalAxis();
				
				if (grid.getHorizontalGrid() instanceof RectilinearGrid) {
					rectgrid = (RectilinearGrid) grid.getHorizontalGrid();
				} else if (grid.getHorizontalGrid() instanceof AbstractTransformedGrid) {
					// curvilinear grid or common projection
					projgrid = (AbstractTransformedGrid) grid.getHorizontalGrid();
				} else {
					throw new RuntimeException("Not supported: " + grid.getHorizontalGrid().getClass().getSimpleName());
				}
			} else if (feature instanceof ProfileFeature) {
				ProfileFeature profile = (ProfileFeature) feature;
				z = profile.getDomain();
				t = new TimeAxisImpl("time", ImmutableList.of(profile.getTime()));
				HorizontalPosition pos = profile.getHorizontalPosition();
				CoordinateSystem cs = pos.getCoordinateReferenceSystem().getCoordinateSystem();
				boolean isLongitudeX = cs.getAxis(0).getRangeMeaning() == RangeMeaning.WRAPAROUND;
				boolean isLongitudeY = cs.getAxis(1).getRangeMeaning() == RangeMeaning.WRAPAROUND;
				// TODO what are the bounds of the single cell here actually?
				//  -> does EDAL derive that from grids automatically?
				rectgrid = new RectilinearGridImpl(
						new ReferenceableAxisImpl("x", ImmutableList.of(pos.getX()), isLongitudeX),
						new ReferenceableAxisImpl("y", ImmutableList.of(pos.getY()), isLongitudeY),
						pos.getCoordinateReferenceSystem());
			} else {
				// TODO should probably say unsupported feature
				throw new UnsupportedDomainException(feature.getDomain());
			}
			
		}
	}
	
	static class UnsupportedDomainException extends RuntimeException {
		private static final long serialVersionUID = 1L;
		Domain<?> domain;
		String info;
		public UnsupportedDomainException(Domain<?> domain, String info) {
			this.domain = domain;
			this.info = info;
		}
		public UnsupportedDomainException(Domain<?> domain) {
			this.domain = domain;
		}
	}
		
	private static Map unsupportedDomain(Domain<?> domain, String info) {
		return ImmutableMap.of(
				"type", domain.getClass().getName(),
				"info", info,
				"ERROR", "UNSUPPORTED"
				);
	}
		
	private static Map getParametersJson(FeatureMetadata meta, String rootUri) {		
		Builder params = ImmutableMap.builder();
		for (Parameter param : meta.rangeMeta.getParameters()) {
			params.put(param.getVariableId(), ParameterResource.getParamJson(meta.datasetId, param, rootUri).build());
		}
		return params.build();
	}
		
	private static Map getRangesJson(FeatureMetadata meta, Supplier<UniformFeature> uniFeatureFn, 
			boolean includeValues, SubsetConstraint subset, String rootUri) {
		String root = rootUri + "/datasets/" + meta.datasetId;
		Builder values = ImmutableMap.builder();

		for (String paramId : meta.rangeMeta.getParameterIds()) {
			if (subset.params.isPresent() && !subset.params.get().contains(paramId)) {
				continue;
			}
			Parameter param = meta.rangeMeta.getParameter(paramId);
			String rangeUrl = root + "/coverages/" + meta.featureId + "/range/" + paramId + subset.getCanonicalSubsetQueryString();
			Object rangeParam;
			
			if (includeValues) {
				// TODO how do we know which axis order the array has?!
				UniformFeature uniFeature = uniFeatureFn.get();
				
				boolean isCategorical = param.getCategories() != null;
				rangeParam = ImmutableMap.builder()
						.put("id", rangeUrl)
						// TODO enable again when CBOR missing-value encoding is implemented and only output for CBOR
//						.put("validMin", meta.rangeMeta.getMinValue(param))
//						.put("validMax", meta.rangeMeta.getMaxValue(param))
						.put("values", CoverageRangeResource.getValues(uniFeature.feature.getValues(paramId), uniFeature, subset, isCategorical))
						.build();
			} else {
				rangeParam = rangeUrl;
			}
			
			values.put(paramId, rangeParam);
		}
		
		return values.build();
	}



}
