package uk.ac.rdg.resc.edal.json;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.msgpack.MessagePack;
import org.restlet.data.MediaType;
import org.restlet.representation.StreamRepresentation;

public class MessagePackRepresentation extends StreamRepresentation {

	public static final MediaType APPLICATION_MSGPACK;
	static {
	     APPLICATION_MSGPACK = MediaType.register(
	            "application/x-msgpack", "MessagePack binary");
	}
	
	private final Object o;
		
    public MessagePackRepresentation(Object o) {
    	super(APPLICATION_MSGPACK);
        this.o = o;        
    }

    @Override
    public void write(OutputStream out) throws IOException {
    	long t0 = System.currentTimeMillis();
    	new MessagePack().write(out, this.o);
    	System.out.println("write to stream: " + String.valueOf(System.currentTimeMillis()-t0));
    }

	@Override
	public InputStream getStream() throws IOException {
		return null;
	}

}
