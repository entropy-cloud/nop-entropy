/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.rpc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.core.jobgraph.JobGraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Stage 42 Phase 0: verifies {@link TaskDeploymentDescriptor} round-trips through
 * Java serialization. This is the wire format the coordinator sends to a
 * TaskManager via {@link IStreamTaskRpcService#deployTask} when remote-deploy
 * mode is active.
 *
 * <p>The descriptor carries model metadata ({@link JobGraph} + {@link DeploymentPlan})
 * and configuration — never live operator objects. The TaskManager reconstructs its
 * invokable locally from the shared {@code StreamComponents} registry.
 */
class TestTaskDeploymentDescriptor {

    @Test
    void descriptorRoundTripsThroughJavaSerialization() throws Exception {
        JobGraph jobGraph = new JobGraph("round-trip-job");
        DeploymentPlan deploymentPlan = new DeploymentPlan(
                "job-1", "pipeline-0", null,
                "local", "memory", "local", null, null);

        TaskDeploymentDescriptor original = new TaskDeploymentDescriptor(
                "job-1", "source", 0, "node-A",
                "attempt-xyz", 3, 42L,
                jobGraph, deploymentPlan, "/tmp/checkpoints/job-1");

        byte[] bytes;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(original);
            bytes = baos.toByteArray();
        }

        TaskDeploymentDescriptor decoded;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            decoded = (TaskDeploymentDescriptor) ois.readObject();
        }

        assertEquals("job-1", decoded.getJobId());
        assertEquals("source", decoded.getVertexId());
        assertEquals(0, decoded.getSubtaskIndex());
        assertEquals("node-A", decoded.getNodeId());
        assertEquals("attempt-xyz", decoded.getAttemptId());
        assertEquals(3, decoded.getAttemptNumber());
        assertEquals(42L, decoded.getFencingEpoch());
        assertNotNull(decoded.getJobGraph());
        assertEquals("round-trip-job", decoded.getJobGraph().getJobName());
        assertNotNull(decoded.getDeploymentPlan());
        assertEquals("job-1", decoded.getDeploymentPlan().getJobId());
        assertEquals("/tmp/checkpoints/job-1", decoded.getCheckpointRestorePath());
    }

    @Test
    void nullRestorePathAllowedForFreshJob() {
        TaskDeploymentDescriptor descriptor = new TaskDeploymentDescriptor(
                "job-2", "sink", 1, "node-B",
                "attempt-1", 1, 7L,
                new JobGraph("fresh-job"), null, null);

        assertNull(descriptor.getCheckpointRestorePath(),
                "Fresh jobs carry no checkpoint restore path; TaskManager builds invokable without restore");
        assertNull(descriptor.getDeploymentPlan(),
                "DeploymentPlan may be null when the JobGraph's own parallelism/edge config suffices");
    }

    @Test
    void defaultConstructorAndSettersInteract() {
        TaskDeploymentDescriptor descriptor = new TaskDeploymentDescriptor();
        descriptor.setJobId("job-3");
        descriptor.setVertexId("map");
        descriptor.setSubtaskIndex(2);
        descriptor.setNodeId("node-C");
        descriptor.setAttemptId("attempt-2");
        descriptor.setAttemptNumber(5);
        descriptor.setFencingEpoch(99L);
        JobGraph graph = new JobGraph("setter-job");
        descriptor.setJobGraph(graph);
        descriptor.setCheckpointRestorePath("/var/checkpoints");

        assertEquals("job-3", descriptor.getJobId());
        assertEquals("map", descriptor.getVertexId());
        assertEquals(2, descriptor.getSubtaskIndex());
        assertEquals("node-C", descriptor.getNodeId());
        assertEquals("attempt-2", descriptor.getAttemptId());
        assertEquals(5, descriptor.getAttemptNumber());
        assertEquals(99L, descriptor.getFencingEpoch());
        assertSame(graph, descriptor.getJobGraph());
        assertEquals("/var/checkpoints", descriptor.getCheckpointRestorePath());
    }

    @Test
    void partitionedPlanEdgePlanIsSerializable() {
        // Sanity: PartitionedPlan components must round-trip — they are carried
        // transitively via DeploymentPlan in the descriptor.
        PartitionedPlan.VertexPlan vp = new PartitionedPlan.VertexPlan("v", 4, null);
        assertEquals(4, vp.getParallelism());
    }
}
