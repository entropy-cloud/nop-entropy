/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.transport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.message.IMessageService;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.execution.EdgeAssembly;
import io.nop.stream.core.execution.GraphExecutionPlan;
import io.nop.stream.core.execution.InputChannel;
import io.nop.stream.core.execution.InputGate;
import io.nop.stream.core.execution.RecordWriter;
import io.nop.stream.core.execution.ResultPartition;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.execution.task.Subtask;
import io.nop.stream.core.execution.flow.EdgeConfig;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.core.execution.transport.TypeRegistry;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;

/**
 * Builds a {@link GraphExecutionPlan} that uses {@link IMessageService} for
 * cross-TaskManager data exchange.
 *
 * <p>When both source and target subtasks are on the same TaskManager, local
 * {@link ResultPartition} instances are used (same as {@link GraphExecutionPlan#build}).
 * When they are on different TaskManagers, {@link RemoteResultPartition} and
 * {@link RemoteInputChannel} are used instead.
 *
 * <p>For the in-process simulation (all tasks on the same TaskManager but using
 * IMessageService for transport), use {@link #buildRemoteOnly} which forces all
 * edges to use the remote transport.
 */
public class RemoteGraphExecutionPlanBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteGraphExecutionPlanBuilder.class);

    /** Items 28+31 (D1): consumer-channel queue capacity (unchanged default). */
    private static final int DEFAULT_CHANNEL_QUEUE_CAPACITY = 1024;

    private final IMessageService messageService;
    private final TypeRegistry typeRegistry;
    private final long epochId;

    public RemoteGraphExecutionPlanBuilder(IMessageService messageService,
                                           TypeRegistry typeRegistry,
                                           long epochId) {
        this.messageService = messageService;
        this.typeRegistry = typeRegistry;
        this.epochId = epochId;
    }

    /**
     * Builds an execution plan where ALL edges use IMessageService transport.
     * This is useful for in-process testing with LocalMessageService.
     *
     * <p>Items 28+31 (D1): full-subscription form — every consumer channel
     * subscribes at construction. This is the semantics of the single-JVM
     * executors (Embedded / Rpc remoteDeployMode=false), where all subtasks
     * run in one JVM and the full channel matrix is consumed.
     *
     * @param jobGraph         the job graph
     * @param deploymentPlan   optional deployment plan
     * @param barrierAlignment barrier alignment mode
     * @return the execution plan
     */
    public GraphExecutionPlan buildRemoteOnly(JobGraph jobGraph,
                                              DeploymentPlan deploymentPlan,
                                              boolean barrierAlignment) {
        return buildRemoteOnly(jobGraph, deploymentPlan, barrierAlignment, null);
    }

    /**
     * Items 28+31 (D1): subscription-scope form of {@link #buildRemoteOnly}.
     *
     * <p>The FULL plan structure is always built (all producer partitions for
     * the whole target matrix, all consumer channels for every subtask) — the
     * scope only decides which consumer channels ACTIVATE their subscription:
     * <ul>
     *   <li>{@code subscribedTargetKeys == null} — full subscription (every
     *       channel subscribes; single-JVM executor semantics);</li>
     *   <li>non-empty set of {@code "vertexId/subtaskIndex"} keys — only
     *       channels whose TARGET subtask is in the set subscribe. Used by the
     *       TM remote-deploy path: a TaskManager deploying one subtask
     *       subscribes exactly that subtask's input topics (unsubscribed
     *       channels exist only to mirror the plan structure — subscription
     *       accumulation across consecutive deployments is a natural union);</li>
     *   <li>empty set — zero subscription (all channels construct-only). Used
     *       by the remoteDeployMode=true coordinator, which runs zero
     *       subtasks.</li>
     * </ul>
     *
     * <p>Producer fan-out is never affected: the send side does not subscribe.
     * Topic naming stays deterministic (StreamTopicNaming) so different JVMs
     * still converge on identical topics.
     *
     * @param jobGraph            the job graph
     * @param deploymentPlan      optional deployment plan
     * @param barrierAlignment    barrier alignment mode
     * @param subscribedTargetKeys target-subtask keys ("vertexId/subtaskIndex")
     *                            whose input channels subscribe; null = all
     * @return the execution plan (full structure regardless of scope)
     */
    public GraphExecutionPlan buildRemoteOnly(JobGraph jobGraph,
                                              DeploymentPlan deploymentPlan,
                                              boolean barrierAlignment,
                                              java.util.Set<String> subscribedTargetKeys) {
        // --- 1. Build adjacency maps ---
        EdgeAdjacency adjacency = buildEdgeAdjacency(jobGraph);

        // --- 2. Resolve parallelism ---
        Map<String, Integer> parallelismMap = resolveParallelism(jobGraph, deploymentPlan);

        // --- 3. Allocate partition matrix per edge ---
        EdgeTransport transport = allocateEdgePartitions(jobGraph, parallelismMap, subscribedTargetKeys);

        // --- 4. Build subtasks ---
        SubtaskAssembly assembly = assembleSubtasks(jobGraph, parallelismMap, adjacency,
                transport, deploymentPlan, barrierAlignment);

        List<String> sorted = topologicalSort(jobGraph);

        return GraphExecutionPlan.create(sorted, assembly.executionVertices(),
                assembly.invokables(), assembly.subtasksMap());
    }

    /** Adjacency maps of the job graph: per-vertex outgoing and incoming edges. */
    private record EdgeAdjacency(Map<String, List<JobEdge>> outgoingEdges,
                                 Map<String, List<JobEdge>> incomingEdges) {
    }

    private static EdgeAdjacency buildEdgeAdjacency(JobGraph jobGraph) {
        Map<String, List<JobEdge>> outgoingEdges = new HashMap<>();
        Map<String, List<JobEdge>> incomingEdges = new HashMap<>();
        for (JobEdge edge : jobGraph.getEdges()) {
            outgoingEdges.computeIfAbsent(edge.getSourceVertex(), k -> new ArrayList<>()).add(edge);
            incomingEdges.computeIfAbsent(edge.getTargetVertex(), k -> new ArrayList<>()).add(edge);
        }
        return new EdgeAdjacency(outgoingEdges, incomingEdges);
    }

    /** Per-edge transport: producer partition matrix + consumer channel list. */
    private record EdgeTransport(Map<JobEdge, ResultPartition[][]> partitionMatrix,
                                 Map<JobEdge, List<RemoteInputChannel>> inputChannels) {
    }

    /**
     * Allocates the partition matrix per edge. We use
     * {@link RemoteResultPartition} for all producer-side partitions
     * and {@link RemoteInputChannel} for all consumer-side channels.
     */
    private EdgeTransport allocateEdgePartitions(JobGraph jobGraph,
                                                 Map<String, Integer> parallelismMap,
                                                 java.util.Set<String> subscribedTargetKeys) {
        String jobId = jobGraph.getJobName();

        Map<JobEdge, ResultPartition[][]> edgePartitionMatrix = new LinkedHashMap<>();
        Map<JobEdge, List<RemoteInputChannel>> edgeInputChannels = new LinkedHashMap<>();

        for (JobEdge edge : jobGraph.getEdges()) {
            int srcP = parallelismMap.getOrDefault(edge.getSourceVertex(), 1);
            int tgtP = parallelismMap.getOrDefault(edge.getTargetVertex(), 1);

            // Build an edge ID from sourceVertex->targetVertex
            String edgeId = edge.getSourceVertex() + "->" + edge.getTargetVertex();

            ResultPartition[][] matrix = new ResultPartition[srcP][tgtP];
            List<RemoteInputChannel> channels = new ArrayList<>();

            for (int s = 0; s < srcP; s++) {
                for (int t = 0; t < tgtP; t++) {
                    String topic = StreamTopicNaming.buildTopic(jobId, edgeId, s, t);
                    // Producer side: RemoteResultPartition
                    matrix[s][t] = new RemoteResultPartition(
                            messageService, topic, typeRegistry, edgeId,
                            epochId);

                    // Consumer side: RemoteInputChannel (created per target subtask per source).
                    // Subscription scope — the channel is always
                    // CONSTRUCTED (full plan structure preserved for checkpoint /
                    // rescale consumers), but only subscribes when its target
                    // subtask is in the subscribed set (null set = subscribe all).
                    boolean subscribe = subscribedTargetKeys == null
                            || subscribedTargetKeys.contains(edge.getTargetVertex() + "/" + t);
                    RemoteInputChannel remoteChannel = new RemoteInputChannel(
                            messageService, topic, epochId,
                            DEFAULT_CHANNEL_QUEUE_CAPACITY, 0L,
                            RemoteInputChannel.DEFAULT_ENQUEUE_OFFER_TIMEOUT_MS, subscribe);
                    if (subscribe) {
                        // Queue-depth gauge ONLY for channels that
                        // activate their subscription — the coordinator form
                        // (zero-subscription) and construct-only mirror channels
                        // must not produce permanently-zero gauges.
                        ChannelQueueGauges.bind(remoteChannel, jobId, edgeId, s, t);
                    }
                    channels.add(remoteChannel);
                }
            }

            edgePartitionMatrix.put(edge, matrix);
            edgeInputChannels.put(edge, channels);
        }

        return new EdgeTransport(edgePartitionMatrix, edgeInputChannels);
    }

    /** The three per-vertex maps a {@link GraphExecutionPlan} is created from. */
    private record SubtaskAssembly(Map<String, JobVertex> executionVertices,
                                   Map<String, StreamTaskInvokable> invokables,
                                   Map<String, List<Subtask>> subtasksMap) {
    }

    private static SubtaskAssembly assembleSubtasks(JobGraph jobGraph,
                                                    Map<String, Integer> parallelismMap,
                                                    EdgeAdjacency adjacency,
                                                    EdgeTransport transport,
                                                    DeploymentPlan deploymentPlan,
                                                    boolean barrierAlignment) {
        Map<String, JobVertex> executionVertices = new LinkedHashMap<>();
        Map<String, StreamTaskInvokable> invokables = new LinkedHashMap<>();
        Map<String, List<Subtask>> subtasksMap = new LinkedHashMap<>();

        for (Map.Entry<String, JobVertex> entry : jobGraph.getVertices().entrySet()) {
            String vertexId = entry.getKey();
            JobVertex original = entry.getValue();
            int parallelism = parallelismMap.getOrDefault(vertexId, 1);

            // R5-CON-05 deployment gate: same check as the embedded plan builder —
            // a source function that cannot honor parallelism > 1 fails fast here.
            validateSourceParallelism(original, parallelism, vertexId);

            List<JobEdge> outEdges = adjacency.outgoingEdges().getOrDefault(vertexId, Collections.emptyList());
            List<JobEdge> inEdges = adjacency.incomingEdges().getOrDefault(vertexId, Collections.emptyList());

            List<Subtask> vertexSubtasks = new ArrayList<>(parallelism);

            for (int taskIndex = 0; taskIndex < parallelism; taskIndex++) {
                // Task-identity pipeline (plan 368 Phase 4, R5-CON-01): the deployment
                // location flows into per-subtask user functions (2PC sink ledger
                // namespace). jobId mirrors the topic naming convention above
                // (StreamTopicNaming.buildTopic uses jobGraph.getJobName() as jobId).
                TaskLocation taskLocation = new TaskLocation(
                        jobGraph.getJobName(), "pipeline-0", vertexId, taskIndex);
                OperatorChain chain = taskIndex == 0
                        ? original.getOperatorChains().get(0)
                        : original.getOperatorChains().get(0).deepCopy(taskLocation);

                RemoteWriters writers = buildRemoteOutputWriters(
                        outEdges, taskIndex, transport.partitionMatrix(), deploymentPlan);
                InputGate inputGate = buildRemoteInputGate(
                        inEdges, taskIndex, parallelismMap, transport.inputChannels(),
                        deploymentPlan, barrierAlignment);

                StreamTaskInvokable invokable;
                if (writers.fanOutWriters() != null && !writers.fanOutWriters().isEmpty()) {
                    if (inputGate != null) {
                        invokable = new StreamTaskInvokable(chain, writers.fanOutWriters(), inputGate);
                    } else {
                        invokable = new StreamTaskInvokable(chain, writers.fanOutWriters());
                    }
                } else if (writers.singleWriter() != null || inputGate != null) {
                    invokable = new StreamTaskInvokable(chain, writers.singleWriter(), inputGate);
                } else {
                    invokable = new StreamTaskInvokable(chain);
                }

                Subtask subtask = new Subtask(vertexId, taskIndex, taskLocation, invokable);
                vertexSubtasks.add(subtask);

                if (taskIndex == 0) {
                    invokables.put(vertexId, invokable);

                    JobVertex execVertex = new JobVertex(
                            original.getId(), original.getName(), original.getParallelism(),
                            original.getOperatorChains(), invokable);
                    executionVertices.put(vertexId, execVertex);
                }
            }

            subtasksMap.put(vertexId, vertexSubtasks);
        }

        return new SubtaskAssembly(executionVertices, invokables, subtasksMap);
    }

    /**
     * R5-CON-05 deployment gate (remote mirror of the embedded check in
     * {@code GraphExecutionPlan}): a source function implementing
     * {@link ParallelismCheckable} rejects unsupported parallelism before any
     * subtask is deployed, instead of duplicating its full dataset per copy.
     */
    private static void validateSourceParallelism(JobVertex vertex, int parallelism, String vertexId) {
        if (parallelism <= 1 || vertex.getOperatorChains() == null || vertex.getOperatorChains().isEmpty()) {
            return;
        }
        for (io.nop.stream.core.operators.StreamOperator<?> op : vertex.getOperatorChains().get(0).getOperators()) {
            if (op instanceof io.nop.stream.core.operators.StreamSourceOperator) {
                io.nop.stream.core.common.functions.source.SourceFunction<?> sourceFunction =
                        ((io.nop.stream.core.operators.StreamSourceOperator<?>) op).getSourceFunction();
                if (sourceFunction instanceof io.nop.stream.core.common.functions.source.ParallelismCheckable) {
                    ((io.nop.stream.core.common.functions.source.ParallelismCheckable) sourceFunction)
                            .validateParallelism(parallelism);
                }
            }
        }
    }

    /** Per-subtask output writers: either a single writer or a per-edge fan-out list. */
    private record RemoteWriters(RecordWriter<Object> singleWriter,
                                 List<RecordWriter<Object>> fanOutWriters) {
    }

    /**
     * Builds the output writers for one subtask from its outgoing edges.
     * Multi-edge fan-out MUST use one RecordWriter PER out-edge (mirroring
     * GraphExecutionPlan.build): lumping every edge's partitions into ONE
     * writer routes each record to a single partition (FORWARD: always
     * partition 0; with a key partitioner: hash-picked edge) — downstream
     * edges of a fan-out vertex silently starve (only watermarks broadcast to
     * all partitions).
     *
     * <p>Delegates the per-edge writer assembly to the shared
     * {@link EdgeAssembly#createWriterForEdge}, passing THIS builder's
     * {@code resolvePartitionPolicy} as the injected policy resolver — the
     * DELIBERATE DIVERGENCE between local and remote policy resolution is
     * preserved bit-for-bit (see the divergence note on resolvePartitionPolicy).
     */
    private static RemoteWriters buildRemoteOutputWriters(List<JobEdge> outEdges,
                                                          int taskIndex,
                                                          Map<JobEdge, ResultPartition[][]> edgePartitionMatrix,
                                                          DeploymentPlan deploymentPlan) {
        RecordWriter<Object> recordWriter = null;
        List<RecordWriter<Object>> fanOutWriters = null;

        if (!outEdges.isEmpty()) {
            if (outEdges.size() == 1) {
                JobEdge edge = outEdges.get(0);
                recordWriter = EdgeAssembly.createWriterForEdge(edge,
                        edgePartitionMatrix.get(edge), taskIndex, deploymentPlan,
                        RemoteGraphExecutionPlanBuilder::resolvePartitionPolicy);
            } else {
                fanOutWriters = new ArrayList<>();
                for (JobEdge edge : outEdges) {
                    RecordWriter<Object> writer = EdgeAssembly.createWriterForEdge(edge,
                            edgePartitionMatrix.get(edge), taskIndex, deploymentPlan,
                            RemoteGraphExecutionPlanBuilder::resolvePartitionPolicy);
                    if (writer != null) {
                        fanOutWriters.add(writer);
                    }
                }
            }
        }

        return new RemoteWriters(recordWriter, fanOutWriters);
    }

    private static InputGate buildRemoteInputGate(List<JobEdge> inEdges,
                                                  int taskIndex,
                                                  Map<String, Integer> parallelismMap,
                                                  Map<JobEdge, List<RemoteInputChannel>> edgeInputChannels,
                                                  DeploymentPlan deploymentPlan,
                                                  boolean barrierAlignment) {
        InputGate inputGate = null;
        // Build InputGate using RemoteInputChannels
        if (!inEdges.isEmpty()) {
            List<InputChannel> channels = new ArrayList<>();
            for (JobEdge edge : inEdges) {
                int srcP = parallelismMap.getOrDefault(edge.getSourceVertex(), 1);
                List<RemoteInputChannel> edgeChannels = edgeInputChannels.get(edge);
                if (edgeChannels != null) {
                    // This target subtask (taskIndex) receives from all source subtasks
                    // Channels are stored as [src0tgt0, src0tgt1, ..., src1tgt0, ...]
                    // We need channels where targetIndex == taskIndex
                    int tgtP = parallelismMap.getOrDefault(edge.getTargetVertex(), 1);
                    for (int s = 0; s < srcP; s++) {
                        int idx = s * tgtP + taskIndex;
                        if (idx < edgeChannels.size()) {
                            channels.add(edgeChannels.get(idx));
                        }
                    }
                }
            }

            if (!channels.isEmpty()) {
                EdgeConfig gateConfig = resolveEdgeConfig(inEdges.get(0), deploymentPlan);
                inputGate = new InputGate(channels, gateConfig,
                        InputGate.alignmentModeFor(barrierAlignment));
            }
        }
        return inputGate;
    }

    // --- Helper methods (same logic as GraphExecutionPlan) ---

    /**
     * DELIBERATE DIVERGENCE from the local builder ({@link GraphExecutionPlan#resolveParallelism},
     * nop-stream-core): the local path honors {@code JobVertex#isParallelismLocked()}
     * (forced back to parallelism 1); THIS path ignores the lock and always uses the
     * DeploymentPlan/vertex parallelism. Unifying the two is a behavior change on the
     * remote plan-assembly semantics and is adjudicated separately (plan 2278 Deferred:
     * "GraphExecutionPlan/RemoteBuilder 语义分歧统一") — do NOT "fix" this asymmetry
     * as drive-by cleanup.
     */
    private static Map<String, Integer> resolveParallelism(JobGraph jobGraph,
                                                           DeploymentPlan deploymentPlan) {
        Map<String, Integer> result = new LinkedHashMap<>();
        Map<String, Integer> planParallelism = null;

        if (deploymentPlan != null && deploymentPlan.getPartitionedPlan() != null) {
            planParallelism = new HashMap<>();
            for (Map.Entry<String, PartitionedPlan.VertexPlan> entry :
                    deploymentPlan.getPartitionedPlan().getVertexPlans().entrySet()) {
                planParallelism.put(entry.getKey(), entry.getValue().getParallelism());
            }
        }

        for (Map.Entry<String, JobVertex> entry : jobGraph.getVertices().entrySet()) {
            String vertexId = entry.getKey();
            int parallelism = entry.getValue().getParallelism();
            if (planParallelism != null && planParallelism.containsKey(vertexId)) {
                parallelism = planParallelism.get(vertexId);
            }
            result.put(vertexId, Math.max(1, parallelism));
        }
        return result;
    }

    /**
     * DELIBERATE DIVERGENCE from the local builder
     * ({@code GraphExecutionPlan#resolvePartitionPolicy}, nop-stream-core): the local
     * path resolves the policy declaratively via {@code PartitionPolicyAware} and FAILS
     * FAST for a non-null partitioner that does not implement it; THIS path falls back
     * to {@link PartitionPolicy#HASH} for any non-null partitioner and never fails.
     * Unifying the two is a behavior change on the remote plan-assembly semantics and
     * is adjudicated separately (plan 2278 Deferred: "GraphExecutionPlan/RemoteBuilder
     * 语义分歧统一") — do NOT "fix" this asymmetry as drive-by cleanup.
     */
    private static PartitionPolicy resolvePartitionPolicy(JobEdge edge, DeploymentPlan deploymentPlan) {
        if (deploymentPlan != null && deploymentPlan.getPartitionedPlan() != null) {
            for (PartitionedPlan.EdgePlan edgePlan :
                    deploymentPlan.getPartitionedPlan().getEdgePlans()) {
                if (edgePlan.getSourceVertexId().equals(edge.getSourceVertex())
                        && edgePlan.getTargetVertexId().equals(edge.getTargetVertex())) {
                    return edgePlan.getPartitionPolicy();
                }
            }
        }
        if (edge.getPartitioner() != null) {
            return PartitionPolicy.HASH;
        }
        return PartitionPolicy.FORWARD;
    }

    // Package-private for the AR-12 topic-sanitization regression test: the edge-config
    // map key keeps its RAW "A->B" form — only the topic produced from it is sanitized.
    // Thin delegate: the single converged implementation lives in
    // {@link EdgeAssembly#resolveEdgeConfig} (nop-stream-core); this keeps the
    // TestRemotePlanTopicLegality direct call site working unchanged.
    static EdgeConfig resolveEdgeConfig(JobEdge edge, DeploymentPlan deploymentPlan) {
        return EdgeAssembly.resolveEdgeConfig(edge, deploymentPlan);
    }

    /**
     * DELIBERATE DIVERGENCE from the local builder
     * ({@code GraphExecutionPlan#topologicalSort}, nop-stream-core): the local path
     * fails fast on a cyclic graph (ERR_STREAM_CYCLIC_JOB_GRAPH); THIS path silently
     * returns a PARTIAL ordering for the same input (vertices inside a cycle are
     * omitted). Unifying the two is a behavior change on the remote plan-assembly
     * semantics and is adjudicated separately (plan 2278 Deferred:
     * "GraphExecutionPlan/RemoteBuilder 语义分歧统一") — do NOT "fix" this asymmetry
     * as drive-by cleanup.
     */
    private static List<String> topologicalSort(JobGraph jobGraph) {
        Map<String, List<String>> adjacency = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();

        for (String vertexId : jobGraph.getVertices().keySet()) {
            adjacency.put(vertexId, new ArrayList<>());
            inDegree.put(vertexId, 0);
        }

        for (JobEdge edge : jobGraph.getEdges()) {
            adjacency.get(edge.getSourceVertex()).add(edge.getTargetVertex());
            inDegree.merge(edge.getTargetVertex(), 1, Integer::sum);
        }

        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<String> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String vertexId = queue.poll();
            sorted.add(vertexId);
            for (String neighbor : adjacency.get(vertexId)) {
                int newDegree = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, newDegree);
                if (newDegree == 0) {
                    queue.add(neighbor);
                }
            }
        }

        return sorted;
    }
}
