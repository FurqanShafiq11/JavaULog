package com.ulog.parser;

public class ChangedParameter {

	private long timestamp;
	private String name;
	private Object value;

	public ChangedParameter(long timestamp, String name, Object value) {
		this.timestamp = timestamp;
		this.name = name;
		this.value = value;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public String getName() {
		return name;
	}

	public Object getValue() {
		return value;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setValue(Object value) {
		this.value = value;
	}

	@Override
	public String toString() {
		return "ChangedParameter{" + "timestamp=" + timestamp + ", name='" + name + '\'' + ", value=" + value + '}';
	}
}