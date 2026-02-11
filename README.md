To make the README more professional and organized, we will use **collapsible sections, tables, and a clear hierarchy**. This makes it easy for both "quick-start" users and "deep-dive" developers to find what they need.

Copy and paste this into your `README.md`:

---

# 🛸 ULog Reader for Java

[![License](https://img.shields.io/badge/License-BSD%203--Clause-blue.svg)](https://opensource.org/licenses/BSD-3-Clause)
[![Java Version](https://img.shields.io/badge/Java-8%2B-orange.svg)](https://www.oracle.com/java/technologies/javase-downloads.html)

A high-performance, standalone Java implementation of the **PX4 ULog file format parser**. This library is a direct port of the official [pyulog](https://github.com/PX4/pyulog) tool, allowing Java and Android developers to analyze flight logs without a Python dependency.

---

## 📌 Table of Contents
- [Core Features](#-core-features)
- [Installation](#-installation)
- [Library Usage Guide](#-library-usage-guide)
- [Command Line Interface (CLI)](#-command-line-interface-cli)
- [API Reference](#-api-reference)
- [Credits & License](#-credits--license)

---

## ✨ Core Features
- ✅ **Full Parsing**: Supports Headers, Definitions, Data, Information, and Parameter blocks.
- ✅ **CSV Export**: Built-in logic to convert binary logs into analysis-ready CSV files.
- ✅ **Parameter Management**: Extract initial values and identify changed parameters mid-flight.
- ✅ **System Logs**: Retrieve all logged system messages (Warning, Error, Debug).
- ✅ **Dropout Detection**: Identify and analyze data gaps/dropouts.

---

## 📦 Installation

### 1. Build the JAR
Clone the repository and build using Maven:
```bash
git clone https://github.com/FurqanShafiq11/JavaULog.git
cd JavaULog
mvn clean install
```

### 2. Add Dependency
Add this to your project's `pom.xml`:
```xml
<dependency>
    <groupId>com.ulog.parser</groupId>
    <artifactId>ulog-reader</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## 🚀 Library Usage Guide

### 🔹 Basic Data Extraction
Use this to access specific flight data (like battery voltage) directly in your Java application.
```java
ULogReader reader = new ULogReader("flight.ulg", null, true, false);

// Retrieve a specific dataset
Data battery = reader.getDataset("battery_status", 0);
if (battery != null) {
    List<Object> voltages = battery.getData().get("voltage_v");
    System.out.println("First Voltage Reading: " + voltages.get(0));
}
```

### 🔹 Exporting to CSV
Use your built-in function to dump all topics into a folder.
```java
ULogReader reader = new ULogReader("flight.ulg", null, true, false);
// Function: ulog2csv(dataList, outputPrefix, startTime, endTime, delimiter)
reader.ulog2csv(reader.getDataList(), "output/log_export", 0, Long.MAX_VALUE, ",");
```

---

## 💻 Command Line Interface (CLI)
You can use the library as a standalone tool to convert `.ulg` files to `.csv` from your terminal.

**Command Syntax:**
```bash
java -cp target/ulog-reader-1.0.0.jar com.ulog.parser.ULogReader <input_file.ulg> <output_prefix>
```

---

## 📖 API Reference

### Primary Methods (`ULogReader`)
| Method | Return Type | Description |
| :--- | :--- | :--- |
| `getDataList()` | `List<Data>` | Returns all parsed flight data topics. |
| `getDataset(name, id)` | `Data` | Gets a specific topic by name and instance. |
| `getInitialParameters()` | `Map<String, Object>` | Returns all parameters set at boot. |
| `getLoggedMessages()` | `List<MessageLogging>`| Returns all text logs from the flight. |
| `ulog2csv(...)` | `void` | Exports the data to CSV files. |

### Data Model Classes
- **`Data`**: Contains the actual message values and timestamps.
- **`ULogUtils`**: Static helper for binary unpacking and string parsing.
- **`MessageFormat`**: Defines the structure of the logged topics.

---

## 🛠 Technical Specifications
- **Time Units**: All timestamps are in microseconds (us).
- **Encoding**: UTF-8 for string parsing.
- **Byte Order**: Little-endian (Standard ULog spec).

---

## 📜 Credits & License
- **Original Implementation**: Ported from [PX4/pyulog](https://github.com/PX4/pyulog).
- **License**: Licensed under the **BSD 3-Clause License**. See the [LICENSE](LICENSE) file for details.

---
**Maintained by:** [Furqan Shafiq/FurqanShafiq11]  
**Feedback:** Open an issue on GitHub for bugs or feature requests.