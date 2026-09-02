/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.scenario;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 15 (Phase 2 device verification): the sampler's Prometheus parsing, JSONL
 * persistence, series analysis (stall / leak signals) and fail-fast validation —
 * no cluster involved.
 */
class TestExerciseSampler {

    @TempDir
    Path tempDir;

    @Test
    void parsesPrometheusTextWithTagsCommentsAndTotalAlias() {
        String text = String.join("\n",
                "# HELP nop_stream_engine_nodes_active active nodes",
                "# TYPE nop_stream_engine_nodes_active gauge",
                "nop_stream_engine_nodes_active 2.0",
                "nop_stream_engine_checkpoints_completed_total{jobId=\"job-x\",} 42.0",
                "nop_stream_engine_checkpoint_duration_count{jobId=\"job-x\",} 40.0",
                "jvm_uptime_seconds 123.5");
        Map<String, Double> values = ExerciseSampler.parsePrometheusText(text);
        assertEquals(2.0, values.get("nop_stream_engine_nodes_active"));
        assertEquals(42.0, values.get("nop_stream_engine_checkpoints_completed_total{jobId=\"job-x\",}"));
        assertEquals(42.0, values.get("nop_stream_engine_checkpoints_completed"));
        assertEquals(40.0, values.get("nop_stream_engine_checkpoint_duration_count"));
        assertEquals(123.5, values.get("jvm_uptime_seconds"));
    }

    @Test
    void malformedPrometheusLineFailsFast() {
        assertThrows(IllegalStateException.class,
                () -> ExerciseSampler.parsePrometheusText("this-is-not-a-metric-line"));
        assertThrows(IllegalStateException.class,
                () -> ExerciseSampler.parsePrometheusText("metric_name not-a-number"));
    }

    @Test
    void samplerWritesJsonlAndKeepsRecords() throws Exception {
        Path file = tempDir.resolve("samples").resolve("samples.jsonl");
        long[] epoch = {1L};
        long[] progress = {0L};
        ExerciseSampler sampler = new ExerciseSampler(file, 100L, new ExerciseSampler.SampleSources() {
            @Override
            public Map<String, Double> fetchMetrics() {
                return Map.of("m", 1.0);
            }

            @Override
            public long fetchQueueDepth() {
                return 3L;
            }

            @Override
            public long fetchDurableEpoch() {
                return epoch[0];
            }

            @Override
            public long fetchRetainedManifestCount() {
                return 2L;
            }

            @Override
            public long fetchOutputProgress() {
                return progress[0];
            }

            @Override
            public Map<String, Boolean> fetchAlive() {
                return Map.of("coordinator-0", true);
            }
        });
        sampler.start();
        long deadline = System.currentTimeMillis() + 2000L;
        while (System.currentTimeMillis() < deadline && sampler.records().size() < 3) {
            Thread.sleep(50L);
            if (sampler.records().size() == 2) {
                epoch[0] = 2L;
                progress[0] = 5L;
            }
        }
        sampler.close();
        assertTrue(sampler.records().size() >= 3, "collected " + sampler.records().size() + " records");
        assertTrue(Files.exists(file));
        List<String> lines = Files.readAllLines(file);
        assertEquals(sampler.records().size(), lines.size());
        for (String line : lines) {
            assertTrue(line.contains("\"wallMs\""));
            assertTrue(line.contains("\"durableEpoch\""));
        }
        assertFalse(sampler.failed());
    }

    @Test
    void samplerValidatesIntervalFailFast() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExerciseSampler(tempDir.resolve("x.jsonl"), 10L, null));
    }

    private static ExerciseSampler.SampleRecord record(long wallMs, long epoch, long queueDepth) {
        ExerciseSampler.SampleRecord r = new ExerciseSampler.SampleRecord();
        r.wallMs = wallMs;
        r.durableEpoch = epoch;
        r.queueDepth = queueDepth;
        return r;
    }

    @Test
    void maxEpochAdvanceGapExcludesTrailingIdleSegment() {
        List<ExerciseSampler.SampleRecord> records = new ArrayList<>();
        records.add(record(0L, 1L, 0L));
        records.add(record(5000L, 1L, 0L));
        records.add(record(7000L, 2L, 0L));
        records.add(record(9000L, 2L, 0L));
        records.add(record(60_000L, 2L, 0L));
        assertEquals(7000L, ExerciseSampler.maxEpochAdvanceGapMs(records));
        assertEquals(2L, ExerciseSampler.epochAdvanceCount(records));
    }

    @Test
    void queueDepthUnboundedGrowthDetectsActiveEndOfRunAccumulation() {
        List<ExerciseSampler.SampleRecord> growth = new ArrayList<>();
        growth.add(record(0L, 1L, 10L));
        growth.add(record(1000L, 2L, 20L));
        growth.add(record(2000L, 3L, 60L));
        growth.add(record(3000L, 4L, 500L));
        growth.add(record(4000L, 5L, 510L));
        growth.add(record(5000L, 6L, 520L));
        growth.add(record(6000L, 7L, 530L));
        assertTrue(ExerciseSampler.queueDepthUnboundedGrowth(growth, 100L));

        List<ExerciseSampler.SampleRecord> plateau = new ArrayList<>();
        plateau.add(record(0L, 1L, 10L));
        plateau.add(record(1000L, 2L, 500L));
        plateau.add(record(2000L, 3L, 1300L));
        plateau.add(record(3000L, 4L, 1300L));
        plateau.add(record(4000L, 5L, 1301L));
        plateau.add(record(5000L, 6L, 1301L));
        assertFalse(ExerciseSampler.queueDepthUnboundedGrowth(plateau, 100L),
                "converged run with bounded residual plateau is recorded, not flagged");

        List<ExerciseSampler.SampleRecord> draining = new ArrayList<>();
        draining.add(record(0L, 1L, 10L));
        draining.add(record(1000L, 2L, 30L));
        draining.add(record(2000L, 3L, 5L));
        draining.add(record(3000L, 4L, 20L));
        assertFalse(ExerciseSampler.queueDepthUnboundedGrowth(draining, 100L));

        List<ExerciseSampler.SampleRecord> bounded = new ArrayList<>();
        bounded.add(record(0L, 1L, 10L));
        bounded.add(record(1000L, 2L, 20L));
        bounded.add(record(2000L, 3L, 40L));
        assertFalse(ExerciseSampler.queueDepthUnboundedGrowth(bounded, 100L),
                "bounded under threshold is not a leak signal");
    }
}
