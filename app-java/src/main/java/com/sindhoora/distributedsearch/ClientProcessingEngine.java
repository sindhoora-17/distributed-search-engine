
package com.sindhoora.distributedsearch;

import io.grpc.ManagedChannel;
import io.grpc.netty.NettyChannelBuilder;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.sindhoora.distributedsearch.RetrievalProto.*;
import com.sindhoora.distributedsearch.FileRetrievalGrpc.FileRetrievalBlockingStub;

public class ClientProcessingEngine {
    private ManagedChannel channel;
    private FileRetrievalBlockingStub stub;
    private String clientId;
    private long totalBytesIndexed;

    public boolean connect(String serverIp, int serverPort) {
        try {
            InetSocketAddress address = new InetSocketAddress(serverIp, serverPort);
            channel = NettyChannelBuilder.forAddress(address)
                    .usePlaintext()
                    .build();
            stub = FileRetrievalGrpc.newBlockingStub(channel);
            RegisterResponse response = stub.register(RegisterRequest.newBuilder().build());
            clientId = response.getClientId();
            return !clientId.isEmpty();
        } catch (Exception e) {
            System.err.println("Failed to connect to server at " + serverIp + ":" + serverPort + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public String getClientId() {
        return clientId;
    }

    public long getTotalBytesIndexed() {
        return totalBytesIndexed;
    }

    public void disconnect() {
        if (channel != null) {
            try {
                channel.shutdown().awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                System.err.println("Channel shutdown interrupted: " + e.getMessage());
            }
        }
    }

    public boolean indexFolder(String folderPath) {
        totalBytesIndexed = 0;
        Path basePath = Paths.get(folderPath).toAbsolutePath().normalize();
        try (Stream<Path> paths = Files.walk(basePath)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().toLowerCase().endsWith(".txt")) // Only .txt files
                 .filter(p -> !p.getFileName().toString().startsWith(".")) // Exclude hidden files
                 .forEach(filePath -> {
                     try {
                         long fileSize = Files.size(filePath);
                         totalBytesIndexed += fileSize;
                         Map<String, Integer> termFrequencies = extractTerms(filePath);
                         String relativePath = basePath.relativize(filePath.toAbsolutePath().normalize()).toString().replace("\\", "/");
                         IndexRequest request = IndexRequest.newBuilder()
                                 .setClientId(clientId)
                                 .setDocumentPath(relativePath)
                                 .putAllWordFrequencies(termFrequencies)
                                 .build();
                         stub.computeIndex(request);
                     } catch (IOException e) {
                         System.err.println("Error indexing file: " + filePath + ": " + e.getMessage());
                     }
                 });
        } catch (IOException e) {
            System.err.println("Error walking folder: " + folderPath + ": " + e.getMessage());
            return false;
        }
        return true;
    }

    private Map<String, Integer> extractTerms(Path filePath) throws IOException {
        Map<String, Integer> termFrequencies = new HashMap<>();
        String content = new String(Files.readAllBytes(filePath));
        Pattern pattern = Pattern.compile("[a-zA-Z0-9_-]+");
        Matcher matcher = pattern.matcher(content.toLowerCase());
        while (matcher.find()) {
            String term = matcher.group();
            if (term.length() > 3) {
                termFrequencies.merge(term, 1, Integer::sum);
            }
        }
        return termFrequencies;
    }

    public List<SearchResult> search(List<String> terms) {
        try {
            List<String> lowerTerms = terms.stream().map(String::toLowerCase).collect(Collectors.toList());
            SearchRequest request = SearchRequest.newBuilder()
                    .addAllTerms(lowerTerms)
                    .build();
            SearchResponse response = stub.computeSearch(request);
            return response.getResultsList();
        } catch (Exception e) {
            System.err.println("Search failed: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    public int getSearchResultCount(List<String> terms) {
        try {
            List<String> lowerTerms = terms.stream().map(String::toLowerCase).collect(Collectors.toList());
            SearchRequest request = SearchRequest.newBuilder()
                    .addAllTerms(lowerTerms)
                    .build();
            SearchResponse response = stub.computeSearch(request);
            return response.getTotalResultCount();
        } catch (Exception e) {
            System.err.println("Search failed: " + e.getMessage());
            return 0;
        }
    }
}