package uk.ac.rdg.resc.edal.json;

import org.restlet.Application;
import org.restlet.Component;
import org.restlet.Restlet;
import org.restlet.data.Protocol;
import org.restlet.resource.Directory;
import org.restlet.service.CorsService;

import com.google.common.collect.ImmutableSet;

public class RestletComponent extends Component {
	public RestletComponent() {
		CorsService corsService = new CorsService();
		corsService.setAllowedOrigins(ImmutableSet.of("*"));
		corsService.setAllowedCredentials(true);
		getServices().add(corsService);
		
		// our REST API
		App app = new App();
		app.getEncoderService().setEnabled(true); // gzip
		getDefaultHost().attach("/api", app);
		
		// static files
		getClients().add(Protocol.CLAP);
		getDefaultHost().attach("/static", new Application() {  
            @Override  
            public Restlet createInboundRoot() {  
                return new Directory(getContext(), "clap://class/static/");
            }  
        });
	}
}
