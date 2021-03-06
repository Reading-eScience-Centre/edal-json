package uk.ac.rdg.resc.edal.json;

public class Constants {
	
	public static final String CoverageHTMLUrlPrefix = "https://covjson.org/playground/#";
	public static final String CoverageCollectionHTMLUrlPrefix = CoverageHTMLUrlPrefix;
	public static final String CoverageOutlinesHTMLUrlPrefix = "http://geojson.io/#data=data:text/x-url,";
	public static final String CoverageCollectionOutlinesHTMLUrlPrefix = CoverageOutlinesHTMLUrlPrefix;
	public static final String DatasetCatalogHTMLUrlPrefix = "http://ec-melodies.github.io/demo-portal/#url=";
	
	public static final String DctNS = "http://purl.org/dc/terms/";

	public static final String GeoJSONLDContext = "https://rawgit.com/geojson/geojson-ld/master/contexts/geojson-time.jsonld";
	public static final String CoverageJSONContext = "https://covjson.org/context.jsonld";
	
	// we don't use the Hydra context anymore since it was unavailable for ~1week and the w3c took too long to fix it
	//public static final String HydraContext = "http://www.w3.org/ns/hydra/core";
	
	public static final String HydraPrefix = "hydra";
	public static final String HydraNamespace = "http://www.w3.org/ns/hydra/core#";
	
	public static final String RdfsPrefix = "rdfs";
	public static final String RdfsNamespace = "http://www.w3.org/2000/01/rdf-schema#";
	public static final String Comment = RdfsPrefix + ":comment";
	
	public static final String OpenSearchGeoPrefix = "opensearchgeo";
	public static final String OpenSearchGeoNamespace = "http://a9.com/-/opensearch/extensions/geo/1.0/";
	
	public static final String OpenSearchTimePrefix = "opensearchtime";
	public static final String OpenSearchTimeNamespace = "http://a9.com/-/opensearch/extensions/time/1.0/";
	
	public static final String CovJSONProfileStandalone = "https://covjson.org/def/core#standalone";
	
	public static final String CovAPINamespace = "http://coverageapi.org/ns#";
	public static final String CovAPIPrefix = "covapi";
		
	public static final String SubsetByIndexURI = "http://coverageapi.org/def#subsetByIndex";
		
	// paging is disabled for geojson for now
	// reason: general geojson clients couldn't handle it anyway
	public static final int DEFAULT_COVERAGES_PER_PAGE = 100;
	public static final int DEFAULT_GEOJSON_FEATURES_PER_PAGE = Integer.MAX_VALUE;
	
	// TODO this should depend on coverage size and whether range/domain is embedded or not
	public static final int MAXIMUM_COVERAGES_PER_PAGE = 10000;
	public static final int MAXIMUM_GEOJSON_FEATURES_PER_PAGE = Integer.MAX_VALUE;	
	
}
