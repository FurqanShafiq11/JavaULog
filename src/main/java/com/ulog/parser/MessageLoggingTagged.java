package com.ulog.parser;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class MessageLoggingTagged {

	private final byte logLevel;
	private final short tag;
	private final long timestamp;
	private final String message;

	public MessageLoggingTagged(byte[] data, MessageHeader header) {
		this.logLevel = data[0];
		this.tag = ByteBuffer.wrap(data, 1, 2).getShort();
		this.timestamp = ByteBuffer.wrap(data, 3, 8).getLong();
		this.message = ULogUtils.parseString(Arrays.copyOfRange(data, 11, data.length - 1));

	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		MessageLoggingTagged other = (MessageLoggingTagged) obj;
		return logLevel == other.logLevel && tag == other.tag && timestamp == other.timestamp
				&& message.equals(other.message);
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

	public short getTag() {
		return tag;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public String getMessage() {
		return message;
	}
}
