package uk.ac.rdg.resc.edal.json;

import java.util.Map;
import java.util.Set;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

import uk.ac.rdg.resc.edal.dataset.Dataset;
import uk.ac.rdg.resc.edal.json.FeatureResource.FeatureMetadata;

class DatasetMetadata {
	private final Map<String,FeatureMetadata> featureMetadata;
	private final String datasetId;
	private final DomainMetadata domainMetadata;
	public DatasetMetadata(String datasetId, Map<String,FeatureMetadata> featureMetadata, DomainMetadata domainMetadata) {
		this.datasetId = datasetId;
		this.featureMetadata = featureMetadata;
		this.domainMetadata = domainMetadata;
	}
	public Set<String> getFeatureIds() {
		return featureMetadata.keySet();
	}
	public FeatureMetadata getFeatureMetadata(String featureId) {
		return featureMetadata.get(featureId);
	}
	public DomainMetadata getDomainMetadata() {
		return domainMetadata;
	}
	public Supplier<Dataset> getLazyDataset() {
		return Suppliers.memoize(() -> Utils.getDataset(datasetId));
	}
}