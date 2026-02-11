package com.ulog.parser;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

public class ULogReader {

	public static void main(String[] args) {
	    if (args.length < 2) {
	        System.out.println("ULog to CSV Converter");
	        System.out.println("Usage: java -jar ulog-reader.jar <input_file.ulg> <output_path_prefix> [delimiter]");
	        System.out.println("Example: java -jar ulog-reader.jar log001.ulg ./output/log_");
	        return;
	    }

	    String inputPath = args[0];
	    String outputPath = args[1];
	    String delimiter = (args.length > 2) ? args[2] : ",";

	    System.out.println("Loading file: " + inputPath);
	    ULogReader parser = new ULogReader(inputPath, null, true, false);
	    
	    System.out.println("Exporting CSVs to: " + outputPath + "...");
	    parser.ulog2csv(parser.getDataList(), outputPath, 0, Long.MAX_VALUE, delimiter);
	    System.out.println("Done.");
	}
	private RandomAccessFile fileHandle;
	private int fileVersion;
	private long startTimestamp;
	private boolean debug = true;
	private boolean fileCorrupt = false;
	private long lastTimestamp = 0;
	private Map<String, Object> msgInfoDict = new HashMap<>();
	private Map<String, String> msgInfoDictTypes = new HashMap<>();
	private Map<String, List<List<Object>>> msgInfoMultipleDict = new HashMap<>();
	private Map<String, String> msgInfoMultipleDictTypes = new HashMap<>();
	private Map<String, Object> initialParameters = new HashMap<>();
	private Map<Integer, Map<String, Object>> defaultParameters = new HashMap<Integer, Map<String, Object>>();
	private List<ChangedParameter> changedParameters = new ArrayList<>();
	private Map<String, MessageFormat> messageFormats = new HashMap<>();
	private List<MessageLogging> loggedMessages = new ArrayList<>();
	private Map<String, List<MessageLoggingTagged>> loggedMessagesTagged = new HashMap<>();
	private List<MessageDropout> dropouts = new ArrayList<>();
	private List<Data> dataList = new ArrayList<>();
	private Map<Integer, MessageAddLogged> subscriptions = new HashMap<>();
	private Set<Integer> filteredMessageIds = new HashSet<>();
	private Set<String> missingMessageIds = new HashSet<>();
	private byte[] compatFlags = new byte[8];
	private byte[] incompatFlags = new byte[8];
	private List<Long> appendedOffsets = new ArrayList<>();
	private boolean hasSync = true;
	private long headerEndOffset = 0L;
	double time = 0;

	public ULogReader(String logFile, List<String> messageNameFilterList, boolean disableStrExceptions,
			boolean parseHeaderOnly) {

		this.debug = false;
		this.fileCorrupt = false;
		this.startTimestamp = 0;
		this.lastTimestamp = 0;
		this.fileVersion = 0;
		this.hasSync = true;
		ULogUtils.disableStrExceptions = disableStrExceptions;

		if (logFile != null) {
			try {
				loadFile(logFile, messageNameFilterList, parseHeaderOnly);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	public long getStartTimestamp() {
		return this.startTimestamp;
	}

	public long getLastTimestamp() {
		return this.lastTimestamp;
	}

	public Map<String, Object> getMsgInfoDict() {
		return this.msgInfoDict;
	}

	public Map<String, List<List<Object>>> getMsgInfoMultipleDict() {
		return this.msgInfoMultipleDict;
	}

	public Map<String, Object> getInitialParameters() {
		return this.initialParameters;
	}

	public Map<String, Object> getDefaultParameters(int defaultType) {
		return this.defaultParameters.getOrDefault(defaultType, new HashMap<>());
	}

	public List<ChangedParameter> getChangedParameters() {
		return this.changedParameters;
	}

	public Map<String, MessageFormat> getMessageFormats() {
		return this.messageFormats;
	}

	public List<MessageLogging> getLoggedMessages() {
		return this.loggedMessages;
	}

	public Map<String, List<MessageLoggingTagged>> getLoggedMessagesTagged() {
		return this.loggedMessagesTagged;
	}

	public List<MessageDropout> getDropouts() {
		return this.dropouts;
	}

	public List<Data> getDataList() {
		return this.dataList;
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

	public boolean hasDataAppended() {
		return (this.incompatFlags[0] & 0x1) != 0;
	}

	public boolean isFileCorrupt() {
		return this.fileCorrupt;
	}

	public boolean hasDefaultParameters() {
		return (this.compatFlags[0] & (0x1 << 0)) != 0;
	}

	public Set<Integer> getFilteredMessageIds() {
		return this.filteredMessageIds;
	}

	public Set<String> getMissingMessageIds() {
		return this.missingMessageIds;
	}

	public RandomAccessFile getFileHandle() {
		return this.fileHandle;
	}

	public boolean isDebug() {
		return this.debug;
	}

	public Data getDataset(String name, int multi_instance) {
		ArrayList<Data> res = new ArrayList<Data>();
		for (Data elem : this.dataList) {
			if (elem.getName().equals(name) && elem.getMultiId() == multi_instance) {
				res.add(elem);
			}
		}
		if (res.size() > 0) {
			return res.get(0);
		} else {
			System.out.println("Dataset not found for name: " + name + ", multiInstance: " + multi_instance);
			return null;
		}
	}

	public void writeUlog(Object logFile) throws IOException {
		OutputStream ulogFile;

		if (logFile instanceof String) {
			ulogFile = new FileOutputStream((String) logFile);
		} else {
			ulogFile = (OutputStream) logFile;
		}

		try (BufferedOutputStream bufferedUlogFile = new BufferedOutputStream(ulogFile)) {
			this.writeFileHeader(bufferedUlogFile);
			this.writeFlags(bufferedUlogFile);
			this.writeFormatMessages(bufferedUlogFile);
			this.writeInfoMessages(bufferedUlogFile);
			this.writeInfoMultipleMessage(bufferedUlogFile);
			this.writeInitialParameters(bufferedUlogFile);
			this.writeDefaultParameters(bufferedUlogFile);
			this.writeLoggedMessageSubscriptions(bufferedUlogFile);
			this.writeDataSection(bufferedUlogFile);
		}
	}

	public void writeFileHeader(OutputStream file) throws IOException {
		ByteBuffer headerBuffer = ByteBuffer.allocate(16);
		headerBuffer.order(ByteOrder.LITTLE_ENDIAN);
		headerBuffer.put(ULogUtils.HEADER_BYTES);
		headerBuffer.put((byte) this.fileVersion);
		headerBuffer.putLong(this.startTimestamp);

		if (headerBuffer.position() != 16) {
			throw new IllegalStateException("Written header is too short");
		}

		file.write(headerBuffer.array());
	}

	public void writeFlags(BufferedOutputStream bufferedUlogFile) throws IOException {
		ByteBuffer dataBuffer = ByteBuffer.allocate(32);

		for (byte compatFlag : this.compatFlags) {
			dataBuffer.put(compatFlag);
		}

		byte[] incompatFlags = Arrays.copyOf(this.incompatFlags, this.incompatFlags.length);
		incompatFlags[0] = (byte) (incompatFlags[0] & 0xFE);
		for (byte incompatFlag : incompatFlags) {
			dataBuffer.put(incompatFlag);
		}

		for (int i = 0; i < 3; i++) {
			dataBuffer.putLong(0L);
		}

		byte[] data = dataBuffer.array();

		ByteBuffer headerBuffer = ByteBuffer.allocate(3);
		headerBuffer.putShort((short) data.length);
		headerBuffer.put((byte) ULogUtils.MSG_TYPE_FLAG_BITS);
		byte[] header = headerBuffer.array();
		bufferedUlogFile.write(header);
		bufferedUlogFile.write(data);
	}

	public void writeInfoMessages(BufferedOutputStream bufferedUlogFile) throws IOException {
		for (Entry<String, Object> message : this.msgInfoDict.entrySet()) {
			String valueType = this.msgInfoDictTypes.get(message.getKey());
			String key = valueType + " " + message.getKey();
			String value = (String) message.getValue();

			byte[] data = this.makeInfoMessageData(key, value, valueType, false);
			byte[] header = new byte[3];
			header[0] = (byte) (data.length & 0xFF);
			header[1] = (byte) ((data.length >> 8) & 0xFF);
			header[2] = (byte) ULogUtils.MSG_TYPE_INFO;
			bufferedUlogFile.write(header);
			bufferedUlogFile.write(data);
		}
	}

	public void writeInfoMultipleMessage(BufferedOutputStream bufferedUlogFile) throws IOException {
		for (Map.Entry<String, List<List<Object>>> entry : this.msgInfoMultipleDict.entrySet()) {
			String baseKey = entry.getKey();
			List<List<Object>> valueSets = entry.getValue();
			String valueType = this.msgInfoMultipleDictTypes.get(baseKey);
			String key = valueType + " " + baseKey;

			for (List<Object> valueSet : valueSets) {
				boolean continued = false;

				for (Object value : valueSet) {
					byte[] data = this.makeInfoMessageData(key, value, valueType, continued);
					ByteBuffer buffer = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN);
					buffer.putShort((short) data.length);
					buffer.put((byte) ULogUtils.MSG_TYPE_INFO_MULTIPLE);
					byte[] header = buffer.array();

					bufferedUlogFile.write(header);
					bufferedUlogFile.write(data);

					continued = true;
				}
			}
		}
	}

	public void writeInitialParameters(OutputStream file) throws IOException {
		for (Map.Entry<String, Object> entry : this.initialParameters.entrySet()) {
			String parameterName = entry.getKey();
			Object value = entry.getValue();
			byte[] data = this.makeParameterData(parameterName, value);
			ByteBuffer headerBuffer = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN);
			headerBuffer.putShort((short) data.length);
			headerBuffer.put((byte) ULogUtils.MSG_TYPE_PARAMETER);
			file.write(headerBuffer.array());
			file.write(data);
		}
	}

	public void writeDefaultParameters(OutputStream file) throws IOException {
		for (Map.Entry<Integer, Map<String, Object>> entry : this.defaultParameters.entrySet()) {
			int bit = entry.getKey();
			int bitfield = 1 << bit;

			for (Map.Entry<String, Object> paramEntry : entry.getValue().entrySet()) {
				String name = paramEntry.getKey();
				Object value = paramEntry.getValue();
				ByteBuffer dataBuffer = ByteBuffer.allocate(256);
				dataBuffer.order(ByteOrder.LITTLE_ENDIAN);
				dataBuffer.put((byte) bitfield);
				byte[] paramData = this.makeParameterData(name, value);
				dataBuffer.put(paramData);
				int dataLength = dataBuffer.position();
				ByteBuffer headerBuffer = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN);
				headerBuffer.putShort((short) dataLength);
				headerBuffer.put((byte) ULogUtils.MSG_TYPE_PARAMETER_DEFAULT);
				file.write(headerBuffer.array());
				file.write(dataBuffer.array(), 0, dataLength);
			}
		}
	}

	public void writeFormatMessages(BufferedOutputStream bufferedUlogFile) throws IOException {
		for (Map.Entry<String, MessageFormat> entry : this.messageFormats.entrySet()) {
			String messageName = entry.getKey();
			MessageFormat fields = entry.getValue();

			ByteBuffer dataBuffer = ByteBuffer.allocate(1024);
			dataBuffer.order(ByteOrder.LITTLE_ENDIAN);
			dataBuffer.put((messageName + ":").getBytes("UTF-8"));

			for (Object[] field : fields.getFields()) {
				String fieldType = (String) field[0];
				int fieldCount = (int) field[1];
				String fieldName = (String) field[2];

				String encodedField;
				if (fieldCount > 1) {
					encodedField = String.format("%s[%d] %s;", fieldType, fieldCount, fieldName);
				} else {
					encodedField = String.format("%s %s;", fieldType, fieldName);
				}
				dataBuffer.put(encodedField.getBytes("UTF-8"));
			}

			int dataLength = dataBuffer.position();

			ByteBuffer headerBuffer = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN);
			headerBuffer.putShort((short) dataLength);
			headerBuffer.put((byte) ULogUtils.MSG_TYPE_FORMAT);
			bufferedUlogFile.write(headerBuffer.array());
			bufferedUlogFile.write(dataBuffer.array(), 0, dataLength);
		}
	}

	public void writeLoggedMessageSubscriptions(BufferedOutputStream bufferedUlogFile) throws IOException {
		Collections.sort(this.dataList, Comparator.comparingInt(Data::getMsgId));

		for (Data dataSet : this.dataList) {
			ByteBuffer dataBuffer = ByteBuffer.allocate(1024);
			dataBuffer.order(ByteOrder.LITTLE_ENDIAN);
			dataBuffer.put((byte) dataSet.getMultiId());
			dataBuffer.putShort((short) dataSet.getMsgId());
			dataBuffer.put(dataSet.getName().getBytes("UTF-8"));
			int dataLength = dataBuffer.position();
			ByteBuffer headerBuffer = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN);
			headerBuffer.putShort((short) dataLength);
			headerBuffer.put((byte) ULogUtils.MSG_TYPE_ADD_LOGGED_MSG);
			bufferedUlogFile.write(headerBuffer.array());
			bufferedUlogFile.write(dataBuffer.array(), 0, dataLength);
		}
	}

	public List<MessageItem> makeDataItems() {
		Collections.sort(this.dataList, Comparator.comparingInt(Data::getMsgId));

		List<MessageItem> messageItems = new ArrayList<>();

		for (Data dataSet : this.dataList) {
			int dataSetLength = dataSet.getData().get("timestamp").size();

			for (int iSample = 0; iSample < dataSetLength; iSample++) {
				long timestamp = (long) dataSet.getData().get("timestamp").get(iSample);
				ByteBuffer dataBuffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
				dataBuffer.putShort((short) dataSet.getMsgId());

				for (FieldData field : dataSet.getFieldData()) {
					String fieldName = field.getFieldName();
					String fieldType = field.getTypeStr();
					Object[] unpackDetails = ULogUtils.UNPACK_TYPES.get(fieldType);
					char fieldEncoding = (char) unpackDetails[0];
					Object fieldData = dataSet.getData().get(fieldName).get(iSample);

					if (fieldEncoding == 'c') {
						fieldData = String.valueOf((char) ((int) fieldData)).getBytes();
						dataBuffer.put((byte[]) fieldData);
					} else if (fieldEncoding == 'i') {
						dataBuffer.putInt((int) fieldData);
					} else if (fieldEncoding == 'f') {
						dataBuffer.putFloat((float) fieldData);
					} else {
						throw new IllegalArgumentException("Unsupported field encoding: " + fieldEncoding);
					}
				}

				int dataLength = dataBuffer.position();
				ByteBuffer headerBuffer = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN);
				headerBuffer.putShort((short) dataLength);
				headerBuffer.put((byte) ULogUtils.MSG_TYPE_DATA);

				byte[] header = headerBuffer.array();
				byte[] data = new byte[dataLength];
				dataBuffer.flip();
				dataBuffer.get(data);

				ByteBuffer combinedBuffer = ByteBuffer.allocate(header.length + data.length);
				combinedBuffer.put(header);
				combinedBuffer.put(data);

				messageItems.add(new MessageItem(timestamp, combinedBuffer.array()));
			}
		}

		return messageItems;
	}

	public void writeDataSection(OutputStream file) throws IOException {
		List<MessageItem> items = new ArrayList<>();
		items.addAll(this.makeDataItems());
		items.addAll(this.makeLoggedMessageItems());
		items.addAll(this.makeTaggedLoggedMessageItems());
		items.addAll(this.makeDropoutItems());
		items.addAll(this.makeChangedParamItems());
		items.sort(Comparator.comparingLong(MessageItem::getTimestamp));

		for (MessageItem item : items) {
			file.write(item.getData());
		}
	}

	public byte[] makeInfoMessageData(String key, Object value, String valueType, boolean continued) {
		byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
		ByteBuffer buffer = ByteBuffer.allocate(1024);

		buffer.put((byte) (continued ? 1 : 0));

		buffer.put((byte) keyBytes.length);
		buffer.put(keyBytes);

		if (valueType.startsWith("char[")) {
			byte[] valueBytes = ((String) value).getBytes(StandardCharsets.UTF_8);
			buffer.put(valueBytes);
		} else if (valueType.startsWith("uint8_t[")) {
			byte[] valueBytes = (byte[]) value;
			buffer.put(valueBytes);
		} else {
			char code = (char) ULogUtils.UNPACK_TYPES.get(valueType)[0];
			if (code == 'B') {
				buffer.put((byte) value);
			} else if (code == 'b') {
				buffer.put((byte) value);
			} else if (code == 'h') {
				buffer.putShort((short) value);
			} else if (code == 'H') {
				buffer.putShort((short) value);
			} else if (code == 'i') {
				buffer.putInt((int) value);
			} else if (code == 'I') {
				buffer.putInt((int) value);
			} else if (code == 'q') {
				buffer.putLong((long) value);
			} else if (code == 'Q') {
				buffer.putLong((long) value);
			} else if (code == 'f') {
				buffer.putFloat((float) value);
			} else if (code == 'd') {
				buffer.putDouble((double) value);
			} else if (code == '?') {
				buffer.put((byte) ((boolean) value ? 1 : 0));
			} else if (code == 'c') {
				buffer.put((byte) value);
			}
		}

		int finalSize = buffer.position();
		byte[] resultData = new byte[finalSize];
		buffer.rewind();
		buffer.get(resultData);
		return resultData;
	}

	public byte[] makeParameterData(String name, Object value) {
		String valueType;

		if (value instanceof Integer) {
			valueType = "int32_t";
		} else if (value instanceof Float) {
			valueType = "float";
		} else {
			throw new IllegalArgumentException("Found unknown parameter value type");
		}

		String key = valueType + " " + name;

		return this.makeInfoMessageData(key, value, valueType, false);
	}

	public List<MessageItem> makeLoggedMessageItems() {
		List<MessageItem> messageItems = new ArrayList<>();

		for (MessageLogging message : this.loggedMessages) {
			ByteArrayOutputStream data = new ByteArrayOutputStream();
			try {
				data.write(ByteBuffer.allocate(8).put((byte) message.getLogLevel()).putLong(message.getTimestamp())
						.array());
				data.write(message.getMessage().getBytes(StandardCharsets.UTF_8));

				ByteBuffer headerBuffer = ByteBuffer.allocate(3);
				headerBuffer.putShort((short) data.size()).put((byte) ULogUtils.MSG_TYPE_LOGGING);
				byte[] header = headerBuffer.array();
				byte[] combinedData = this.concatenateByteArrays(header, data.toByteArray());
				messageItems.add(new MessageItem(message.getTimestamp(), combinedData));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return messageItems;
	}

	public List<MessageItem> makeTaggedLoggedMessageItems() {
		List<MessageItem> messageItems = new ArrayList<>();

		for (List<MessageLoggingTagged> messageList : this.loggedMessagesTagged.values()) {
			for (MessageLoggingTagged message : messageList) {
				ByteBuffer data = ByteBuffer.allocate(1024);
				data.put((byte) message.getLogLevel());
				data.putLong(message.getTag());
				data.putLong(message.getTimestamp());
				data.put(message.getMessage().getBytes());

				byte[] header = new byte[3];
				ByteBuffer headerBuffer = ByteBuffer.wrap(header);
				headerBuffer.putShort((short) data.position());
				headerBuffer.put((byte) ULogUtils.MSG_TYPE_LOGGING_TAGGED);

				byte[] fullData = this.concatenateByteArrays(header, data.array());
				messageItems.add(new MessageItem(message.getTimestamp(), fullData));
			}
		}
		return messageItems;
	}

	public List<MessageItem> makeDropoutItems() {
		List<MessageItem> dropoutItems = new ArrayList<>();
		for (MessageDropout dropout : this.dropouts) {
			ByteBuffer data = ByteBuffer.allocate(1024);
			data.putShort((short) dropout.getDuration());
			byte[] header = new byte[3];
			ByteBuffer headerBuffer = ByteBuffer.wrap(header);
			headerBuffer.putShort((short) data.position());
			headerBuffer.put((byte) ULogUtils.MSG_TYPE_DROPOUT);

			byte[] fullData = this.concatenateByteArrays(header, data.array());
			dropoutItems.add(new MessageItem(dropout.getTimestamp(), fullData));
		}

		return dropoutItems;
	}

	public List<MessageItem> makeChangedParamItems() {
		List<MessageItem> changedParamItems = new ArrayList<>();

		for (ChangedParameter param : this.changedParameters) {
			byte[] data = this.makeParameterData(param.getName(), param.getValue());

			ByteBuffer headerBuffer = ByteBuffer.allocate(3);
			headerBuffer.putShort((short) data.length);
			headerBuffer.put((byte) ULogUtils.MSG_TYPE_PARAMETER);

			byte[] fullData = this.concatenateByteArrays(headerBuffer.array(), data);
			changedParamItems.add(new MessageItem(param.getTimestamp(), fullData));
		}

		return changedParamItems;
	}

	private byte[] concatenateByteArrays(byte[] array1, byte[] array2) {
		byte[] result = new byte[array1.length + array2.length];
		System.arraycopy(array1, 0, result, 0, array1.length);
		System.arraycopy(array2, 0, result, array1.length, array2.length);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof ULogReader)) {
			return false;
		}

		ULogReader other = (ULogReader) obj;
		java.lang.reflect.Field[] fields = this.getClass().getDeclaredFields();

		try {
			for (java.lang.reflect.Field field : fields) {
				field.setAccessible(true);
				Object thisValue = field.get(this);
				Object otherValue = field.get(other);
				if (!Objects.equals(thisValue, otherValue)) {
					return false;
				}
			}
		} catch (IllegalAccessException e) {
			throw new RuntimeException("Error accessing fields for comparison", e);
		}

		return true;
	}

	private void loadFile(String logFile, List<String> messageNameFilterList, boolean parseHeaderOnly)
			throws IOException {

		this.fileHandle = new RandomAccessFile(logFile, "r");
		readFileHeader();
		this.lastTimestamp = this.startTimestamp;
		readFileDefinitions();

		this.headerEndOffset = this.fileHandle.getFilePointer();
		if (this.debug) {
			System.out.println("Header end offset: " + this.headerEndOffset);
		}

		if (parseHeaderOnly) {
			this.fileHandle.close();
			return;
		}

		if (this.hasDataAppended() && this.appendedOffsets != null && this.appendedOffsets.size() > 0) {
			if (this.debug) {
				System.out.println("This file has data appended");
			}
			for (Long offset : appendedOffsets) {
				readFileData(messageNameFilterList, offset);
				this.fileHandle.seek(offset);
			}
		}

		readFileData(messageNameFilterList, null);

		this.fileHandle.close();
	}

	public void readFileHeader() throws IOException {
		byte[] headerData = new byte[16];

		if (this.fileHandle.length() < 16) {
			throw new IllegalArgumentException("File is too short to read the header");
		}

		this.fileHandle.readFully(headerData);

		for (int i = 0; i < ULogUtils.HEADER_BYTES.length; i++) {
			if (headerData[i] != ULogUtils.HEADER_BYTES[i]) {
				throw new IllegalArgumentException("Invalid file format: header mismatch");
			}
		}

		StringBuilder headerString = new StringBuilder("b'");
		for (byte b : headerData) {
			if (b >= 32 && b <= 126) {
				headerString.append((char) b);
			} else {
				headerString.append(String.format("\\x%02X", b));
			}
		}
		headerString.append("'");

		if (this.debug) {
			System.out.println("Header: " + headerString.toString());
		}

		this.fileVersion = headerData[7] & 0xFF;

		if (this.fileVersion > 1) {
			System.out.println("Warning: unknown file version. Attempting to continue...");
		}

		this.startTimestamp = ByteBuffer.wrap(Arrays.copyOfRange(headerData, 8, 16)).order(ByteOrder.LITTLE_ENDIAN)
				.getLong();

		if (this.debug) {
			System.out.println("Header parsed successfully:");
			System.out.println("File Version: " + fileVersion);
			System.out.println("Start Timestamp: " + startTimestamp);
		}
	}

	private void readFileDefinitions() throws IOException {
		MessageHeader header = new MessageHeader();
		byte[] data;

		while (true) {
			data = new byte[3];
			int bytesRead = this.fileHandle.read(data);
			if (bytesRead == -1) {
				break;
			}
			header.initialize(data);
			data = new byte[header.getMsgSize()];
			bytesRead = this.fileHandle.read(data);
			if (bytesRead == -1) {
				break;
			}

			try {
				if ((int) header.getMsgType() == ULogUtils.MSG_TYPE_INFO) {
					MessageInfo msgInfo = new MessageInfo(data, false);
					this.msgInfoDict.put(msgInfo.getKey(), msgInfo.getValue());
					this.msgInfoDictTypes.put(msgInfo.getKey(), msgInfo.getType());
				} else if ((int) header.getMsgType() == ULogUtils.MSG_TYPE_INFO_MULTIPLE) {
					MessageInfo msgInfoMultiple = new MessageInfo(data, true);
					addMessageInfoMultiple(msgInfoMultiple);

				} else if ((int) header.getMsgType() == ULogUtils.MSG_TYPE_FORMAT) {
					MessageFormat msgFormat = new MessageFormat(data);
					this.messageFormats.put(msgFormat.getName(), msgFormat);
				} else if ((int) header.getMsgType() == ULogUtils.MSG_TYPE_PARAMETER) {
					MessageInfo msgParam = new MessageInfo(data, false);
					this.initialParameters.put(msgParam.getKey(), msgParam.getValue());
				} else if ((int) header.getMsgType() == ULogUtils.MSG_TYPE_PARAMETER_DEFAULT) {
					MessageParameterDefault msgParamDefault = new MessageParameterDefault(data);
					addParameterDefault(msgParamDefault);
				} else if ((int) header.getMsgType() == ULogUtils.MSG_TYPE_ADD_LOGGED_MSG
						|| header.getMsgType() == ULogUtils.MSG_TYPE_LOGGING
						|| header.getMsgType() == ULogUtils.MSG_TYPE_LOGGING_TAGGED) {
					this.fileHandle.seek(this.fileHandle.getFilePointer() - (3 + header.getMsgSize()));
					break;
				} else if ((int) header.getMsgType() == ULogUtils.MSG_TYPE_FLAG_BITS) {
					if (this.fileHandle.getFilePointer() != 16 + 3 + header.getMsgSize()) {
						System.out.println("Error: FLAGS_BITS message must be the first message.");
						System.out.println("Offset: " + this.fileHandle.getFilePointer());
					}
					MessageFlagBits msgFlagBits = new MessageFlagBits(data, header);
					this.compatFlags = msgFlagBits.getCompatFlags();
					this.incompatFlags = msgFlagBits.getInCompatFlags();
					this.appendedOffsets = msgFlagBits.getAppendedOffsets();

					if (this.debug) {
						System.out.println("Compat Flags:");
						for (byte x : this.compatFlags) {
							System.out.println(x);
						}
						System.out.println("InCompat Flags:");
						for (byte x : this.incompatFlags) {
							System.out.println(x);
						}
						System.out.println("Appended Offsets:");
						for (long x : this.appendedOffsets) {
							System.out.println(x);
						}
					}

					if ((this.incompatFlags[0] & ~1) != 0) {
						throw new IllegalArgumentException("Unknown incompatible flag set: cannot parse the log");
					}
					for (int i = 1; i < 8; i++) {
						if (this.incompatFlags[i] != 0) {
							throw new UnsupportedOperationException(
									"Unknown incompatible flag set: cannot parse the log");
						}
					}
				} else {
					if (this.debug) {
						System.out.println("Unknown message type: " + header.getMsgType());
						long filePosition = fileHandle.getFilePointer();
						System.out.println(
								"File position: " + filePosition + " (0x" + Long.toHexString(filePosition) + ")");
						System.out.println("Message size: " + header.getMsgSize());
					}
					if (checkPacketCorruption(header)) {
						this.fileHandle.seek(-2 - header.getMsgSize());
					}
				}
			} catch (IndexOutOfBoundsException e) {
				if (!this.fileCorrupt) {
					System.out.println("File corruption detected while reading file definitions!");
					this.fileCorrupt = true;
				}
			}
		}

	}

	private void readFileData(List<String> messageNameFilterList, Long readUntil) {
		if (readUntil == null) {
			readUntil = 1L << 50;
		}

		try {
			MessageHeader header = new MessageHeader();
			MessageData msgData = new MessageData();
			long currFilePos = this.fileHandle.getFilePointer();

			while (true) {
				byte[] data = new byte[3];
				int bytesRead = this.fileHandle.read(data);
				if (bytesRead < 3) {
					break;
				}

				currFilePos += bytesRead;
				header.initialize(data);

				data = new byte[header.getMsgSize()];
				bytesRead = this.fileHandle.read(data);

				currFilePos += bytesRead;

				if (bytesRead < header.getMsgSize()) {
					break;
				}
				if (currFilePos > readUntil) {
					if (this.debug) {
						System.out.printf("Read until offset=%d done, current pos=%d%n", readUntil, currFilePos);
					}
					break;
				}

				try {
					switch (header.getMsgType()) {
					case ULogUtils.MSG_TYPE_INFO:
						MessageInfo msgInfo = new MessageInfo(data, false);
						this.msgInfoDict.put(msgInfo.getKey(), msgInfo.getValue());
						this.msgInfoDictTypes.put(msgInfo.getKey(), msgInfo.getType());
						break;

					case ULogUtils.MSG_TYPE_INFO_MULTIPLE:
						MessageInfo multipleMsgInfo = new MessageInfo(data, true);
						addMessageInfoMultiple(multipleMsgInfo);
						break;

					case ULogUtils.MSG_TYPE_PARAMETER:
						MessageInfo paramMsgInfo = new MessageInfo(data, false);
						this.changedParameters.add(new ChangedParameter(this.lastTimestamp, paramMsgInfo.getKey(),
								paramMsgInfo.getValue()));
						break;

					case ULogUtils.MSG_TYPE_PARAMETER_DEFAULT:
						MessageParameterDefault paramDefault = new MessageParameterDefault(data);
						addParameterDefault(paramDefault);
						break;

					case ULogUtils.MSG_TYPE_ADD_LOGGED_MSG:
						MessageAddLogged msgAddLogged = new MessageAddLogged(data, this.messageFormats);
						if (messageNameFilterList == null
								|| messageNameFilterList.contains(msgAddLogged.getMessageName())) {
							this.subscriptions.put(msgAddLogged.getMsgId(), msgAddLogged);
						} else {
							this.filteredMessageIds.add(msgAddLogged.getMsgId());
						}
						break;

					case ULogUtils.MSG_TYPE_LOGGING:
						MessageLogging msgLogging = new MessageLogging(data, header);

						this.loggedMessages.add(msgLogging);
						break;

					case ULogUtils.MSG_TYPE_LOGGING_TAGGED:
						MessageLoggingTagged msgLogTagged = new MessageLoggingTagged(data, header);
						this.loggedMessagesTagged
								.computeIfAbsent(String.valueOf(msgLogTagged.getTag()), k -> new ArrayList<>())
								.add(msgLogTagged);
						break;

					case ULogUtils.MSG_TYPE_DATA:
						boolean hasCorruption = msgData.initialize(data, header, this.subscriptions, this);
						if (hasCorruption) {
							this.fileCorrupt = true;
						} else if (msgData.getTimestamp() > this.lastTimestamp) {
							this.lastTimestamp = msgData.getTimestamp();
						}
						break;

					case ULogUtils.MSG_TYPE_DROPOUT:
						MessageDropout msgDropout = new MessageDropout(data, header, this.lastTimestamp);
						dropouts.add(msgDropout);
						break;

					case ULogUtils.MSG_TYPE_SYNC:
						break;

					default:
						if (this.debug) {
							System.out.printf("Unknown message type: %d%n", header.getMsgType());
							System.out.printf("File position: %d msg size: %d%n", currFilePos, header.getMsgSize());
						}

						if (this.checkPacketCorruption(header)) {
							long newPos = currFilePos - 2 - header.getMsgSize();
							this.fileHandle.seek(newPos);
							currFilePos = this.fileHandle.getFilePointer();
							if (this.hasSync) {
								this.findSync(-1);
							}
						} else {
							if (this.hasSync) {
								this.findSync(header.getMsgSize());
							}
						}
						break;
					}

				} catch (Exception e) {
					if (!this.fileCorrupt) {
						System.err.println("File corruption detected while reading file data!" + e);
						this.fileCorrupt = true;
					}
				}
			}

			for (int i = this.subscriptions.size() - 1; i >= 0; i--) {
				MessageAddLogged value = this.subscriptions.remove(i);

				if (value.getBuffer().size() > 0) {
					this.dataList.add(new Data(value));
				}
			}

			this.dataList.sort(Comparator.comparing(Data::getName).thenComparing(Data::getMultiId));
		} catch (IOException e) {
			System.err.println("Error reading file: " + e.getMessage());
		}
	}

	public int[] getVersionInfo(String keyName) {
		if (this.msgInfoDict.containsKey(keyName)) {
			Object value = this.msgInfoDict.get(keyName);
			Integer val = null;
			if (value instanceof Integer) {
				val = (Integer) value;
			} else if (value instanceof Long) {
				long longValue = (Long) value;
				if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
					val = (int) longValue;
				}
			} else if (value instanceof String) {
				try {
					val = Integer.parseInt((String) value);
				} catch (NumberFormatException e) {
					System.out.println("Error: Unable to parse string value to an integer for key '" + keyName + "'");
				}
			}

			if (val != null) {
				int major = (val >> 24) & 0xff;
				int minor = (val >> 16) & 0xff;
				int patch = (val >> 8) & 0xff;
				int type = val & 0xff;
				return new int[] { major, minor, patch, type };
			} else {
				System.out.println("Warning: Value for key '" + keyName + "' could not be converted to an Integer.");
			}
		}
		return null;
	}

	public String getVersionInfoStr(String keyName) {

		int[] version = getVersionInfo(keyName);

		if (version != null && version[3] >= 64) {
			String typeStr = "";
			if (version[3] < 128) {
				typeStr = " (alpha)";
			} else if (version[3] < 192) {
				typeStr = " (beta)";
			} else if (version[3] < 255) {
				typeStr = " (RC)";
			}
			return String.format("v%d.%d.%d%s", version[0], version[1], version[2], typeStr);
		}
		return null;
	}

	public void addMessageInfoMultiple(MessageInfo msgInfo) {
		if (this.msgInfoMultipleDict.containsKey(msgInfo.getKey())) {
			if (msgInfo.isContinued()) {
				List<List<Object>> temp = this.msgInfoMultipleDict.get(msgInfo.getKey());
				List<Object> tempList = temp.get(temp.size() - 1);
				tempList.add(tempList.size() - 1, msgInfo.getValue());
			} else {
				this.msgInfoMultipleDict.get(msgInfo.getKey()).add(new ArrayList<>(Arrays.asList(msgInfo.getValue())));
			}
		} else {
			List<List<Object>> newList = new ArrayList<>();
			newList.add(new ArrayList<>(Arrays.asList(msgInfo.getValue())));
			this.msgInfoMultipleDict.put(msgInfo.getKey(), newList);
			this.msgInfoMultipleDictTypes.put(msgInfo.getKey(), msgInfo.getType());
		}
	}

	private void addParameterDefault(MessageParameterDefault msgParamDefault) {
		int defaultTypes = msgParamDefault.getDefaultTypes();

		while (defaultTypes != 0) {
			int defType = defaultTypes & -defaultTypes;
			defaultTypes ^= defType;
			defType--;

			this.defaultParameters.computeIfAbsent(defType, k -> new HashMap<>());
			this.defaultParameters.get(defType).put(msgParamDefault.getKey(), msgParamDefault.getValue());
		}
	}

	public boolean findSync(long lastNBytes) throws IOException {
		boolean syncSeqFound = false;
		long initialFilePosition = this.fileHandle.getFilePointer();
		long currentFilePosition = initialFilePosition;

		int searchChunkSize = 512;

		if (lastNBytes != -1) {
			currentFilePosition = Math.max(0, currentFilePosition - lastNBytes);
			this.fileHandle.seek(currentFilePosition);
			searchChunkSize = (int) Math.min(lastNBytes, 512);
		}

		byte[] chunk = new byte[searchChunkSize];
		int bytesRead = this.fileHandle.read(chunk);

		while (bytesRead >= ULogUtils.SYNC_BYTES.length) {
			currentFilePosition += bytesRead;

			int chunkIndex = findSyncInChunk(chunk);
			if (chunkIndex >= 0) {
				if (this.debug) {
					System.out.printf("Found sync at %d%n", currentFilePosition - bytesRead + chunkIndex);
				}
				fileHandle.seek(currentFilePosition - bytesRead + chunkIndex + ULogUtils.SYNC_BYTES.length);
				currentFilePosition = this.fileHandle.getFilePointer();
				syncSeqFound = true;
				break;
			}

			if (lastNBytes != -1) {
				break;
			}

			this.fileHandle.seek(currentFilePosition - (ULogUtils.SYNC_BYTES.length - 1));
			currentFilePosition = this.fileHandle.getFilePointer();
			bytesRead = this.fileHandle.read(chunk);
		}

		if (!syncSeqFound) {
			this.fileHandle.seek(initialFilePosition);
			currentFilePosition = this.fileHandle.getFilePointer();
			if (lastNBytes == -1) {
				this.hasSync = false;
				if (this.debug) {
					System.out.printf("Failed to find sync in file from %d%n", initialFilePosition);
				}
			} else {
				if (this.debug) {
					System.out.printf("Failed to find sync in (%d, %d)%n", initialFilePosition - lastNBytes,
							initialFilePosition);
				}
			}
		} else {
			this.fileCorrupt = true;
		}

		return syncSeqFound;
	}

	private int findSyncInChunk(byte[] chunk) {
		for (int i = 0; i <= chunk.length - ULogUtils.SYNC_BYTES.length; i++) {
			if (Arrays.equals(Arrays.copyOfRange(chunk, i, i + ULogUtils.SYNC_BYTES.length), ULogUtils.SYNC_BYTES)) {
				return i;
			}
		}
		return -1;
	}

	private boolean checkPacketCorruption(MessageHeader header) {
		boolean dataCorrupt = false;

		if (header.getMsgType() == 0 || header.getMsgSize() == 0 || header.getMsgSize() > 10000) {
			if (!this.fileCorrupt && this.debug) {
				System.out.println("File corruption detected");
			}
			dataCorrupt = true;
			this.fileCorrupt = true;
		}

		return dataCorrupt;
	}

	public void ulog2csv(List<Data> dataList, String outputFilePrefix, long time_s, long time_e, String delimiter) {
		for (Data d : dataList) {
			String sanitizedFileName = d.getName().replace("/", "_");
			String filePath = String.format("%s_%s_%d.csv", outputFilePrefix, sanitizedFileName, d.getMultiId());

			Map<String, List<Object>> data = d.getData();
			if (!data.containsKey("timestamp") || data.get("timestamp").isEmpty()) {
				System.err.println("Skipping " + filePath + " - No timestamp data found!");
				continue;
			}

			List<Object> timestamps = data.get("timestamp");
			int time_s_i = (time_s > 0) ? getIndexForTimestamp(timestamps, time_s * 1_000_000) : 0;
			int time_e_i = (time_e > 0 && time_e != Long.MAX_VALUE)
					? getIndexForTimestamp(timestamps, time_e * 1_000_000)
					: timestamps.size();

			if (time_s_i >= time_e_i) {
				System.err.println("Skipping " + filePath + " - Invalid timestamp range!");
				continue;
			}

			try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
				List<String> dataKeys = new ArrayList<>();
				for (FieldData f : d.getFieldData()) {
					dataKeys.add(f.getFieldName());
				}
				dataKeys.remove("timestamp");
				dataKeys.add(0, "timestamp");
				writer.write(String.join(delimiter, dataKeys));
				writer.newLine();
				for (int i = time_s_i; i < time_e_i; i++) {
					StringBuilder row = new StringBuilder();
					for (int k = 0; k < dataKeys.size(); k++) {
						List<Object> values = data.get(dataKeys.get(k));
						row.append((i < values.size()) ? values.get(i).toString() : "");
						if (k < dataKeys.size() - 1) {
							row.append(delimiter);
						}
					}
					writer.write(row.toString());
					writer.newLine();
				}

				System.out.println("CSV file written successfully: " + filePath);
			} catch (IOException e) {
				System.err.println("Error writing CSV: " + e.getMessage());
			}
		}
	}

	private int getIndexForTimestamp(List<Object> timestamps, long targetTime) {
		for (int i = 0; i < timestamps.size(); i++) {
			if (Long.parseLong(timestamps.get(i).toString()) >= targetTime) {
				return i;
			}
		}
		return timestamps.size();
	}

}
