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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Test
    void maxChannelQueueDepthAggregatesAcrossTmFaces() {
        String gauge = ExerciseSampler.CHANNEL_QUEUE_GAUGE_WIRE_NAME;
        assertEquals("nop_stream_io_channel_queue_size", gauge,
                "wire name derived from the authoritative dot-form gauge name");
        // Empty / null faces → -1 (not sampled).
        assertEquals(-1L, ExerciseSampler.maxChannelQueueDepth(null));
        assertEquals(-1L, ExerciseSampler.maxChannelQueueDepth(Map.of()));

        Map<String, Map<String, Double>> tmMetrics = new java.util.LinkedHashMap<>();
        tmMetrics.put("tm-0", Map.of(
                gauge + "{edgeId=\"a->b\",jobId=\"j\",}", 7.0,
                gauge, 7.0));
        tmMetrics.put("tm-1", Map.of(
                gauge + "{edgeId=\"c->d\",jobId=\"j\",}", 1024.0));
        tmMetrics.put("tm-2", Map.of("nop_stream_task_deployed_total", 3.0));
        // MAX across faces/channels (the worst channel is the backpressure signal).
        assertEquals(1024L, ExerciseSampler.maxChannelQueueDepth(tmMetrics));

        Map<String, Map<String, Double>> facesOnlyOthers = new java.util.LinkedHashMap<>();
        facesOnlyOthers.put("tm-0", Map.of("nop_stream_engine_nodes_active", 2.0));
        assertEquals(-1L, ExerciseSampler.maxChannelQueueDepth(facesOnlyOthers),
                "no gauge entry on any face → -1 (not sampled), not 0");
    }

    @Test
    void samplerRecordsTmFacesAndChannelGaugeDepth() throws Exception {
        Path file = tempDir.resolve("samples").resolve("samples.jsonl");
        java.util.List<Map<String, Double>> faces = new java.util.ArrayList<>();
        faces.add(new java.util.LinkedHashMap<>(Map.of(
                "nop_stream_io_channel_queue_size{edgeId=\"a->b\",jobId=\"j\",}", 5.0)));
        faces.add(new java.util.LinkedHashMap<>(Map.of(
                "nop_stream_io_emit_time_seconds_sum{jobId=\"j\",}", 0.25)));
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
                return 1L;
            }

            @Override
            public long fetchRetainedManifestCount() {
                return 2L;
            }

            @Override
            public long fetchOutputProgress() {
                return 0L;
            }

            @Override
            public Map<String, Boolean> fetchAlive() {
                return Map.of("coordinator-0", true);
            }

            @Override
            public Map<String, Map<String, Double>> fetchTmMetrics() {
                Map<String, Map<String, Double>> snapshot = new java.util.LinkedHashMap<>();
                for (int i = 0; i < faces.size(); i++) {
                    snapshot.put("tm-" + i, faces.get(i));
                }
                return snapshot;
            }
        });
        sampler.start();
        long deadline = System.currentTimeMillis() + 2000L;
        while (System.currentTimeMillis() < deadline && sampler.records().size() < 2) {
            Thread.sleep(50L);
        }
        sampler.close();
        assertTrue(sampler.records().size() >= 2);
        for (ExerciseSampler.SampleRecord record : sampler.records()) {
            assertEquals(5L, record.channelQueueDepth,
                    "channelQueueDepth = max gauge across TM faces (item 32)");
            assertNotNull(record.tmMetrics);
            assertTrue(record.tmMetrics.containsKey("tm-1"));
        }
        for (String line : Files.readAllLines(file)) {
            assertTrue(line.contains("\"channelQueueDepth\":5"), line);
            assertTrue(line.contains("\"nop_stream_io_channel_queue_size"), line);
        }
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
        // Jam signature: tail depth actively growing with a FROZEN epoch — flagged.
        List<ExerciseSampler.SampleRecord> growth = new ArrayList<>();
        growth.add(record(0L, 1L, 10L));
        growth.add(record(1000L, 2L, 20L));
        growth.add(record(2000L, 3L, 60L));
        growth.add(record(3000L, 3L, 500L));
        growth.add(record(4000L, 3L, 510L));
        growth.add(record(5000L, 3L, 520L));
        growth.add(record(6000L, 3L, 530L));
        assertTrue(ExerciseSampler.queueDepthUnboundedGrowth(growth, 100L),
                "growth with no epoch progress across the tail window is the jam/leak signature");

        // Items 28+31 recalibration: growth WITH epoch progress is active traffic
        // on the insert-only JDBC backend (send-side rows are never deleted), not
        // a leak — the healthy SOAK-3 shape (36k rows, epoch advancing every sample).
        List<ExerciseSampler.SampleRecord> healthyGrowth = new ArrayList<>();
        healthyGrowth.add(record(0L, 1L, 10L));
        healthyGrowth.add(record(1000L, 2L, 20L));
        healthyGrowth.add(record(2000L, 3L, 60L));
        healthyGrowth.add(record(3000L, 4L, 500L));
        healthyGrowth.add(record(4000L, 5L, 510L));
        healthyGrowth.add(record(5000L, 6L, 520L));
        healthyGrowth.add(record(6000L, 7L, 530L));
        assertFalse(ExerciseSampler.queueDepthUnboundedGrowth(healthyGrowth, 100L),
                "tail growth with advancing epochs + converging output is healthy emission/"
                        + "control traffic on an insert-only backend, not a leak");

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
