/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import io.nop.message.core.local.LocalMessageService;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.plan.DeploymentAssignment;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.core.jobgraph.JobGraph;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R-19 (runtime audit 2026-09-01): a failure during {@code RpcDistributedExecutor.startJob}
 * AFTER the TaskManagers/RPC servers were started must tear the partially-started
 * topology down. Before the fix there was no try/catch: a failure in coordinator
 * construction, the data-plane plan build, or the initial assignment leaked every
 * already-started TaskManager (heartbeat scheduler threads, registry entries) and
 * RPC server/proxy subscription.
 *
 * <p>The failure is injected deterministically via a materialized
 * {@link DeploymentAssignment} that has no node mapping for the plan's only
 * vertex — {@code assignTasks()} then fails fast ("DeploymentAssignment has no
 * node mapping"), which is the last step of startJob, i.e. AFTER everything
 * else (TaskManagers, both RPC server sides, coordinator) has started.
 */
class TestRpcDistributedExecutorFailureTeardown {

    @Test
    void startJobFailureTearsDownPartiallyStartedTopology() throws Exception {
        LocalMessageService messageService = new LocalMessageService();
        RpcDistributedExecutor executor = new RpcDistributedExecutor(messageService, 2, 30);

        JobGraph emptyGraph = new JobGraph("teardown-job");

        Map<String, PartitionedPlan.VertexPlan> vertexPlans = new LinkedHashMap<>();
        vertexPlans.put("source", new PartitionedPlan.VertexPlan("source", 1, null));
        PartitionedPlan partitionedPlan = new PartitionedPlan(
                "teardown-job", "pipeline-0", vertexPlans, Collections.emptyList(), null, null);

        // Materialized assignment that is non-empty but has no mapping for
        // "source" -> assignTasks throws deterministically after the full
        // topology has started.
        Map<String, java.util.List<String>> incomplete = new LinkedHashMap<>();
        incomplete.put("some-other-vertex", Collections.singletonList("node-0"));
        DeploymentAssignment missingMapping = new DeploymentAssignment(incomplete);
        DeploymentPlan deploymentPlan = new DeploymentPlan(
                "teardown-job", "pipeline-0", partitionedPlan,
                "local", "memory", "local", null, null, missingMapping);

        assertThrows(StreamException.class,
                () -> executor.startJob(emptyGraph, partitionedPlan, deploymentPlan),
                "assignTasks must fail fast on the incomplete materialized assignment");

        // The partially-started TaskManagers must be torn down: their heartbeat
        // scheduler threads ("tm-heartbeat-node-*") must be gone. Before R-19
        // they stayed alive for the lifetime of the JVM (leak).
        boolean heartbeatGone = false;
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            boolean anyHeartbeat = Thread.getAllStackTraces().keySet().stream()
                    .anyMatch(t -> t.isAlive() && t.getName().startsWith("tm-heartbeat-node-"));
            if (!anyHeartbeat) {
                heartbeatGone = true;
                break;
            }
            Thread.sleep(100);
        }
        assertTrue(heartbeatGone,
                "startJob failure must stop the TaskManager heartbeat threads (leaked before R-19)");

        // And a fresh startJob on the same message service is not wedged by the
        // residue of the failed attempt (subscriptions/proxies were cleaned up).
        Map<String, java.util.List<String>> complete = new LinkedHashMap<>();
        complete.put("source", Collections.singletonList("node-0"));
        DeploymentPlan goodPlan = new DeploymentPlan(
                "teardown-job-2", "pipeline-0", partitionedPlan,
                "local", "memory", "local", null, null, new DeploymentAssignment(complete));
        JobGraph graph2 = new JobGraph("teardown-job-2");
        RpcDistributedExecutor.DistributedJobHandle handle =
                executor.startJob(graph2, partitionedPlan, goodPlan);
        try {
            assertFalse(handle.getCoordinator().getTaskAssignments().isEmpty(),
                    "a retry after the teardown must be able to assign tasks");
        } finally {
            handle.close();
        }

        boolean anyLeft = Thread.getAllStackTraces().keySet().stream()
                .anyMatch(t -> t.isAlive() && t.getName().startsWith("tm-heartbeat-node-"));
        assertFalse(anyLeft, "handle.close() must stop the retried topology as well");
    }
}
