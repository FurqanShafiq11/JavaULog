package com.ulog.parser;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Data {

	private byte multiId;
	private int msgId;
	private String name;
	private List<FieldData> fieldData;
	private int timestampIdx;
	private Map<String, List<Object>> data;

	public Data(MessageAddLogged messageAddLoggedObj) {
		this.multiId = messageAddLoggedObj.getMultiId();
		this.msgId = messageAddLoggedObj.getMsgId();
		this.name = messageAddLoggedObj.getMessageName();
		this.fieldData = messageAddLoggedObj.getFieldData();
		this.timestampIdx = messageAddLoggedObj.getTimestampIdx();
		this.data = new HashMap<>();

		List<List<String>> dtypeList = messageAddLoggedObj.getDtype();
		List<Byte> tempbuffer = messageAddLoggedObj.getBuffer();

		byte[] buffer = new byte[tempbuffer.size()];
		for (int i = 0; i < tempbuffer.size(); i++) {
			buffer[i] = tempbuffer.get(i);
		}

		int arraySize = 0;
		for (FieldData fd : this.fieldData) {
			arraySize += (int) ULogUtils.UNPACK_TYPES.get(fd.getTypeStr())[1];
		}
		arraySize = buffer.length / arraySize;
		int index = 0;
		for (int j = 0; j < arraySize; j++) {
			for (int i = 0; i < this.fieldData.size(); i++) {
				String dtypeKey = dtypeList.get(i).get(1);
				if (ULogUtils.UNPACK_TYPES.containsKey(dtypeKey)) {
					Object[] unpackDetails = ULogUtils.UNPACK_TYPES.get(dtypeKey);
					int typeSize = (int) unpackDetails[1];

					byte[] value = Arrays.copyOfRange(buffer, index, index + typeSize);
					Object val = readBuffer(value, dtypeKey);
					index += typeSize;

					if (!this.data.containsKey(this.fieldData.get(i).getFieldName())) {
						List<Object> intList = new ArrayList<>();
						intList.add(val);
						this.data.put(this.fieldData.get(i).getFieldName(), intList);
					} else {
						List<Object> entry = this.data.get(this.fieldData.get(i).getFieldName());
						entry.add(val);
					}
				}
			}
		}

	}

	public byte getMultiId() {
		return this.multiId;
	}

	public int getMsgId() {
		return this.msgId;
	}

	public String getName() {
		return this.name;
	}

	public int getTimestamp() {
		return this.timestampIdx;
	}

	public List<FieldData> getFieldData() {
		return this.fieldData;
	}

	public Map<String, List<Object>> getData() {
		return this.data;
	}

	private Object readBuffer(byte[] buffer, String dtype) {
		Object[] typeInfo = ULogUtils.UNPACK_TYPES.get(dtype);
		if (typeInfo == null) {
			throw new IllegalArgumentException("Unsupported data type: " + dtype);
		}
		char typeChar = (char) typeInfo[0];
		ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
		byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
		switch (typeChar) {
		case 'b':
			return byteBuffer.get(); // int8
		case 'B':
			return Byte.toUnsignedInt(byteBuffer.get()); // uint8
		case 'h':
			return byteBuffer.getShort(); // int16
		case 'H':
			return Short.toUnsignedInt(byteBuffer.getShort()); // uint16
		case 'i':
			return byteBuffer.getInt(); // int32
		case 'I':
			return Integer.toUnsignedLong(byteBuffer.getInt()); // uint32
		case 'q':
			return byteBuffer.getLong(); // int64
		case 'Q':
			return byteBuffer.getLong();
		case 'f':
			return byteBuffer.getFloat(); // float
		case 'd':
			return byteBuffer.getDouble(); // double
		case '?':
			return byteBuffer.get() != 0; // boolean
		case 'c':
			return (char) byteBuffer.get(); // char
		default:
			throw new IllegalArgumentException("Unknown type character: " + typeChar);
		}
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof Data)) {
			return false;
		}

		Data other = (Data) obj;

		if (this.multiId != other.multiId) {
			return false;
		}
		if (this.msgId != other.msgId) {
			return false;
		}
		if (!this.name.equals(other.name)) {
			return false;
		}
		if (!this.fieldData.equals(other.fieldData)) {
			return false;
		}
		if (this.timestampIdx != other.timestampIdx) {
			return false;
		}

		if (!this.data.keySet().equals(other.data.keySet())) {
			return false;
		}
		for (String key : this.data.keySet()) {
			List<Object> thisValues = this.data.get(key);
			List<Object> otherValues = other.data.get(key);

			if (!thisValues.equals(otherValues)) {
				return false;
			}
		}

		return true;
	}

	public List<Map.Entry<Long, Object>> listValueChanges(String fieldName) {
		if (!data.containsKey("timestamp") || !data.containsKey(fieldName)) {
			throw new IllegalArgumentException("Required fields are missing.");
		}

		List<Object> timestamps = data.get("timestamp");
		List<Object> values = data.get(fieldName);

		List<Map.Entry<Long, Object>> changes = new ArrayList<>();
		if (timestamps.isEmpty()) {
			return changes;
		}

		changes.add(new AbstractMap.SimpleEntry<>((Long) timestamps.get(0), values.get(0)));
		for (int i = 1; i < values.size(); i++) {
			if (!values.get(i).equals(values.get(i - 1))) {
				changes.add(new AbstractMap.SimpleEntry<>((Long) timestamps.get(i), values.get(i)));
			}
		}

		return changes;
	}
}
