package uk.ac.rdg.resc.edal.json;

public class Constants {

	public static final String GeoJSONLDContext = "https://rawgit.com/geojson/geojson-ld/master/contexts/geojson-time.jsonld";
	public static final String CoverageJSONContext = "https://rawgit.com/reading-escience-centre/coveragejson/master/contexts/coveragejson-base.jsonld";
	public static final String HydraContext = "http://www.w3.org/ns/hydra/core";
	
	public static final String CovJSONNamespace = "http://coveragejson.org/ns#";
	public static final String Domain = CovJSONNamespace + "Domain";
	public static final String Range = CovJSONNamespace + "Range";
	
	public static final String CovAPINamespace = "http://coverageapi.org/ns#";
		
	public static final String CanIncludeURI = CovAPINamespace + "canInclude";
	public static final String SubsetByIndexURI = "http://coverageapi.org/def#subsetByIndex";
	public static final String SubsetByCoordinateURI = "http://coverageapi.org/def#subsetByCoordinate";
	public static final String FilterByCoordinateURI = "http://coverageapi.org/def#filterByCoordinate";
	
	public static final String SubsetOfURI = "http://coverageapi.org/def#subsetOf";
	
	public static final int DEFAULT_COVERAGES_PER_PAGE = 100;
	public static final int DEFAULT_GEOJSON_FEATURES_PER_PAGE = 10000;
	
	// TODO this should depend on coverage size and whether range/domain is embedded or not
	public static final int MAXIMUM_COVERAGES_PER_PAGE = 10000;
	public static final int MAXIMUM_GEOJSON_FEATURES_PER_PAGE = 100000;	
	
}
