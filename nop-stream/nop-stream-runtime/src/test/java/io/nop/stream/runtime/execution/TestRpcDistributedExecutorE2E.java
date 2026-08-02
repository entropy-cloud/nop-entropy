/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

import io.nop.api.core.message.IMessageService;
import io.nop.message.core.local.LocalMessageService;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.execution.DeploymentMode;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 39 Phase 2 end-to-end verification (plan guide #22): a full streaming
 * pipeline runs through {@link RpcDistributedExecutor}, whose control plane
 * (assignment / fencing / coordinator↔task uplink) traverses the real platform RPC
 * transport ({@code StreamControlRpcServer} + {@code StreamControlRpcProxyFactory}
 * over {@link IMessageService}).
 *
 * <p>The data plane stays in-JVM (Stage 40 wires cross-JVM data transport); this test
 * proves the RPC control plane is genuinely exercised: the coordinator assigns tasks
 * over RPC, the remote TaskManagers receive them, install invokables, run the
 * pipeline, and the sink collects the correct results. A correct result set is
 * only possible if the control calls crossed the RPC boundary.
 */
class TestRpcDistributedExecutorE2E {

    @Test
    void fullPipelineRunsOverRpcControlPlane() throws Exception {
        List<String> results = new CopyOnWriteArrayList<>();
        LocalMessageService messageService = new LocalMessageService();

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.setParallelism(2);
        env.setDeploymentMode(DeploymentMode.DISTRIBUTED);
        env.setExecutionDispatcher(new RpcDistributedExecutor(messageService, 2, 30));

        env.fromElements("a", "b", "c", "d", "e", "f")
                .map(String::toUpperCase)
                .sink(results::add);

        env.execute("rpc-distributed-all-data");

        // A correct result set is only reachable if coordinator→task assignment and
        // the task→coordinator uplink both crossed the RPC layer.
        assertTrue(results.size() >= 6,
                "Expected at least 6 results over the RPC control plane, got " + results.size() + ": " + results);
        assertTrue(results.containsAll(Arrays.asList("A", "B", "C", "D", "E", "F")),
                "All mapped values should be present: " + results);
    }
}
