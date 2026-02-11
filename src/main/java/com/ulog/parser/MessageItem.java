package com.ulog.parser;

public class MessageItem {

	private long timestamp;
	private byte[] data;

	public MessageItem(long timestamp, byte[] data) {
		this.timestamp = timestamp;
		this.data = data;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public byte[] getData() {
		return data;
	}
}