package com.ulog.parser;

import java.util.Objects;

public class FieldData {

	private String fieldName;
	private String typeStr;

	public FieldData(String fieldName, String typeStr) {
		this.fieldName = fieldName;
		this.typeStr = typeStr;
	}

	public String getFieldName() {
		return fieldName;
	}

	public String getTypeStr() {
		return typeStr;
	}

	public boolean equals(FieldData obj) {
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		return this.fieldName == obj.fieldName && this.typeStr == obj.typeStr;
	}

	@Override
	public int hashCode() {
		return Objects.hash(fieldName, typeStr);
	}

	@Override
	public String toString() {
		return "FieldData{" + "fieldName='" + fieldName + '\'' + ", typeStr='" + typeStr + '\'' + '}';
	}
}