package uk.ac.rdg.resc.edal.json;

import org.restlet.Component;
import org.restlet.service.CorsService;

import com.google.common.collect.ImmutableSet;

public class RestletComponent extends Component {
	public RestletComponent() {
		CorsService corsService = new CorsService();
		corsService.setAllowedOrigins(ImmutableSet.of("*"));
		corsService.setExposedHeaders(ImmutableSet.of("Link"));
		corsService.setAllowedCredentials(true);
		getServices().add(corsService);
		
		// our REST API
		App app = new App();
		app.getEncoderService().setEnabled(true); // gzip
		getDefaultHost().attach("/api", app);
	}
}
