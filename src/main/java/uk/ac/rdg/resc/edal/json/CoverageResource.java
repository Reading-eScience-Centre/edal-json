package uk.ac.rdg.resc.edal.json;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.joda.time.DateTime;
import org.opengis.referencing.cs.CoordinateSystem;
import org.opengis.referencing.cs.RangeMeaning;
import org.restlet.data.Header;
import org.restlet.data.Reference;
import org.restlet.ext.jackson.JacksonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.restlet.util.Series;

import com.fasterxml.jackson.databind.SerializationFeature;
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
import uk.ac.rdg.resc.edal.geometry.BoundingBox;
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
	
	public static Builder getCoverageGeoJson(Supplier<Dataset> dataset, FeatureMetadata meta, String rootUri, 
			Embed embed, SubsetConstraint subset) throws EdalException {
		String coverageUrl = rootUri + "/datasets/" + meta.datasetId + "/coverages/" + meta.featureId;
		
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
				.put("parameters", paramTitles);
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
				.put("id", coverageUrl)
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
	
	public static Builder getCoverageCovJson(Supplier<Dataset> dataset, FeatureMetadata meta, String rootUri, 
			Embed details, SubsetConstraint subset) throws EdalException {
		String coverageUrl = rootUri + "/datasets/" + meta.datasetId + "/coverages/" + meta.featureId;
		
		Builder j = ImmutableMap.builder()
				.put("type", meta.domainMeta.getType() + "Coverage")
				.put("id", coverageUrl)
				.put("title", meta.name);
				
		Supplier<UniformFeature> feature =
				Suppliers.memoize(() -> new UniformFeature((DiscreteFeature)dataset.get().readFeature(meta.featureId)));

		try {
			if (details.domain) {
				Map domainJson = CoverageDomainResource.getDomainJson(feature.get(), subset);
				j.put("domain", domainJson);
			} else {
				j.put("domain", coverageUrl + "/domain");
			}
			j.put("parameters", getParametersJson(meta, rootUri));
			j.put("ranges", getRangesJson(meta, feature, details.range, subset, rootUri));
		
		} catch (UnsupportedDomainException e) {
			j.put("domain", unsupportedDomain(e.domain, e.info));
		}

		addPhenomenonTime(j, meta.domainMeta);
		
		return j;
	}
	
	private Map getCoverageAsJson(boolean asGeoJson) throws EdalException, IOException {
		String datasetId = Reference.decode(getAttribute("datasetId"));
		String coverageId = Reference.decode(getAttribute("coverageId"));
		Embed defaultEmbed = new Embed(true, false);
		Embed embed = Embed.from(getRequest().getHeaders(), defaultEmbed);
		SubsetConstraint subset = new SubsetConstraint(getQueryValue("subsetByCoordinate"));
		
		DatasetMetadata meta = DatasetResource.getDatasetMetadata(datasetId);
		Map coverageJson;
		if (asGeoJson) {
			coverageJson = getCoverageGeoJson(meta.getLazyDataset(), 
					meta.getFeatureMetadata(coverageId), getRootRef().toString(), embed, subset)
					.put("@context", Constants.GeoJSONLDContext)
					.build();
		} else {
			FeatureMetadata featureMeta = meta.getFeatureMetadata(coverageId);
			
			Builder ldContext = ImmutableMap.builder();
			for (String paramId : featureMeta.rangeMeta.getParameterIds()) {
				ldContext.put(paramId, ImmutableMap.of(
						"@id", ParameterResource.getParamUrl(datasetId, paramId, getRootRef().toString()),
						"@type", "@id"
						));
			}
			
			coverageJson = getCoverageCovJson(meta.getLazyDataset(), 
					meta.getFeatureMetadata(coverageId), getRootRef().toString(), embed, subset)
					.put("@context", ImmutableList.of(
							Constants.CoverageJSONContext,
							ldContext
								.put("qudt", "http://qudt.org/1.1/schema/qudt#")
								.put("unit", "qudt:unit")
								.put("symbol", "qudt:symbol")
								.build()
							))
					.build();	
		}		
		return coverageJson;
	}
	
	@Get("geojson")
	public Representation geojson() throws IOException, EdalException {
		addLinkHeaders();
		Map featureJson = getCoverageAsJson(true);
		JacksonRepresentation r = new JacksonRepresentation(featureJson);
		r.setMediaType(App.GeoJSON);
		if (!App.acceptsJSON(this)) {
			r.getObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
		}
		return r;
	}
	
	@Get("covjson|covcbor|covmsgpack")
	public Representation covjson() throws IOException, EdalException {
		addLinkHeaders();
		Series<Header> headers = this.getResponse().getHeaders();
		
		headers.add(new Header("Link", "<" + Constants.Domain + ">; rel=\"" + Constants.CanIncludeURI + "\""));
		headers.add(new Header("Link", "<" + Constants.Range + ">; rel=\"" + Constants.CanIncludeURI + "\""));
		
		Map json = getCoverageAsJson(false);
		
		return App.getCovJsonRepresentation(this, json);
	}
	
	void addLinkHeaders() {
		Series<Header> headers = this.getResponse().getHeaders();
		
		String datasetId = Reference.decode(getAttribute("datasetId"));
		String collectionUrl = getRootRef() + "/datasets/" + datasetId + "/coverages";
		
		// TODO hacky, refactor
		boolean isSubset = new SubsetConstraint(getQueryValue("subsetByCoordinate")).isSubset;
		if (isSubset) {
			String coverageId = Reference.decode(getAttribute("coverageId"));
			String coverageUrl = collectionUrl + "/" + coverageId;
			headers.add(new Header("Link", "<" + coverageUrl + ">; rel=\"" + Constants.SubsetOfURI + "\""));
		} else {
			headers.add(new Header("Link", "<" + collectionUrl + ">; rel=\"collection\""));
		}
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
		TimeAxis t;
		VerticalAxis z;
		String type;
		public UniformFeature(DiscreteFeature<?,?> feature) {
			this.feature = feature;
						
			// FIXME feature types should be interfaces
			
			// the following piece of code checks and uniformizes different feature types 
			if (feature instanceof GridFeature) {
				GridFeature gridFeature = (GridFeature) feature;
				GridDomain grid = gridFeature.getDomain();
				t = grid.getTimeAxis();
				z = grid.getVerticalAxis();
				
				if (grid.getHorizontalGrid() instanceof RectilinearGrid) {
					rectgrid = (RectilinearGrid) grid.getHorizontalGrid();
					type = "Grid";
				} else {
					throw new UnsupportedDomainException(feature.getDomain(), grid.getHorizontalGrid().getClass().getName());
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
				
				type = "Profile";
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
		Builder values = ImmutableMap.builder()
				.put("type", "RangeSet");

		for (String paramId : meta.rangeMeta.getParameterIds()) {
			if (subset.params.isPresent() && !subset.params.get().contains(paramId)) {
				continue;
			}
			Parameter param = meta.rangeMeta.getParameter(paramId);
			String rangeUrl = root + "/coverages/" + meta.featureId + "/range/" + paramId;
			Object rangeParam;
			
			if (includeValues) {
				// TODO how do we know which axis order the array has?!
				UniformFeature uniFeature = uniFeatureFn.get();
				
				rangeParam = ImmutableMap.builder()
						.put("id", rangeUrl)
						.put("validMin", meta.rangeMeta.getMinValue(param))
						.put("validMax", meta.rangeMeta.getMaxValue(param))
						.put("values", CoverageRangeResource.getValues(uniFeature.feature.getValues(paramId), uniFeature, subset))
						.build();
			} else {
				rangeParam = rangeUrl;
			}
			
			values.put(paramId, rangeParam);
		}
		
		return values.build();
	}



}
