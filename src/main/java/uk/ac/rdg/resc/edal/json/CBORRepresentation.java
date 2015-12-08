package uk.ac.rdg.resc.edal.json;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.restlet.data.MediaType;
import org.restlet.representation.StreamRepresentation;

import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.DoublePrecisionFloat;
import co.nstant.in.cbor.model.HalfPrecisionFloat;
import co.nstant.in.cbor.model.NegativeInteger;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.UnicodeString;
import co.nstant.in.cbor.model.UnsignedInteger;


public class CBORRepresentation extends StreamRepresentation {

	public static final MediaType APPLICATION_CBOR;
	static {
	     APPLICATION_CBOR = MediaType.register(
	            "application/cbor", "CBOR binary");
	}
	
	private final Object o;
		
    public CBORRepresentation(Object o) {
    	super(APPLICATION_CBOR);
        this.o = o;
    }

    @Override
    public void write(OutputStream out) throws IOException {
    	long t0 = System.currentTimeMillis();
    	DataItem data = getDataItem(this.o);
    	try {
			new CborEncoder(out).encode(data);
		} catch (CborException e) {
			throw new RuntimeException(e);
		}
    	System.out.println("write to stream: " + String.valueOf(System.currentTimeMillis()-t0));
    }

	private static DataItem getDataItem(Object o) {
		// recursively transform the input object to DataItem objects
		if (o == null) {
			return SimpleValue.NULL;
		} else if (o instanceof Boolean) {
			Boolean b = (Boolean) o;
			return b ? SimpleValue.TRUE : SimpleValue.FALSE;
		} else if (o instanceof Integer) {
			Integer n = (Integer) o;
			if (n < 0) {
				return new NegativeInteger(n);
			} else {
				return new UnsignedInteger(n);
			}
		} else if (o instanceof Float) {
			Float n = (Float) o;
			return new HalfPrecisionFloat(n);
		} else if (o instanceof Double) {
			Double n = (Double) o;
			return new DoublePrecisionFloat(n);
		} else if (o instanceof String) {
			String s = (String) o;
			return new UnicodeString(s);
		} else if (o instanceof List) {
			List<?> l = (List<?>) o;
			Array arr = new Array();
			for (Object el : l) {
				arr.add(getDataItem(el));
			}
			return arr;
		} else if (o instanceof Map) {
			Map<?,?> m = (Map<?, ?>) o;
			co.nstant.in.cbor.model.Map map = new co.nstant.in.cbor.model.Map();
			for (Entry<?,?> entry : m.entrySet()) {
				map.put(getDataItem(entry.getKey()), getDataItem(entry.getValue()));
			}
			return map;
		} else if (o instanceof char[]) {
			char[] arr = (char[]) o;
			return new TypedArray(arr);
		} else if (o instanceof byte[]) {
			byte[] arr = (byte[]) o;
			return new TypedArray(arr);
		} else if (o instanceof short[]) {
			short[] arr = (short[]) o;
			return new TypedArray(arr);
		} else if (o instanceof int[]) {
			int[] arr = (int[]) o;
			return new TypedArray(arr);
		} else if (o instanceof long[]) {
			long[] arr = (long[]) o;
			return new TypedArray(arr);
		} else if (o instanceof float[]) {
			float[] arr = (float[]) o;
			return new TypedArray(arr);
		} else if (o instanceof double[]) {
			double[] arr = (double[]) o;
			return new TypedArray(arr);
		} else if (o instanceof Object[]) {
			Object[] a = (Object[]) o;
			Array arr = new Array();
			for (Object el : a) {
				arr.add(getDataItem(el));
			}
			return arr;
		} else {
			throw new RuntimeException("Type not supported: " + o.getClass().getName());
		}
	}

	@Override
	public InputStream getStream() throws IOException {
		return null;
	}

}
