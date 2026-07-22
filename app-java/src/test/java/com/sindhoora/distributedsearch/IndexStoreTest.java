package com.sindhoora.distributedsearch;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrency correctness tests for IndexStore — the core claim of the project
 * is that the global index is safe under concurrent client writes, so we test
 * exactly that.
 */
class IndexStoreTest {

    @Test
    void documentIdsAreUniqueUnderConcurrentWrites() throws InterruptedException {
        IndexStore store = new IndexStore();
        int threads = 16, perThread = 500;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        Set<Integer> ids = ConcurrentHashMap.newKeySet();

        for (int t = 0; t < threads; t++) {
            final int tid = t;
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException ignored) {}
                for (int i = 0; i < perThread; i++) {
                    ids.add(store.putDocument("client" + tid + "/doc" + i));
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        // Every distinct path got a distinct id, and none were lost to a race.
        assertEquals(threads * perThread, ids.size());
        assertEquals(threads * perThread, store.getDocumentCount());
    }

    @Test
    void sameDocumentPathReturnsStableId() {
        IndexStore store = new IndexStore();
        int id1 = store.putDocument("client1/a.txt");
        int id2 = store.putDocument("client1/a.txt");
        assertEquals(id1, id2, "computeIfAbsent must return the same id for the same path");
        assertEquals(1, store.getDocumentCount());
    }

    @Test
    void concurrentPostingsForSameTermAreNotLost() throws InterruptedException {
        IndexStore store = new IndexStore();
        int threads = 8, perThread = 1000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);

        for (int t = 0; t < threads; t++) {
            final int tid = t;
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException ignored) {}
                for (int i = 0; i < perThread; i++) {
                    int doc = store.putDocument("t" + tid + "/d" + i);
                    store.updateIndex(doc, Map.of("shared", 1)); // every thread writes the same term
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        // All postings for "shared" survived concurrent appends to the one postings list.
        assertEquals(threads * perThread, store.lookupIndex("shared").size());
    }
}
