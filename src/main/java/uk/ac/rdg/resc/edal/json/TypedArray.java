package uk.ac.rdg.resc.edal.json;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import co.nstant.in.cbor.model.ByteString;

// Note: Java is big endian.
// By default, all input is converted to little endian (since this is what most clients will be).
// Overloaded constructors can be used to control this behavior.
public class TypedArray extends ByteString {

	public TypedArray(char[] arr) {
		this(arr, true);
	}

	public TypedArray(char[] arr, boolean asLittleEndian) {
		super(toByteArray(arr, asLittleEndian));
		setTag(asLittleEndian ? 69 : 65);
	}

	public TypedArray(byte[] arr) {
		super(arr);
		setTag(72);
	}

	public TypedArray(short[] arr) {
		this(arr, true);
	}

	public TypedArray(short[] arr, boolean asLittleEndian) {
		super(toByteArray(arr, asLittleEndian));
		setTag(asLittleEndian ? 77 : 73);
	}

	public TypedArray(int[] arr) {
		this(arr, true);
	}

	public TypedArray(int[] arr, boolean asLittleEndian) {
		super(toByteArray(arr, asLittleEndian));
		setTag(asLittleEndian ? 78 : 74);
	}

	public TypedArray(long[] arr) {
		this(arr, true);
	}

	public TypedArray(long[] arr, boolean asLittleEndian) {
		super(toByteArray(arr, asLittleEndian));
		setTag(asLittleEndian ? 79 : 75);
	}

	public TypedArray(float[] arr) {
		this(arr, true);
	}

	public TypedArray(float[] arr, boolean asLittleEndian) {
		super(toByteArray(arr, asLittleEndian));
		setTag(asLittleEndian ? 85 : 81);
	}

	public TypedArray(double[] arr) {
		this(arr, true);
	}

	public TypedArray(double[] arr, boolean asLittleEndian) {
		super(toByteArray(arr, asLittleEndian));
		setTag(asLittleEndian ? 86 : 82);
	}

	static byte[] toByteArray(char[] arr, boolean asLittleEndian) {
		ByteBuffer buffer = getBuffer(arr.length, 2, asLittleEndian);
		for (char value : arr) {
			buffer.putChar(value);
		}
		return buffer.array();
	}

	static byte[] toByteArray(short[] arr, boolean asLittleEndian) {
		ByteBuffer buffer = getBuffer(arr.length, 2, asLittleEndian);
		for (short value : arr) {
			buffer.putShort(value);
		}
		return buffer.array();
	}

	static byte[] toByteArray(int[] arr, boolean asLittleEndian) {
		ByteBuffer buffer = getBuffer(arr.length, 4, asLittleEndian);
		for (int value : arr) {
			buffer.putInt(value);
		}
		return buffer.array();
	}

	static byte[] toByteArray(long[] arr, boolean asLittleEndian) {
		ByteBuffer buffer = getBuffer(arr.length, 8, asLittleEndian);
		for (long value : arr) {
			buffer.putLong(value);
		}
		return buffer.array();
	}

	static byte[] toByteArray(float[] arr, boolean asLittleEndian) {
		ByteBuffer buffer = getBuffer(arr.length, 4, asLittleEndian);
		for (float value : arr) {
			buffer.putFloat(value);
		}
		return buffer.array();
	}

	static byte[] toByteArray(double[] arr, boolean asLittleEndian) {
		ByteBuffer buffer = getBuffer(arr.length, 8, asLittleEndian);
		for (double value : arr) {
			buffer.putDouble(value);
		}
		return buffer.array();
	}

	static ByteBuffer getBuffer(int elementCount, int bytesPerElement, boolean asLittleEndian) {
		ByteBuffer buffer = ByteBuffer.allocate(bytesPerElement * elementCount);
		if (asLittleEndian) {
			buffer.order(ByteOrder.LITTLE_ENDIAN);
		}
		return buffer;
	}
}
