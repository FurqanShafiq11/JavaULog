package com.ulog.parser;

public class MessageParameterDefault {

	private int defaultTypes; 
	private String type;
	private String key;
	private Object value;

	public MessageParameterDefault(byte[] data) {
		if (data.length < 1) {
			throw new IllegalArgumentException("Data too short to extract default types.");
		}

		this.defaultTypes = Byte.toUnsignedInt(data[0]);

		byte[] remainingData = new byte[data.length - 1];
		System.arraycopy(data, 1, remainingData, 0, remainingData.length);

		MessageInfo msgInfo = new MessageInfo(remainingData, false);
		this.type = msgInfo.getType();
		this.key = msgInfo.getKey();
		this.value = msgInfo.getValue();
	}

	public int getDefaultTypes() {
		return defaultTypes;
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

	@Override
	public String toString() {
		return "MessageParameterDefault{" + "defaultTypes=" + defaultTypes + ", type='" + type + '\'' + ", key='" + key
				+ '\'' + ", value=" + value + '}';
	}
}