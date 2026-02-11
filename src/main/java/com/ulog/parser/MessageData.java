package com.ulog.parser;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Map;

public class MessageData {

	private long timestamp;

	public MessageData() {
		this.timestamp = 0;
	}

	public long getTimestamp() {
		return this.timestamp;
	}

	public boolean initialize(byte[] data, MessageHeader header, Map<Integer, MessageAddLogged> subscriptions,
			ULogReader ulogObject) throws IOException {

		boolean hasCorruption = false;

		int msgId = ULogUtils.unpackUShort(Arrays.copyOfRange(data, 0, 2));

		if (subscriptions.containsKey(msgId)) {

			MessageAddLogged subscription = subscriptions.get(msgId);

			int minDataSize = subscription.getItemSize(subscription.getDtype());

			int dataSize = data.length - 2;

			if (dataSize < minDataSize || dataSize > subscription.getMaxDataSize()) {
				this.timestamp = 0;
				hasCorruption = true;

			} else {
				if (dataSize > minDataSize) {
					data = Arrays.copyOfRange(data, 0, 2 + minDataSize);
				}

				subscription.appendToBuffer(Arrays.copyOfRange(data, 0, data.length));

				int timestampOffset = subscription.getTimestampOffset();
				this.timestamp = ByteBuffer.wrap(Arrays.copyOfRange(data, timestampOffset + 2, timestampOffset + 10))
						.order(ByteOrder.LITTLE_ENDIAN).getLong();
			}

		} else {
			if (!ulogObject.getFilteredMessageIds().contains(msgId)) {
				if (!ulogObject.getMissingMessageIds().contains(Integer.toString(msgId))) {
					ulogObject.getMissingMessageIds().add(String.valueOf(msgId));
					if (ulogObject.isDebug()) {
						System.out.println(ulogObject.getFileHandle().getFilePointer());
					}
					System.out.printf(
							"Warning: no subscription found for message id %d. Continuing, but file is most likely corrupt%n",
							msgId);
				}
				hasCorruption = true;
			}
			this.timestamp = 0;
		}
		return hasCorruption;
	}
}
