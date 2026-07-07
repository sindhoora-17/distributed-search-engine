# Distributed File Retrieval Engine

## Overview

Distributed File Retrieval Engine is a distributed document indexing and search system built using Java, gRPC, and Protocol Buffers.

The system enables multiple clients to process large document collections concurrently, build local indexes, and communicate with a central server that maintains a global searchable index.

Key engineering features:

- Distributed client-server architecture
- Concurrent document indexing
- Remote communication using gRPC
- Efficient data serialization with Protocol Buffers
- Multithreaded request processing
- Thread-safe global index management
- Benchmarking and throughput evaluation

---

## Technologies

- Java
- gRPC
- Protocol Buffers
- Maven
- ConcurrentHashMap
- Multithreading
- Client-Server Architecture
- Distributed Systems

---

## Project Structure

```text
distributed-file-retrieval-engine/
├── app-java/
│   ├── src/main/java/com/sindhoora/distributedsearch/
│   ├── src/main/proto/
│   └── pom.xml
├── datasets/
│   └── sample_data/
├── README.md
└── .gitignore
```

---

## Requirements

- Java 11+
- Maven 3.8+

Install Java and Maven:

```bash
sudo apt install openjdk-11-jdk maven
```

---

## Datasets

Large datasets used during benchmarking are not included in this repository due to size limitations.

A small sample dataset is provided:

```text
datasets/sample_data/
```

You can replace it with any text document collection for indexing and search testing.

---

## System Architecture

The application follows a distributed client-server architecture:

1. Clients scan assigned document collections.
2. Each client computes local word frequency indexes.
3. Index data is sent to the server using gRPC.
4. The server combines client results into a global inverted index.
5. Search queries are executed against the distributed index.

The server supports concurrent requests using multithreading and thread-safe data structures.

---

## Build Instructions

Navigate into the Java application:

```bash
cd app-java
```

Build:

```bash
mvn clean package
```

---

## Running the Application

### Start the Server

```bash
java -cp target/app-java-1.0-SNAPSHOT.jar com.sindhoora.distributedsearch.FileRetrievalServer 50051
```

---

### Start a Client

Open another terminal:

```bash
java -cp target/app-java-1.0-SNAPSHOT.jar com.sindhoora.distributedsearch.FileRetrievalClient
```

Available commands:

```text
connect
get_info
index
search
quit
```

---

## Example Workflow

Connect to server:

```text
connect localhost 50051
```

Index documents:

```text
index ../datasets/sample_data
```

Search:

```text
search child-like
search distortion AND adaptation
```

---

## Benchmarking

Run automated benchmark tests:

```bash
java -cp target/app-java-1.0-SNAPSHOT.jar com.sindhoora.distributedsearch.FileRetrievalBenchmark localhost 50051 4 ../datasets/sample_data
```

---

## Handling Large Datasets

For very large datasets, increase JVM memory:

```bash
java -Xmx2G -cp target/app-java-1.0-SNAPSHOT.jar com.sindhoora.distributedsearch.FileRetrievalServer 50051
```

---

## Performance

The system was tested with datasets exceeding 2GB and demonstrated improved throughput through:

- Parallel client processing
- Multithreaded indexing
- Distributed workload execution
- Optimized gRPC communication
