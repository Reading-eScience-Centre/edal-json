package uk.ac.rdg.resc.edal.json;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.geotoolkit.metadata.iso.citation.Citations;
import org.geotoolkit.referencing.IdentifiedObjects;
import org.geotoolkit.referencing.crs.DefaultGeographicCRS;
import org.joda.time.DateTime;
import org.opengis.util.FactoryException;
import org.restlet.Server;
import org.restlet.data.Protocol;
import org.restlet.data.Reference;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.restlet.routing.Router;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import uk.ac.rdg.resc.edal.dataset.Dataset;
import uk.ac.rdg.resc.edal.dataset.DatasetFactory;
import uk.ac.rdg.resc.edal.dataset.GriddedDataset;
import uk.ac.rdg.resc.edal.dataset.cdm.CdmGridDatasetFactory;
import uk.ac.rdg.resc.edal.domain.Domain;
import uk.ac.rdg.resc.edal.domain.GridDomain;
import uk.ac.rdg.resc.edal.domain.PointCollectionDomain;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.feature.Feature;
import uk.ac.rdg.resc.edal.feature.PointCollectionFeature;
import uk.ac.rdg.resc.edal.geometry.BoundingBox;
import uk.ac.rdg.resc.edal.grid.RegularGrid;
import uk.ac.rdg.resc.edal.metadata.GridVariableMetadata;
import uk.ac.rdg.resc.edal.metadata.Parameter;
import uk.ac.rdg.resc.edal.metadata.VariableMetadata;
import uk.ac.rdg.resc.edal.position.HorizontalPosition;
import uk.ac.rdg.resc.edal.position.VerticalCrs;
import uk.ac.rdg.resc.edal.position.VerticalPosition;
import uk.ac.rdg.resc.edal.util.Array1D;
import uk.ac.rdg.resc.edal.util.CollectionUtils;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class FeatureResource extends ServerResource {

	@Get("json")
	public Representation json() throws IOException, EdalException {
		String datasetId = Reference.decode(getAttribute("datasetId"));
		String featureId = Reference.decode(getAttribute("featureId"));
		Dataset dataset = Utils.getDataset(datasetId);
		Feature feature = dataset.readFeature(featureId);
		
		String featureUrl = "http://foo/datasets/" + dataset.getId() + "/features/" + feature.getId();
		
		Map j = ImmutableMap.of(
				"id", featureUrl,
				"title", feature.getName(),
				"phenomenonTime", "?",
				"result", ImmutableMap.of(
						"domain", getDomainJson(feature),
						"rangeType", ImmutableMap.of(
								"fields", getParameterTypesJson(feature)
								),
						"range", getParameterValuesJson(feature)
						)
				);
		
		JsonRepresentation r = new JsonRepresentation(j);
		r.setIndenting(true);
		return r;
	}
	
	private Map getDomainJson(Feature<?> feature) {
		Domain<?> domain = feature.getDomain();
		Map domainJson;
		
		if (domain instanceof GridDomain) {
			GridDomain grid = (GridDomain) domain;
			if (grid.getHorizontalGrid() instanceof RegularGrid) {
				RegularGrid reggrid = (RegularGrid) grid.getHorizontalGrid();
				BoundingBox bb = reggrid.getBoundingBox();
				String crs;
				try {
					crs = IdentifiedObjects.lookupIdentifier(Citations.HTTP_OGC, reggrid.getCoordinateReferenceSystem(), true);
					if (crs == null) {
						crs = reggrid.getCoordinateReferenceSystem().getName().toString();
					}
				} catch (FactoryException e) {
					crs = "UNKNOWN: " + e.toString(); 
				}
				domainJson = ImmutableMap.of(
						"type", "RegularGrid",
					    "crs", crs,
					    "bbox", ImmutableList.of(bb.getMinX(), bb.getMinY(), bb.getMaxX(), bb.getMaxY())
						);
			} else {
				domainJson = unsupportedDomain(domain);
			}
		} else {
			domainJson = unsupportedDomain(domain);
		}
		
		 
		return domainJson;
	}
	
	private static Map unsupportedDomain(Domain<?> domain) {
		return ImmutableMap.of(
				"type", domain.getClass().getName(),
				"info", "UNSUPPORTED"
				);
	}
	
	
	private List getParameterTypesJson(Feature<?> feature) {
		List types = new LinkedList();
		for (String paramId : feature.getParameterIds()) {
			Parameter param = feature.getParameter(paramId);
			types.add(ImmutableMap.of(
					"name", param.getId(),
					"title", param.getTitle(),
					"description", param.getDescription(),
					"observedProperty", param.getStandardName() == null ? "UNKNOWN" : param.getStandardName(),
					"uom", param.getUnits()
					));
		}
		return types;
	}
	
	private List getParameterValuesJson(Feature<?> feature) {
		List values = new LinkedList();
		return values;
	}

}
