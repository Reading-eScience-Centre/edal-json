package uk.ac.rdg.resc.edal.json;

import java.util.Map;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;

public class Hydra {

	/**
	 * 
	 * @param baseUrl without any query parameters
	 * @param canFilter whether to include filtering parameters
	 * @param canSubset whether to include subset parameters
	 * @return
	 */
	public static Map<String,Object> getApiIriTemplate(String baseUrl, String queryString, boolean canFilter, boolean canSubset) {
		assert canFilter || canSubset;
		
		String filter = "bbox,timeStart,timeEnd,verticalStart,verticalEnd";
		String subset = "subsetBbox,subsetTimeStart,subsetTimeEnd,subsetVerticalStart,subsetVerticalEnd,subsetVerticalTarget";
		
		String templateStart = queryString.equals("") ? "{?" : "{&";
		String template = baseUrl + queryString + templateStart;
		if (canFilter) {
			template += filter;
		}
		if (canSubset) {
			if (canFilter) {
				template += ",";
			}
			template += subset;
		}
		template += "}";
		
		Builder<Object> mappings = ImmutableList.builder();
		if (canFilter) {
			mappings.add(
				mapping("bbox", "opensearchgeo:box", "xsd:string", 
						"The box is defined by 'west, south, east, north' coordinates of longitude, latitude, "
						+ "in EPSG:4326 decimal degrees. For values crossing the 180 degrees meridian the "
						+ "west value should be bigger than the east value."),
				
				mapping("timeStart", "opensearchtime:start", "xsd:string", 
						"Character string with the start of the temporal interval according to RFC3339."),
				
				mapping("timeEnd", "opensearchtime:end", "xsd:string", 
						"Character string with the end of the temporal interval according to RFC3339."),
				
				mapping("verticalStart", Constants.CovAPIPrefix + ":verticalStart", "xsd:string", 
						"Numeric string with the start of the vertical interval given in native CRS units."),
				
				mapping("verticalEnd", Constants.CovAPIPrefix + ":verticalEnd", "xsd:string", 
						"Numeric string with the end of the vertical interval given in native CRS units.")
				);
		}
		if (canSubset) {
			mappings.add(
				mapping("subsetBbox", Constants.CovAPIPrefix + ":subsetBbox", "opensearchgeo:box", 
						"The box is defined by 'west, south, east, north' coordinates of longitude, latitude, "
						+ "in EPSG:4326 decimal degrees. For values crossing the 180 degrees meridian the "
						+ "west value should be bigger than the east value."),
				
				mapping("subsetTimeStart", Constants.CovAPIPrefix + ":subsetTimeStart", "opensearchtime:start", 
						"Character string with the start of the temporal interval according to RFC3339."),
				
				mapping("subsetTimeEnd", Constants.CovAPIPrefix + ":subsetTimeEnd", "opensearchtime:end", 
						"Character string with the end of the temporal interval according to RFC3339."),
				
				mapping("subsetVerticalStart", Constants.CovAPIPrefix + ":subsetVerticalStart", "xsd:string", 
						"Numeric string with the start of the vertical interval given in native CRS units."),
				
				mapping("subsetVerticalEnd", Constants.CovAPIPrefix + ":subsetVerticalEnd", "xsd:string", 
						"Numeric string with the end of the vertical interval given in native CRS units."),
				
				mapping("subsetVerticalTarget", Constants.CovAPIPrefix + ":subsetVerticalTarget", "xsd:string", 
						"Numeric string with a vertical target given in native CRS units. "
						+ "The subsetted coverage will only contain the single vertical coordinate which is closest "
						+ "to the target.")
				);
		}
		
		Map<String,Object> templateObj = ImmutableMap.of(
				"type", Constants.HydraPrefix + ":IriTemplate",
				Constants.HydraPrefix + ":template", template,
				Constants.HydraPrefix + ":mapping", mappings.build()
				);
		
		return templateObj;
		// JSON-LD framing doesn't support named graphs yet, therefore we don't use non-default graphs yet.
		/*
		return ImmutableMap.of(
			"id", "#api",
			"@graph", templateObj
			);
		*/
	}
	
	private static Map<String,Object> mapping(String variable, String propertyId, String propertyRange, String propertyComment) {
		return ImmutableMap.of(
				"type", Constants.HydraPrefix + ":IriTemplateMapping",
				Constants.HydraPrefix + ":variable", variable,
				Constants.HydraPrefix + ":property", ImmutableMap.of(
						"id", propertyId,
						"comment", propertyComment,
						Constants.RdfsPrefix + ":range", propertyRange
						),
				Constants.HydraPrefix + ":required", false
				);
	}
	
}
