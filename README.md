# Distributed File Retrieval Engine

## Overview

Distributed File Retrieval Engine is a distributed document indexing and search system built using Java, gRPC, and Protocol Buffers. The system supports concurrent document indexing across multiple clients and enables distributed keyword-based search over large datasets.

The project implements:

- Client-server distributed architecture
- Concurrent indexing workflows
- gRPC-based remote procedure calls
- Protocol Buffers message serialization
- Multithreaded request processing
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
app-java/
├── src/main/java/csc435/app/
├── src/main/proto/
├── pom.xml
└── README.md
```

---

## Requirements

The project requires:

- Java 21+
- Maven 3.8+

Install Java and Maven on Ubuntu using:

```bash
sudo apt install openjdk-21-jdk maven
```

---

## Datasets

Large benchmarking datasets used during performance evaluation are not included in this repository due to size limitations.

You can place datasets inside:

```text
datasets/
```

A small sample dataset can also be used for testing and demonstration purposes.

---

## System Architecture

The system follows a distributed client-server architecture:

- Clients compute local term frequencies from assigned document collections.
- Partial indexes are transmitted to the server using gRPC.
- The server maintains a global inverted index for distributed search operations.
- Concurrent indexing and search requests are handled using multithreaded processing and thread-safe data structures.

---

## Build Instructions

```bash
cd app-java
mvn compile
mvn package
```

---

## Running the Application

### Start the Server

```bash
java -cp target/distributed-file-retrieval-engine-1.0-SNAPSHOT.jar app.FileRetrievalServer 50051
```

### Start the Client

```bash
java -cp target/distributed-file-retrieval-engine-1.0-SNAPSHOT.jar app.FileRetrievalClient
```

Supported client commands:

```text
connect | get_info | index | search | quit
```

### Run the Benchmark

```bash
java -cp target/distributed-file-retrieval-engine-1.0-SNAPSHOT.jar app.FileRetrievalBenchmark localhost 50051 <number_of_clients> [<dataset_path>]
```

### Fix OutOfMemoryError

```bash
java -Xmx2G -cp target/distributed-file-retrieval-engine-1.0-SNAPSHOT.jar app.FileRetrievalServer 50051
```

---

## Example Workflow

### Start the Server

```bash
java -cp target/distributed-file-retrieval-engine-1.0-SNAPSHOT.jar app.FileRetrievalServer 50051
```

### Connect Clients

```bash
java -cp target/distributed-file-retrieval-engine-1.0-SNAPSHOT.jar app.FileRetrievalClient
> connect localhost 50051
```

### Index Documents

```bash
> index ../datasets/sample_data
```

### Search Documents

```bash
> search child-like
> search distortion AND adaptation
```

---

## Performance

The system was benchmarked on datasets exceeding 2GB and demonstrated improved throughput using concurrent distributed clients and multithreaded indexing workflows.
