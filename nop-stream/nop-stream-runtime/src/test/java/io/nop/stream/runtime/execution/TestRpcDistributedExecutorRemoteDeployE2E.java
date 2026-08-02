/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
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
 * Stage 42 Phase 0 end-to-end verification (plan guide #22 — anti-hollow): runs
 * a full streaming pipeline through {@link RpcDistributedExecutor} with
 * {@code remoteDeployMode=true}. In this mode the coordinator's
 * {@code assignTasks()} calls {@code deployTask} RPC for each subtask (not
 * {@code receiveAssignment} + direct {@code installInvokable}); each TaskManager
 * rebuilds its own invokable locally via {@link io.nop.stream.runtime.transport.SubtaskPlanBuilder}.
 *
 * <p>A correct sink result is only reachable if:
 * <ol>
 *   <li>{@link io.nop.stream.runtime.coordinator.JobCoordinator#assignTasks()}
 *       actually built a {@link io.nop.stream.runtime.rpc.TaskDeploymentDescriptor}
 *       per subtask and sent it via the {@code deployTask} RPC;</li>
 *   <li>each TaskManager received the descriptor, rebuilt its invokable locally
 *       from the {@link io.nop.stream.core.jobgraph.JobGraph} + edge config, and
 *       started running it;</li>
 *   <li>the rebuilt invokables connected to the same data-plane topics the
 *       coordinator would have wired (deterministic topic naming) so records
 *       actually flow across the RPC-reached task nodes.</li>
 * </ol>
 *
 * <p>This is the in-process analog of the Stage 42 multi-JVM test (Phase 3): the
 * control plane traverses real RPC; the deployTask path is genuinely exercised.
 */
class TestRpcDistributedExecutorRemoteDeployE2E {

    @Test
    void fullPipelineRunsViaDeployTaskRpc() throws Exception {
        List<String> results = new CopyOnWriteArrayList<>();
        LocalMessageService messageService = new LocalMessageService();

        RpcDistributedExecutor dispatcher = new RpcDistributedExecutor(messageService, 2, 30);
        dispatcher.setRemoteDeployMode(true);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.setParallelism(2);
        env.setDeploymentMode(DeploymentMode.DISTRIBUTED);
        env.setExecutionDispatcher(dispatcher);

        env.fromElements("a", "b", "c", "d", "e", "f")
                .map(String::toUpperCase)
                .sink(results::add);

        env.execute("rpc-distributed-remote-deploy");

        // A correct result set is only reachable if deployTask actually built
        // and ran the invokables on the (in-process RPC-reached) task nodes.
        assertTrue(results.size() >= 6,
                "Expected at least 6 results via deployTask RPC, got " + results.size() + ": " + results);
        assertTrue(results.containsAll(Arrays.asList("A", "B", "C", "D", "E", "F")),
                "All mapped values should be present via deployTask: " + results);
    }
}
