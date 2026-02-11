package com.ulog.parser;

import java.util.Arrays;

public class MessageInfo {

	private String type;
	private String key;
	private Object value;
	private boolean isContinued;

	public MessageInfo(byte[] data, boolean isInfoMultiple) {
		int offset = 0;

		if (isInfoMultiple) {
			if (data.length < 1) {
				throw new IllegalArgumentException("Data too short for INFO_MULTIPLE message.");
			}
			this.isContinued = (data[0] == 1); 
			offset += 1; 
		}

		if (data.length <= offset) {
			throw new IllegalArgumentException("Data too short to contain key length.");
		}
		int keyLen = Byte.toUnsignedInt(data[offset]);
		offset += 1;

		if (data.length < offset + keyLen) {
			throw new IllegalArgumentException("Data too short for the specified key length.");
		}
		String typeKey = ULogUtils.parseString(Arrays.copyOfRange(data, offset, offset + keyLen));
		String[] typeKeySplit = typeKey.split(" ");
		if (typeKeySplit.length != 2) {
			throw new IllegalArgumentException("Invalid type-key format.");
		}
		this.type = typeKeySplit[0];
		this.key = typeKeySplit[1];
		offset += keyLen; 
		String unpackType = "";

		if (this.type.startsWith("char[")) { 
			this.value = ULogUtils.parseString(Arrays.copyOfRange(data, offset, data.length));
		} else if (ULogUtils.UNPACK_TYPES.containsKey(this.type)) {

			Object[] unpackTypeArray = ULogUtils.UNPACK_TYPES.get(this.type);

			if (unpackTypeArray != null && unpackTypeArray.length > 0) {
				unpackType = Character.toString((Character) unpackTypeArray[0]);

				this.value = unpack(data, offset, unpackType);
			} else {
				System.out.println("Key not found or invalid format.");
			}

		} else {
			this.value = Arrays.copyOfRange(data, offset, data.length);
		}
	}

	private Object unpack(byte[] data, int offset, String unpackType) {
		byte[] dataWithOffset = new byte[data.length - offset];
		System.arraycopy(data, offset, dataWithOffset, 0, data.length - offset);
		switch (unpackType) {
		case "b": // int8_t
			return ULogUtils.unpackInt8(dataWithOffset);
		case "B": // uint8_t
			return ULogUtils.unpackUInt8(dataWithOffset);
		case "h": // int16_t
			return ULogUtils.unpackInt16(dataWithOffset);
		case "H": // uint16_t
			return ULogUtils.unpackUInt16(dataWithOffset);
		case "i": // int32_t
			return ULogUtils.unpackInt32(dataWithOffset);
		case "I": // uint32_t
			return ULogUtils.unpackUInt32(dataWithOffset);
		case "q": // int64_t
			return ULogUtils.unpackInt64(dataWithOffset);
		case "Q": // uint64_t
			return ULogUtils.unpackUInt64(dataWithOffset);
		case "f": // float
			return ULogUtils.unpackFloat(dataWithOffset);
		case "d": // double
			return ULogUtils.unpackFloat(dataWithOffset);
		case "?": // boolean
			return ULogUtils.unpackBool(dataWithOffset);
		case "c": // char
			return ULogUtils.unpackChar(dataWithOffset);
		default:
			throw new IllegalArgumentException("Unsupported unpack type: " + unpackType);
		}
	}

	public String getType() {
		return type;
	}

	public String getKey() {
		return key;
	}

	public Object getValue() {
		return value;
	}

	public boolean isContinued() {
		return isContinued;
	}

	@Override
	public String toString() {
		return "MessageInfo{" + "type='" + type + '\'' + ", key='" + key + '\'' + ", value=" + value + ", isContinued="
				+ isContinued + '}';
	}
}
