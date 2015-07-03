package uk.ac.rdg.resc.edal.json;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.joda.time.DateTime;
import org.opengis.referencing.cs.CoordinateSystem;
import org.opengis.referencing.cs.RangeMeaning;
import org.restlet.data.Reference;
import org.restlet.ext.jackson.JacksonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

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
import uk.ac.rdg.resc.edal.grid.ReferenceableAxis;
import uk.ac.rdg.resc.edal.grid.ReferenceableAxisImpl;
import uk.ac.rdg.resc.edal.grid.RegularGrid;
import uk.ac.rdg.resc.edal.grid.TimeAxis;
import uk.ac.rdg.resc.edal.grid.TimeAxisImpl;
import uk.ac.rdg.resc.edal.grid.VerticalAxis;
import uk.ac.rdg.resc.edal.metadata.Parameter;
import uk.ac.rdg.resc.edal.position.HorizontalPosition;
import uk.ac.rdg.resc.edal.util.Array;
import uk.ac.rdg.resc.edal.util.Array4D;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class FeatureResource extends ServerResource {
	
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
	
	static class Details {
		final boolean domain, rangeMetadata, range;
		static Details from(String details, Details fallback) {
			if (details == null) return fallback;
			List<String> parts = Arrays.asList(details.split(","));
			return new Details(parts.contains("domain"), parts.contains("rangeMetadata"), 
					parts.contains("range"));
		}
		public Details(boolean domain, boolean rangeMetadata, boolean range) {
			this.domain = domain || range;
			this.rangeMetadata = rangeMetadata;
			this.range = range;
		}
	}
	
	public static Map getFeatureJson(Supplier<Dataset> dataset, FeatureMetadata meta, String rootUri, 
			Details details, Constraint subset) throws EdalException {
		String featureUrl = rootUri + "/datasets/" + meta.datasetId + "/features/" + meta.featureId;
				
		Map j = new HashMap(ImmutableMap.of(
				"type", "oml:Observation",
				"id", featureUrl,
				"title", meta.name
				));
				
		if (details.domain || details.range || details.rangeMetadata) {

			Map result = new HashMap();
			Supplier<UniformFeature> feature =
					Suppliers.memoize(() -> new UniformFeature((DiscreteFeature)dataset.get().readFeature(meta.featureId)));

			try {
				if (details.domain) {
					Map domainJson = getDomainJson(feature.get(), subset);
					result.put("domain", domainJson);
				}
				// parameter metadata is repeated so that a feature can be processed stand-alone
				if (details.rangeMetadata) {
					result.put("rangeType", getParameterTypesJson(meta, rootUri));
				}
				result.put("range", getParameterValuesJson(meta, feature, details.range, subset, rootUri));
			
			} catch (UnsupportedDomainException e) {
				result.put("domain", unsupportedDomain(e.domain, e.info));
			}
			j.put("result", result);
		}
		addPhenomenonTime(j, meta.domainMeta);
		
		return j;
	}
	
	private Map getFeatureJson() throws EdalException, IOException {
		String datasetId = Reference.decode(getAttribute("datasetId"));
		String featureId = Reference.decode(getAttribute("featureId"));
		Details fallback = new Details(true, true, false);
		Details details = Details.from(getQueryValue("details"), fallback);
		Constraint subset = new Constraint(getQueryValue("subset"));
		
		DatasetMetadata meta = DatasetResource.getDatasetMetadata(datasetId);
		Map featureJson = getFeatureJson(meta.getLazyDataset(), 
				meta.getFeatureMetadata(featureId), getRootRef().toString(), details, subset);
		featureJson.put("@context", "/static/Feature.jsonld");
		return featureJson;
	}
	
	@Get("json")
	public Representation json() throws IOException, EdalException {
		Map featureJson = getFeatureJson();
		JacksonRepresentation r = new JacksonRepresentation(featureJson);
		if (!App.acceptsJSON(getClientInfo())) {
			r.getObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
		}
		return r;
	}
	
	@Get("msgpack")
	public Representation msgpack() throws IOException, EdalException {
		return new MessagePackRepresentation(getFeatureJson());
	}
	
	private static void addPhenomenonTime(Map featureJson, DomainMetadata meta) {
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
					"hasBeginning", ImmutableMap.of(
							"type", "Instant",
							"dateTime", time.getLow().toString()
							),
					"hasEnd", ImmutableMap.of(
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
			
			// TODO should we name the type "RegularGrid" even if z or t is irregular?
			
			// FIXME feature types should be interfaces
			
			// the following piece of code checks and uniformizes different feature types 
			if (feature instanceof GridFeature) {
				GridFeature gridFeature = (GridFeature) feature;
				GridDomain grid = gridFeature.getDomain();
				t = grid.getTimeAxis();
				z = grid.getVerticalAxis();
				
				if (grid.getHorizontalGrid() instanceof RectilinearGrid) {
					rectgrid = (RectilinearGrid) grid.getHorizontalGrid();
					type = rectgrid instanceof RegularGrid ? "RegularGrid" : "RectilinearGrid";
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
	
	private static Map getDomainJson(UniformFeature uniFeature, Constraint subset) {
		Map domainJson = new HashMap();
		
		// no support for trajectories currently
		// we support everything which is a subtype of a rectilinear grid (includes profiles)
		
		// TODO add shortcuts when no subsetting is requested
		
		addHorizontalGrid(uniFeature.rectgrid, subset, domainJson);
		addVerticalAxis(uniFeature.z, subset, domainJson);
		addTimeAxis(uniFeature.t, subset, domainJson);
		domainJson.put("type", uniFeature.type);
		
		return domainJson;
	}
		
	private static IntStream getVerticalAxisIndices(VerticalAxis ax, Constraint subset) {
		if (ax == null) {
			return IntStream.of(0);
		}
		List<Double> v = ax.getCoordinateValues();
		
		IntStream axIndices = IntStream.range(0, v.size())
			.filter(i -> subset.verticalExtent.contains(v.get(i)));
		
		if (subset.verticalTarget == null) {
			return axIndices;
		}
		
		// find vertical value closest to target and return its index
		double target = subset.verticalTarget;
		List<Integer> indices = Ints.asList(axIndices.toArray());
		List<Double> distances = Utils.mapList(indices, i -> Math.abs(v.get(i) - target));
		double minDistance = Double.POSITIVE_INFINITY;
		int minIdx = 0;
		int i = 0;
		for (double distance : distances) {
			if (distance < minDistance) {
				minDistance = distance;
				minIdx = i;
			}
			++i;
		}		
		return IntStream.of(indices.get(minIdx));
	}
	
	private static IntStream getTimeAxisIndices(TimeAxis ax, Constraint subset) {
		if (ax == null) {
			return IntStream.of(0);
		}
		List<DateTime> v = ax.getCoordinateValues();		
		IntStream axIndices = IntStream.range(0, v.size())
			.filter(i -> subset.timeExtent.contains(v.get(i)));
		return axIndices;
	}
	
	/**
	 * NOTE: supports rectilinear lon-lat grids only for now
	 */
	private static IntStream getXAxisIndices(ReferenceableAxis<Double> ax, Constraint subset) {
		List<Double> v = ax.getCoordinateValues();
		// FIXME longitudes must be (un)wrapped the same way!!
		IntStream axIndices = IntStream.range(0, v.size())
			.filter(i -> subset.longitudeExtent.contains(v.get(i)));
		return axIndices;
	}

	/**
	 * NOTE: supports rectilinear lon-lat grids only for now
	 */
	private static IntStream getYAxisIndices(ReferenceableAxis<Double> ax, Constraint subset) {
		List<Double> v = ax.getCoordinateValues();
		IntStream axIndices = IntStream.range(0, v.size())
			.filter(i -> subset.latitudeExtent.contains(v.get(i)));
		return axIndices;
	}
	
	/**
	 * NOTE: supports rectilinear lon-lat grids only for now
	 */
	private static void addHorizontalGrid(RectilinearGrid grid, Constraint subset, Map domainJson) {
		List<Double> x = grid.getXAxis().getCoordinateValues();
		List<Double> y = grid.getYAxis().getCoordinateValues();
		double[] subsettedX = getXAxisIndices(grid.getXAxis(), subset).mapToDouble(x::get).toArray();
		double[] subsettedY = getYAxisIndices(grid.getYAxis(), subset).mapToDouble(y::get).toArray();
				
		BoundingBox bb = grid.getBoundingBox();
		domainJson.putAll(ImmutableMap.of(
			    "crs", Utils.getCrsUri(grid.getCoordinateReferenceSystem()),
			    // FIXME have to subset bbox as well
			    "bbox", ImmutableList.of(bb.getMinX(), bb.getMinY(), bb.getMaxX(), bb.getMaxY()),
			    "x", subsettedX.length == 1 ? subsettedX[0] : subsettedX,
				"y", subsettedY.length == 1 ? subsettedY[0] : subsettedY
				));
		
		if (grid instanceof RegularGrid && (grid.getXSize() > 1 || grid.getYSize() > 1)) {
			RegularGrid reggrid = (RegularGrid) grid;
			domainJson.putAll(ImmutableMap.of(
					"delta", ImmutableList.of(
				    		reggrid.getXAxis().getCoordinateSpacing(),
				    		reggrid.getYAxis().getCoordinateSpacing()
				    		)
				    ));
		}
	}
	
	private static void addVerticalAxis(VerticalAxis z, Constraint subset, Map domainJson) {
		if (z == null) {
			return;
		}
		List<Double> heights = z.getCoordinateValues();
		Stream<Double> subsettedHeights = getVerticalAxisIndices(z, subset).mapToObj(heights::get);
		
		domainJson.put("vertical", subsettedHeights.toArray());
		//domainJson.put("verticalBounds", z.getDomainObjects().iterator());
		
		// TODO are there no standards for vertical CRS, with codes etc.?
		domainJson.put("verticalCrs", ImmutableMap.of(
				"uom", z.getVerticalCrs().getUnits(),
				"positiveUpwards", z.getVerticalCrs().isPositiveUpwards(),
				"dimensionless", z.getVerticalCrs().isDimensionless(),
				"pressure", z.getVerticalCrs().isPressure()
				));
		
	}
		
	private static void addTimeAxis(TimeAxis t, Constraint subset, Map domainJson) {
		if (t == null) {
			return;
		}
		List<DateTime> times = t.getCoordinateValues();
		String[] subsettedTimes = getTimeAxisIndices(t, subset).mapToObj(i -> times.get(i).toString()).toArray(String[]::new);
		domainJson.put("time", subsettedTimes.length == 1 ? subsettedTimes[0] : subsettedTimes);
		//domainJson.put("timeBounds", t.getDomainObjects().iterator());
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
		
	private static Map getParameterTypesJson(FeatureMetadata meta, String rootUri) {
		String root = rootUri + "/datasets/" + meta.datasetId + "/params/";
		
		Builder types = ImmutableMap.builder();
		for (Parameter param : meta.rangeMeta.getParameters()) {
			types.put(root + param.getVariableId(), ImmutableMap.of(
					"title", param.getTitle(),
					"description", param.getDescription(),
					"observedProperty", param.getStandardName() == null ? "UNKNOWN" : param.getStandardName(),
					"uom", param.getUnits()
					));
		}
		return types.build();
	}
		
	private static Map getParameterValuesJson(FeatureMetadata meta, Supplier<UniformFeature> uniFeatureFn, boolean includeValues,
			Constraint subset, String rootUri) {
		String root = rootUri + "/datasets/" + meta.datasetId;
		Builder values = ImmutableMap.builder();

		for (String paramId : meta.rangeMeta.getParameterIds()) {
			Parameter param = meta.rangeMeta.getParameter(paramId);
			Map rangeParam = ImmutableMap.of(
					"id", root + "/features/" + meta.featureId + "/range/" + paramId,
					"min", meta.rangeMeta.getMinValue(param),
					"max", meta.rangeMeta.getMaxValue(param)
					);
			
			if (includeValues) {
				// TODO how do we know which axis order the array has?!
				UniformFeature uniFeature = uniFeatureFn.get();
				
				rangeParam = ImmutableMap.builder()
						.putAll(rangeParam)
						.put("values", getValues(uniFeature.feature.getValues(paramId), uniFeature, subset))
						.build();
			}
			
			values.put(root + "/params/" + paramId, rangeParam);
		}
		
		return values.build();
	}

	public static List<Number> getValues(Array<Number> valsArr, DiscreteFeature<?,?> feature, Constraint subset) {
		return getValues(valsArr, new UniformFeature(feature), subset);
	}
	
	public static List<Number> getValues(Array<Number> valsArr, UniformFeature uniFeature, Constraint subset) {
		if (valsArr.size() > Integer.MAX_VALUE) {
			throw new RuntimeException("Array too big, consider subsetting!");
		}
		
		int[] xIndices = getXAxisIndices(uniFeature.rectgrid.getXAxis(), subset).toArray();
		int[] yIndices = getYAxisIndices(uniFeature.rectgrid.getYAxis(), subset).toArray();
		int[] zIndices = getVerticalAxisIndices(uniFeature.z, subset).toArray();
		int[] tIndices = getTimeAxisIndices(uniFeature.t, subset).toArray();
		
		
//		valsArr.forEach(vals::add);
		
				
		Array4D<Number> vals4D;
		
		if (valsArr instanceof Array4D) {
			vals4D = (Array4D<Number>) valsArr;
		} else if (uniFeature.feature instanceof ProfileFeature) {
			// Array1D varying over vertical axis
			vals4D = new Array4D<Number>(1, zIndices.length, 1, 1) {
				@Override
				public Number get(int... coords) {
					return valsArr.get(coords[1]);
				}
				@Override
				public void set(Number value, int... coords) {
					throw new UnsupportedOperationException();
				}
			};
		} else {
			throw new RuntimeException("not supported: " + valsArr.getClass().getName());
		}
		

		
		Number[] vals = new Number[xIndices.length * yIndices.length * 
				zIndices.length * tIndices.length];
		
		int i = 0;
		for (int t : tIndices) {
			for (int z : zIndices) {
				for (int y : yIndices) {
					for (int x : xIndices) {
						vals[i++] = vals4D.get(t, z, y, x);
					}
				}
			}
		}
		
		return Arrays.asList(vals);
	}
	
	public static float[] getValues_(Array<Number> valsArr) {
		if (valsArr.size() > Integer.MAX_VALUE) {
			throw new RuntimeException("Array too big, consider subsetting!");
		}
		
		// TODO make this more clever, depending on input data
		float[] vals = new float[(int) valsArr.size()];
		
		Iterator<Number> it = valsArr.iterator();
		int i = 0;
		while (it.hasNext()) {
			Number v = it.next();
			if (v == null) {
				vals[i] = Integer.MIN_VALUE;
			} else {
				vals[i] = v.intValue();
			}
			i++;
		}
		return vals;
	}

}
