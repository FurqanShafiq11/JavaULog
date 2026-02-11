package com.ulog.parser;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class MessageFlagBits {

	private byte[] compatFlags = new byte[8];
	private byte[] incompatFlags = new byte[8];
	private List<Long> appendedOffsets;

	public MessageFlagBits(byte[] data, MessageHeader header) {
		if (header.getMsgSize() > 8 + 8 + 3 * 8) {
			System.out.println("Warning: Flags Bit message is longer than expected");
		}

		for (int i = 0; i < 8; i++) {
			this.compatFlags[i] = ((byte) Byte.toUnsignedInt(data[i]));
		}

		for (int i = 8; i < 16; i++) {
			this.incompatFlags[i - 8] = ((byte) Byte.toUnsignedInt(data[i]));
		}

		this.appendedOffsets = new ArrayList<>();
		ByteBuffer buffer = ByteBuffer.wrap(data, 16, 24).order(ByteOrder.LITTLE_ENDIAN);
		for (int i = 0; i < 3; i++) {
			long offset = buffer.getLong();
			if (offset != 0) {
				this.appendedOffsets.add(offset);
			}
		}
	}

	public byte[] getCompatFlags() {
		return this.compatFlags;
	}

	public byte[] getInCompatFlags() {
		return this.incompatFlags;
	}

	public List<Long> getAppendedOffsets() {
		return this.appendedOffsets;
	}
}
