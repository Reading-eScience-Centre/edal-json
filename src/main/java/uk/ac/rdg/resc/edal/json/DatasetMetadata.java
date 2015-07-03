package uk.ac.rdg.resc.edal.json;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import uk.ac.rdg.resc.edal.dataset.Dataset;
import uk.ac.rdg.resc.edal.feature.DiscreteFeature;
import uk.ac.rdg.resc.edal.feature.Feature;
import uk.ac.rdg.resc.edal.json.FeatureResource.FeatureMetadata;
import uk.ac.rdg.resc.edal.metadata.Parameter;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

class DatasetMetadata {
	private final String datasetId;
	private final Map<String,FeatureMetadata> featureMetadata = new HashMap<>();
	private final Map<Class<?>, Integer> featureCounts = new HashMap<>();
	private final Map<Class<?>, Set<Parameter>> featureParams = new HashMap<>();
	private DomainMetadata domainMetadata;
	
	public DatasetMetadata(String datasetId) {
		this.datasetId = datasetId;
		extractMetadata();
	}
	
	private void extractMetadata() {
		Dataset dataset = Utils.getDataset(datasetId);
		
		for (String featureId : dataset.getFeatureIds()) {
			Feature<?> feature = dataset.readFeature(featureId);
			if (!(feature instanceof DiscreteFeature)) {
				continue;
			}
			FeatureMetadata meta = new FeatureMetadata(
					datasetId, feature);
			featureMetadata.put(featureId, meta);
			
			int count = featureCounts.getOrDefault(meta.type, 0);
			featureCounts.put(meta.type, count+1);
			
			Set<Parameter> params = featureParams.getOrDefault(meta.type, new HashSet<>());
			params.addAll(meta.rangeMeta.getParameters());
			featureParams.putIfAbsent(meta.type, params);
		}
		List<DomainMetadata> domainMetadatas = Utils.mapList(featureMetadata.values(), m -> m.domainMeta);
		domainMetadata = new DomainMetadata(domainMetadatas);
	}
	
	public Set<String> getFeatureIds() {
		return featureMetadata.keySet();
	}
	public FeatureMetadata getFeatureMetadata(String featureId) {
		return featureMetadata.get(featureId);
	}
	
	public Set<Class<?>> getFeatureTypes() {
		return featureCounts.keySet();
	}
	
	public int getFeatureCount(Class<?> type) {
		return featureCounts.getOrDefault(type, 0);
	}
	
	public Set<Parameter> getFeatureParameters(Class<?> type) {
		return featureParams.get(type);
	}
	
	public DomainMetadata getDomainMetadata() {
		return domainMetadata;
	}
	
	public Supplier<Dataset> getLazyDataset() {
		return Suppliers.memoize(() -> Utils.getDataset(datasetId));
	}
}