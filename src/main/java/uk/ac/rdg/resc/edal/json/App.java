package uk.ac.rdg.resc.edal.json;

import org.restlet.Application;
import org.restlet.Component;
import org.restlet.Restlet;
import org.restlet.data.ClientInfo;
import org.restlet.data.MediaType;
import org.restlet.data.Preference;
import org.restlet.data.Protocol;
import org.restlet.resource.Directory;
import org.restlet.routing.Router;
import org.restlet.service.CorsService;

import com.google.common.collect.ImmutableSet;

public class App extends Application {
	public static void main(String[] args) throws Exception {
		Component component = new Component();
		component.getServers().add(Protocol.HTTP, 8182);
		
		CorsService corsService = new CorsService();
		corsService.setAllowedOrigins(ImmutableSet.of("*"));
		corsService.setAllowedCredentials(true);
		component.getServices().add(corsService);
		
		// our REST API
		App app = new App();
		app.getEncoderService().setEnabled(true); // gzip
		component.getDefaultHost().attach("/api", app);
		
		// static files
		component.getClients().add(Protocol.CLAP);
		component.getDefaultHost().attach("/static", new Application() {  
            @Override  
            public Restlet createInboundRoot() {  
                return new Directory(getContext(), "clap://class/static/");
            }  
        });
		
		component.start();
	}

	@Override
	public Restlet createInboundRoot() {
        getTunnelService().setExtensionsTunnel(true);
        
        // clear common extensions so that we can define ours in preferred order
        // Note that this doesn't influence content negotiation, it is
        // just for serving JSON by default if no matching Accept headers are given.
        getMetadataService().clearExtensions();
        MediaType jsonLd = new MediaType("application/ld+json");
        MediaType covjson = new MediaType("application/cov+json");
        MediaType covjsonMsgpack = new MediaType("application/cov+json;encoding=msgpack");
        MediaType covjsonCBOR = new MediaType("application/cov+json;encoding=cbor");
        MediaType geojson = new MediaType("application/vnd.geo+json");
        
        // GeoJSON is the default for resources that support it (features)
        // Otherwise JSON-LD, then CoverageJSON.
        // Binary CoverageJSON will only be delivered if explicitly requested.
        getMetadataService().addExtension("geojson", geojson);
        getMetadataService().addExtension("jsonld", jsonLd);
        getMetadataService().addExtension("covjson", covjson);
        getMetadataService().addExtension("covjsonb", covjsonMsgpack); // TODO switch to CBOR later
        getMetadataService().addExtension("msgpack", covjsonMsgpack);
        getMetadataService().addExtension("cbor", covjsonCBOR);        
		
		Router router = new Router();
		router.attach("/datasets/{datasetId}/features/{featureId}/range/{parameterId}",
				FeatureParameterRangeResource.class);
		router.attach("/datasets/{datasetId}/features/{featureId}",
				FeatureResource.class);
		router.attach("/datasets/{datasetId}/features",
				FeaturesResource.class);
		router.attach("/datasets/{datasetId}/params/{paramId}",
				ParamResource.class);
		router.attach("/datasets/{datasetId}", DatasetResource.class);
		// router.attach("/datasets",
		// DatasetsResource.class);
		return router;
	}
	
	public static boolean acceptsJSON(ClientInfo info) {
		for (Preference<MediaType> pref : info.getAcceptedMediaTypes()) {
			if (pref.getMetadata().equals(MediaType.APPLICATION_JSON)) {
				return true;
			}
		}
		return false;
	}
}