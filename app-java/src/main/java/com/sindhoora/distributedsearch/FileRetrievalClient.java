package com.sindhoora.distributedsearch;

import com.sindhoora.distributedsearch.RetrievalProto.QueryMode;
import com.sindhoora.distributedsearch.RetrievalProto.SearchResult;
import java.util.*;
import java.util.stream.Collectors;

public class FileRetrievalClient {
    public static void main(String[] args) {
        ClientProcessingEngine engine = new ClientProcessingEngine();
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("> ");
            if (!scanner.hasNextLine()) break;
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\s+");
            String command = parts[0].toLowerCase();

            try {
                switch (command) {
                    case "quit":
                        engine.disconnect();
                        scanner.close();
                        return;
                    case "connect":
                        if (parts.length != 3) { System.out.println("Usage: connect <server_ip> <port>"); break; }
                        boolean ok = engine.connect(parts[1], Integer.parseInt(parts[2]));
                        System.out.println(ok ? "Connection successful!" : "Connection failed!");
                        break;
                    case "get_info":
                        System.out.println("Client ID: " + engine.getClientId());
                        break;
                    case "index":
                        if (parts.length < 2) { System.out.println("Usage: index <folder_path>"); break; }
                        String folderPath = String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
                        long t0 = System.nanoTime();
                        if (engine.indexFolder(folderPath)) {
                            double sec = (System.nanoTime() - t0) / 1_000_000_000.0;
                            System.out.printf("Indexed %d bytes in %.3f s%n", engine.getTotalBytesIndexed(), sec);
                        } else {
                            System.out.println("Indexing failed!");
                        }
                        break;
                    case "search":   // AND
                    case "search_or": // OR
                        if (parts.length < 2) { System.out.println("Usage: " + command + " <term1> [term2 ...]"); break; }
                        QueryMode mode = command.equals("search_or") ? QueryMode.OR : QueryMode.AND;
                        List<String> terms = Arrays.stream(parts).skip(1)
                                .filter(t -> !t.equalsIgnoreCase("AND") && !t.equalsIgnoreCase("OR"))
                                .collect(Collectors.toList());
                        long s = System.nanoTime();
                        List<SearchResult> results = engine.search(terms, mode);
                        int total = engine.getSearchResultCount(terms, mode);
                        double ms = (System.nanoTime() - s) / 1_000_000.0;
                        System.out.printf("Search (%s) completed in %.2f ms%n", mode, ms);
                        System.out.printf("Top %d of %d results:%n", results.size(), total);
                        for (SearchResult r : results) {
                            String[] pp = r.getDocumentPath().split(":", 2);
                            System.out.printf("* client %s : %s : freq %d%n",
                                    pp[0], pp.length > 1 ? pp[1] : "", r.getFrequency());
                        }
                        break;
                    default:
                        System.out.println("Unknown command: " + command);
                        System.out.println("Commands: connect, get_info, index, search, search_or, quit");
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
        scanner.close();
    }
}
