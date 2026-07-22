package com.sindhoora.distributedsearch;

import io.grpc.ManagedChannel;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.sindhoora.distributedsearch.RetrievalProto.*;
import com.sindhoora.distributedsearch.FileRetrievalGrpc.FileRetrievalBlockingStub;
import com.sindhoora.distributedsearch.FileRetrievalGrpc.FileRetrievalStub;

public class ClientProcessingEngine {
    private ManagedChannel channel;
    private FileRetrievalBlockingStub blockingStub;   // register + search
    private FileRetrievalStub asyncStub;              // client-streaming index
    private String clientId;
    private long totalBytesIndexed;

    private static final Pattern TERM_PATTERN = Pattern.compile("[a-zA-Z0-9_-]+");
    private static final int MIN_TERM_LENGTH = 3;

    public boolean connect(String serverIp, int serverPort) {
        try {
            InetSocketAddress address = new InetSocketAddress(serverIp, serverPort);
            channel = NettyChannelBuilder.forAddress(address)
                    .usePlaintext()
                    .build();
            blockingStub = FileRetrievalGrpc.newBlockingStub(channel);
            asyncStub = FileRetrievalGrpc.newStub(channel);
            RegisterResponse response = blockingStub.register(RegisterRequest.newBuilder().build());
            clientId = response.getClientId();
            return clientId != null && !clientId.isEmpty();
        } catch (Exception e) {
            System.err.println("Failed to connect to " + serverIp + ":" + serverPort + ": " + e.getMessage());
            return false;
        }
    }

    public String getClientId() { return clientId; }
    public long getTotalBytesIndexed() { return totalBytesIndexed; }

    public void disconnect() {
        if (channel != null) {
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Channel shutdown interrupted: " + e.getMessage());
            }
        }
    }

    /**
     * Index all .txt files under folderPath by streaming one IndexDocument per file
     * over a single client-streaming RPC. Blocks until the server sends its summary.
     */
    public boolean indexFolder(String folderPath) {
        return indexFiles(collectTextFiles(folderPath), Paths.get(folderPath).toAbsolutePath().normalize());
    }

    /** Index an explicit list of files (used by the partitioned benchmark). */
    public boolean indexFiles(List<Path> files, Path basePath) {
        totalBytesIndexed = 0;
        final CountDownLatch finishLatch = new CountDownLatch(1);
        final AtomicReference<Throwable> streamError = new AtomicReference<>();

        StreamObserver<IndexSummary> responseObserver = new StreamObserver<IndexSummary>() {
            @Override public void onNext(IndexSummary summary) { /* summary available if needed */ }
            @Override public void onError(Throwable t) { streamError.set(t); finishLatch.countDown(); }
            @Override public void onCompleted() { finishLatch.countDown(); }
        };

        StreamObserver<IndexDocument> requestObserver = asyncStub.streamIndex(responseObserver);
        try {
            for (Path filePath : files) {
                try {
                    long fileSize = Files.size(filePath);
                    Map<String, Integer> termFrequencies = extractTerms(filePath);
                    String relativePath = basePath
                            .relativize(filePath.toAbsolutePath().normalize())
                            .toString().replace("\\", "/");
                    IndexDocument doc = IndexDocument.newBuilder()
                            .setClientId(clientId)
                            .setDocumentPath(relativePath)
                            .putAllWordFrequencies(termFrequencies)
                            .build();
                    requestObserver.onNext(doc);
                    totalBytesIndexed += fileSize;
                } catch (IOException e) {
                    System.err.println("Error indexing " + filePath + ": " + e.getMessage());
                }
            }
            requestObserver.onCompleted();
        } catch (RuntimeException e) {
            requestObserver.onError(e);
            return false;
        }

        try {
            if (!finishLatch.await(1, TimeUnit.HOURS)) {
                System.err.println("Indexing timed out");
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        if (streamError.get() != null) {
            System.err.println("Indexing failed: " + streamError.get().getMessage());
            return false;
        }
        return true;
    }

    private List<Path> collectTextFiles(String folderPath) {
        Path basePath = Paths.get(folderPath).toAbsolutePath().normalize();
        try (Stream<Path> paths = Files.walk(basePath)) {
            return paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".txt"))
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            System.err.println("Error walking " + folderPath + ": " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private Map<String, Integer> extractTerms(Path filePath) throws IOException {
        Map<String, Integer> termFrequencies = new HashMap<>();
        String content = new String(Files.readAllBytes(filePath));
        Matcher matcher = TERM_PATTERN.matcher(content.toLowerCase());
        while (matcher.find()) {
            String term = matcher.group();
            if (term.length() > MIN_TERM_LENGTH) {
                termFrequencies.merge(term, 1, Integer::sum);
            }
        }
        return termFrequencies;
    }

    public List<SearchResult> search(List<String> terms, QueryMode mode) {
        try {
            SearchRequest request = SearchRequest.newBuilder()
                    .addAllTerms(terms.stream().map(String::toLowerCase).collect(Collectors.toList()))
                    .setMode(mode)
                    .build();
            return blockingStub.computeSearch(request).getResultsList();
        } catch (Exception e) {
            System.err.println("Search failed: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    public int getSearchResultCount(List<String> terms, QueryMode mode) {
        try {
            SearchRequest request = SearchRequest.newBuilder()
                    .addAllTerms(terms.stream().map(String::toLowerCase).collect(Collectors.toList()))
                    .setMode(mode)
                    .build();
            return blockingStub.computeSearch(request).getTotalResultCount();
        } catch (Exception e) {
            System.err.println("Search failed: " + e.getMessage());
            return 0;
        }
    }

    // Public helper so the benchmark can list + partition a corpus.
    public List<Path> listTextFiles(String folderPath) { return collectTextFiles(folderPath); }
}
