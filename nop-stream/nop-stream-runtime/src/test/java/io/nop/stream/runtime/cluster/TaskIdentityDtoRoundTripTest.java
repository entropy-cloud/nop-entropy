/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.cluster;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import io.nop.core.lang.json.JsonTool;
import io.nop.stream.runtime.coordinator.TaskStatusReport;
import io.nop.stream.runtime.rpc.TaskDeploymentDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2278 Phase 2: equivalence evidence for the TaskIdentity convergence.
 * TaskAssignment / TaskStatusReport / TaskDeploymentDescriptor now embed the
 * shared {@link TaskIdentity} behind their historical flat getters/setters, so
 * both the Java-serialization round trip and the @DataBean JSON round trip must
 * preserve every property — and the JSON form must stay FLAT (the embedded
 * object must NOT surface as a nested "identity" property).
 */
class TaskIdentityDtoRoundTripTest {

    private static <T> T javaRoundTrip(T dto) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(dto);
        }
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
            @SuppressWarnings("unchecked")
            T copy = (T) ois.readObject();
            return copy;
        }
    }

    private static <T> T jsonRoundTrip(T dto, Class<T> type) {
        String json = JsonTool.serialize(dto, false);
        // Flat shape: every identity property at the top level, no nested bean.
        assertTrue(json.contains("\"jobId\""), "flat JSON must carry jobId: " + json);
        assertTrue(json.contains("\"vertexId\""), "flat JSON must carry vertexId: " + json);
        assertTrue(json.contains("\"subtaskIndex\""), "flat JSON must carry subtaskIndex: " + json);
        assertTrue(json.contains("\"attemptNumber\""), "flat JSON must carry attemptNumber: " + json);
        assertTrue(json.contains("\"fencingEpoch\""), "flat JSON must carry fencingEpoch: " + json);
        assertFalse(json.contains("\"identity\""), "embedded TaskIdentity must not leak as a JSON property: " + json);
        return JsonTool.parseBeanFromText(json, type);
    }

    @Test
    void taskAssignmentSurvivesJavaAndJsonRoundTrips() throws Exception {
        TaskAssignment dto = new TaskAssignment("job-1", "vertex-1", 2, "tm-1",
                "att-1", 42L, 123456789L, 3);

        TaskAssignment javaCopy = javaRoundTrip(dto);
        assertEquals("job-1", javaCopy.getJobId());
        assertEquals("vertex-1", javaCopy.getVertexId());
        assertEquals(2, javaCopy.getSubtaskIndex());
        assertEquals(3, javaCopy.getAttemptNumber());
        assertEquals(42L, javaCopy.getFencingEpoch());
        assertEquals("tm-1", javaCopy.getNodeId());
        assertEquals("att-1", javaCopy.getAttemptId());
        assertEquals(123456789L, javaCopy.getAssignedAt());

        TaskAssignment jsonCopy = jsonRoundTrip(dto, TaskAssignment.class);
        assertEquals("job-1", jsonCopy.getJobId());
        assertEquals("vertex-1", jsonCopy.getVertexId());
        assertEquals(2, jsonCopy.getSubtaskIndex());
        assertEquals(3, jsonCopy.getAttemptNumber());
        assertEquals(42L, jsonCopy.getFencingEpoch());
        assertEquals("tm-1", jsonCopy.getNodeId());
        assertEquals("att-1", jsonCopy.getAttemptId());
        assertEquals(123456789L, jsonCopy.getAssignedAt());
    }

    @Test
    void taskAssignmentLegacyConstructorDefaultsAttemptNumberToOne() {
        TaskAssignment dto = new TaskAssignment("job-1", "vertex-1", 0, "tm-1",
                "att-1", 7L, 99L);
        assertEquals(1, dto.getAttemptNumber());
        assertEquals(7L, dto.getFencingEpoch());
    }

    @Test
    void taskAssignmentSettersWriteThroughEmbeddedIdentity() throws Exception {
        TaskAssignment dto = new TaskAssignment();
        dto.setJobId("job-set");
        dto.setVertexId("vertex-set");
        dto.setSubtaskIndex(5);
        dto.setAttemptNumber(9);
        dto.setFencingEpoch(77L);

        TaskAssignment javaCopy = javaRoundTrip(dto);
        assertEquals("job-set", javaCopy.getJobId());
        assertEquals("vertex-set", javaCopy.getVertexId());
        assertEquals(5, javaCopy.getSubtaskIndex());
        assertEquals(9, javaCopy.getAttemptNumber());
        assertEquals(77L, javaCopy.getFencingEpoch());
    }

    @Test
    void taskStatusReportSurvivesJavaAndJsonRoundTrips() throws Exception {
        TaskStatusReport dto = new TaskStatusReport("job-2", "vertex-2", 4,
                6, TaskStatusReport.TerminalState.FAILED, "boom",
                111L, 88L, 222L);

        TaskStatusReport javaCopy = javaRoundTrip(dto);
        assertEquals("job-2", javaCopy.getJobId());
        assertEquals("vertex-2", javaCopy.getVertexId());
        assertEquals(4, javaCopy.getSubtaskIndex());
        assertEquals(6, javaCopy.getAttemptNumber());
        assertEquals(88L, javaCopy.getFencingEpoch());
        assertEquals(TaskStatusReport.TerminalState.FAILED, javaCopy.getTerminalState());
        assertEquals("boom", javaCopy.getErrorCause());
        assertEquals(111L, javaCopy.getLastProgressTime());
        assertEquals(222L, javaCopy.getReportedAt());

        TaskStatusReport jsonCopy = jsonRoundTrip(dto, TaskStatusReport.class);
        assertEquals("job-2", jsonCopy.getJobId());
        assertEquals("vertex-2", jsonCopy.getVertexId());
        assertEquals(4, jsonCopy.getSubtaskIndex());
        assertEquals(6, jsonCopy.getAttemptNumber());
        assertEquals(88L, jsonCopy.getFencingEpoch());
        assertEquals(TaskStatusReport.TerminalState.FAILED, jsonCopy.getTerminalState());
        assertEquals("boom", jsonCopy.getErrorCause());
        assertEquals(111L, jsonCopy.getLastProgressTime());
        assertEquals(222L, jsonCopy.getReportedAt());
    }

    @Test
    void taskDeploymentDescriptorSurvivesJavaAndJsonRoundTrips() throws Exception {
        TaskDeploymentDescriptor dto = new TaskDeploymentDescriptor("job-3", "vertex-3", 1,
                "tm-3", "att-3", 8, 55L, null, null, "/ckpt");

        TaskDeploymentDescriptor javaCopy = javaRoundTrip(dto);
        assertEquals("job-3", javaCopy.getJobId());
        assertEquals("vertex-3", javaCopy.getVertexId());
        assertEquals(1, javaCopy.getSubtaskIndex());
        assertEquals(8, javaCopy.getAttemptNumber());
        assertEquals(55L, javaCopy.getFencingEpoch());
        assertEquals("tm-3", javaCopy.getNodeId());
        assertEquals("att-3", javaCopy.getAttemptId());
        assertEquals("/ckpt", javaCopy.getCheckpointRestorePath());

        TaskDeploymentDescriptor jsonCopy = jsonRoundTrip(dto, TaskDeploymentDescriptor.class);
        assertEquals("job-3", jsonCopy.getJobId());
        assertEquals("vertex-3", jsonCopy.getVertexId());
        assertEquals(1, jsonCopy.getSubtaskIndex());
        assertEquals(8, jsonCopy.getAttemptNumber());
        assertEquals(55L, jsonCopy.getFencingEpoch());
        assertEquals("tm-3", jsonCopy.getNodeId());
        assertEquals("att-3", jsonCopy.getAttemptId());
        assertEquals("/ckpt", jsonCopy.getCheckpointRestorePath());
    }
}
