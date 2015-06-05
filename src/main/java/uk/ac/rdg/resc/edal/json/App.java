package uk.ac.rdg.resc.edal.json;

import org.restlet.Application;
import org.restlet.Component;
import org.restlet.Restlet;
import org.restlet.data.Protocol;
import org.restlet.routing.Router;

public class App extends Application {
	public static void main(String[] args) throws Exception {
		Component component = new Component();
		component.getServers().add(Protocol.HTTP, 8182);
		component.getDefaultHost().attach(new App());
		component.start();
	}
	
	@Override
	public Restlet createInboundRoot() {
		Router router = new Router();
		router.attach("/datasets/{datasetId}/features/{featureId}",
				FeatureResource.class);
		router.attach("/datasets/{datasetId}",
				DatasetResource.class);
//		router.attach("/datasets",
//				DatasetsResource.class);
		return router;
	}
}