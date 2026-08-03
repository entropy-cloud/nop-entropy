/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder;

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
import io.nop.stream.flow.builder.functions.XplFilterFunction;
import io.nop.stream.flow.builder.functions.XplFlatMapFunction;
import io.nop.stream.flow.builder.functions.XplMapFunction;
import io.nop.stream.flow.builder.functions.XplSinkFunction;
import io.nop.stream.flow.builder.functions.XplSourceFunction;
import io.nop.stream.flow.model.CheckpointConfigModel;
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
 *       fails fast with {@link UnsupportedOperationException}.</li>
 *   <li>Top-level registries with no live execution consumer
 *       ({@code <streams>}/{@code <sideInputs>}/{@code <environments>}/...) also fail
 *       fast rather than being silently ignored.</li>
 * </ul>
 */
public final class StreamModelDslBuilder {

    private final StreamModel model;
    private final BeanFunctionResolver beanResolver;

    private final Map<String, Object> streamRegistry = new LinkedHashMap<>();

    private StreamModelDslBuilder(StreamModel model, BeanFunctionResolver beanResolver) {
        if (model == null) {
            throw new IllegalArgumentException("StreamModel must not be null");
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
    }

    /**
     * Phase 1 fail-fast: registries/callbacks declared in {@code stream.xdef} that have no
     * consumer in the current execution chain must not be silently ignored.
     */
    private void failFastOnUnsupportedRegistries() {
        if (model.hasStreams()) {
            throw new UnsupportedOperationException(
                    "not yet implemented: <streams> registry has no execution consumer");
        }
        if (model.hasSideInputs()) {
            throw new UnsupportedOperationException(
                    "not yet implemented: <sideInputs> registry has no execution consumer");
        }
        if (model.hasEnvironments()) {
            throw new UnsupportedOperationException(
                    "not yet implemented: <environments> registry has no execution consumer");
        }
        if (!model.getRequirements().isEmpty()) {
            throw new UnsupportedOperationException(
                    "not yet implemented: <requirements> declarations have no builder-side consumer");
        }
        if (!model.getCheckpointParticipants().isEmpty()) {
            throw new UnsupportedOperationException(
                    "not yet implemented: <checkpointParticipants> declarations have no builder-side consumer");
        }
        if (model.getOnStart() != null || model.getOnEnd() != null || model.getOnError() != null) {
            throw new UnsupportedOperationException(
                    "not yet implemented: <onStart>/<onEnd>/<onError> lifecycle callbacks");
        }
        if (model.hasSchemas()) {
            throw new UnsupportedOperationException(
                    "not yet implemented: <schemas> registry has no execution consumer");
        }
        if (model.hasCoders()) {
            throw new UnsupportedOperationException(
                    "not yet implemented: <coders> registry has no execution consumer");
        }
    }

    // ----------------------------------------------------------------
    // Transform DAG walk
    // ----------------------------------------------------------------

    private void buildTransforms(StreamExecutionEnvironment env) {
        Map<String, StreamTransformModel> byId = new LinkedHashMap<>();
        for (StreamTransformModel t : model.getTransforms()) {
            if (t.getId() == null) {
                throw new IllegalArgumentException(
                        "Stream DSL transform missing required 'id': " + t);
            }
            if (byId.put(t.getId(), t) != null) {
                throw new IllegalArgumentException(
                        "Duplicate Stream DSL transform id: " + t.getId());
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
                throw new IllegalArgumentException("Duplicate Stream DSL edge id: " + e.getId());
            }
            if (!byId.containsKey(e.getFrom())) {
                throw new IllegalArgumentException(
                        "Stream DSL edge '" + e.getId() + "' references unknown source transform: "
                                + e.getFrom());
            }
            if (!byId.containsKey(e.getTo())) {
                throw new IllegalArgumentException(
                        "Stream DSL edge '" + e.getId() + "' references unknown target transform: "
                                + e.getTo());
            }
            upstreams.get(e.getTo()).add(e.getFrom());
        }

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
            throw new IllegalStateException(
                    "Stream DSL transforms form a cycle or unreachable node; processed="
                            + ordered.size() + " declared=" + model.getTransforms().size());
        }

        for (StreamTransformModel t : ordered) {
            Object built = buildTransform(env, t, upstreams.get(t.getId()));
            streamRegistry.put(t.getId(), built);
        }
    }

    private Object buildTransform(StreamExecutionEnvironment env, StreamTransformModel t,
                                  Set<String> upstreamIds) {
        // Dispatch on the parsed Java subtype.
        if (t instanceof StreamSourceModel) {
            return buildSource(env, (StreamSourceModel) t);
        }
        if (t instanceof StreamMapModel) {
            return buildMap(requireSingleInput(upstreamIds), (StreamMapModel) t);
        }
        if (t instanceof StreamFilterModel) {
            return buildFilter(requireSingleInput(upstreamIds), (StreamFilterModel) t);
        }
        if (t instanceof StreamFlatMapModel) {
            return buildFlatMap(requireSingleInput(upstreamIds), (StreamFlatMapModel) t);
        }
        if (t instanceof StreamKeyByModel) {
            return buildKeyBy(requireSingleInput(upstreamIds), (StreamKeyByModel) t);
        }
        if (t instanceof StreamSinkModel) {
            buildSink(requireSingleInput(upstreamIds), (StreamSinkModel) t);
            return null;
        }
        // Advanced transforms are delegated to AdvancedTransforms (Phase 2).
        return AdvancedTransforms.build(this, env, t, upstreamIds, streamRegistry);
    }

    private DataStream<?> requireSingleInput(Set<String> upstreamIds) {
        if (upstreamIds.size() != 1) {
            throw new IllegalArgumentException(
                    "Stream DSL transform requires exactly one upstream edge, found "
                            + upstreamIds.size());
        }
        String upstream = upstreamIds.iterator().next();
        Object in = streamRegistry.get(upstream);
        if (!(in instanceof DataStream)) {
            throw new IllegalStateException(
                    "Stream DSL transform upstream '" + upstream
                            + "' is not a DataStream (was " + (in == null ? "null" : in.getClass()) + ")");
        }
        return (DataStream<?>) in;
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
            throw new IllegalArgumentException(
                    "Stream DSL <source id='" + t.getId()
                            + "'> must declare either bean=\"...\" or inline <source>xpl</source>");
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
            throw new IllegalArgumentException(
                    "Stream DSL <map id='" + t.getId()
                            + "'> must declare either bean=\"...\" or inline <source>xpl</source>");
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
            throw new IllegalArgumentException(
                    "Stream DSL <filter id='" + t.getId()
                            + "'> must declare either bean=\"...\" or inline <source>xpl</source>");
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
            throw new IllegalArgumentException(
                    "Stream DSL <flatMap id='" + t.getId()
                            + "'> must declare either bean=\"...\" or inline <source>xpl</source>");
        }
        return ((DataStream<T>) in).flatMap((FlatMapFunction) fn);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T, K> KeyedStream<T, K> buildKeyBy(DataStream<?> in, StreamKeyByModel t) {
        if (t.getKeyExpr() == null) {
            throw new IllegalArgumentException(
                    "Stream DSL <keyBy id='" + t.getId() + "'> requires a keyExpr expression");
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
            throw new IllegalArgumentException(
                    "Stream DSL <sink id='" + t.getId()
                            + "'> must declare either bean=\"...\" or inline <source>xpl</source>");
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

    public PartitionPolicy resolveEdgePartition(String from, String to) {
        for (StreamEdgeModel e : model.getEdges()) {
            if (from.equals(e.getFrom()) && to.equals(e.getTo())) {
                PartitionPolicy p = e.getPartition();
                return p == null ? PartitionPolicy.FORWARD : p;
            }
        }
        return PartitionPolicy.FORWARD;
    }

    public <F> F resolveFunction(StreamTransformModel t, String beanAttr, IEvalFunction xplBody,
                                 Class<F> targetType, Function<IEvalFunction, F> xplWrapper) {
        if (beanAttr != null) {
            return beanResolver.resolve(beanAttr, targetType);
        }
        if (xplBody != null) {
            return xplWrapper.apply(xplBody);
        }
        throw new IllegalArgumentException(
                "Stream DSL <" + t.getId() + "> must declare either bean=\"...\" or inline <source>xpl</source>");
    }
}
