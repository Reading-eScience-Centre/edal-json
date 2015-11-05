package uk.ac.rdg.resc.edal.json;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.joda.time.DateTime;
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
import com.google.common.primitives.Ints;

import uk.ac.rdg.resc.edal.dataset.Dataset;
import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.feature.DiscreteFeature;
import uk.ac.rdg.resc.edal.geometry.BoundingBox;
import uk.ac.rdg.resc.edal.grid.RectilinearGrid;
import uk.ac.rdg.resc.edal.grid.ReferenceableAxis;
import uk.ac.rdg.resc.edal.grid.RegularGrid;
import uk.ac.rdg.resc.edal.grid.TimeAxis;
import uk.ac.rdg.resc.edal.grid.VerticalAxis;
import uk.ac.rdg.resc.edal.json.CoverageResource.FeatureMetadata;
import uk.ac.rdg.resc.edal.json.CoverageResource.UniformFeature;
import uk.ac.rdg.resc.edal.metadata.Parameter;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class CoverageDomainResource extends ServerResource {

	@Get("covjson|covcbor|covmsgpack")
	public Representation json() throws IOException, EdalException {
		String datasetId = Reference.decode(getAttribute("datasetId"));
		String featureId = Reference.decode(getAttribute("coverageId"));
		SubsetConstraint subset = new SubsetConstraint(getQueryValue("subsetByCoordinate"));
		Dataset dataset = Utils.getDataset(datasetId);
		
		UniformFeature uniFeature =
				new UniformFeature((DiscreteFeature)dataset.readFeature(featureId));
		
		Series<Header> headers = this.getResponse().getHeaders();
		headers.add(new Header("Link", "<http://coveragejson.org/def#Domain>; rel=\"type\""));
		headers.add(new Header("Link", "<http://coveragejson.org/def#" + uniFeature.type + ">; rel=\"type\""));
		// TODO add as soon as subsetting by index is supported
		//headers.add(new Header("Link", "<" + CoverageResource.SubsetByIndexURI + ">; rel=\"" + CoverageResource.CapabilityURI + "\""));
		headers.add(new Header("Link", "<" + CoverageResource.SubsetByCoordinateURI + ">; rel=\"" + CoverageResource.CapabilityURI + "\""));
		
		// TODO add subsetOf rel if subsetted
		// TODO add link to coverage
		
		
		Map j = getDomainJson(uniFeature, subset);
		Representation r = App.getCovJsonRepresentation(this, j);
		
		// TODO think about caching strategy
		Date exp = Date.from(LocalDate.now().plusDays(1).atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
		r.setExpirationDate(exp);
		
		return r;
	}
	
	public static Map getDomainJson(UniformFeature uniFeature, SubsetConstraint subset) {
		Builder domainJson = ImmutableMap.builder();
		
		// no support for trajectories currently
		// we support everything which is a subtype of a rectilinear grid (includes profiles)
		
		// TODO add shortcuts when no subsetting is requested
		
		addHorizontalGrid(uniFeature.rectgrid, subset, domainJson);
		addVerticalAxis(uniFeature.z, subset, domainJson);
		addTimeAxis(uniFeature.t, subset, domainJson);
		domainJson.put("type", uniFeature.type);
		
		return domainJson.build();
	}
	
	/**
	 * NOTE: supports rectilinear lon-lat grids only for now
	 */
	private static void addHorizontalGrid(RectilinearGrid grid, Constraint subset, Builder domainJson) {
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
		
		// FIXME add bounds if not infinitesimal
		//  -> how do we query that except checking if low==high?
		
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
	
	private static void addVerticalAxis(VerticalAxis z, SubsetConstraint subset, Builder domainJson) {
		if (z == null) {
			return;
		}
		List<Double> heights = z.getCoordinateValues();
		double[] subsettedHeights = getVerticalAxisIndices(z, subset).mapToDouble(heights::get).toArray();
		
		domainJson.put("z", subsettedHeights);
		
		// FIXME add bounds if not infinitesimal
		//  -> how do we query that except checking if low==high?
		//domainJson.put("verticalBounds", z.getDomainObjects().iterator());
		
		// TODO are there no standards for vertical CRS, with codes etc.?
		domainJson.put("zCrs", ImmutableMap.of(
				"uom", z.getVerticalCrs().getUnits(),
				"positiveUpwards", z.getVerticalCrs().isPositiveUpwards(),
				"dimensionless", z.getVerticalCrs().isDimensionless(),
				"pressure", z.getVerticalCrs().isPressure()
				));
		
	}
		
	private static void addTimeAxis(TimeAxis t, Constraint subset, Builder domainJson) {
		if (t == null) {
			return;
		}
		List<DateTime> times = t.getCoordinateValues();
		String[] subsettedTimes = getTimeAxisIndices(t, subset)
				.mapToObj(i -> times.get(i).toString())
				.toArray(String[]::new);
		domainJson.put("t", subsettedTimes.length == 1 ? subsettedTimes[0] : subsettedTimes);
		
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
		// FIXME longitudes must be (un)wrapped the same way!!
		//  -> are longitudes normalized in some way when read in EDAL?
		IntStream axIndices = IntStream.range(0, ax.size())
			.filter(i -> subset.longitudeExtent.intersects(bounds.get(i)));
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
