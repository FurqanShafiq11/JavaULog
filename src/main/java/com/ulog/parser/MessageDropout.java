package com.ulog.parser;

import java.nio.ByteBuffer;

public class MessageDropout {

	private final int duration;
	private final long timestamp;

	public MessageDropout(byte[] data, MessageHeader header, long timestamp) {
		this.duration = ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
		this.timestamp = timestamp;
	}

	public boolean equals(MessageDropout other) {

		if (other == null || getClass() != other.getClass()) {
			return false;
		}
		return this.duration == other.duration && this.timestamp == other.timestamp;
	}

	public int getDuration() {
		return duration;
	}

	public long getTimestamp() {
		return timestamp;
	}
}