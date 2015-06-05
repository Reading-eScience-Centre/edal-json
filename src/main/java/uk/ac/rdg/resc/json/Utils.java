package uk.ac.rdg.resc.json;

import java.io.IOException;
import java.net.URL;

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
}
