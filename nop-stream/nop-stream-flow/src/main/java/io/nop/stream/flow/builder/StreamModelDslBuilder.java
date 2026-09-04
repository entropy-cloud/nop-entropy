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
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_UPSTREAM_NULL;
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
import io.nop.stream.core.common.functions.sink.SinkConsistencyCapability;
import io.nop.stream.core.common.functions.source.SourceConsistencyCapability;
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
        // F-04b (plan 1326-2 Phase 4): the `> 0` guard silently dropped a root-level
        // `watermarkInterval="0"` declaration (per-event emission) — the env default
        // (200ms) kept running while the DSL promised per-event. `>= 0` keeps the
        // explicit 0 declaration.
        if (model.getWatermarkInterval() >= 0) {
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
                    .param(ARG_DETAIL, "<streams> registry has no execution consumer")
                    .loc(model.getLocation());
        }
        if (model.hasSideInputs()) {
            throw new StreamException(ERR_STREAM_NOT_IMPLEMENTED)
                    .param(ARG_DETAIL, "<sideInputs> registry has no execution consumer")
                    .loc(model.getLocation());
        }
        if (model.hasEnvironments()) {
            throw new StreamException(ERR_STREAM_NOT_IMPLEMENTED)
                    .param(ARG_DETAIL, "<environments> registry has no execution consumer")
                    .loc(model.getLocation());
        }
        if (!model.getRequirements().isEmpty()) {
            throw new StreamException(ERR_STREAM_NOT_IMPLEMENTED)
                    .param(ARG_DETAIL, "<requirements> declarations have no builder-side consumer")
                    .loc(model.getLocation());
        }
        if (!model.getCheckpointParticipants().isEmpty()) {
            throw new StreamException(ERR_STREAM_NOT_IMPLEMENTED)
                    .param(ARG_DETAIL,
                            "<checkpointParticipants> declarations have no builder-side consumer")
                    .loc(model.getLocation());
        }
        if (model.getOnStart() != null || model.getOnEnd() != null || model.getOnError() != null) {
            throw new StreamException(ERR_STREAM_NOT_IMPLEMENTED)
                    .param(ARG_DETAIL, "<onStart>/<onEnd>/<onError> lifecycle callbacks")
                    .loc(model.getLocation());
        }
        if (model.hasSchemas()) {
            throw new StreamException(ERR_STREAM_NOT_IMPLEMENTED)
                    .param(ARG_DETAIL, "<schemas> registry has no execution consumer")
                    .loc(model.getLocation());
        }
        if (model.hasCoders()) {
            throw new StreamException(ERR_STREAM_NOT_IMPLEMENTED)
                    .param(ARG_DETAIL, "<coders> registry has no execution consumer")
                    .loc(model.getLocation());
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
                        .param(ARG_ELEMENT, "transform").param(ARG_ATTR_NAME, "id")
                        .loc(t.getLocation());
            }
            if (byId.put(t.getId(), t) != null) {
                throw new StreamException(ERR_STREAM_DUPLICATE_ID)
                        .param(ARG_ELEMENT, "transform").param(ARG_ID, t.getId())
                        .loc(t.getLocation());
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
                        .param(ARG_ELEMENT, "edge '" + e.getId() + "'").param(ARG_ID, e.getId())
                        .loc(e.getLocation());
            }
            if (!byId.containsKey(e.getFrom())) {
                throw new StreamException(ERR_STREAM_REF_UNKNOWN)
                        .param(ARG_ELEMENT, "edge '" + e.getId() + "'")
                        .param(ARG_REF_TYPE, "source transform").param(ARG_REF_NAME, e.getFrom())
                        .loc(e.getLocation());
            }
            if (!byId.containsKey(e.getTo())) {
                throw new StreamException(ERR_STREAM_REF_UNKNOWN)
                        .param(ARG_ELEMENT, "edge '" + e.getId() + "'")
                        .param(ARG_REF_TYPE, "target transform").param(ARG_REF_NAME, e.getTo())
                        .loc(e.getLocation());
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
            // Include the offending transform ids so the user can locate the cycle /
            // unreachable nodes without re-deriving them from the counts (item 11 FL-4).
            Set<String> processedIds = new HashSet<>();
            for (StreamTransformModel t : ordered) {
                processedIds.add(t.getId());
            }
            List<String> stuck = new ArrayList<>();
            for (StreamTransformModel t : model.getTransforms()) {
                if (!processedIds.contains(t.getId())) {
                    stuck.add(t.getId());
                }
            }
            throw new StreamException(ERR_STREAM_CYCLIC_JOB_GRAPH)
                    .param(ARG_DETAIL, "Stream DSL transforms form a cycle or unreachable node; processed="
                            + ordered.size() + " declared=" + model.getTransforms().size()
                            + " unprocessed=" + stuck)
                    .loc(model.getLocation());
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
                            .param(ARG_EDGE_ID, e.getId())
                            .loc(e.getLocation());
                }
                if (byId.get(e.getTo()) instanceof StreamKeyByModel) {
                    throw new StreamException(ERR_STREAM_EDGE_HASH_REDUNDANT)
                            .param(ARG_EDGE_ID, e.getId()).param(ARG_TRANSFORM_ID, e.getTo())
                            .loc(e.getLocation());
                }
            } else if (p == PartitionPolicy.REBALANCE || p == PartitionPolicy.BROADCAST) {
                throw new StreamException(ERR_STREAM_EDGE_PARTITION_UNSUPPORTED)
                        .param(ARG_EDGE_ID, e.getId()).param(ARG_PARTITION, p)
                        .loc(e.getLocation());
            } else if (e.getKeyExpr() != null) {
                throw new StreamException(ERR_STREAM_EDGE_KEY_EXPR_WITHOUT_HASH)
                        .param(ARG_EDGE_ID, e.getId())
                        .loc(e.getLocation());
            }
            String unsupportedAttr = firstDeclaredFlowControlAttr(e);
            if (unsupportedAttr != null) {
                throw new StreamException(ERR_STREAM_EDGE_ATTR_UNSUPPORTED)
                        .param(ARG_EDGE_ID, e.getId()).param(ARG_ATTR_NAME, unsupportedAttr)
                        .loc(e.getLocation());
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
                    .param(ARG_DETAIL, "exactly one upstream edge required, found " + upstreamIds.size())
                    .loc(t.getLocation());
        }
        String upstream = upstreamIds.iterator().next();
        Object in = streamRegistry.get(upstream);
        if (in == null) {
            // Same condition must carry the same error code as the AdvancedTransforms
            // variant (item 11 FL-6 unification).
            throw new StreamException(ERR_STREAM_UPSTREAM_NULL)
                    .param(ARG_ELEMENT, elementDesc(t))
                    .loc(t.getLocation());
        }
        if (!(in instanceof DataStream)) {
            throw new StreamException(ERR_STREAM_UPSTREAM_TYPE)
                    .param(ARG_ELEMENT, elementDesc(t))
                    .param(ARG_EXPECTED_STREAM_TYPE, "DataStream")
                    .param(ARG_ACTUAL_STREAM_TYPE, in.getClass().getName())
                    .loc(t.getLocation());
        }
        // P1-XDSL-5: apply the declared edge partition to the input stream.
        return applyEdgePartition((DataStream<?>) in, upstream, t.getId(), t.getParallelism());
    }

    // ----------------------------------------------------------------
    // Phase 1 base transforms
    // ----------------------------------------------------------------

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> DataStream<T> buildSource(StreamExecutionEnvironment env, StreamSourceModel t) {
        failFastOnUnsupportedSourceConfig(t);
        SourceFunction<T> fn;
        if (t.getBean() != null) {
            fn = resolveBean(t, t.getBean(), SourceFunction.class);
        } else if (t.getSource() != null) {
            fn = new XplSourceFunction<>(t.getSource());
        } else {
            throw new StreamException(ERR_STREAM_REQUIRED_BODY)
                    .param(ARG_ELEMENT, elementDesc(t))
                    .loc(t.getLocation());
        }
        String name = t.getName() == null ? "Source:" + t.getId() : t.getName();
        // Item 29: per-transform parallelism consumes the DataStreamSource entry
        // (covers the source vertex; undeclared inherits the stream-level value).
        return applyDeclaredParallelism((DataStream<T>) env.addSource(fn, name), t);
    }

    /**
     * Item 11 FL-1: source-side connector configuration declarations that the current
     * execution chain cannot honor must fail fast instead of being silently dropped.
     * {@code <params>}, {@code outputType} and {@code maxParallelism} have no consumer
     * (connector beans validate their own constructor args, see the item 10 audit
     * §2.2 table), and {@code consistencyCapability} is not plumbed into the runtime
     * source contract — declaring a non-default value promises semantics the engine
     * does not deliver. Consumption of these fields is tracked as a follow-up.
     */
    private static void failFastOnUnsupportedSourceConfig(StreamSourceModel t) {
        if (t.hasParams()) {
            throw new StreamException(ERR_STREAM_NOT_IMPLEMENTED)
                    .param(ARG_ELEMENT, elementDesc(t)).param(ARG_ATTR_NAME, "params")
                    .param(ARG_DETAIL, "<params> on <source> has no execution consumer; "
                            + "configure the source bean constructor/xpl body instead")
                    .loc(t.getLocation());
        }
        if (t.getMaxParallelism() > 0) {
            throw new StreamException(ERR_STREAM_NOT_IMPLEMENTED)
                    .param(ARG_ELEMENT, elementDesc(t)).param(ARG_ATTR_NAME, "maxParallelism")
                    .param(ARG_DETAIL, "source maxParallelism has no execution consumer")
                    .loc(t.getLocation());
        }
        if (t.getOutputType() != null) {
            throw new StreamException(ERR_STREAM_NOT_IMPLEMENTED)
                    .param(ARG_ELEMENT, elementDesc(t)).param(ARG_ATTR_NAME, "outputType")
                    .param(ARG_DETAIL, "source outputType has no execution consumer")
                    .loc(t.getLocation());
        }
        if (t.getConsistencyCapability() != null
                && t.getConsistencyCapability() != SourceConsistencyCapability.AT_LEAST_ONCE) {
            throw new StreamException(ERR_STREAM_NOT_IMPLEMENTED)
                    .param(ARG_ELEMENT, elementDesc(t)).param(ARG_ATTR_NAME, "consistencyCapability")
                    .param(ARG_DETAIL, "source consistencyCapability has no execution consumer; declared="
                            + t.getConsistencyCapability())
                    .loc(t.getLocation());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T, R> SingleOutputStreamOperator<R> buildMap(DataStream<?> in, StreamMapModel t) {
        MapFunction<T, R> fn;
        if (t.getBean() != null) {
            fn = resolveBean(t, t.getBean(), MapFunction.class);
        } else if (t.getSource() != null) {
            fn = new XplMapFunction<>(t.getSource());
        } else {
            throw new StreamException(ERR_STREAM_REQUIRED_BODY)
                    .param(ARG_ELEMENT, elementDesc(t))
                    .loc(t.getLocation());
        }
        return applyDeclaredParallelism(((DataStream<T>) in).map((MapFunction) fn), t);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> SingleOutputStreamOperator<T> buildFilter(DataStream<?> in, StreamFilterModel t) {
        FilterFunction<T> fn;
        if (t.getBean() != null) {
            fn = resolveBean(t, t.getBean(), FilterFunction.class);
        } else if (t.getSource() != null) {
            fn = new XplFilterFunction<>(t.getSource());
        } else {
            throw new StreamException(ERR_STREAM_REQUIRED_BODY)
                    .param(ARG_ELEMENT, elementDesc(t))
                    .loc(t.getLocation());
        }
        return applyDeclaredParallelism(((DataStream<T>) in).filter((FilterFunction) fn), t);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T, R> SingleOutputStreamOperator<R> buildFlatMap(DataStream<?> in, StreamFlatMapModel t) {
        FlatMapFunction<T, R> fn;
        if (t.getBean() != null) {
            fn = resolveBean(t, t.getBean(), FlatMapFunction.class);
        } else if (t.getSource() != null) {
            fn = new XplFlatMapFunction<>(t.getSource());
        } else {
            throw new StreamException(ERR_STREAM_REQUIRED_BODY)
                    .param(ARG_ELEMENT, elementDesc(t))
                    .loc(t.getLocation());
        }
        return applyDeclaredParallelism(((DataStream<T>) in).flatMap((FlatMapFunction) fn), t);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T, K> KeyedStream<T, K> buildKeyBy(DataStream<?> in, StreamKeyByModel t) {
        if (t.getKeyExpr() == null) {
            throw new StreamException(ERR_STREAM_REQUIRED_ATTR)
                    .param(ARG_ELEMENT, elementDesc(t)).param(ARG_ATTR_NAME, "keyExpr")
                    .loc(t.getLocation());
        }
        KeySelector<T, K> selector = new EvalActionKeySelector<>(t.getKeyExpr());
        // Item 29: the keyBy vertex (PartitionTransformation) takes the declared
        // parallelism; downstream keyed consumers declare their own value.
        return applyDeclaredParallelism(((DataStream<T>) in).keyBy((KeySelector) selector), t);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> void buildSink(DataStream<?> in, StreamSinkModel t) {
        failFastOnUnsupportedSinkConfig(t);
        SinkFunction<T> fn;
        if (t.getBean() != null) {
            fn = resolveBean(t, t.getBean(), SinkFunction.class);
        } else if (t.getSource() != null) {
            fn = new XplSinkFunction<>(t.getSource());
        } else {
            throw new StreamException(ERR_STREAM_REQUIRED_BODY)
                    .param(ARG_ELEMENT, elementDesc(t))
                    .loc(t.getLocation());
        }
        Integer declared = declaredSinkParallelism(t);
        if (declared != null) {
            // Item 29: explicit per-operator parallelism on the sink registration
            // (the sink transformation is terminal — no stream object to retarget).
            ((DataStream<T>) in).sink((SinkFunction) fn, declared);
        } else {
            ((DataStream<T>) in).sink((SinkFunction) fn);
        }
    }

    /**
     * Item 11 FL-1 (sink side, mirrors {@link #failFastOnUnsupportedSourceConfig}):
     * {@code <params>}/{@code inputType}/{@code maxParallelism} have no consumer and a
     * non-default {@code consistencyCapability} is not plumbed into the runtime sink
     * contract — all fail fast instead of being silently dropped.
     */
    private static void failFastOnUnsupportedSinkConfig(StreamSinkModel t) {
        if (t.hasParams()) {
            throw new StreamException(ERR_STREAM_NOT_IMPLEMENTED)
                    .param(ARG_ELEMENT, elementDesc(t)).param(ARG_ATTR_NAME, "params")
                    .param(ARG_DETAIL, "<params> on <sink> has no execution consumer; "
                            + "configure the sink bean constructor/xpl body instead")
                    .loc(t.getLocation());
        }
        if (t.getMaxParallelism() > 0) {
            throw new StreamException(ERR_STREAM_NOT_IMPLEMENTED)
                    .param(ARG_ELEMENT, elementDesc(t)).param(ARG_ATTR_NAME, "maxParallelism")
                    .param(ARG_DETAIL, "sink maxParallelism has no execution consumer")
                    .loc(t.getLocation());
        }
        if (t.getInputType() != null) {
            throw new StreamException(ERR_STREAM_NOT_IMPLEMENTED)
                    .param(ARG_ELEMENT, elementDesc(t)).param(ARG_ATTR_NAME, "inputType")
                    .param(ARG_DETAIL, "sink inputType has no execution consumer")
                    .loc(t.getLocation());
        }
        if (t.getConsistencyCapability() != null
                && t.getConsistencyCapability() != SinkConsistencyCapability.AT_LEAST_ONCE) {
            throw new StreamException(ERR_STREAM_NOT_IMPLEMENTED)
                    .param(ARG_ELEMENT, elementDesc(t)).param(ARG_ATTR_NAME, "consistencyCapability")
                    .param(ARG_DETAIL, "sink consistencyCapability has no execution consumer; declared="
                            + t.getConsistencyCapability())
                    .loc(t.getLocation());
        }
    }

    // ----------------------------------------------------------------
    // Helpers used by both base and advanced transform builders.
    // ----------------------------------------------------------------

    public BeanFunctionResolver beanResolver() {
        return beanResolver;
    }

    /**
     * Item 29 (Phase 3): bean resolution wrapped so a missing/mistyped bean error
     * carries the declaring transform element's source location (the resolvers
     * themselves have no model object to anchor on). An existing location is kept.
     */
    public <F> F resolveBean(StreamTransformModel t, String beanName, Class<F> targetType) {
        try {
            return beanResolver.resolve(beanName, targetType);
        } catch (NopException e) {
            if (e.getErrorLocation() == null) {
                e.loc(t.getLocation());
            }
            throw e;
        }
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
     *
     * <p>Item 29: when the HASH edge feeds a target with a declared parallelism, the
     * implicit partition vertex takes the target's value. The partitioner spreads
     * records across the partition vertex's subtasks, and the partition→target edge is
     * FORWARD — if the partition vertex kept the stream-level parallelism while the
     * target is wider, FORWARD's modulo semantics would concentrate all traffic on
     * target subtask 0 (a structural false-green for per-transform parallelism).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public DataStream<?> applyEdgePartition(DataStream<?> in, String fromId, String toId,
                                            Integer targetParallelism) {
        if (resolveEdgePartition(fromId, toId) != PartitionPolicy.HASH) {
            return in;
        }
        StreamEdgeModel edge = findEdge(fromId, toId);
        if (edge == null || edge.getKeyExpr() == null) {
            return in;
        }
        KeyedStream keyed = ((DataStream<Object>) in).keyBy(
                (KeySelector) new EvalActionKeySelector<>(edge.getKeyExpr()));
        if (targetParallelism != null && targetParallelism > 0) {
            keyed.setParallelism(targetParallelism);
        }
        return keyed;
    }

    /**
     * Item 29: applies the transform's declared {@code parallelism} attribute to the
     * stream object the build point returned. Resolution order: transform-level
     * declaration &gt; stream-level (environment) &gt; engine default 1 — undeclared
     * means "inherit the environment value already stamped at construction".
     *
     * @return the stream with the declared parallelism applied (same instance)
     */
    static <S extends DataStream<?>> S applyDeclaredParallelism(S stream, StreamTransformModel t) {
        if (t.getParallelism() != null && t.getParallelism() > 0) {
            stream.setParallelism(t.getParallelism());
        }
        return stream;
    }

    /**
     * Item 29 (sink side): the declared parallelism value for the sink registration
     * overload, or {@code null} when undeclared (inherit the environment value).
     */
    static Integer declaredSinkParallelism(StreamTransformModel t) {
        return t.getParallelism() != null && t.getParallelism() > 0 ? t.getParallelism() : null;
    }

    public <F> F resolveFunction(StreamTransformModel t, String beanAttr, IEvalFunction xplBody,
                                 Class<F> targetType, Function<IEvalFunction, F> xplWrapper) {
        if (beanAttr != null) {
            return resolveBean(t, beanAttr, targetType);
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
