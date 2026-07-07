package com.sindhoora.distributedsearch;

import com.sindhoora.distributedsearch.RetrievalProto.SearchResult;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

public class FileRetrievalClient {
    public static void main(String[] args) {
        ClientProcessingEngine engine = new ClientProcessingEngine();
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("> ");
            if (!scanner.hasNextLine()) break;
            String line = scanner.nextLine().trim();
            String[] parts = line.split("\\s+");
            if (parts.length == 0) continue;

            String command = parts[0].toLowerCase();
            try {
                switch (command) {
                    case "quit":
                        engine.disconnect();
                        scanner.close();
                        return;
                    case "connect":
                        if (parts.length != 3) {
                            System.out.println("Usage: connect <server_ip> <port>");
                            break;
                        }
                        String serverIp = parts[1];
                        int serverPort = Integer.parseInt(parts[2]);
                        if (engine.connect(serverIp, serverPort)) {
                            System.out.println("Connection successful!");
                        } else {
                            System.out.println("Connection failed!");
                        }
                        break;
                    case "get_info":
                        System.out.println("Client ID: " + engine.getClientId());
                        break;
                    case "index":
                        if (parts.length < 2) {
                            System.out.println("Usage: index <folder_path>");
                            break;
                        }
                        String folderPath = String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
                        long startTime = System.currentTimeMillis();
                        if (engine.indexFolder(folderPath)) {
                            long endTime = System.currentTimeMillis();
                            double seconds = (endTime - startTime) / 1000.0;
                            System.out.println("Completed indexing " + engine.getTotalBytesIndexed() + " bytes of data");
                            System.out.printf("Completed indexing in %.3f seconds%n", seconds);
                        } else {
                            System.out.println("Indexing failed!");
                        }
                        break;
                    case "search":
                        if (parts.length < 2) {
                            System.out.println("Usage: search <term1> [AND <term2> [AND <term3>]]");
                            break;
                        }
                        List<String> terms = Arrays.stream(parts)
                                .skip(1)
                                .filter(term -> !term.equalsIgnoreCase("AND"))
                                .collect(Collectors.toList());
                        long searchStart = System.currentTimeMillis();
                        List<SearchResult> results = engine.search(terms);
                        int totalResultCount = engine.getSearchResultCount(terms);
                        long searchEnd = System.currentTimeMillis();
                        double searchSeconds = (searchEnd - searchStart) / 1000.0;
                        System.out.printf("Search completed in %.1f seconds%n", searchSeconds);
                        System.out.printf("Search results (top 10 out of %d):%n", totalResultCount);
                        for (SearchResult result : results) {
                            String[] pathParts = result.getDocumentPath().split(":", 2);
                            String clientId = pathParts[0];
                            String docPath = pathParts.length > 1 ? pathParts[1] : "";
                            System.out.printf("* client %s:%s:%d%n", clientId, docPath, result.getFrequency());
                        }
                        break;
                    default:
                        System.out.println("Unknown command: " + command);
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
        scanner.close();
    }
}
