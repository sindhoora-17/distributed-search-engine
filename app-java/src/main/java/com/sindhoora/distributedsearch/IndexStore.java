package com.sindhoora.distributedsearch;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe global index shared across all client connections.
 *
 * Concurrency design:
 *  - documentMap / reverseDocumentMap: ConcurrentHashMap for lock-free reads.
 *  - nextDocNumber: AtomicInteger so concurrent clients never collide on doc IDs.
 *  - putDocument uses computeIfAbsent so ID assignment + reverse-map insert is atomic
 *    per key (no check-then-act race).
 *  - termInvertedIndex: postings lists are synchronizedList because a single term can
 *    be written by multiple client threads at once.
 */
public class IndexStore {
    private final ConcurrentHashMap<String, Integer> documentMap;
    private final ConcurrentHashMap<Integer, String> reverseDocumentMap;
    private final ConcurrentHashMap<String, List<Map.Entry<Integer, Integer>>> termInvertedIndex;
    private final AtomicInteger nextDocNumber;
    private final AtomicLong totalTermsIndexed;

    public IndexStore() {
        documentMap = new ConcurrentHashMap<>();
        reverseDocumentMap = new ConcurrentHashMap<>();
        termInvertedIndex = new ConcurrentHashMap<>();
        nextDocNumber = new AtomicInteger(1);
        totalTermsIndexed = new AtomicLong(0);
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
            termInvertedIndex
                .computeIfAbsent(term, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(new AbstractMap.SimpleEntry<>(docNumber, freq));
            totalTermsIndexed.incrementAndGet();
        });
    }

    public List<Map.Entry<Integer, Integer>> lookupIndex(String term) {
        return termInvertedIndex.getOrDefault(term, Collections.emptyList());
    }

    public long getTotalTermsIndexed() {
        return totalTermsIndexed.get();
    }

    public int getDocumentCount() {
        return documentMap.size();
    }
}
