package com.sindhoora.distributedsearch;

import io.grpc.stub.StreamObserver;
import com.sindhoora.distributedsearch.RetrievalProto.*;
import com.sindhoora.distributedsearch.FileRetrievalGrpc.FileRetrievalImplBase;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class ServerProcessingEngine extends FileRetrievalImplBase {
    private final IndexStore indexStore;
    private final AtomicInteger nextClientId;

    public ServerProcessingEngine() {
        indexStore = new IndexStore();
        nextClientId = new AtomicInteger(1);
    }

    @Override
    public void register(RegisterRequest request, StreamObserver<RegisterResponse> responseObserver) {
        RegisterResponse response = RegisterResponse.newBuilder()
                .setClientId(String.valueOf(nextClientId.getAndIncrement()))
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * Client-streaming index. The client streams one IndexDocument per file;
     * the server merges each into the global index as it arrives and replies
     * exactly once, after onCompleted, with a summary. This replaces the old
     * one-RPC-per-file design and removes a network round-trip per document.
     */
    @Override
    public StreamObserver<IndexDocument> streamIndex(StreamObserver<IndexSummary> responseObserver) {
        // Per-stream counters. Safe without extra locking because gRPC delivers
        // messages on a single stream sequentially (no concurrent onNext for one call).
        final AtomicInteger docsInThisStream = new AtomicInteger(0);
        final AtomicLong termsInThisStream = new AtomicLong(0);

        return new StreamObserver<IndexDocument>() {
            @Override
            public void onNext(IndexDocument doc) {
                String fullPath = doc.getClientId() + ":" + doc.getDocumentPath();
                int docNumber = indexStore.putDocument(fullPath);

                Map<String, Integer> lowerCaseFrequencies = new HashMap<>();
                doc.getWordFrequenciesMap().forEach((term, freq) ->
                        lowerCaseFrequencies.merge(term.toLowerCase(), freq, Integer::sum));

                indexStore.updateIndex(docNumber, lowerCaseFrequencies);
                docsInThisStream.incrementAndGet();
                termsInThisStream.addAndGet(lowerCaseFrequencies.size());
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("Index stream error: " + t.getMessage());
            }

            @Override
            public void onCompleted() {
                IndexSummary summary = IndexSummary.newBuilder()
                        .setDocumentsIndexed(docsInThisStream.get())
                        .setTermsIndexed(termsInThisStream.get())
                        .build();
                responseObserver.onNext(summary);
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public void computeSearch(SearchRequest request, StreamObserver<SearchResponse> responseObserver) {
        List<String> terms = request.getTermsList().stream()
                .map(String::toLowerCase)
                .collect(Collectors.toList());
        QueryMode mode = request.getMode();

        // Build per-term doc->freq maps.
        List<Map<Integer, Integer>> docFrequencies = new ArrayList<>();
        for (String term : terms) {
            Map<Integer, Integer> freqMap = new HashMap<>();
            for (Map.Entry<Integer, Integer> entry : indexStore.lookupIndex(term)) {
                freqMap.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
            docFrequencies.add(freqMap);
        }

        Map<Integer, Integer> resultFrequencies = (mode == QueryMode.OR)
                ? unionResults(docFrequencies)
                : intersectResults(docFrequencies);

        List<Map.Entry<Integer, Integer>> allResults = resultFrequencies.entrySet().stream()
                .sorted((a, b) -> {
                    int freqCompare = b.getValue().compareTo(a.getValue());
                    if (freqCompare != 0) return freqCompare;
                    return indexStore.getDocument(a.getKey()).compareTo(indexStore.getDocument(b.getKey()));
                })
                .collect(Collectors.toList());

        List<Map.Entry<Integer, Integer>> topResults = allResults.stream()
                .limit(10)
                .collect(Collectors.toList());

        SearchResponse.Builder responseBuilder = SearchResponse.newBuilder();
        for (Map.Entry<Integer, Integer> entry : topResults) {
            responseBuilder.addResults(SearchResult.newBuilder()
                    .setDocumentPath(indexStore.getDocument(entry.getKey()))
                    .setFrequency(entry.getValue())
                    .build());
        }
        responseBuilder.setTotalResultCount(allResults.size());

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    /** AND: doc must appear in every term's postings; frequencies summed. */
    private Map<Integer, Integer> intersectResults(List<Map<Integer, Integer>> docFrequencies) {
        Map<Integer, Integer> result = new HashMap<>();
        if (docFrequencies.isEmpty()) return result;

        for (Map.Entry<Integer, Integer> entry : docFrequencies.get(0).entrySet()) {
            int docNumber = entry.getKey();
            int totalFreq = entry.getValue();
            boolean inAll = true;
            for (int i = 1; i < docFrequencies.size(); i++) {
                Integer f = docFrequencies.get(i).get(docNumber);
                if (f == null) { inAll = false; break; }
                totalFreq += f;
            }
            if (inAll) result.put(docNumber, totalFreq);
        }
        return result;
    }

    /** OR: doc appearing in any term's postings; frequencies summed across terms. */
    private Map<Integer, Integer> unionResults(List<Map<Integer, Integer>> docFrequencies) {
        Map<Integer, Integer> result = new HashMap<>();
        for (Map<Integer, Integer> freqMap : docFrequencies) {
            freqMap.forEach((doc, freq) -> result.merge(doc, freq, Integer::sum));
        }
        return result;
    }
}
