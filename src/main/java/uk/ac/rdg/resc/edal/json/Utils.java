package uk.ac.rdg.resc.edal.json;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import uk.ac.rdg.resc.edal.dataset.Dataset;
import uk.ac.rdg.resc.edal.dataset.DatasetFactory;
import uk.ac.rdg.resc.edal.dataset.cdm.CdmGridDatasetFactory;
import uk.ac.rdg.resc.edal.exceptions.EdalException;

public final class Utils {
	public static Dataset getDataset(String datasetId) throws IOException,
			EdalException {

		URL resource = Utils.class.getResource("/" + datasetId);
		DatasetFactory datasetFactory = new CdmGridDatasetFactory();
		Dataset dataset = datasetFactory.createDataset(datasetId,
				resource.getFile());
		return dataset;
	}
	
	public static <I,O> Iterable<O> map(Collection<I> l, Function<? super I,O> fn) {
		return l.stream().map(fn)::iterator;
	}
	
	public static <I,O> List<O> mapList(Collection<I> l, Function<? super I,O> fn) {
		return l.stream().map(fn).collect(Collectors.toList());
	}
}
