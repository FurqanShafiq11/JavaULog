package com.ulog.parser;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class MessageAddLogged {

	private byte multiId;
	private int msgId;
	private String messageName;
	private List<FieldData> fieldData;
	private int timestampIdx = -1;
	private int maxDataSize = 0;
	private int timestampOffset = 0;
	private List<Byte> buffer = new ArrayList<Byte>();
	private List<List<String>> dtype;

	public MessageAddLogged(byte[] data, Map<String, MessageFormat> messageFormats) {
		this.multiId = data[0];
		this.msgId = ByteBuffer.wrap(data, 1, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
		this.messageName = ULogUtils.parseString(Arrays.copyOfRange(data, 3, data.length));
		this.fieldData = new ArrayList<>();
		this.parseFormat(messageFormats);

		for (FieldData field : this.fieldData) {
			if (field.getFieldName().equals("timestamp")) {
				break;
			}
			this.timestampOffset += (Integer) ULogUtils.UNPACK_TYPES.get(field.getTypeStr())[1];
		}

		List<List<String>> dtypeList = new ArrayList<>();
		for (FieldData field : this.fieldData) {
			Object numpyType = field.getTypeStr();
			Object typeSize = ULogUtils.UNPACK_TYPES.get(numpyType)[1];
			int typeSize_int = (Integer) typeSize;
			this.maxDataSize += typeSize_int;
			List<String> temp = new ArrayList<String>();
			temp.add(field.getFieldName());
			temp.add(numpyType.toString());
			dtypeList.add(temp);
		}
		this.dtype = (List<List<String>>) dtypeList;
	}

	private void parseFormat(Map<String, MessageFormat> messageFormats) {
		this.parseNestedType("", this.messageName, messageFormats);

		while (!this.fieldData.isEmpty()
				&& this.fieldData.get(this.fieldData.size() - 1).getFieldName().startsWith("_padding")) {
			this.fieldData.remove(this.fieldData.size() - 1);
		}
	}

	private void parseNestedType(String prefixStr, String typeName, Map<String, MessageFormat> messageFormats) {
		if (!typeName.trim().equals(typeName)) {
			typeName = typeName.trim();
		}

		MessageFormat messageFormat = messageFormats.get(typeName);

		if (messageFormat != null) {
			for (Object[] field : messageFormat.getFields()) {
				String typeNameFmt = (String) field[0];
				int arraySize = (int) field[1];
				String fieldName = (String) field[2];
				if (ULogUtils.UNPACK_TYPES.containsKey(typeNameFmt)) {
					if (arraySize > 1) {
						for (int i = 0; i < arraySize; i++) {
							this.fieldData.add(new FieldData(prefixStr + fieldName + "[" + i + "]", typeNameFmt));
						}
					} else {
						fieldData.add(new FieldData(prefixStr + fieldName, typeNameFmt));
					}
					if ((prefixStr + fieldName).equals("timestamp")) {
						this.timestampIdx = fieldData.size() - 1;
					}
				} else {
					if (arraySize > 1) {
						for (int i = 0; i < arraySize; i++) {
							parseNestedType(prefixStr + fieldName + "[" + i + "].", typeNameFmt, messageFormats);
						}
					} else {
						parseNestedType(prefixStr + fieldName + ".", typeNameFmt, messageFormats);
					}
				}
			}
		}
	}

	// Getters
	public byte getMultiId() {
		return this.multiId;
	}

	public int getMsgId() {
		return this.msgId;
	}

	public String getMessageName() {
		return this.messageName;
	}

	public List<FieldData> getFieldData() {
		return this.fieldData;
	}

	public int getMaxDataSize() {
		return this.maxDataSize;
	}

	public List<List<String>> getDtype() {
		return this.dtype;
	}

	public int getTimestampOffset() {
		return this.timestampOffset;
	}

	public int getTimestampIdx() {
		return this.timestampIdx;
	}

	public int getItemSize(List<List<String>> dtype) {
		int s = 0;

		for (List<String> item : dtype) {
			String value = item.get(1).trim();
			s += (int) ULogUtils.UNPACK_TYPES.get(value)[1];
		}
		return s;

	}

	public List<Byte> getBuffer() {
		return this.buffer;
	}

	public void appendToBuffer(byte[] data) {

		if (data.length < 2) {
			return; 
		}
		
		for (int i = 2; i < data.length; i++) {
			this.buffer.add(data[i]);
		}

	}

}