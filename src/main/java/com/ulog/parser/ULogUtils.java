package com.ulog.parser;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ULogUtils {
	public static final byte[] HEADER_BYTES = { (byte) 0x55, (byte) 0x4C, (byte) 0x6F, (byte) 0x67, (byte) 0x01,
			(byte) 0x12, (byte) 0x35 };
	public static final byte[] SYNC_BYTES = { (byte) 0x2F, (byte) 0x73, (byte) 0x13, (byte) 0x20, (byte) 0x25,
			(byte) 0x0C, (byte) 0xBB, (byte) 0x12 };

	public static final int MSG_TYPE_FORMAT = (int) 'F';
	public static final int MSG_TYPE_DATA = (int) 'D';
	public static final int MSG_TYPE_INFO = (int) 'I';
	public static final int MSG_TYPE_INFO_MULTIPLE = (int) 'M';
	public static final int MSG_TYPE_PARAMETER = (int) 'P';
	public static final int MSG_TYPE_PARAMETER_DEFAULT = (int) 'Q';
	public static final int MSG_TYPE_ADD_LOGGED_MSG = (int) 'A';
	public static final int MSG_TYPE_REMOVE_LOGGED_MSG = (int) 'R';
	public static final int MSG_TYPE_SYNC = (int) 'S';
	public static final int MSG_TYPE_DROPOUT = (int) 'O';
	public static final int MSG_TYPE_LOGGING = (int) 'L';
	public static final int MSG_TYPE_LOGGING_TAGGED = (int) 'C';
	public static final int MSG_TYPE_FLAG_BITS = (int) 'B';

	public static final Map<String, Object[]> UNPACK_TYPES = new HashMap<>();

	static {
		UNPACK_TYPES.put("int8_t", new Object[] { 'b', 1, (Integer) 0 });
		UNPACK_TYPES.put("uint8_t", new Object[] { 'B', 1, (Integer) 0 });
		UNPACK_TYPES.put("int16_t", new Object[] { 'h', 2, (Integer) 0 });
		UNPACK_TYPES.put("uint16_t", new Object[] { 'H', 2, (Integer) 0 });
		UNPACK_TYPES.put("int32_t", new Object[] { 'i', 4, (Integer) 0 });
		UNPACK_TYPES.put("uint32_t", new Object[] { 'I', 4, (Integer) 0 });
		UNPACK_TYPES.put("int64_t", new Object[] { 'q', 8, (Long) 0L });
		UNPACK_TYPES.put("uint64_t", new Object[] { 'Q', 8, (Long) 0L });
		UNPACK_TYPES.put("float", new Object[] { 'f', 4, (Float) 0f });
		UNPACK_TYPES.put("double", new Object[] { 'd', 8, (Double) 0.0 });
		UNPACK_TYPES.put("bool", new Object[] { '?', 1, (Integer) 0 });
		UNPACK_TYPES.put("char", new Object[] { 'c', 1, (Integer) 0 });
	}

	public static int getFieldSize(String typeStr) {
		Object[] typeInfo = UNPACK_TYPES.get(typeStr);
		if (typeInfo != null) {
			return (int) typeInfo[1];
		}
		throw new IllegalArgumentException("Unknown type: " + typeStr);
	}

	public static byte unpackInt8(byte[] data) {
		return data[0];
	}

	public static short unpackUInt8(byte[] data) {
		return (short) (data[0] & 0xFF);
	}

	public static short unpackInt16(byte[] data) {
		return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getShort();
	}

	public static int unpackUInt16(byte[] data) {
		return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
	}

	public static int unpackInt32(byte[] data) {
		return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt();
	}

	public static long unpackUInt32(byte[] data) {
		return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;
	}

	public static long unpackInt64(byte[] data) {
		return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getLong();
	}

	public static BigInteger unpackUInt64(byte[] data) {
		return new BigInteger(1, data);
	}

	public static float unpackFloat(byte[] data) {
		return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getFloat();
	}

	public static double unpackDouble(byte[] data) {
		return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getDouble();
	}

	public static boolean unpackBool(byte[] data) {
		return data[0] != 0;
	}

	public static char unpackChar(byte[] data) {
		return (char) (data[0] & 0xFF);
	}

	public static Object[] unpackUShortByte(byte[] data) {
		ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
		short ushortValue = buffer.getShort();
		byte byteValue = buffer.get();
		return new Object[] { ushortValue, byteValue };

	}

	public static int unpackUShort(byte[] data) {
		ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
		return buffer.getShort() & 0xFFFF;
	}

	public static boolean disableStrExceptions = false;

	public static String parseString(byte[] byteArray) {
		if (disableStrExceptions) {
			return parseStringWithHandling(byteArray, "ignore");
		} else {
			return parseStringWithHandling(byteArray, "strict");
		}
	}

	public static String parseStringWithHandling(byte[] byteArray, String errors) {
		try {
			if ("ignore".equalsIgnoreCase(errors)) {
				return decodeIgnoringErrors(byteArray);
			} else {
				return decodeWithStrictErrors(byteArray);
			}
		} catch (CharacterCodingException e) {
			return "Error in parsing string: Invalid UTF-8 sequence";
		} catch (Exception e) {
			return "Unexpected error: " + e.getMessage();
		}
	}

	public static String decodeWithStrictErrors(byte[] byteArray) throws CharacterCodingException {
		CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
		decoder.onMalformedInput(CodingErrorAction.REPORT);
		decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
		return decoder.decode(ByteBuffer.wrap(byteArray)).toString();
	}

	public static String decodeIgnoringErrors(byte[] byteArray) {
		CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
		decoder.onMalformedInput(CodingErrorAction.IGNORE);
		decoder.onUnmappableCharacter(CodingErrorAction.IGNORE);

		try {
			return decoder.decode(ByteBuffer.wrap(byteArray)).toString();
		} catch (CharacterCodingException e) {
			return "Error in parsing string: Unexpected failure in ignore mode";
		}
	}

}
