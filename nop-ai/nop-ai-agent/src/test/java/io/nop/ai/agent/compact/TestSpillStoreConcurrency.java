package io.nop.ai.agent.compact;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2519 closure-audit remediation (M1a / 裁定⑥ C5-008 判据面) focused
 * concurrency pin for {@link InMemorySpillStore}: the spill store is a
 * per-session shared carrier — concurrent compaction writers put while
 * concurrent readers get, so the CHM-backed map must never lose entries,
 * never serve torn content, never throw {@code ConcurrentModificationException},
 * and {@code size()} must conserve the live-entry count.
 *
 * <p>Defect state (red probe): reverting the store map from
 * {@code ConcurrentHashMap} to a plain {@code HashMap} loses concurrent puts
 * (size mismatch / null reads on recorded ids) — RED here. The green state
 * (CHM restored) is the shipped implementation.
 */
public class TestSpillStoreConcurrency {

    private static final int WRITER_THREADS = 8;
    private static final int PUTS_PER_WRITER = 300;
    private static final int READER_THREADS = 4;
    private static final int READS_PER_READER = 500;

    /**
     * Concurrent put/get storm: every put yields a unique spill id, every
     * recorded id reads back the exact content it was written with (no
     * tearing, no lost entry), unknown/null ids read as explicit null, and
     * after quiescence {@code size()} equals the number of distinct puts.
     */
    @Test
    void concurrentPutGet_noLossNoTearing_sizeConserved() throws Exception {
        InMemorySpillStore store = new InMemorySpillStore("concurrency-probe");
        Map<String, String> expected = new ConcurrentHashMap<>();
        AtomicInteger violations = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(WRITER_THREADS + READER_THREADS, r -> {
            Thread t = new Thread(r, "spill-store-concurrency-probe");
            t.setDaemon(true);
            return t;
        });
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();

            for (int w = 0; w < WRITER_THREADS; w++) {
                final int writerId = w;
                futures.add(pool.submit(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        violations.incrementAndGet();
                        return;
                    }
                    for (int j = 0; j < PUTS_PER_WRITER; j++) {
                        String content = spillPayload(writerId, j);
                        String id = store.put(content);
                        if (expected.putIfAbsent(id, content) != null) {
                            // duplicate spill id under concurrency = the
                            // id-uniqueness invariant broke
                            violations.incrementAndGet();
                        }
                    }
                }));
            }

            for (int r = 0; r < READER_THREADS; r++) {
                futures.add(pool.submit(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        violations.incrementAndGet();
                        return;
                    }
                    for (int i = 0; i < READS_PER_READER; i++) {
                        String probeId = probeLiveId(expected);
                        if (probeId == null) {
                            continue;
                        }
                        String got = store.get(probeId);
                        if (got == null || !got.equals(expected.get(probeId))) {
                            // lost entry or torn content under concurrent write
                            violations.incrementAndGet();
                        }
                    }
                    if (store.get("no-such-spill-id") != null || store.get(null) != null) {
                        violations.incrementAndGet();
                    }
                }));
            }

            start.countDown();
            for (Future<?> f : futures) {
                f.get(60, TimeUnit.SECONDS);
            }

            assertEquals(0, violations.get(),
                    "concurrent put/get must not lose entries, tear content, or return "
                            + "non-null for unknown ids (CHM revert = defect state, RED)");
            int total = WRITER_THREADS * PUTS_PER_WRITER;
            assertEquals(total, expected.size(), "every put yields a unique spill id");
            assertEquals(total, store.size(), "size() must conserve the live-entry count "
                    + "(lost concurrent puts = HashMap defect state, RED)");

            // post-quiescence integrity sweep over every recorded entry
            for (Map.Entry<String, String> entry : expected.entrySet()) {
                assertEquals(entry.getValue(), store.get(entry.getKey()),
                        "each recorded spill id must read back its exact content");
            }
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS),
                    "probe pool must settle");
        }
    }

    /** Distinct, well-sized payload so torn/aliased content is detectable. */
    private static String spillPayload(int writerId, int seq) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("spill-concurrency-").append(writerId).append('-').append(seq).append(':');
        while (sb.length() < 256) {
            sb.append("payload-marker;");
        }
        return sb.toString();
    }

    /**
     * Pick a quasi-random live key from the CHM view. The weakly-consistent
     * iterator is concurrent-safe, so probing cannot itself introduce a CME —
     * only the store implementation under test is on trial.
     */
    private static String probeLiveId(Map<String, String> expected) {
        Set<String> ids = expected.keySet();
        if (ids.isEmpty()) {
            return null;
        }
        int skip = ThreadLocalRandom.current().nextInt(ids.size());
        for (String id : ids) {
            if (skip-- == 0) {
                return id;
            }
        }
        return null;
    }
}
