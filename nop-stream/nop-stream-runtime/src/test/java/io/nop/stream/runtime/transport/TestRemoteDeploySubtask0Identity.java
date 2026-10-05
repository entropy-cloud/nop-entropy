/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.transport;

import io.nop.api.core.message.IMessageService;
import io.nop.stream.core.execution.GraphExecutionPlan;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.jobgraph.ResultPartitionType;
import io.nop.stream.core.execution.transport.TypeRegistry;
import io.nop.stream.core.execution.task.Subtask;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.operators.StreamSinkOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.runtime.source.CollectionReplayableSource;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R5 REG-1 regression pin: the remote builder must give taskIndex==0 the SAME
 * per-subtask identity copy as every other subtask. Reusing the original chain
 * for subtask-0 left the 2PC ledger namespace unset there (guard fell back to
 * the table name and silently skipped writes when two jobs shared a database).
 */
class TestRemoteDeploySubtask0Identity {

    @Test
    public void remoteSubtask0GetsPerSubtaskIdentityCopy() {
        String jobId = "reg1-subtask0-" + System.nanoTime();
        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(
                new CollectionReplayableSource<>(Collections.emptyList()));
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(
                new io.nop.stream.core.common.functions.sink.PrintSinkFunction<>());
        OperatorChain sourceChain = new OperatorChain(Collections.singletonList(sourceOp));
        OperatorChain sinkChain = new OperatorChain(Collections.singletonList(sinkOp));
        JobGraph graph = new JobGraph(jobId);
        graph.addVertex(new JobVertex("source", "Source", 2,
                Collections.singletonList(sourceChain), new StreamTaskInvokable(sourceChain)));
        graph.addVertex(new JobVertex("sink", "Sink", 2,
                Collections.singletonList(sinkChain), new StreamTaskInvokable(sinkChain)));
        graph.addEdge(new JobEdge("source", "sink", ResultPartitionType.PIPELINED));

        IMessageService messageService = new IMessageService() {
            final List<Object> sent = new java.util.concurrent.CopyOnWriteArrayList<>();

            @Override
            public io.nop.api.core.message.IMessageSubscription subscribe(String topic,
                    io.nop.api.core.message.IMessageConsumer consumer,
                    io.nop.api.core.message.MessageSubscribeOptions options) {
                return new io.nop.api.core.message.IMessageSubscription() {
                    @Override public void cancel() {}
                    @Override public boolean isSuspended() { return false; }
                    @Override public boolean isCancelled() { return false; }
                    @Override public void suspend() {}
                    @Override public void resume() {}
                };
            }

            @Override
            public java.util.concurrent.CompletionStage<Void> sendAsync(String topic, Object message,
                    io.nop.api.core.message.MessageSendOptions options) {
                sent.add(message);
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }
        };
        GraphExecutionPlan plan = new RemoteGraphExecutionPlanBuilder(
                messageService, new TypeRegistry(), 1L)
                .buildRemoteOnly(graph, null, true, Collections.emptySet());

        for (JobVertex vertex : graph.getVertices().values()) {
            OperatorChain original = vertex.getOperatorChains().get(0);
            List<Subtask> subtasks = plan.getSubtasks(vertex.getId());
            assertEquals(2, subtasks.size());
            for (Subtask subtask : subtasks) {
                assertTrue(subtask.getTaskLocation().getTaskIndex() == 0
                                || subtask.getTaskLocation().getTaskIndex() == 1,
                        "remote subtask carries its deployment taskIndex");
                // R5 REG-1: subtask-0's chain must be a fresh per-subtask copy —
                // NOT the original template chain (identity check).
                assertNotSame(original.getOperators().get(0),
                        subtask.getInvokable().getOperatorChain().getOperators().get(0),
                        "subtask " + subtask.getTaskLocation().getTaskIndex()
                                + " must not run the original template chain (R5 REG-1)");
            }
        }
    }
}
