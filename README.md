# Distributed Inverted-Index Search Engine

> A hands-on distributed systems project: build a search index across many
> clients, keep it correct under concurrent writes, and measure where it stops
> scaling.

A distributed document indexing and search system in Java. Multiple clients
tokenize document collections **in parallel** and stream per-document term
frequencies to a central gRPC server, which merges them into a **thread-safe
global inverted index** that serves boolean (AND / OR) search queries.

## Architecture

```
   ┌───────────┐   ┌───────────┐   ┌───────────┐
   │ Client 1  │   │ Client 2  │   │ Client N  │   each indexes a DISJOINT
   │ tokenize  │   │ tokenize  │   │ tokenize  │   slice of the corpus
   └─────┬─────┘   └─────┬─────┘   └─────┬─────┘
         │  StreamIndex  │  (client-streaming gRPC)
         │  one stream   │               │
         └───────────────┼───────────────┘
                         ▼
              ┌──────────────────────┐
              │  gRPC Server         │
              │  ServerProcessing-   │
              │  Engine              │
              │                      │
              │  IndexStore          │  ConcurrentHashMap + AtomicInteger
              │  (global inverted    │  lock-free doc IDs,
              │   index)             │  synchronized postings lists
              └──────────┬───────────┘
                         ▲
                         │  ComputeSearch (unary)  AND / OR
                    ┌────┴────┐
                    │ Query   │
                    └─────────┘
```

## Key engineering points

- **Client-streaming indexing.** Each client opens a single `StreamIndex` RPC and
  streams one message per document, instead of a blocking round-trip per file.
  This removes N network round-trips per client on the index path.
- **Thread-safe global index.** `IndexStore` uses `ConcurrentHashMap`, lock-free
  document-ID assignment (`AtomicInteger` + `computeIfAbsent`), and synchronized
  postings lists — no global lock on the write path. Verified by concurrency tests.
- **Boolean queries.** AND (intersection) and OR (union), ranked by summed term
  frequency.
- **Honest benchmark.** The corpus is partitioned into disjoint slices, one per
  client, so N clients index *different* files — real distributed indexing, not
  the same folder N times.

## Results

Benchmarked indexing throughput from 1 to 8 concurrent clients over a
partitioned ~23 MB corpus:

| Clients | Index time | Throughput |
|---------|-----------|------------|
| 1       | 1.06 s    | 21.7 MB/s  |
| 2       | 0.76 s    | 30.2 MB/s  |
| 4       | 0.67 s    | 34.6 MB/s  |
| 8       | 0.70 s    | 32.8 MB/s  |

Throughput improves ~1.6x and peaks at 4 clients, then plateaus as the single
server's merge path becomes the bottleneck — which is where the design would
need index sharding to scale further.

> Note: benchmarks were run with the server and all clients on a single machine,
> so these numbers measure concurrent-client scaling, not cross-network
> performance.

## Project structure

```
app-java/src/main/
├── proto/retrieval.proto            gRPC service + message definitions
└── java/com/sindhoora/distributedsearch/
    ├── IndexStore.java              thread-safe global inverted index (the core)
    ├── ServerProcessingEngine.java  gRPC handlers: streaming index + AND/OR search
    ├── ClientProcessingEngine.java  client-side streaming + query logic
    ├── FileRetrievalServer.java     server entry point
    ├── FileRetrievalClient.java     interactive CLI client
    └── FileRetrievalBenchmark.java  partitioned concurrent-client benchmark
app-java/src/test/                   IndexStoreTest — concurrency correctness tests
```

Start with `IndexStore.java` (the concurrency design) and
`ServerProcessingEngine.java` (the streaming index handler) — that's where the
interesting work is.

## Tech

Java 11+ · gRPC · Protocol Buffers · Maven · JUnit 5

## Build

```bash
cd app-java
mvn clean package
```

Runs the concurrency test suite and produces a shaded jar in `target/`.

## Run

Start the server:

```bash
java -cp target/app-java-1.0-SNAPSHOT.jar com.sindhoora.distributedsearch.FileRetrievalServer 50051
```

Interactive client (in another terminal):

```bash
java -cp target/app-java-1.0-SNAPSHOT.jar com.sindhoora.distributedsearch.FileRetrievalClient
> connect localhost 50051
> index ../datasets/sample_data
> search distributed system          # AND
> search_or vortex adaptation         # OR
> quit
```

## Benchmark

Generate a reproducible corpus, then run the partitioned benchmark:

```bash
python3 scripts/make_dataset.py datasets/bench 2000 1500   # 2000 files
java -cp target/app-java-1.0-SNAPSHOT.jar \
  com.sindhoora.distributedsearch.FileRetrievalBenchmark localhost 50051 4 datasets/bench
```

See [BENCHMARKS.md](BENCHMARKS.md) for full methodology and results.

## Tests

```bash
mvn test
```

`IndexStoreTest` verifies the index stays correct under concurrent writes:
unique document IDs under contention, stable IDs for repeated paths, and no lost
postings when many threads append to the same term.