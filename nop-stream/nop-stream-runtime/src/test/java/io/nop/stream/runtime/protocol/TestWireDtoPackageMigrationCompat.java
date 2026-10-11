/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.runtime.protocol;

import io.nop.core.lang.json.JsonTool;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * WI6 plan 2416 regression pin for the wire-type package migration
 * (rpc/taskmanager/coordinator → io.nop.stream.runtime.protocol).
 *
 * <p>Pins the two cross-JVM encoding paths of the moved types:
 * (a) Java serialization — PollingJdbcMessageService encodes queue rows this way;
 * the stream embeds the class FQN, so this round-trip guards the same-version
 * compatibility of the migrated classes (serialVersionUIDs preserved);
 * (b) nop JSON bean encoding — the @DataBean field-based shape used by JSON
 * wire paths; field names must be unchanged by the migration.
 *
 * <p>Cross-version queue rows written by pre-migration builds are out of scope
 * (watch-only residual, recorded in the WI6 plan): Java serialization would
 * reject the old FQN, and PollingJdbcMessageService already skips such rows.
 */
class TestWireDtoPackageMigrationCompat {

    private static Object javaRoundTrip(Object obj) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(obj);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
            return in.readObject();
        }
    }

    private static Object jsonRoundTrip(Object obj) {
        String text = JsonTool.stringify(obj);
        return JsonTool.parseBeanFromText(text, obj.getClass());
    }

    @Test
    void testJavaSerializationRoundTripAfterMigration() throws Exception {
        CheckpointAckMessage ack = new CheckpointAckMessage();
        ack.setCheckpointId(7L);
        CheckpointAckMessage ack2 = (CheckpointAckMessage) javaRoundTrip(ack);
        assertEquals(7L, ack2.getCheckpointId(), "same-version Java-serialization round-trip must be stable");

        TaskDeploymentDescriptor descriptor = new TaskDeploymentDescriptor();
        descriptor.setNodeId("node-1");
        TaskDeploymentDescriptor descriptor2 = (TaskDeploymentDescriptor) javaRoundTrip(descriptor);
        assertEquals("node-1", descriptor2.getNodeId());

        TaskProgress progress = new TaskProgress();
        progress.setVertexId("vertex-1");
        TaskProgress progress2 = (TaskProgress) javaRoundTrip(progress);
        assertEquals("vertex-1", progress2.getVertexId());

        TaskStatusReport report = new TaskStatusReport();
        report.setErrorCause("boom");
        TaskStatusReport report2 = (TaskStatusReport) javaRoundTrip(report);
        assertEquals("boom", report2.getErrorCause());

        JobStatusResponse response = new JobStatusResponse(JobStatus.FAILED, "cause");
        JobStatusResponse response2 = (JobStatusResponse) javaRoundTrip(response);
        assertSame(JobStatus.FAILED, response2.getJobStatus());
    }

    @Test
    void testJsonBeanRoundTripKeepsFieldShape() {
        CheckpointAckMessage ack = new CheckpointAckMessage();
        ack.setCheckpointId(9L);
        CheckpointAckMessage ack2 = (CheckpointAckMessage) jsonRoundTrip(ack);
        assertEquals(9L, ack2.getCheckpointId(), "@DataBean field encoding must be unchanged by the package move");

        TaskProgress progress = new TaskProgress();
        progress.setVertexId("vertex-2");
        TaskProgress progress2 = (TaskProgress) jsonRoundTrip(progress);
        assertEquals("vertex-2", progress2.getVertexId());

        JobStatusResponse response = new JobStatusResponse(JobStatus.RUNNING, null);
        JobStatusResponse response2 = (JobStatusResponse) jsonRoundTrip(response);
        assertSame(JobStatus.RUNNING, response2.getJobStatus());
    }

    @Test
    void testJobStatusEnumSurvivesMigration() {
        // JobStatus moved together with JobStatusResponse (its same-package field type).
        for (JobStatus status : JobStatus.values()) {
            assertSame(status, jsonRoundTrip(status));
        }
    }
}
