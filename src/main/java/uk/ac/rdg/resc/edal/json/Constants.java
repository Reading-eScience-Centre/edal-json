package uk.ac.rdg.resc.edal.json;

public class Constants {
	
	public static final String CoverageHTMLUrlPrefix = "http://reading-escience-centre.github.io/leaflet-coverage-demo/#";
	public static final String CoverageCollectionHTMLUrlPrefix = CoverageHTMLUrlPrefix;
	public static final String CoverageOutlinesHTMLUrlPrefix = "http://geojson.io/#data=data:text/x-url,";
	public static final String CoverageCollectionOutlinesHTMLUrlPrefix = CoverageOutlinesHTMLUrlPrefix;
	public static final String DatasetCatalogHTMLUrlPrefix = "http://ec-melodies.github.io/demo-portal/#";
	
	public static final String DctNS = "http://purl.org/dc/terms/";

	public static final String GeoJSONLDContext = "https://rawgit.com/geojson/geojson-ld/master/contexts/geojson-time.jsonld";
	public static final String CoverageJSONContext = "https://rawgit.com/reading-escience-centre/coveragejson/master/contexts/coveragejson-base.jsonld";
	public static final String HydraContext = "http://www.w3.org/ns/hydra/core";
	
	public static final String OpenSearchGeoNamespace = "http://a9.com/-/opensearch/extensions/geo/1.0/";
	public static final String OpenSearchTimeNamespace = "http://a9.com/-/opensearch/extensions/time/1.0/";
	
	public static final String CovJSONNamespace = "http://coveragejson.org/ns#";
	public static final String Domain = CovJSONNamespace + "Domain";
	public static final String Range = CovJSONNamespace + "Range";
	
	public static final String CovAPINamespace = "http://coverageapi.org/ns#";
	public static final String CovAPIPrefix = "covapi";
		
	public static final String CanIncludeURI = CovAPINamespace + "canInclude";
	public static final String SubsetByIndexURI = "http://coverageapi.org/def#subsetByIndex";
		
	// paging is disabled for geojson for now
	// reason: general geojson clients couldn't handle it anyway
	public static final int DEFAULT_COVERAGES_PER_PAGE = 100;
	public static final int DEFAULT_GEOJSON_FEATURES_PER_PAGE = Integer.MAX_VALUE;
	
	// TODO this should depend on coverage size and whether range/domain is embedded or not
	public static final int MAXIMUM_COVERAGES_PER_PAGE = 10000;
	public static final int MAXIMUM_GEOJSON_FEATURES_PER_PAGE = Integer.MAX_VALUE;	
	
}
