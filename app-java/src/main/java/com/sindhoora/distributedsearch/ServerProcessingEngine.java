package com.sindhoora.distributedsearch;

import io.grpc.stub.StreamObserver;
import com.sindhoora.distributedsearch.RetrievalProto.*;
import com.sindhoora.distributedsearch.FileRetrievalGrpc.FileRetrievalImplBase;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
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

    @Override
    public void computeIndex(IndexRequest request, StreamObserver<IndexResponse> responseObserver) {
        String clientId = request.getClientId();
        String documentPath = request.getDocumentPath();

        String fullPath = clientId + ":" + documentPath;
        int docNumber = indexStore.putDocument(fullPath);

        Map<String, Integer> lowerCaseFrequencies = new HashMap<>();
        request.getWordFrequenciesMap().forEach((term, freq) -> 
            lowerCaseFrequencies.put(term.toLowerCase(), freq));
        indexStore.updateIndex(docNumber, lowerCaseFrequencies);
        IndexResponse response = IndexResponse.newBuilder()
                .setAcknowledgement("Index updated for " + documentPath)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void computeSearch(SearchRequest request, StreamObserver<SearchResponse> responseObserver) {
        List<String> terms = request.getTermsList();

        terms = terms.stream().map(String::toLowerCase).collect(Collectors.toList());
        List<Map<Integer, Integer>> docFrequencies = new ArrayList<>();


        for (String term : terms) {
            Map<Integer, Integer> freqMap = new HashMap<>();
            for (Map.Entry<Integer, Integer> entry : indexStore.lookupIndex(term)) {
                freqMap.put(entry.getKey(), entry.getValue());
            }
            docFrequencies.add(freqMap);
        }


        Map<Integer, Integer> resultFrequencies = new HashMap<>();
        if (!docFrequencies.isEmpty()) {
            for (Map.Entry<Integer, Integer> entry : docFrequencies.get(0).entrySet()) {
                int docNumber = entry.getKey();
                int totalFreq = entry.getValue();
                boolean inAll = true;
                for (int i = 1; i < docFrequencies.size(); i++) {
                    if (!docFrequencies.get(i).containsKey(docNumber)) {
                        inAll = false;
                        break;
                    }
                    totalFreq += docFrequencies.get(i).get(docNumber);
                }
                if (inAll) {
                    resultFrequencies.put(docNumber, totalFreq);
                }
            }
        }


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
            String path = indexStore.getDocument(entry.getKey());
            responseBuilder.addResults(SearchResult.newBuilder()
                    .setDocumentPath(path)
                    .setFrequency(entry.getValue())
                    .build());
        }


        responseBuilder.setTotalResultCount(allResults.size());

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }
}