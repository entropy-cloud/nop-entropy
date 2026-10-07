/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.file;

import io.nop.stream.core.connector.registry.ConnectorDirection;
import io.nop.stream.core.connector.registry.StreamConnectorConfig;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.source.AssignmentDeliveryService;
import io.nop.stream.core.source.SplitEnumeratorContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI5 (plan 2330): rate limiting and skew-aware split assignment. Time assertions use
 * the plan ruling — rate-limited run duration >= theoretical value x 0.9 where
 * theoretical = max(N/rateRows, B/rateBytes) (empty-start bucket math bound, clock
 * slack 0.9) — and never compare against an unlimited baseline (slow-machine robust).
 */
public class TestFileSourceRateLimitAndSkew {

    @TempDir
    Path dir;

    // ------------------------------------------------------------------
    // rate limiting
    // ------------------------------------------------------------------

    @Test
    public void rateLimitedE2eTakesAtLeastTheoreticalTime() throws Exception {
        Files.write(dir.resolve("in.txt"), List.of("l1", "l2", "l3", "l4", "l5"));
        // 20 rows/s over 5 rows -> theoretical 4/20 = 200ms (first token free per the
        // bucket refill accounting: 5 acquisitions, one fits immediately)
        FileSource source = new FileSource(dir.toString(), FileSourceReader.DEFAULT_MAX_LINE_BYTES,
                20L, 0L, FileSplitEnumerator.POLICY_ROUND_ROBIN);
        FileSourceReader reader = new FileSourceReader(null, dir.toString(),
                FileSourceReader.DEFAULT_MAX_LINE_BYTES, 20L, 0L);
        reader.addSplits(List.of(new FileSplit(dir.resolve("in.txt").toString(), 0,
                Files.size(dir.resolve("in.txt")))));
        long start = System.nanoTime();
        int count = 0;
        while (reader.pollNext().isPresent()) {
            count++;
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertEquals(5, count);
        assertTrue(elapsedMs >= 180, () -> "rate-limited run took only " + elapsedMs
                + "ms; theoretical ~200ms at 20 rows/s");
    }

    @Test
    public void unlimitedReaderIsNotThrottled() throws Exception {
        Files.write(dir.resolve("in.txt"), List.of("a", "b", "c"));
        FileSourceReader reader = new FileSourceReader(null, dir.toString(),
                FileSourceReader.DEFAULT_MAX_LINE_BYTES, 0L, 0L);
        reader.addSplits(List.of(new FileSplit(dir.resolve("in.txt").toString(), 0,
                Files.size(dir.resolve("in.txt")))));
        long start = System.nanoTime();
        int count = 0;
        while (reader.pollNext().isPresent()) {
            count++;
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertEquals(3, count);
        assertTrue(elapsedMs < 5_000, "sanity bound only (slow CI): " + elapsedMs);
    }

    @Test
    public void factoryRejectsExplicitNonPositiveRate() {
        FileSourceConnectorFactory factory = new FileSourceConnectorFactory();
        StreamException e = assertThrows(StreamException.class, () -> factory.createSource(
                new StreamConnectorConfig("file", Map.of(
                        "directoryPath", dir.toString(),
                        "rateLimitRowsPerSecond", "-5"))));
        assertTrue(String.valueOf(e).contains("rateLimitRowsPerSecond"), String.valueOf(e));
    }

    @Test
    public void factoryAcceptsUnsetRateAsUnlimited() {
        FileSourceConnectorFactory factory = new FileSourceConnectorFactory();
        FileSource source = (FileSource) factory.createSource(
                new StreamConnectorConfig("file", Map.of("directoryPath", dir.toString())));
        assertEquals(0L, source.getRateLimitRowsPerSecond());
        assertEquals(0L, source.getRateLimitBytesPerSecond());
    }

    // ------------------------------------------------------------------
    // split assignment policy
    // ------------------------------------------------------------------

    @Test
    public void sizeAwareBalancesSkewedSplits() throws Exception {
        // 1 big file (400 bytes) + 4 small files (100 bytes each), parallelism 2.
        // Greedy: big -> reader0 (400); smalls balance 100/0 -> r1, 100/100 -> r0? no:
        // accumulated after big: r0=400,r1=0; small1 -> r1 (100); small2 -> r1 (200);
        // small3 -> r1 (300); small4 -> r1 (400). Both 400 — balanced.
        Path big = dir.resolve("big.txt");
        Files.write(big, new byte[400]);
        List<FileSplit> discovered = new ArrayList<>();
        discovered.add(new FileSplit(big.toString(), 400));
        for (int i = 0; i < 4; i++) {
            Path small = dir.resolve("s" + i + ".txt");
            Files.write(small, new byte[100]);
            discovered.add(new FileSplit(small.toString(), 100));
        }
        FileSplitEnumerator enumerator = new FileSplitEnumerator(dir.toString(),
                FileSplitEnumerator.POLICY_SIZE_AWARE);
        enumerator.restoreState(new FileSplitEnumeratorState(dir.toString(),
                discovered.stream().map(FileSplit::getFilePath).collect(java.util.stream.Collectors.toSet()),
                new java.util.HashSet<>(), new java.util.HashSet<>(),
                discovered.stream().collect(java.util.stream.Collectors.toMap(FileSplit::getFilePath, f -> f)),
                2));
        List<List<FileSplit>> deliveries = new ArrayList<>();
        SplitEnumeratorContext<FileSplit> ctx = new SplitEnumeratorContext<>(2,
                new AssignmentDeliveryService<FileSplit>() {
                    @Override
                    public void assignSplits(int subtaskIndex, List<FileSplit> splits) {
                        deliveries.add(new ArrayList<>(splits));
                    }

                    @Override
                    public boolean isReaderRegistered(int subtaskIndex) {
                        return true;
                    }
                });
        // start() re-discovers the directory and sets the context; the restored
        // assigned/finished sets stay intact per restoreState semantics.
        enumerator.start(ctx);
        enumerator.addReader(0);
        enumerator.addReader(1);
        assertEquals(2, deliveries.size(), "both readers must receive their share");
        long bytes0 = deliveries.get(0).stream().mapToLong(FileSplit::getEndOffset).sum();
        long bytes1 = deliveries.get(1).stream().mapToLong(FileSplit::getEndOffset).sum();
        // measurement ruling (plan 2330 F1): delivered-byte difference bounded by the
        // largest split; the re-plan-on-pull remainder stays undelivered until requested
        assertTrue(Math.abs(bytes0 - bytes1) <= 400,
                () -> "delivered bytes unbalanced: r0=" + bytes0 + " r1=" + bytes1);
        assertTrue(bytes0 + bytes1 < 800,
                "the F1 semantics deliver per-event shares; a remainder must exist: "
                        + (bytes0 + bytes1));
    }

    @Test
    public void roundRobinRemainsTheDefault() throws Exception {
        Path a = dir.resolve("a.txt");
        Files.write(a, new byte[10]);
        FileSplitEnumerator enumerator = new FileSplitEnumerator(dir.toString());
        FileSplitEnumeratorState state = new FileSplitEnumeratorState(dir.toString(),
                java.util.Set.of(a.toString()), new java.util.HashSet<>(), new java.util.HashSet<>(),
                Map.of(a.toString(), new FileSplit(a.toString(), 10)), 2);
        enumerator.restoreState(state);
        List<List<FileSplit>> deliveries = new ArrayList<>();
        enumerator.start(new SplitEnumeratorContext<>(2,
                new AssignmentDeliveryService<FileSplit>() {
                    @Override
                    public void assignSplits(int subtaskIndex, List<FileSplit> splits) {
                        deliveries.add(new ArrayList<>(splits));
                    }

                    @Override
                    public boolean isReaderRegistered(int subtaskIndex) {
                        return true;
                    }
                }));
        enumerator.addReader(0);
        enumerator.addReader(1);
        // discovery index 0 -> reader 0 (round-robin): the single file lands on reader 0
        assertEquals(1, deliveries.get(0).size());
        assertEquals(a.toString(), deliveries.get(0).get(0).getFilePath());
        assertTrue(deliveries.size() == 1 || deliveries.get(1).isEmpty(),
                "no split may go to reader 1");
    }

    @Test
    public void sizeAwarePolicySurvivesRestorePath() throws Exception {
        // discriminator fixture: discovery order [s0(100), s1(100), big(300)].
        // round-robin delivers r0={s0,big}; size-aware delivers r0={big,s1}, r1={s0}
        // — the file landing on reader 1 discriminates the two policies.
        Path s0 = dir.resolve("s0.txt");
        Path s1 = dir.resolve("s1.txt");
        Path big = dir.resolve("big.txt");
        Files.write(s0, new byte[100]);
        Files.write(s1, new byte[100]);
        Files.write(big, new byte[300]);
        FileSource source = new FileSource(dir.toString(), FileSourceReader.DEFAULT_MAX_LINE_BYTES,
                0L, 0L, FileSplitEnumerator.POLICY_SIZE_AWARE);
        @SuppressWarnings("unchecked")
        FileSplitEnumerator enumerator = (FileSplitEnumerator) source.restoreEnumerator(
                new FileSplitEnumeratorState(
                dir.toString(), new java.util.HashSet<>(), new java.util.HashSet<>(),
                new java.util.HashSet<>(), new java.util.HashMap<>(), 2));
        List<List<FileSplit>> deliveries = new ArrayList<>();
        enumerator.start(new SplitEnumeratorContext<>(2,
                new AssignmentDeliveryService<FileSplit>() {
                    @Override
                    public void assignSplits(int subtaskIndex, List<FileSplit> splits) {
                        deliveries.add(new ArrayList<>(splits));
                    }

                    @Override
                    public boolean isReaderRegistered(int subtaskIndex) {
                        return true;
                    }
                }));
        enumerator.addReader(0);
        enumerator.addReader(1);
        assertEquals(2, deliveries.size());
        // F1 re-plan semantics: reader1's call re-plans over {s0,s1} without attributing
        // reader0's big file, so r1 = {s1}; the discriminator vs round-robin (which
        // would give r0 = {s0,big}) is reader0's content: big WITHOUT s0.
        assertTrue(deliveries.get(0).stream().anyMatch(f -> f.getFilePath().equals(big.toString())),
                () -> "reader0 must hold the big file under size-aware: " + deliveries);
        assertTrue(deliveries.get(0).stream().noneMatch(f -> f.getFilePath().equals(s0.toString())),
                () -> "round-robin fallback would give reader0 s0 — policy did not survive "
                        + "the restore path: " + deliveries);
    }

    @Test
    public void unknownPolicyIsTypedRejectedOnBothConstructionPaths() {
        assertThrows(StreamException.class, () -> new FileSource(dir.toString(),
                FileSourceReader.DEFAULT_MAX_LINE_BYTES, 0, 0, "fastest"));
        assertThrows(StreamException.class, () -> new FileSplitEnumerator(dir.toString(), "fastest"));
    }

}
