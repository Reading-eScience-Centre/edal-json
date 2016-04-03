package uk.ac.rdg.resc.edal.json;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.joda.time.DateTime;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.crs.DerivedCRS;
import org.opengis.referencing.crs.GeodeticCRS;
import org.opengis.referencing.crs.ProjectedCRS;
import org.restlet.data.Header;
import org.restlet.data.Reference;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.restlet.util.Series;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.primitives.Ints;

import uk.ac.rdg.resc.edal.dataset.Dataset;
import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.feature.DiscreteFeature;
import uk.ac.rdg.resc.edal.grid.AbstractTransformedGrid;
import uk.ac.rdg.resc.edal.grid.RectilinearGrid;
import uk.ac.rdg.resc.edal.grid.ReferenceableAxis;
import uk.ac.rdg.resc.edal.grid.RegularGrid;
import uk.ac.rdg.resc.edal.grid.TimeAxis;
import uk.ac.rdg.resc.edal.grid.VerticalAxis;
import uk.ac.rdg.resc.edal.json.CoverageResource.UniformFeature;
import uk.ac.rdg.resc.edal.position.VerticalCrs;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class CoverageDomainResource extends ServerResource {
	
	public static final String COMPONENTS = "components";
	public static final String SYSTEM = "system";

	@Get("covjson|covcbor|covmsgpack")
	public Representation json() throws IOException, EdalException {
		String datasetId = Reference.decode(getAttribute("datasetId"));
		String featureId = Reference.decode(getAttribute("coverageId"));
		SubsetConstraint subset = new SubsetConstraint(getQuery());
		Dataset dataset = Utils.getDataset(datasetId);
		
		String coverageUrl = getRootRef() + "/datasets/" + datasetId + "/coverages/" + featureId;
		
		UniformFeature uniFeature =
				new UniformFeature((DiscreteFeature)dataset.readFeature(featureId));
		
		Series<Header> headers = this.getResponse().getHeaders();
		
		// TODO add subsetOf rel if subsetted
		// TODO add link to coverage
		
		
		Map j = getDomainJson(uniFeature, subset, coverageUrl);
		return App.getCovJsonRepresentation(this, j);
	}
	
	public static Map getDomainJson(UniformFeature uniFeature, SubsetConstraint subset, String coverageUrl) {
		Builder axes = ImmutableMap.builder();
		List referencing = new LinkedList();
		
		if (uniFeature.rectgrid != null) {
			addHorizontalGrid(uniFeature.rectgrid, subset, axes, referencing);
		} else {
			addHorizontalGrid(uniFeature.projgrid, subset, axes, referencing);
		}		
		addVerticalAxis(uniFeature.z, subset, axes, referencing);
		addTimeAxis(uniFeature.t, subset, axes, referencing);
		
		String queryString = Constraint.getQueryString(subset.getCanonicalQueryParams());
		
		Builder domainJson = ImmutableMap.builder()
				.put("id", coverageUrl + "/domain" + queryString)
				.put("type", "Domain")
				.put("profile", uniFeature.type)
				.put("axes", axes.build())
				.put("referencing", referencing);
		
		List<String> axisOrder = new LinkedList<>();
		axisOrder.add("y");
		axisOrder.add("x");
		if (uniFeature.z != null) {
			axisOrder.add(0, "z");
		}
		if (uniFeature.t != null) {
			axisOrder.add(0, "t");
		}
		domainJson.put("rangeAxisOrder", axisOrder);
		
		// no support for trajectories currently
		// we support everything which is a subtype of a rectilinear grid (includes profiles)
		
		// TODO add shortcuts when no subsetting is requested
		
		return domainJson.build();
	}
	
	private static void addHorizontalGrid(RectilinearGrid grid, Constraint subset, Builder axes, List referencing) {
		List<Double> x = grid.getXAxis().getCoordinateValues();
		List<Double> y = grid.getYAxis().getCoordinateValues();
		double[] subsettedX = getXAxisIndices(grid.getXAxis(), subset).mapToDouble(x::get).toArray();
		double[] subsettedY = getYAxisIndices(grid.getYAxis(), subset).mapToDouble(y::get).toArray();
				
		if (grid instanceof RegularGrid && (grid.getXSize() > 1 || grid.getYSize() > 1)) {
			RegularGrid reggrid = (RegularGrid) grid;
			int xnum = subsettedX.length;
			int ynum = subsettedY.length;
			double xstart = subsettedX[0];
			double xstop = subsettedX[xnum-1];
			double ystart = subsettedY[0];
			double ystop = subsettedY[ynum-1];
			double xstep = reggrid.getXAxis().getCoordinateSpacing();
			double ystep = reggrid.getYAxis().getCoordinateSpacing();
			// calculate num from step to check consistency
			int xnumcheck = (int) Math.round(1 + (xstop-xstart)/xstep);
			int ynumcheck = (int) Math.round(1 + (ystop-ystart)/ystep);
			if (xnum != xnumcheck || ynum != ynumcheck) {
				throw new IllegalStateException();
			}
			axes.putAll(ImmutableMap.of(
				    "x", ImmutableMap.of(
				    		"start", xstart,
				    		"stop", xstop,
				    		"num", xnum
				    		),
					"y", ImmutableMap.of(
				    		"start", ystart,
				    		"stop", ystop,
				    		"num", ynum
				    		)
					));
		} else {
			axes.putAll(ImmutableMap.of(
				    "x", ImmutableMap.of("values", subsettedX),
					"y", ImmutableMap.of("values", subsettedY)
					));
		}
			
		referencing.add(ImmutableMap.of(
				COMPONENTS, ImmutableList.of("x", "y"),
				SYSTEM, getCRSJson(grid.getCoordinateReferenceSystem())
				));
				
	    // FIXME have to subset bbox as well
//		BoundingBox bb = grid.getBoundingBox();
//	    "bbox", ImmutableList.of(bb.getMinX(), bb.getMinY(), bb.getMaxX(), bb.getMaxY()),
		
		// FIXME add bounds if not infinitesimal
		//  -> how do we query that except checking if low==high?
	}
	
	private static void addHorizontalGrid(AbstractTransformedGrid grid, Constraint subset, Builder axes, List referencing) {
		if (subset.latitudeExtent.getLow() != null || subset.latitudeExtent.getHigh() != null ||
				subset.longitudeExtent.getLow() != null || subset.longitudeExtent.getHigh() != null) {
			throw new IllegalStateException("Horizontal subsetting not supported for projected grids");
		}
		axes.putAll(ImmutableMap.of(
			    "x", ImmutableMap.of(
			    		"start", 0,
			    		"stop", grid.getXSize()-1,
			    		"num", grid.getXSize()
			    		),
				"y", ImmutableMap.of(
			    		"start", 0,
			    		"stop", grid.getYSize()-1,
			    		"num", grid.getYSize()
			    		)
				));
	
		referencing.add(ImmutableMap.of(
				COMPONENTS, ImmutableList.of("x", "y"),
				SYSTEM, ImmutableMap.of(
						"type", "ProjectedCRS",
						"baseCRS", getCRSJson(grid.getCoordinateReferenceSystem())
						)
				));
	}
	
	private static Map getCRSJson(CoordinateReferenceSystem crs) {
		Builder crsMap = ImmutableMap.builder();
				
		String crsType;
		if (crs instanceof GeodeticCRS) {
			crsType = "GeodeticCRS";
		} else if (crs instanceof ProjectedCRS) {
			crsType = "ProjectedCRS";
		} else {
			throw new RuntimeException("Unsupported CRS type: " + crs.getClass().getSimpleName());
		}
		crsMap.put("type", crsType);
		
		if (crs instanceof DerivedCRS) {
			CoordinateReferenceSystem baseCrs = ((DerivedCRS) crs).getBaseCRS();
			crsMap.put("baseCRS", getCRSJson(baseCrs));
		}
		
		String crsUri = Utils.getCrsUri(crs);
		if (crsUri != null) {
			crsMap.put("id", crsUri);
		}
		
		return crsMap.build();
	}
	
	private static Map getCRSJson(VerticalCrs crs) {
		String axisName = "Vertical";
		if (crs.isPressure()) {
			axisName = "Pressure";
		} else if ("m".equals(crs.getUnits())) {
			if (crs.isPositiveUpwards()) {
				axisName = "Height";
			} else {
				axisName = "Depth";
			}
		}
		return ImmutableMap.of(
				"type", "VerticalCRS",
				"cs", ImmutableMap.of(
						"axes", ImmutableList.of(ImmutableMap.of(
								"name", ImmutableMap.of("en", axisName),
								"direction", crs.isPositiveUpwards() ? "up" : "down",
								"unit", ImmutableMap.of(
										"symbol", crs.getUnits()
										)
								))
						)
				);
	}
	
	private static void addVerticalAxis(VerticalAxis z, SubsetConstraint subset, Builder axes, List referencing) {
		if (z == null) {
			return;
		}
		List<Double> heights = z.getCoordinateValues();
		double[] subsettedHeights = getVerticalAxisIndices(z, subset).mapToDouble(heights::get).toArray();
		
		axes.put("z", ImmutableMap.of("values", subsettedHeights));
		
		// FIXME add bounds if not infinitesimal
		//  -> how do we query that except checking if low==high?
		//domainJson.put("verticalBounds", z.getDomainObjects().iterator());
		
		referencing.add(ImmutableMap.of(
				COMPONENTS, ImmutableList.of("z"),
				SYSTEM, getCRSJson(z.getVerticalCrs())
				));
		
	}
		
	private static void addTimeAxis(TimeAxis t, Constraint subset, Builder axes, List referencing) {
		if (t == null) {
			return;
		}
		List<DateTime> times = t.getCoordinateValues();
		String[] subsettedTimes = getTimeAxisIndices(t, subset)
				.mapToObj(i -> times.get(i).toString())
				.toArray(String[]::new);
		
		axes.put("t", ImmutableMap.of("values", subsettedTimes));
		
		// TODO does EDAL support only Gregorian dates?
		
		referencing.add(ImmutableMap.of(
				COMPONENTS, ImmutableList.of("t"),
				SYSTEM, ImmutableMap.of(
						"type", "TemporalRS",
						"calendar", "Gregorian"
						)
				));
		
		// FIXME add bounds if not infinitesimal
		//  -> how do we query that except checking if low==high?
		//domainJson.put("timeBounds", t.getDomainObjects().iterator());
	}
	
	public static IntStream getVerticalAxisIndices(VerticalAxis ax, SubsetConstraint subset) {
		if (ax == null) {
			return IntStream.of(0);
		}
		
		// FIXME bounds are wrong for EN3 dataset
		// -> they should be single points but are calculated bounds which becomes a problem
		//    e.g. if the only z coords are [ 0.0, 3022] then extent and bounds will cover a lot more
		
		List<Extent<Double>> bounds = IntStream.range(0, ax.size())
				.mapToObj(ax::getCoordinateBounds)
				.collect(Collectors.toList());
		
		IntStream axIndices = IntStream.range(0, ax.size())
			.filter(i -> subset.verticalExtent.intersects(bounds.get(i)));
		
		if (!subset.verticalTarget.isPresent()) {
			return axIndices;
		}
		
		// find vertical value closest to target and return its index
		List<Double> values = ax.getCoordinateValues();
		double target = subset.verticalTarget.get();
		List<Integer> indices = Ints.asList(axIndices.toArray());
		List<Double> distances = Utils.mapList(indices, i -> Math.abs(values.get(i) - target));
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
	
	public static IntStream getTimeAxisIndices(TimeAxis ax, Constraint subset) {
		if (ax == null) {
			return IntStream.of(0);
		}
		// FIXME add overload in EDAL to get all coordinate bounds as List like the values
		//  -> very similar to getDomainObjects but better semantics and List type
		List<Extent<DateTime>> bounds = IntStream.range(0, ax.size())
				.mapToObj(ax::getCoordinateBounds)
				.collect(Collectors.toList());
		IntStream axIndices = IntStream.range(0, ax.size())
			.filter(i -> subset.timeExtent.intersects(bounds.get(i)));
		return axIndices;
	}
	
	/**
	 * NOTE: supports rectilinear lon-lat grids only for now
	 */
	public static IntStream getXAxisIndices(ReferenceableAxis<Double> ax, Constraint subset) {
		List<Extent<Double>> bounds = IntStream.range(0, ax.size())
				.mapToObj(ax::getCoordinateBounds)
				.collect(Collectors.toList());

		Extent<Double> lonExtent;
		if (subset.longitudeExtent.getLow() != null) {
			// wrap the (unwrapped) query longitude to the longitude range of the coverage domain
			// e.g. [-170,-160] might get wrapped to [190,200] if the domain extent is [0,360] and not [-180,180]
			lonExtent = Utils.wrapLongitudeExtent(subset.longitudeExtent, ax.getCoordinateExtent());
		} else {
			lonExtent = subset.longitudeExtent;
		}
		
		IntStream axIndices = IntStream.range(0, ax.size())
			.filter(i -> lonExtent.intersects(bounds.get(i)));
		return axIndices;
	}

	/**
	 * NOTE: supports rectilinear lon-lat grids only for now
	 */
	public static IntStream getYAxisIndices(ReferenceableAxis<Double> ax, Constraint subset) {
		List<Extent<Double>> bounds = IntStream.range(0, ax.size())
				.mapToObj(ax::getCoordinateBounds)
				.collect(Collectors.toList());
		IntStream axIndices = IntStream.range(0, ax.size())
			.filter(i -> subset.latitudeExtent.intersects(bounds.get(i)));
		return axIndices;
	}
	
		
}
