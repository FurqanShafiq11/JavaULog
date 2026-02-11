package com.ulog.parser;
public class MessageHeader {
	private int msgSize;
	private short msgType;

	public MessageHeader() {
		this.msgSize = 0;
		this.msgType = 0;
	} 

	public void initialize(byte[] data) {
		this.msgSize = ULogUtils.unpackUInt16(data); 
		this.msgType = ULogUtils.unpackUInt8(new byte[] { data[2] }); 
	}

	public int getMsgSize() {
		return msgSize;
	}

	public short getMsgType() {
		return msgType;
	}

}
