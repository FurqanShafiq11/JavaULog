package com.ulog.parser;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class MessageLogging {

	private byte logLevel;
	private long timestamp;
	private String message;

	public MessageLogging(byte[] data, MessageHeader header) {
		this.logLevel = data[0];
		this.timestamp = ByteBuffer.wrap(data, 1, 8).getLong();
		this.message = ULogUtils.parseString(Arrays.copyOfRange(data, 9, data.length));

		this.message = this.message.replaceAll("\u001B\\[[;\\d]*m", "");
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		MessageLogging other = (MessageLogging) obj;
		return logLevel == other.logLevel && timestamp == other.timestamp && message.equals(other.message);
	}

	public String logLevelStr() {
		Map<Byte, String> logLevelMap = new HashMap<>();
		logLevelMap.put((byte) 0, "EMERGENCY");
		logLevelMap.put((byte) 1, "ALERT");
		logLevelMap.put((byte) 2, "CRITICAL");
		logLevelMap.put((byte) 3, "ERROR");
		logLevelMap.put((byte) 4, "WARNING");
		logLevelMap.put((byte) 5, "NOTICE");
		logLevelMap.put((byte) 6, "INFO");
		logLevelMap.put((byte) 7, "DEBUG");

		return logLevelMap.getOrDefault(logLevel, "UNKNOWN");
	}

	public byte getLogLevel() {
		return logLevel;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public String getMessage() {
		return message;
	}
}
