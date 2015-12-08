package uk.ac.rdg.resc.edal.json;

import java.io.IOException;
import java.io.OutputStream;

import org.restlet.ext.jackson.JacksonRepresentation;

public class JacksonRepresentationWithTimer<T> extends JacksonRepresentation<T> {

	public JacksonRepresentationWithTimer(T object) {
		super(object);
	}

	@Override
	public void write(OutputStream outputStream) throws IOException {
		long t0 = System.currentTimeMillis();
		super.write(outputStream);
		System.out.println("write to stream: " + String.valueOf(System.currentTimeMillis() - t0));
	}

}
