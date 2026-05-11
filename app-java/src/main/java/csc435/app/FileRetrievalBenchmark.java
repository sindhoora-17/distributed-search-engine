package csc435.app;

import csc435.app.RetrievalProto.SearchResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class FileRetrievalBenchmark {
    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("Usage: java -cp target/app-java-1.0-SNAPSHOT.jar csc435.app.FileRetrievalBenchmark " +
                    "<server_ip> <port> <num_clients> <dataset_path1> [<dataset_path2> ...]");
            System.exit(1);
        }

        String serverIp = args[0];
        int serverPort = Integer.parseInt(args[1]);
        int numClients = Integer.parseInt(args[2]);
        List<String> datasetPaths = Arrays.asList(args).subList(3, args.length);

        long totalBytes = 0;
        for (String path : datasetPaths) {
            try (var stream = Files.walk(Paths.get(path))) {
                List<Path> files = stream
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().toLowerCase().endsWith(".txt"))
                        .filter(p -> !p.getFileName().toString().startsWith("."))
                        .collect(Collectors.toList());
                for (Path p : files) {
                    long size = Files.size(p);
                    totalBytes += size;
                }
            }
        }

        ExecutorService executor = Executors.newFixedThreadPool(numClients);
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < numClients; i++) {
            String datasetPath = datasetPaths.get(i % datasetPaths.size());
            executor.submit(() -> {
                ClientProcessingEngine engine = new ClientProcessingEngine();
                if (engine.connect(serverIp, serverPort)) {
                    engine.indexFolder(datasetPath);
                    engine.disconnect();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.HOURS);

        long endTime = System.currentTimeMillis();
        double seconds = (endTime - startTime) / 1000.0;
        System.out.println("Completed indexing " + totalBytes + " bytes of data");
        System.out.printf("Completed indexing in %.3f seconds%n", seconds);

        ClientProcessingEngine searchEngine = new ClientProcessingEngine();
        if (searchEngine.connect(serverIp, serverPort)) {
            List<List<String>> queries = Arrays.asList(
                    Arrays.asList("the"),
                    Arrays.asList("child-like"),
                    Arrays.asList("vortex"),
                    Arrays.asList("moon", "vortex"),
                    Arrays.asList("distortion", "adaptation")
            );

            for (List<String> terms : queries) {
                String queryDisplay = String.join(" AND ", terms);
                System.out.println("Searching search " + queryDisplay);
                long searchStart = System.currentTimeMillis();
                List<SearchResult> results = searchEngine.search(terms);
                int totalResultCount = searchEngine.getSearchResultCount(terms);
                long searchEnd = System.currentTimeMillis();
                double searchSeconds = (searchEnd - searchStart) / 1000.0;
                System.out.printf("Search completed in %.3f seconds%n", searchSeconds);
                System.out.printf("Search results (top 10 out of %d):%n", totalResultCount);
                for (SearchResult result : results) {
                    String[] pathParts = result.getDocumentPath().split(":", 2);
                    String clientId = pathParts[0];
                    String docPath = pathParts.length > 1 ? pathParts[1] : "";
                    System.out.printf("* client %s:%s:%d%n", clientId, docPath, result.getFrequency());
                }
            }
            searchEngine.disconnect();
        }
    }
}
