/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.scenario;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 15 (Phase 2 device verification): the BP-1 live-stepped throttle sink —
 * level-file parsing (fail-fast on missing/garbage/negative), per-record delay
 * behavior, copy/serialization contract, and mutual exclusion with the C3
 * static throttle.
 */
class TestSteppedThrottleSinks {

    @TempDir
    Path tempDir;

    @Test
    void levelFileParsingFailFast() throws Exception {
        Path levelFile = tempDir.resolve("level.txt");
        Files.writeString(levelFile, "50", StandardCharsets.UTF_8);
        assertEquals(50L, ThrottledScenarioSinks.currentThrottleLevel(levelFile.toString()));

        Files.writeString(levelFile, "0", StandardCharsets.UTF_8);
        assertEquals(0L, ThrottledScenarioSinks.currentThrottleLevel(levelFile.toString()));

        Files.writeString(levelFile, "  120 \n", StandardCharsets.UTF_8);
        assertEquals(120L, ThrottledScenarioSinks.currentThrottleLevel(levelFile.toString()));

        Path missing = tempDir.resolve("missing.txt");
        assertThrows(IllegalStateException.class,
                () -> ThrottledScenarioSinks.currentThrottleLevel(missing.toString()));

        Files.writeString(levelFile, "abc", StandardCharsets.UTF_8);
        assertThrows(IllegalStateException.class,
                () -> ThrottledScenarioSinks.currentThrottleLevel(levelFile.toString()));

        Files.writeString(levelFile, "-5", StandardCharsets.UTF_8);
        assertThrows(IllegalStateException.class,
                () -> ThrottledScenarioSinks.currentThrottleLevel(levelFile.toString()));
    }

    @Test
    void sinkDelaysPerCurrentLevelAndPassesThroughAtZero() throws Exception {
        Path levelFile = tempDir.resolve("level.txt");
        Path outputDir = tempDir.resolve("output");
        ThrottledScenarioSinks.SteppedThrottleFileTwoPhaseCommitSink<String> sink =
                new ThrottledScenarioSinks.SteppedThrottleFileTwoPhaseCommitSink<>(
                        outputDir.toString(), levelFile.toString());

        Files.writeString(levelFile, "0", StandardCharsets.UTF_8);
        long start = System.nanoTime();
        sink.invoke("row-at-full-speed");
        long zeroMs = (System.nanoTime() - start) / 1_000_000L;

        Files.writeString(levelFile, "40", StandardCharsets.UTF_8);
        start = System.nanoTime();
        sink.invoke("row-throttled");
        long throttledMs = (System.nanoTime() - start) / 1_000_000L;

        assertTrue(throttledMs >= 35L, "throttle level must delay the record (took " + throttledMs + "ms)");
        assertTrue(zeroMs < 35L, "level 0 must pass through at full speed (took " + zeroMs + "ms)");
    }

    @Test
    void sinkFailsFastWhenLevelFileDisappears() {
        Path levelFile = tempDir.resolve("gone.txt");
        ThrottledScenarioSinks.SteppedThrottleFileTwoPhaseCommitSink<String> sink =
                new ThrottledScenarioSinks.SteppedThrottleFileTwoPhaseCommitSink<>(
                        tempDir.resolve("out").toString(), levelFile.toString());
        assertThrows(IllegalStateException.class, () -> sink.invoke("row"));
    }

    @Test
    void copyAndSerializationKeepTheThrottleConfiguration() throws Exception {
        Path levelFile = tempDir.resolve("level.txt");
        Files.writeString(levelFile, "0", StandardCharsets.UTF_8);
        ThrottledScenarioSinks.SteppedThrottleFileTwoPhaseCommitSink<String> sink =
                new ThrottledScenarioSinks.SteppedThrottleFileTwoPhaseCommitSink<>(
                        tempDir.resolve("out").toString(), levelFile.toString());

        ThrottledScenarioSinks.SteppedThrottleFileTwoPhaseCommitSink<String> copy = sink.copyForSubtask(0);
        copy.invoke("row-via-copy");

        ThrottledScenarioSinks.SteppedThrottleFileTwoPhaseCommitSink<String> roundTripped =
                DistributedScenarioSupport.roundTrip(sink);
        roundTripped.invoke("row-via-roundtrip");
    }

    @Test
    void steppedThrottleIsMutuallyExclusiveWithStaticThrottle() {
        assertThrows(IllegalArgumentException.class,
                () -> DistributedScenarioSupport.s2DistributedResolver(
                        tempDir.resolve("in").toString(), tempDir.resolve("out").toString(),
                        10L, 10L, 50L, null, tempDir.resolve("level.txt").toString()));
    }

    @Test
    void levelFileConstructorValidatesBlankPath() {
        assertThrows(IllegalArgumentException.class,
                () -> new ThrottledScenarioSinks.SteppedThrottleFileTwoPhaseCommitSink<String>(
                        "out", " "));
    }

    @Test
    void levelSinkLevelCanBeSteppedLive() throws Exception {
        Path levelFile = tempDir.resolve("level.txt");
        ThrottledScenarioSinks.SteppedThrottleFileTwoPhaseCommitSink<String> sink =
                new ThrottledScenarioSinks.SteppedThrottleFileTwoPhaseCommitSink<>(
                        tempDir.resolve("out").toString(), levelFile.toString());
        Files.writeString(levelFile, "30", StandardCharsets.UTF_8);
        long start = System.nanoTime();
        sink.invoke("first");
        long firstMs = (System.nanoTime() - start) / 1_000_000L;
        Files.writeString(levelFile, "90", StandardCharsets.UTF_8);
        start = System.nanoTime();
        sink.invoke("second");
        long secondMs = (System.nanoTime() - start) / 1_000_000L;
        assertTrue(secondMs > firstMs, "raised level must delay more (first=" + firstMs
                + "ms second=" + secondMs + "ms)");
    }
}
