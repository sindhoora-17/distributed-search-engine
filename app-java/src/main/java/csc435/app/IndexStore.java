package csc435.app;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class IndexStore {
    private final ConcurrentHashMap<String, Integer> documentMap;
    private final ConcurrentHashMap<Integer, String> reverseDocumentMap;
    private final ConcurrentHashMap<String, List<Map.Entry<Integer, Integer>>> termInvertedIndex;
    private final AtomicInteger nextDocNumber;

    public IndexStore() {
        documentMap = new ConcurrentHashMap<>();
        reverseDocumentMap = new ConcurrentHashMap<>();
        termInvertedIndex = new ConcurrentHashMap<>();
        nextDocNumber = new AtomicInteger(1);
    }

    public int putDocument(String documentPath) {
        return documentMap.computeIfAbsent(documentPath, k -> {
            int docNumber = nextDocNumber.getAndIncrement();
            reverseDocumentMap.put(docNumber, documentPath);
            return docNumber;
        });
    }

    public String getDocument(int docNumber) {
        return reverseDocumentMap.getOrDefault(docNumber, "");
    }

    public void updateIndex(int docNumber, Map<String, Integer> termFrequencies) {
        termFrequencies.forEach((term, freq) -> {
            termInvertedIndex.computeIfAbsent(term, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(new AbstractMap.SimpleEntry<>(docNumber, freq));
        });
    }

    public List<Map.Entry<Integer, Integer>> lookupIndex(String term) {
        return termInvertedIndex.getOrDefault(term, Collections.emptyList());
    }
}