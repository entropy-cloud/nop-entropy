/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ACTUAL_STREAM_TYPE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ACTUAL_TYPE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ATTR_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_BEAN_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_EDGE_ID;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ELEMENT;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_EXPECTED_STREAM_TYPE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_EXPECTED_TYPE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ID;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_PARTITION;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_REF_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_REF_TYPE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_TRANSFORM_ID;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_BEAN_NOT_FOUND;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_BEAN_TYPE_MISMATCH;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CYCLIC_JOB_GRAPH;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_DUPLICATE_ID;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_EDGE_ATTR_UNSUPPORTED;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_EDGE_HASH_KEY_EXPR_REQUIRED;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_EDGE_HASH_REDUNDANT;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_EDGE_KEY_EXPR_WITHOUT_HASH;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_EDGE_PARTITION_UNSUPPORTED;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NOT_IMPLEMENTED;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_REF_UNKNOWN;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_REQUIRED_ATTR;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_REQUIRED_BODY;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_UPSTREAM_TYPE;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.stream.core.common.functions.FilterFunction;
import io.nop.stream.core.common.functions.FlatMapFunction;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.functions.MapFunction;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.datastream.DataStream;
import io.nop.stream.core.datastream.KeyedStream;
import io.nop.stream.core.datastream.SingleOutputStreamOperator;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.flow.builder.functions.XplFilterFunction;
import io.nop.stream.flow.builder.functions.XplFlatMapFunction;
import io.nop.stream.flow.builder.functions.XplMapFunction;
import io.nop.stream.flow.builder.functions.XplSinkFunction;
import io.nop.stream.flow.builder.functions.XplSourceFunction;
import io.nop.stream.flow.model.CheckpointConfigModel;
import io.nop.stream.flow.model.StorageConfigEntryModel;
import io.nop.stream.flow.model.StreamEdgeModel;
import io.nop.stream.flow.model.StreamFilterModel;
import io.nop.stream.flow.model.StreamFlatMapModel;
import io.nop.stream.flow.model.StreamKeyByModel;
import io.nop.stream.flow.model.StreamMapModel;
import io.nop.stream.flow.model.StreamModel;
import io.nop.stream.flow.model.StreamSinkModel;
import io.nop.stream.flow.model.StreamSourceModel;
import io.nop.stream.flow.model.StreamTransformModel;

/**
 * Translates a parsed {@link StreamModel} (the XDSL declarative form) into a fully wired
 * {@link StreamExecutionEnvironment} whose DataStream-API call chain produces an
 * equivalent Transformation DAG.
 *
 * <p>The builder walks the transforms in topological order implied by the declared
 * {@code <edges>}: a transform is processed only after every upstream transform it
 * consumes has been processed. Each processed transform registers its output stream
 * ({@link DataStream}/{@link KeyedStream}) in a registry keyed by transform id, so
 * that downstream transforms can pick up their inputs.
 *
 * <h3>Function-specification forms</h3>
 * <ul>
 *   <li>{@code bean="xxx"} attribute &mdash; looked up via the configured
 *       {@link BeanFunctionResolver} (NopIoC container, in-process registry, ...).</li>
 *   <li>inline {@code <source>xpl</source>} body &mdash; compiled by XDSL into an
 *       {@link IEvalFunction} and wrapped into the matching {@code io.nop.stream.core}
 *       function interface via {@link io.nop.stream.flow.builder.functions} adapters.</li>
 * </ul>
 *
 * <h3>Anti-Hollow guarantees</h3>
 * <ul>
 *   <li>Every {@code xdef}-declared transform element is either implemented here or
 *       fails fast with a {@link StreamException} carrying an {@code ERR_STREAM_*} code.</li>
 *   <li>Top-level registries with no live execution consumer
 *       ({@code <streams>}/{@code <sideInputs>}/{@code <environments>}/...) also fail
 *       fast rather than being silently ignored.</li>
 *   <li>Declared {@code <edge>} partition/keyExpr/flow-control attributes are either
 *       consumed (HASH + keyExpr via {@link DataStream#keyBy}) or rejected at build
 *       time with an error that locates the declaration (edge id + attribute name) —
 *       never silently downgraded to FORWARD (P1-XDSL-5).</li>
 * </ul>
 */
public final class StreamModelDslBuilder {

    private static final long XDEF_DEFAULT_BARRIER_ALIGNMENT_TIMEOUT = 30000L;
    private static final int XDEF_DEFAULT_MAX_CONSECUTIVE_CHECKPOINT_FAILURES = 3;
    private static final String XDEF_DEFAULT_STORAGE_TYPE = "local";

    private final StreamModel model;
    private final BeanFunctionResolver beanResolver;

    private final Map<String, Object> streamRegistry = new LinkedHashMap<>();

    private StreamModelDslBuilder(StreamModel model, BeanFunctionResolver beanResolver) {
        if (model == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "model");
        }
        this.model = model;
        this.beanResolver = beanResolver == null ? GlobalBeanFunctionResolver.INSTANCE : beanResolver;
    }

    public static StreamModelDslBuilder of(StreamModel model) {
        return new StreamModelDslBuilder(model, null);
    }

    public static StreamModelDslBuilder of(StreamModel model, BeanFunctionResolver beanResolver) {
        return new StreamModelDslBuilder(model, beanResolver);
    }

    /**
     * Build a fresh {@link StreamExecutionEnvironment} populated with an equivalent
     * transformation chain for the parsed {@link StreamModel}.
     */
    public StreamExecutionEnvironment build() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        if (model.getParallelism() > 0) {
            env.setParallelism(model.getParallelism());
        }
        if (model.getWatermarkInterval() > 0) {
            env.setWatermarkInterval(model.getWatermarkInterval());
        }
        applyCheckpointConfig(env);
        failFastOnUnsupportedRegistries();
        buildTransforms(env);
        return env;
    }

    // ----------------------------------------------------------------
    // Checkpoint + top-level registry handling
    // ----------------------------------------------------------------

    private void applyCheckpointConfig(StreamExecutionEnvironment env) {
        CheckpointConfigModel cfg = model.getCheckpoint();
        if (cfg == null) {
            return;
        }
        if (cfg.isEnabled() && cfg.getInterval() > 0) {
            env.enableCheckpointing(cfg.getInterval());
        }
        if (cfg.getProcessingGuarantee() != null) {
            env.getCheckpointConfig().setProcessingGuarantee(cfg.getProcessingGuarantee());
        }
        if (cfg.getTimeout() > 0) {
            env.getCheckpointConfig().setCheckpointTimeout(cfg.getTimeout());
        }
        if (cfg.getMaxConcurrentCheckpoints() > 0) {
            env.getCheckpointConfig().setMaxConcurrentCheckpoints(cfg.getMaxConcurrentCheckpoints());
        }
        if (cfg.getMinPause() > 0) {
            env.getCheckpointConfig().setMinPause(cfg.getMinPause());
        }
        if (cfg.getMaxRetainedCheckpoints() > 0) {
            env.getCheckpointConfig().setMaxRetainedCheckpoints(cfg.getMaxRetainedCheckpoints());
        }
        if (cfg.getJobTerminationMode() != null) {
            env.getCheckpointConfig().setJobTerminationMode(cfg.getJobTerminationMode());
        }
        // P1-XDSL-6: consume the six previously-ignored checkpoint fields. The
        // non-default test is pinned to the xdef defaults (barrierAlignmentTimeout=30000,
        // maxConsecutiveCheckpointFailures=3, storageType="local", jobId/pipelineId unset,
        // storageConfig empty) — never a `> 0` guard, otherwise a declared 0 is
        // silently ignored.
        if (cfg.getBarrierAlignmentTimeout() != XDEF_DEFAULT_BARRIER_ALIGNMENT_TIMEOUT) {
            env.getCheckpointConfig().setBarrierAlignmentTimeout(cfg.getBarrierAlignmentTimeout());
        }
        if (cfg.getMaxConsecutiveCheckpointFailures() != XDEF_DEFAULT_MAX_CONSECUTIVE_CHECKPOINT_FAILURES) {
            env.getCheckpointConfig()
                    .setMaxConsecutiveCheckpointFailures(cfg.getMaxConsecutiveCheckpointFailures());
        }
        if (cfg.getStorageType() != null && !XDEF_DEFAULT_STORAGE_TYPE.equals(cfg.getStorageType())) {
            env.getCheckpointConfig().setStorageType(cfg.getStorageType());
        }
        if (cfg.getJobId() != null) {
            env.getCheckpointConfig().setJobId(cfg.getJobId());
        }
        if (cfg.getPipelineId() != null) {
            env.getCheckpointConfig().setPipelineId(cfg.getPipelineId());
        }
        if (cfg.hasStorageConfig()) {
            Map<String, String> storageConfig = new HashMap<>();
            for (StorageConfigEntryModel entry : cfg.getStorageConfig()) {
                storageConfig.put(entry.getKey(), entry.getValue());
            }
            env.getCheckpointConfig().setStorageConfig(storageConfig);
        }
    }

    /**
     * Phase 1 fail-fast: registries/callbacks declared in {@code stream.xdef} that have no
     * consumer in the current execution chain must not be silently ignored.
     */
    private void failFastOnUnsupportedRegistries() {
        if (model.hasStreams()) {
            throw new StreamException(ERR_STREAM_NOT_IMPLEMENTED)
                    .param(ARG_DETAIL, "<streams> registry has no execution consumer");
        }
        if (model.hasSideInputs()) {
            throw new StreamException(ERR_STREAM_NOT_IMPLEMENTED)
                    .param(ARG_DETAIL, "<sideInputs> registry has no execution consumer");
        }
        if (model.hasEnvironments()) {
            throw new StreamException(ERR_STREAM_NOT_IMPLEMENTED)
                    .param(ARG_DETAIL, "<environments> registry has no execution consumer");
        }
        if (!model.getRequirements().isEmpty()) {
            throw new StreamException(ERR_STREAM_NOT_IMPLEMENTED)
                    .param(ARG_DETAIL, "<requirements> declarations have no builder-side consumer");
        }
        if (!model.getCheckpointParticipants().isEmpty()) {
            throw new StreamException(ERR_STREAM_NOT_IMPLEMENTED)
                    .param(ARG_DETAIL,
                            "<checkpointParticipants> declarations have no builder-side consumer");
        }
        if (model.getOnStart() != null || model.getOnEnd() != null || model.getOnError() != null) {
            throw new StreamException(ERR_STREAM_NOT_IMPLEMENTED)
                    .param(ARG_DETAIL, "<onStart>/<onEnd>/<onError> lifecycle callbacks");
        }
        if (model.hasSchemas()) {
            throw new StreamException(ERR_STREAM_NOT_IMPLEMENTED)
                    .param(ARG_DETAIL, "<schemas> registry has no execution consumer");
        }
        if (model.hasCoders()) {
            throw new StreamException(ERR_STREAM_NOT_IMPLEMENTED)
                    .param(ARG_DETAIL, "<coders> registry has no execution consumer");
        }
    }

    // ----------------------------------------------------------------
    // Transform DAG walk
    // ----------------------------------------------------------------

    private void buildTransforms(StreamExecutionEnvironment env) {
        Map<String, StreamTransformModel> byId = new LinkedHashMap<>();
        for (StreamTransformModel t : model.getTransforms()) {
            if (t.getId() == null) {
                throw new StreamException(ERR_STREAM_REQUIRED_ATTR)
                        .param(ARG_ELEMENT, "transform").param(ARG_ATTR_NAME, "id");
            }
            if (byId.put(t.getId(), t) != null) {
                throw new StreamException(ERR_STREAM_DUPLICATE_ID)
                        .param(ARG_ELEMENT, "transform").param(ARG_ID, t.getId());
            }
        }

        Map<String, Set<String>> upstreams = new HashMap<>();
        for (StreamTransformModel t : model.getTransforms()) {
            upstreams.put(t.getId(), new HashSet<>());
        }
        List<StreamEdgeModel> edges = model.getEdges() == null
                ? Collections.emptyList() : model.getEdges();
        Set<String> edgeIds = new HashSet<>();
        for (StreamEdgeModel e : edges) {
            if (!edgeIds.add(e.getId())) {
                throw new StreamException(ERR_STREAM_DUPLICATE_ID)
                        .param(ARG_ELEMENT, "edge '" + e.getId() + "'").param(ARG_ID, e.getId());
            }
            if (!byId.containsKey(e.getFrom())) {
                throw new StreamException(ERR_STREAM_REF_UNKNOWN)
                        .param(ARG_ELEMENT, "edge '" + e.getId() + "'")
                        .param(ARG_REF_TYPE, "source transform").param(ARG_REF_NAME, e.getFrom());
            }
            if (!byId.containsKey(e.getTo())) {
                throw new StreamException(ERR_STREAM_REF_UNKNOWN)
                        .param(ARG_ELEMENT, "edge '" + e.getId() + "'")
                        .param(ARG_REF_TYPE, "target transform").param(ARG_REF_NAME, e.getTo());
            }
            upstreams.get(e.getTo()).add(e.getFrom());
        }
        validateEdgeDeclarations(edges, byId);

        Deque<String> ready = new ArrayDeque<>();
        Map<String, Integer> remaining = new HashMap<>();
        for (StreamTransformModel t : model.getTransforms()) {
            int n = upstreams.get(t.getId()).size();
            remaining.put(t.getId(), n);
            if (n == 0) {
                ready.add(t.getId());
            }
        }
        List<StreamTransformModel> ordered = new ArrayList<>();
        while (!ready.isEmpty()) {
            String id = ready.poll();
            ordered.add(byId.get(id));
            for (StreamEdgeModel e : edges) {
                if (id.equals(e.getFrom())) {
                    int left = remaining.merge(e.getTo(), -1, Integer::sum);
                    if (left == 0) {
                        ready.add(e.getTo());
                    }
                }
            }
        }
        if (ordered.size() != model.getTransforms().size()) {
            throw new StreamException(ERR_STREAM_CYCLIC_JOB_GRAPH)
                    .param(ARG_DETAIL, "Stream DSL transforms form a cycle or unreachable node; processed="
                            + ordered.size() + " declared=" + model.getTransforms().size());
        }

        for (StreamTransformModel t : ordered) {
            Object built = buildTransform(env, t, upstreams.get(t.getId()));
            streamRegistry.put(t.getId(), built);
        }
    }

    /**
     * P1-XDSL-5: every declared {@code <edge>} attribute must be consumed or rejected at
     * build time. Decision matrix: {@code partition="HASH"} + keyExpr is implemented via
     * {@link DataStream#keyBy} on the edge's target input; HASH without keyExpr,
     * HASH targeting an existing keyBy transform (redundant), keyExpr on a non-HASH edge,
     * REBALANCE/BROADCAST and all four flow-control attributes
     * (flowControlPolicy/queueCapacity/receiveWindow/packetSize) fail fast with the edge id
     * and offending attribute — never silently downgraded to FORWARD.
     */
    private void validateEdgeDeclarations(List<StreamEdgeModel> edges,
                                          Map<String, StreamTransformModel> byId) {
        for (StreamEdgeModel e : edges) {
            PartitionPolicy p = e.getPartition();
            if (p == PartitionPolicy.HASH) {
                if (e.getKeyExpr() == null) {
                    throw new StreamException(ERR_STREAM_EDGE_HASH_KEY_EXPR_REQUIRED)
                            .param(ARG_EDGE_ID, e.getId());
                }
                if (byId.get(e.getTo()) instanceof StreamKeyByModel) {
                    throw new StreamException(ERR_STREAM_EDGE_HASH_REDUNDANT)
                            .param(ARG_EDGE_ID, e.getId()).param(ARG_TRANSFORM_ID, e.getTo());
                }
            } else if (p == PartitionPolicy.REBALANCE || p == PartitionPolicy.BROADCAST) {
                throw new StreamException(ERR_STREAM_EDGE_PARTITION_UNSUPPORTED)
                        .param(ARG_EDGE_ID, e.getId()).param(ARG_PARTITION, p);
            } else if (e.getKeyExpr() != null) {
                throw new StreamException(ERR_STREAM_EDGE_KEY_EXPR_WITHOUT_HASH)
                        .param(ARG_EDGE_ID, e.getId());
            }
            String unsupportedAttr = firstDeclaredFlowControlAttr(e);
            if (unsupportedAttr != null) {
                throw new StreamException(ERR_STREAM_EDGE_ATTR_UNSUPPORTED)
                        .param(ARG_EDGE_ID, e.getId()).param(ARG_ATTR_NAME, unsupportedAttr);
            }
        }
    }

    private static String firstDeclaredFlowControlAttr(StreamEdgeModel e) {
        if (e.getFlowControlPolicy() != null) {
            return "flowControlPolicy";
        }
        if (e.getQueueCapacity() != null) {
            return "queueCapacity";
        }
        if (e.getReceiveWindow() != null) {
            return "receiveWindow";
        }
        if (e.getPacketSize() != null) {
            return "packetSize";
        }
        return null;
    }

    private Object buildTransform(StreamExecutionEnvironment env, StreamTransformModel t,
                                  Set<String> upstreamIds) {
        // Dispatch on the parsed Java subtype.
        if (t instanceof StreamSourceModel) {
            return buildSource(env, (StreamSourceModel) t);
        }
        if (t instanceof StreamMapModel) {
            return buildMap(requireSingleInput(upstreamIds, t), (StreamMapModel) t);
        }
        if (t instanceof StreamFilterModel) {
            return buildFilter(requireSingleInput(upstreamIds, t), (StreamFilterModel) t);
        }
        if (t instanceof StreamFlatMapModel) {
            return buildFlatMap(requireSingleInput(upstreamIds, t), (StreamFlatMapModel) t);
        }
        if (t instanceof StreamKeyByModel) {
            return buildKeyBy(requireSingleInput(upstreamIds, t), (StreamKeyByModel) t);
        }
        if (t instanceof StreamSinkModel) {
            buildSink(requireSingleInput(upstreamIds, t), (StreamSinkModel) t);
            return null;
        }
        // Advanced transforms are delegated to AdvancedTransforms (Phase 2).
        return AdvancedTransforms.build(this, env, t, upstreamIds, streamRegistry);
    }

    private DataStream<?> requireSingleInput(Set<String> upstreamIds, StreamTransformModel t) {
        if (upstreamIds.size() != 1) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ARG_NAME, "upstream edges")
                    .param(ARG_DETAIL, "exactly one upstream edge required, found " + upstreamIds.size());
        }
        String upstream = upstreamIds.iterator().next();
        Object in = streamRegistry.get(upstream);
        if (!(in instanceof DataStream)) {
            throw new StreamException(ERR_STREAM_UPSTREAM_TYPE)
                    .param(ARG_ELEMENT, elementDesc(t))
                    .param(ARG_EXPECTED_STREAM_TYPE, "DataStream")
                    .param(ARG_ACTUAL_STREAM_TYPE, in == null ? "null" : in.getClass().getName());
        }
        // P1-XDSL-5: apply the declared edge partition to the input stream.
        return applyEdgePartition((DataStream<?>) in, upstream, t.getId());
    }

    // ----------------------------------------------------------------
    // Phase 1 base transforms
    // ----------------------------------------------------------------

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> DataStream<T> buildSource(StreamExecutionEnvironment env, StreamSourceModel t) {
        SourceFunction<T> fn;
        if (t.getBean() != null) {
            fn = beanResolver.resolve(t.getBean(), SourceFunction.class);
        } else if (t.getSource() != null) {
            fn = new XplSourceFunction<>(t.getSource());
        } else {
            throw new StreamException(ERR_STREAM_REQUIRED_BODY)
                    .param(ARG_ELEMENT, elementDesc(t));
        }
        String name = t.getName() == null ? "Source:" + t.getId() : t.getName();
        return (DataStream<T>) env.addSource(fn, name);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T, R> SingleOutputStreamOperator<R> buildMap(DataStream<?> in, StreamMapModel t) {
        MapFunction<T, R> fn;
        if (t.getBean() != null) {
            fn = beanResolver.resolve(t.getBean(), MapFunction.class);
        } else if (t.getSource() != null) {
            fn = new XplMapFunction<>(t.getSource());
        } else {
            throw new StreamException(ERR_STREAM_REQUIRED_BODY)
                    .param(ARG_ELEMENT, elementDesc(t));
        }
        return ((DataStream<T>) in).map((MapFunction) fn);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> SingleOutputStreamOperator<T> buildFilter(DataStream<?> in, StreamFilterModel t) {
        FilterFunction<T> fn;
        if (t.getBean() != null) {
            fn = beanResolver.resolve(t.getBean(), FilterFunction.class);
        } else if (t.getSource() != null) {
            fn = new XplFilterFunction<>(t.getSource());
        } else {
            throw new StreamException(ERR_STREAM_REQUIRED_BODY)
                    .param(ARG_ELEMENT, elementDesc(t));
        }
        return ((DataStream<T>) in).filter((FilterFunction) fn);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T, R> SingleOutputStreamOperator<R> buildFlatMap(DataStream<?> in, StreamFlatMapModel t) {
        FlatMapFunction<T, R> fn;
        if (t.getBean() != null) {
            fn = beanResolver.resolve(t.getBean(), FlatMapFunction.class);
        } else if (t.getSource() != null) {
            fn = new XplFlatMapFunction<>(t.getSource());
        } else {
            throw new StreamException(ERR_STREAM_REQUIRED_BODY)
                    .param(ARG_ELEMENT, elementDesc(t));
        }
        return ((DataStream<T>) in).flatMap((FlatMapFunction) fn);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T, K> KeyedStream<T, K> buildKeyBy(DataStream<?> in, StreamKeyByModel t) {
        if (t.getKeyExpr() == null) {
            throw new StreamException(ERR_STREAM_REQUIRED_ATTR)
                    .param(ARG_ELEMENT, elementDesc(t)).param(ARG_ATTR_NAME, "keyExpr");
        }
        KeySelector<T, K> selector = new EvalActionKeySelector<>(t.getKeyExpr());
        return ((DataStream<T>) in).keyBy((KeySelector) selector);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> void buildSink(DataStream<?> in, StreamSinkModel t) {
        SinkFunction<T> fn;
        if (t.getBean() != null) {
            fn = beanResolver.resolve(t.getBean(), SinkFunction.class);
        } else if (t.getSource() != null) {
            fn = new XplSinkFunction<>(t.getSource());
        } else {
            throw new StreamException(ERR_STREAM_REQUIRED_BODY)
                    .param(ARG_ELEMENT, elementDesc(t));
        }
        ((DataStream<T>) in).sink((SinkFunction) fn);
    }

    // ----------------------------------------------------------------
    // Helpers used by both base and advanced transform builders.
    // ----------------------------------------------------------------

    public BeanFunctionResolver beanResolver() {
        return beanResolver;
    }

    public StreamModel model() {
        return model;
    }

    public Object registeredStream(String transformId) {
        return streamRegistry.get(transformId);
    }

    /**
     * Resolves the declared partition policy of the edge connecting {@code from} to
     * {@code to}, defaulting to {@link PartitionPolicy#FORWARD} when no edge is declared.
     * This is the single consumption entry point for the edge's {@code partition}
     * attribute (P1-XDSL-5); callers must not read {@link StreamEdgeModel#getPartition()}
     * directly.
     */
    public PartitionPolicy resolveEdgePartition(String from, String to) {
        StreamEdgeModel e = findEdge(from, to);
        PartitionPolicy p = e == null ? null : e.getPartition();
        return p == null ? PartitionPolicy.FORWARD : p;
    }

    private StreamEdgeModel findEdge(String fromId, String toId) {
        for (StreamEdgeModel e : model.getEdges()) {
            if (fromId.equals(e.getFrom()) && toId.equals(e.getTo())) {
                return e;
            }
        }
        return null;
    }

    /**
     * Applies the declared edge partition to the stream crossing the edge {@code fromId}
     * → {@code toId}. Only {@code partition="HASH"} with a keyExpr is consumed here: the
     * upstream stream is wrapped with a {@link DataStream#keyBy} selector so the target
     * transform receives a {@link KeyedStream}. Every other non-default declaration was
     * already rejected by {@link #validateEdgeDeclarations(List, Map)}.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public DataStream<?> applyEdgePartition(DataStream<?> in, String fromId, String toId) {
        if (resolveEdgePartition(fromId, toId) != PartitionPolicy.HASH) {
            return in;
        }
        StreamEdgeModel edge = findEdge(fromId, toId);
        if (edge == null || edge.getKeyExpr() == null) {
            return in;
        }
        return ((DataStream<Object>) in).keyBy((KeySelector) new EvalActionKeySelector<>(edge.getKeyExpr()));
    }

    public <F> F resolveFunction(StreamTransformModel t, String beanAttr, IEvalFunction xplBody,
                                 Class<F> targetType, Function<IEvalFunction, F> xplWrapper) {
        if (beanAttr != null) {
            return beanResolver.resolve(beanAttr, targetType);
        }
        if (xplBody != null) {
            return xplWrapper.apply(xplBody);
        }
        throw new StreamException(ERR_STREAM_REQUIRED_BODY).param(ARG_ELEMENT, elementDesc(t));
    }

    /**
     * Renders a human-friendly element description for error messages, e.g.
     * {@code window 'w'} for a {@code StreamWindowModel} with id {@code w}.
     */
    public static String elementDesc(StreamTransformModel t) {
        String simple = t.getClass().getSimpleName();
        String name = simple;
        if (name.startsWith("Stream")) {
            name = name.substring("Stream".length());
        }
        if (name.endsWith("Model")) {
            name = name.substring(0, name.length() - "Model".length());
        }
        return name.toLowerCase() + " '" + t.getId() + "'";
    }

    public static NopException beanNotFound(String beanName) {
        return new StreamException(ERR_STREAM_BEAN_NOT_FOUND).param(ARG_BEAN_NAME, beanName);
    }

    public static NopException beanTypeMismatch(String beanName, Class<?> targetType,
                                                Class<?> actualType) {
        return new StreamException(ERR_STREAM_BEAN_TYPE_MISMATCH)
                .param(ARG_BEAN_NAME, beanName)
                .param(ARG_EXPECTED_TYPE, targetType.getName())
                .param(ARG_ACTUAL_TYPE, actualType.getName());
    }
}
