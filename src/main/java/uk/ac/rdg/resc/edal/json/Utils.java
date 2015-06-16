package uk.ac.rdg.resc.edal.json;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.List;
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
import uk.ac.rdg.resc.edal.exceptions.EdalException;

public final class Utils {
	public static Dataset getDataset(String datasetId) {

		URL resource = Utils.class.getResource("/" + datasetId);
		Dataset dataset;
		DatasetFactory datasetFactory = new CdmGridDatasetFactory();
		// TODO this should happen automatically in EDAL
		try {
			dataset = datasetFactory.createDataset(datasetId,
					resource.getFile());
		} catch (Exception e) {
			datasetFactory = new En3DatasetFactory();
			try {
				dataset = datasetFactory.createDataset(datasetId,
						resource.getFile());
			} catch (IOException | EdalException e1) {
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
				} else {
					crsUri = crs.getName().toString();
				}
			}
		} catch (FactoryException e) {
			crsUri = "UNKNOWN: " + e.toString(); 
		}
		return crsUri;
	}
	
	public static <I,O> Iterable<O> map(Collection<I> l, Function<? super I,O> fn) {
		return l.stream().map(fn)::iterator;
	}
	
	public static <I,O> List<O> mapList(Collection<I> l, Function<? super I,O> fn) {
		return l.stream().map(fn).collect(Collectors.toList());
	}
}
