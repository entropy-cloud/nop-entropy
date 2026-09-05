/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.transport;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.stream.core.execution.GraphExecutionPlan;
import io.nop.stream.core.execution.flow.EdgeConfig;
import io.nop.stream.core.execution.flow.FlowControlPolicy;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.jobgraph.ResultPartitionType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AR-12 (plan 2026-09-04-1326-3): in-repo end-to-end proof of data-plane topic
 * legality — no real broker required. A full distributed plan is built through the
 * REAL {@link RemoteGraphExecutionPlanBuilder#buildRemoteOnly} with a hostile job
 * name (CJK) and {@code "A->B"}-form edge keys, over a recording message service
 * wrapped by the REAL {@link DataPlaneMessageServiceAdapter}. Every generated topic
 * (channel subscription) and every derived subscribeName must match the Kafka-legal
 * charset — and the topic set must be non-empty with exactly the remote-edge ×
 * subtask-pair cardinality (guards against a vacuously-true "all topics legal"
 * assertion over an empty set).
 */
class TestRemotePlanTopicLegality {

    private static final String LEGAL = "^[a-zA-Z0-9._-]{1,249}$";

    /** Records every delegate subscribe() call: topic + derived subscribeName. */
    static final class RecordingMessageService implements IMessageService {
        final List<String> subscribedTopics = new CopyOnWriteArrayList<>();
        final List<String> subscribeNames = new CopyOnWriteArrayList<>();

        @Override
        public IMessageSubscription subscribe(String topic, IMessageConsumer listener,
                                              MessageSubscribeOptions options) {
            subscribedTopics.add(topic);
            subscribeNames.add(options != null ? String.valueOf(options.getSubscribeName()) : "null");
            return new IMessageSubscription() {
                @Override
                public void cancel() {
                }

                @Override
                public boolean isCancelled() {
                    return false;
                }

                @Override
                public boolean isSuspended() {
                    return false;
                }

                @Override
                public void suspend() {
                }

                @Override
                public void resume() {
                }
            };
        }

        @Override
        public CompletableFuture<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Graph {@code A(p=2) → B(p=2) → C(p=1)} plus fan-in {@code D(p=1) → B}: remote
     * channel matrix = A→B (2×2) + B→C (2×1) + D→B (1×2) = 8 topics. The job name
     * contains CJK characters — before AR-12 every topic of this plan was
     * Kafka-illegal (CJK + the '>' of the "A->B" edge ids).
     */
    private static JobGraph buildGraph(String jobId) {
        JobGraph graph = new JobGraph(jobId);
        for (String vertex : new String[]{"A", "B", "C", "D"}) {
            int parallelism = "B".equals(vertex) ? 2 : ("A".equals(vertex) ? 2 : 1);
            OperatorChain chain = new OperatorChain(Collections.singletonList(
                    new io.nop.stream.core.operators.StreamMap<>(new IdentityMap<>())));
            graph.addVertex(new JobVertex(vertex, vertex, parallelism,
                    Collections.singletonList(chain),
                    new StreamTaskInvokable(chain)));
        }
        graph.addEdge(new JobEdge("A", "B", ResultPartitionType.PIPELINED));
        graph.addEdge(new JobEdge("B", "C", ResultPartitionType.PIPELINED));
        graph.addEdge(new JobEdge("D", "B", ResultPartitionType.PIPELINED));
        return graph;
    }

    /** Serializable identity function (lambdas would break the deepCopy serialization). */
    public static final class IdentityMap<T> implements io.nop.stream.core.common.functions.MapFunction<T, T> {
        private static final long serialVersionUID = 1L;

        @Override
        public T map(T value) {
            return value;
        }
    }

    /** Deployment plan whose edge configs are keyed by the RAW "A->B" edge-key form. */
    private static DeploymentPlan buildDeploymentPlan(String jobId) {
        Map<String, PartitionedPlan.VertexPlan> vertexPlans = new LinkedHashMap<>();
        vertexPlans.put("A", new PartitionedPlan.VertexPlan("A", 2, null));
        vertexPlans.put("B", new PartitionedPlan.VertexPlan("B", 2, null));
        vertexPlans.put("C", new PartitionedPlan.VertexPlan("C", 1, null));
        vertexPlans.put("D", new PartitionedPlan.VertexPlan("D", 1, null));
        List<PartitionedPlan.EdgePlan> edgePlans = new java.util.ArrayList<>();
        edgePlans.add(new PartitionedPlan.EdgePlan("A", "B", PartitionPolicy.FORWARD));
        edgePlans.add(new PartitionedPlan.EdgePlan("B", "C", PartitionPolicy.FORWARD));
        edgePlans.add(new PartitionedPlan.EdgePlan("D", "B", PartitionPolicy.FORWARD));

        EdgeConfig abConfig = new EdgeConfig(FlowControlPolicy.BLOCKING_QUEUE, 7, 1024, 4096);
        Map<String, EdgeConfig> edgeConfigs = new LinkedHashMap<>();
        edgeConfigs.put("A->B", abConfig);

        return new DeploymentPlan(jobId, "pipeline-0",
                new PartitionedPlan(jobId, "pipeline-0", vertexPlans, edgePlans, null, null),
                "local", "memory", "local", edgeConfigs, null);
    }

    @Test
    void fullPlanTopicsAndSubscribeNamesAreTransportLegal() {
        String jobId = "作业job-一";
        RecordingMessageService recorder = new RecordingMessageService();
        // The REAL adapter: the same wrapper production uses — its derived subscribeName
        // embeds the topic, so subscribeName legality is asserted too.
        DataPlaneMessageServiceAdapter dataPlane =
                new DataPlaneMessageServiceAdapter(recorder, IdentityWireCodec.INSTANCE);

        RemoteGraphExecutionPlanBuilder builder =
                new RemoteGraphExecutionPlanBuilder(dataPlane, null, 1L);
        GraphExecutionPlan plan = builder.buildRemoteOnly(
                buildGraph(jobId), buildDeploymentPlan(jobId), true, null);

        assertTrue(plan != null, "plan must build");

        // Anti-vacuity: the channel matrix is exactly the remote-edge × subtask pairs.
        assertEquals(8, recorder.subscribedTopics.size(),
                "3 remote edges × subtask pairs (A→B 2×2, B→C 2×1, D→B 1×2) = 8 channel "
                        + "subscriptions; got: " + recorder.subscribedTopics);
        assertEquals(8, new HashSet<>(recorder.subscribedTopics).size(),
                "sanitization must not merge distinct channels into one topic");

        for (String topic : recorder.subscribedTopics) {
            assertTrue(topic.matches(LEGAL),
                    "generated topic must be Kafka/Pulsar-legal: '" + topic + "'");
        }
        for (String name : recorder.subscribeNames) {
            assertTrue(name.matches(LEGAL),
                    "DataPlaneMessageServiceAdapter subscribeName must be transport-legal: '" + name + "'");
        }
    }

    @Test
    void edgeConfigLookupUnaffectedByTopicSanitization() {
        // The edge-config map key keeps its RAW "A->B" form; sanitization only applies
        // to the topic produced from the edge — the lookup must still resolve.
        String jobId = "job-edge-config";
        DeploymentPlan deploymentPlan = buildDeploymentPlan(jobId);
        EdgeConfig expected = deploymentPlan.getEdgeConfigs().get("A->B");
        assertTrue(expected != null, "fixture: the raw-form edge key must be present");

        JobEdge edge = new JobEdge("A", "B", ResultPartitionType.PIPELINED);
        assertSame(expected, RemoteGraphExecutionPlanBuilder.resolveEdgeConfig(edge, deploymentPlan),
                "resolveEdgeConfig must keep resolving RAW 'A->B' keys after topic sanitization");
    }
}
