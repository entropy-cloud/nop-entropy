/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.connector.file;

import io.nop.stream.core.exceptions.StreamException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2511 C3-002 (R01 ledger-retention-policy SSOT member row, face
 * {@code epochledger}): file manifest form-B commit-time pruning.
 *
 * <p>Assertion set: after multi-epoch commits the manifest key count is bounded by
 * the retention epoch window (expired keys pruned inside the same AR-14-locked
 * rewrite), fresh (recoverable-window) keys are retained, the prune is idempotent,
 * the window formula is the SSOT ceiling conversion, and a zero window disables
 * pruning explicitly. Red ④ carrier: {@link #manifestKeysBoundedByRetentionWindow} —
 * pre-fix the manifest accumulated one key per epoch without bound (O(all epochs)).
 */
public class TestFileTwoPhaseCommitSinkManifestPrune_C3_002 {

    private Path outputDir;

    @BeforeEach
    void setUp() throws Exception {
        outputDir = Files.createTempDirectory("file-sink-prune-test");
    }

    @AfterEach
    void tearDown() throws Exception {
        Files.walk(outputDir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                        // teardown best-effort
                    }
                });
    }

    private FileTwoPhaseCommitSink<String> newSink(long retentionEpochWindow) throws Exception {
        FileTwoPhaseCommitSink<String> sink = new FileTwoPhaseCommitSink<>(
                outputDir.toString(), StandardCharsets.UTF_8, 0, retentionEpochWindow);
        sink.beginTransaction();
        return sink;
    }

    private void commitEpoch(FileTwoPhaseCommitSink<String> sink, long epoch, String line) throws Exception {
        sink.invoke(line);
        sink.saveState(epoch);
        sink.commit(epoch);
    }

    private Properties loadManifest() throws Exception {
        Properties props = new Properties();
        Path manifestPath = outputDir.resolve("manifest.properties");
        if (Files.exists(manifestPath)) {
            try (var in = Files.newInputStream(manifestPath)) {
                props.load(in);
            }
        }
        return props;
    }

    /** Red ④ carrier (see class javadoc). */
    @Test
    void manifestKeysBoundedByRetentionWindow() throws Exception {
        FileTwoPhaseCommitSink<String> sink = newSink(10);
        assertEquals(10, sink.getRetentionEpochWindow());

        for (long epoch = 1; epoch <= 30; epoch++) {
            commitEpoch(sink, epoch, "line-" + epoch);
        }

        Properties manifest = loadManifest();
        // predicate: epoch(key) < currentEpoch(30) - window(10) = 20 → epochs 20..30 kept
        assertEquals(11, manifest.size(),
                "manifest keys must be bounded by the retention window, got: " + manifest.keySet());
        assertFalse(manifest.containsKey("1"), "expired epoch key must be pruned");
        assertFalse(manifest.containsKey("19"), "epoch below the window must be pruned");
        assertTrue(manifest.containsKey("20"), "keys inside the window must be retained");
        assertTrue(manifest.containsKey("30"), "the latest epoch must be retained");
    }

    /** Subtask-suffixed keys ({@code N.sK}) decode their epoch and prune with the same rule. */
    @Test
    void subtaskSuffixedKeysPruneByEncodedEpoch() throws Exception {
        FileTwoPhaseCommitSink<String> sink = newSink(5);
        // seed a manifest with subtask-suffixed keys via the same commit path of a
        // subtask-1 copy sharing the directory (AR-14 lock serializes them)
        FileTwoPhaseCommitSink<String> sub1 = new FileTwoPhaseCommitSink<>(
                outputDir.toString(), StandardCharsets.UTF_8, 1, 5);
        sub1.beginTransaction();

        commitEpoch(sub1, 1, "s1-line");
        commitEpoch(sub1, 2, "s1-line2");
        commitEpoch(sink, 10, "main-line");

        Properties manifest = loadManifest();
        assertTrue(manifest.containsKey("10"), "current epoch must be present");
        // window 5, current epoch 10 → keepAbove=5: epochs 1/2 (subtask-1 keys "1.s1","2.s1") pruned
        assertFalse(manifest.containsKey("1.s1"), "expired subtask-suffixed key must be pruned");
        assertFalse(manifest.containsKey("2.s1"), "expired subtask-suffixed key must be pruned");
        assertEquals(1, manifest.size());
    }

    /** SSOT conversion: ceiling rounding — never under-retains. */
    @Test
    void retentionEpochWindowIsCeilingConverted() {
        // 30d default ÷ 60s interval = 43200 exactly
        assertEquals(43200L, FileTwoPhaseCommitSink.computeRetentionEpochWindow(30, 60_000));
        // ceiling: 1d ÷ 16h → 86400000/57600000 = 1.5 → 2 (ceil keeps the extra half)
        assertEquals(2L, FileTwoPhaseCommitSink.computeRetentionEpochWindow(1, 57_600_000L));
        // retention-days = 0 → window 0 (pruning disabled), no fail
        assertEquals(0L, FileTwoPhaseCommitSink.computeRetentionEpochWindow(0, 60_000));
        assertThrows(StreamException.class,
                () -> FileTwoPhaseCommitSink.computeRetentionEpochWindow(-1, 60_000));
        assertThrows(StreamException.class,
                () -> FileTwoPhaseCommitSink.computeRetentionEpochWindow(30, 0));
        assertThrows(StreamException.class,
                () -> newSink(-5), "negative window = assembly fail-fast (would over-prune)");
    }

    /** Zero window = explicit disable: keys accumulate as before (opt-out leg). */
    @Test
    void zeroWindowDisablesPruning() throws Exception {
        FileTwoPhaseCommitSink<String> sink = newSink(0);
        for (long epoch = 1; epoch <= 5; epoch++) {
            commitEpoch(sink, epoch, "line-" + epoch);
        }
        assertEquals(5, loadManifest().size(), "pruning disabled: all keys retained");
    }

    /** Manifest key epoch decoding (C3-002 helper contract). */
    @Test
    void epochOfKeyDecodesManifestKeyShapes() {
        assertEquals(7L, FileTwoPhaseCommitSink.epochOfKey("7"));
        assertEquals(9L, FileTwoPhaseCommitSink.epochOfKey("9.s2"));
        assertNull(FileTwoPhaseCommitSink.epochOfKey("not-numeric"));
        assertNull(FileTwoPhaseCommitSink.epochOfKey(".s1"));
        assertNull(FileTwoPhaseCommitSink.epochOfKey(""));
    }
}
