package com.sindhoora.distributedsearch;

import com.sindhoora.distributedsearch.RetrievalProto.QueryMode;
import com.sindhoora.distributedsearch.RetrievalProto.SearchResult;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

/**
 * Benchmarks distributed indexing throughput.
 *
 * The corpus is listed once and PARTITIONED into disjoint slices, one per client,
 * so N clients index DIFFERENT files concurrently (true distributed indexing)
 * rather than all indexing the same folder.
 */
public class FileRetrievalBenchmark {
    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("Usage: FileRetrievalBenchmark <server_ip> <port> <num_clients> <dataset_path>");
            System.exit(1);
        }
        String serverIp = args[0];
        int serverPort = Integer.parseInt(args[1]);
        int numClients = Integer.parseInt(args[2]);
        String datasetPath = args[3];
        Path basePath = Paths.get(datasetPath).toAbsolutePath().normalize();

        // List the corpus once, then partition round-robin into N disjoint slices.
        ClientProcessingEngine lister = new ClientProcessingEngine();
        List<Path> allFiles = lister.listTextFiles(datasetPath);
        if (allFiles.isEmpty()) {
            System.err.println("No .txt files found under " + datasetPath);
            System.exit(1);
        }
        List<List<Path>> partitions = new ArrayList<>();
        for (int i = 0; i < numClients; i++) partitions.add(new ArrayList<>());
        for (int i = 0; i < allFiles.size(); i++) partitions.get(i % numClients).add(allFiles.get(i));

        long totalBytes = 0;
        for (Path p : allFiles) totalBytes += java.nio.file.Files.size(p);

        System.out.printf("Corpus: %d files, %.2f MB, across %d clients%n",
                allFiles.size(), totalBytes / (1024.0 * 1024.0), numClients);

        ExecutorService executor = Executors.newFixedThreadPool(numClients);
        long startTime = System.nanoTime();

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < numClients; i++) {
            final List<Path> slice = partitions.get(i);
            futures.add(executor.submit(() -> {
                ClientProcessingEngine engine = new ClientProcessingEngine();
                if (engine.connect(serverIp, serverPort)) {
                    engine.indexFiles(slice, basePath);
                    engine.disconnect();
                }
            }));
        }
        for (Future<?> f : futures) f.get();  // surface any worker exception

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.HOURS);
        double seconds = (System.nanoTime() - startTime) / 1_000_000_000.0;

        double mb = totalBytes / (1024.0 * 1024.0);
        System.out.printf("Indexed %.2f MB in %.3f s  (%.2f MB/s)%n", mb, seconds, mb / seconds);

        // Sanity-check a few queries.
        ClientProcessingEngine searchEngine = new ClientProcessingEngine();
        if (searchEngine.connect(serverIp, serverPort)) {
            List<List<String>> queries = Arrays.asList(
                    Arrays.asList("distributed"),
                    Arrays.asList("system", "network"),
                    Arrays.asList("vortex", "adaptation"));
            for (List<String> terms : queries) {
                long s = System.nanoTime();
                List<SearchResult> results = searchEngine.search(terms, QueryMode.AND);
                int count = searchEngine.getSearchResultCount(terms, QueryMode.AND);
                double ms = (System.nanoTime() - s) / 1_000_000.0;
                System.out.printf("search %-30s -> %d hits in %.2f ms%n",
                        String.join(" AND ", terms), count, ms);
            }
            searchEngine.disconnect();
        }
    }
}
