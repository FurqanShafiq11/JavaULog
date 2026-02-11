package com.ulog.parser;

import java.util.List;

public class TypedList {

	List<Integer> values;
	String type;

	public TypedList(List<Integer> values, String type) {
		this.values = values;
		this.type = type;
	}

	@Override
	public String toString() {
		return values + ", " + type;
	}
}
