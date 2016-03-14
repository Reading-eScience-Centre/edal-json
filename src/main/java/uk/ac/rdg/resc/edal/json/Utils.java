package uk.ac.rdg.resc.edal.json;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.geotoolkit.metadata.iso.citation.Citations;
import org.geotoolkit.referencing.IdentifiedObjects;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.util.FactoryException;

import uk.ac.rdg.resc.edal.dataset.Dataset;
import uk.ac.rdg.resc.edal.dataset.DatasetFactory;
import uk.ac.rdg.resc.edal.dataset.cdm.CdmGridDatasetFactory;
import uk.ac.rdg.resc.edal.dataset.cdm.En3DatasetFactory;
import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.util.Extents;

public final class Utils {
	// TODO probably not the right place
	private static Map<String,Dataset> datasetCache = new HashMap<>();
	
	/**
	 * Folder within classpath resources containing dataset files.
	 * This is temporary to just make it work.
	 */
	public static String DATASETS_FOLDER = "/datasets/";
	
	public static Dataset getDataset(String datasetId) {
		return datasetCache.computeIfAbsent(datasetId, Utils::doGetDataset);
	}
	
	private static Dataset doGetDataset(String datasetId) {
		URL resource = Utils.class.getResource(DATASETS_FOLDER + datasetId);
		Dataset dataset;
		DatasetFactory datasetFactory = new CdmGridDatasetFactory();
		// TODO this should happen automatically in EDAL
		try {
			dataset = datasetFactory.createDataset(datasetId,
					resource.getFile());
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("trying EN3 reader");
			datasetFactory = new En3DatasetFactory();
			try {
				dataset = datasetFactory.createDataset(datasetId,
						resource.getFile());
			} catch (IOException e1) {
				throw new RuntimeException(e1);
			}
		}
		return dataset;
	}
	
	public static String getCrsUri(CoordinateReferenceSystem crs) {
		String crsUri;
		try {
			crsUri = IdentifiedObjects.lookupIdentifier(Citations.HTTP_OGC, crs, true);
			if (crsUri == null) {
				// geotoolkit doesn't return this URI yet
				if (crs.getName().toString() == "WGS84(DD)") {
					crsUri = "http://www.opengis.net/def/crs/OGC/1.3/CRS84";
				}
			}
		} catch (FactoryException e) {
			throw new RuntimeException(e); 
		}
		return crsUri;
	}
	
	public static <I,O> Iterable<O> map(Collection<I> l, Function<? super I,O> fn) {
		return l.stream().map(fn)::iterator;
	}
	
	public static <I,O> List<O> mapList(Collection<I> l, Function<? super I,O> fn) {
		return l.stream().map(fn).collect(Collectors.toList());
	}
	
	public static Extent<Double> wrapLongitudeExtent(Extent<Double> extent, Extent<Double> wrapExtent) {
		double lonLow = wrapLongitude(extent.getLow(), wrapExtent);
		double lonHigh = wrapLongitude(extent.getHigh(), wrapExtent);
		if (lonHigh < lonLow) {
			lonHigh += 360;
		}
		Extent<Double> wrappedExtent = Extents.newExtent(lonLow, lonHigh);
		return wrappedExtent;
	}
	
	/**
	 * Wraps a longitude such that it falls within the given extent. If the extent
	 * is smaller than 360 degrees, then it gets extended on both sides.
	 */
	public static Double wrapLongitude(double lon, Extent<Double> extent) {
		double lonMid = (extent.getLow() + extent.getHigh()) / 2.0;
		double lonMinExtented = lonMid - 180;
		double lonWrapped = ((lon - lonMinExtented) % 360 + 360) % 360 + lonMinExtented;
		return lonWrapped;
	}
}
