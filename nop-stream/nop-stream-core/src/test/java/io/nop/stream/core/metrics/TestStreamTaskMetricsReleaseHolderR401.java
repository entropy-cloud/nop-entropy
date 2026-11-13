/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.core.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.41 R4-01 red⑧ pin (finding 1.1-C7-007 / C10-023; plan 2534 ruling E.2):
 * {@code releaseHolder} must RELEASE the whole meter surface — every meter id
 * removed from the registry (sequence disappears, aligned with the
 * {@code EngineMetrics.releaseJob} family) and the static holder entry dropped —
 * not merely CAS the holder reference to null. Pre-fix the meters stayed
 * registered forever (jobId churn grew ~11 meters per job) and the holder map
 * never shrank: this pin's post-release lookups returned live meters (red).
 *
 * <p>Generation-safe rebuild (review R1 Major-1): a task rebuild on the same key
 * reuses the SAME holder AtomicReference, so a stale instance's release must not
 * drop a newer generation's entry — the stale release is a no-op and the new
 * generation keeps serving its gauges.
 */
class TestStreamTaskMetricsReleaseHolderR401 {

    private static final String[] ALL_METER_NAMES = {
            MicrometerStreamTaskMetrics.METRIC_OPERATOR_RECORDS_IN,
            MicrometerStreamTaskMetrics.METRIC_OPERATOR_RECORDS_OUT,
            MicrometerStreamTaskMetrics.METRIC_IO_RECORDS_CONSUMED,
            MicrometerStreamTaskMetrics.METRIC_IO_RECORDS_EMITTED,
            MicrometerStreamTaskMetrics.METRIC_OPERATOR_PROCESSING_TIME,
            MicrometerStreamTaskMetrics.METRIC_IO_EMIT_TIME,
            MicrometerStreamTaskMetrics.METRIC_OPERATOR_TIME_IDLE_NANOS,
            MicrometerStreamTaskMetrics.METRIC_OPERATOR_TIME_ELAPSED_NANOS,
            MicrometerStreamTaskMetrics.METRIC_OPERATOR_TIME_BUSY_RATIO,
            MicrometerStreamTaskMetrics.METRIC_OPERATOR_TIME_BACKPRESSURED_RATIO,
            MicrometerStreamTaskMetrics.METRIC_OPERATOR_TIME_IDLE_RATIO,
    };

    private static io.micrometer.core.instrument.Meter find(SimpleMeterRegistry registry,
                                                            String name, String jobId, String vertexId, String subtask) {
        return registry.find(name)
                .tags(MicrometerStreamTaskMetrics.TAG_JOB_ID, jobId,
                        MicrometerStreamTaskMetrics.TAG_VERTEX_ID, vertexId,
                        MicrometerStreamTaskMetrics.TAG_SUBTASK, subtask)
                .meter();
    }

    @Test
    void releaseRemovesEveryMeterAndTheHolderEntry() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerStreamTaskMetrics metrics = new MicrometerStreamTaskMetrics(registry, "job-r8", "v1", 0);

        // pre-condition: the constructor registered the base meters + ratio gauges
        for (String name : ALL_METER_NAMES) {
            assertNotNull(find(registry, name, "job-r8", "v1", "0"),
                    "precondition: meter must be registered: " + name);
        }
        assertTrue(MicrometerStreamTaskMetrics.TASK_HOLDERS.containsKey("job-r8|v1|0"),
                "precondition: the static holder must track the live task");

        metrics.bindWatermarkGauges(null); // registration-only bind (no gate: NaN reads)
        assertNotNull(find(registry, MicrometerStreamTaskMetrics.METRIC_OPERATOR_WATERMARK, "job-r8", "v1", "0"),
                "precondition: watermark gauge must be registered after bind");
        assertNotNull(find(registry, MicrometerStreamTaskMetrics.METRIC_OPERATOR_WATERMARK_CHANNEL_LAG,
                "job-r8", "v1", "0"), "precondition: channel-lag gauge must be registered after bind");

        metrics.releaseHolder();

        for (String name : ALL_METER_NAMES) {
            assertNull(find(registry, name, "job-r8", "v1", "0"),
                    "release must remove the meter from the registry: " + name);
        }
        assertNull(find(registry, MicrometerStreamTaskMetrics.METRIC_OPERATOR_WATERMARK, "job-r8", "v1", "0"),
                "release must remove the watermark gauge");
        assertNull(find(registry, MicrometerStreamTaskMetrics.METRIC_OPERATOR_WATERMARK_CHANNEL_LAG, "job-r8", "v1", "0"),
                "release must remove the channel-lag gauge");
        assertFalse(MicrometerStreamTaskMetrics.TASK_HOLDERS.containsKey("job-r8|v1|0"),
                "release must drop the static holder entry (not just null its reference)");
    }

    @Test
    void releaseAfterRebuildKeepsNewGenerationIntact() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerStreamTaskMetrics first = new MicrometerStreamTaskMetrics(registry, "job-gen", "v", 1);
        MicrometerStreamTaskMetrics rebuilt = new MicrometerStreamTaskMetrics(registry, "job-gen", "v", 1);

        java.util.concurrent.atomic.AtomicReference<MicrometerStreamTaskMetrics> holder =
                MicrometerStreamTaskMetrics.TASK_HOLDERS.get("job-gen|v|1");
        assertNotNull(holder, "precondition: holder entry exists");
        assertSame(rebuilt, holder.get(), "precondition: the rebuild owns the holder reference");

        // the STALE generation releases after the rebuild — the holder entry and the
        // new generation's meters must survive untouched
        first.releaseHolder();

        holder = MicrometerStreamTaskMetrics.TASK_HOLDERS.get("job-gen|v|1");
        assertNotNull(holder, "stale release must not drop the newer generation's holder entry");
        assertSame(rebuilt, holder.get(), "stale release must not clear the newer generation's reference");
        assertNotNull(find(registry, MicrometerStreamTaskMetrics.METRIC_OPERATOR_TIME_BUSY_RATIO, "job-gen", "v", "1"),
                "stale release must not remove the newer generation's gauges");

        // the live generation releases — full removal
        rebuilt.releaseHolder();
        assertFalse(MicrometerStreamTaskMetrics.TASK_HOLDERS.containsKey("job-gen|v|1"),
                "live-generation release must drop the holder entry");
        for (String name : ALL_METER_NAMES) {
            assertNull(find(registry, name, "job-gen", "v", "1"),
                    "live-generation release must remove the meter: " + name);
        }
    }

    @Test
    void rebuildAfterReleaseReRegistersFreshMeters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerStreamTaskMetrics first = new MicrometerStreamTaskMetrics(registry, "job-re", "v", 2);
        first.releaseHolder();
        assertFalse(MicrometerStreamTaskMetrics.TASK_HOLDERS.containsKey("job-re|v|2"));

        // single-jobId reuse: the next construction naturally rebuilds (ruling E.2)
        MicrometerStreamTaskMetrics second = new MicrometerStreamTaskMetrics(registry, "job-re", "v", 2);
        assertNotNull(find(registry, MicrometerStreamTaskMetrics.METRIC_OPERATOR_RECORDS_IN, "job-re", "v", "2"),
                "rebuild must re-register the meters");
        assertTrue(MicrometerStreamTaskMetrics.TASK_HOLDERS.containsKey("job-re|v|2"),
                "rebuild must re-register the holder entry");
        second.recordsIn(5);
        assertEquals(5.0, registry.find(MicrometerStreamTaskMetrics.METRIC_OPERATOR_RECORDS_IN)
                .tags(MicrometerStreamTaskMetrics.TAG_JOB_ID, "job-re",
                        MicrometerStreamTaskMetrics.TAG_VERTEX_ID, "v",
                        MicrometerStreamTaskMetrics.TAG_SUBTASK, "2")
                .counter().count(), "the rebuilt counter must count");
    }
}
