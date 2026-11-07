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
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_OUTPUT_TAG;
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
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.beans.Introspector;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.resource.component.AbstractComponentModel;
import io.nop.stream.core.common.functions.FilterFunction;
import io.nop.stream.core.common.functions.FlatMapFunction;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.functions.MapFunction;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.sink.SinkConsistencyCapability;
import io.nop.stream.core.common.functions.source.SourceConsistencyCapability;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.source.RateLimitedSourceFunction;
import io.nop.stream.core.source.SourceRateLimiter;
import io.nop.stream.core.datastream.DataStream;
import io.nop.stream.core.datastream.DataStreamImpl;
import io.nop.stream.core.datastream.KeyedStream;
import io.nop.stream.core.datastream.SingleOutputStreamOperator;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.model.StreamComponentEntry;
import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.common.typeinfo.UnknownTypeInformation;
import io.nop.stream.core.transformation.OneInputTransformation;
import io.nop.stream.core.util.OutputTag;
import io.nop.stream.flow.builder.functions.XplFilterFunction;
import io.nop.stream.flow.builder.functions.XplFlatMapFunction;
import io.nop.stream.flow.builder.functions.XplMapFunction;
import io.nop.stream.flow.builder.functions.XplSinkFunction;
import io.nop.stream.flow.builder.functions.XplSourceFunction;
import io.nop.stream.flow.model.CheckpointConfigModel;
import io.nop.stream.flow.model.StorageConfigEntryModel;
import io.nop.stream.flow.model.StreamAggregateModel;
import io.nop.stream.flow.model.StreamCustomModel;
import io.nop.stream.flow.model.StreamEdgeModel;
import io.nop.stream.flow.model.StreamFilterModel;
import io.nop.stream.flow.model.StreamFlatMapModel;
import io.nop.stream.flow.model.StreamSideInputModel;
import io.nop.stream.core.model.JoinType;
import io.nop.stream.flow.model.StreamJoinModel;
import io.nop.stream.flow.model.StreamJoinSpecModel;
import io.nop.stream.flow.model.StreamKeyByModel;
import io.nop.stream.flow.model.WindowingStrategyModel;
import io.nop.stream.flow.model.StreamMapModel;
import io.nop.stream.flow.model.StreamModel;
import io.nop.stream.flow.model.StreamProcessModel;
import io.nop.stream.flow.model.StreamReduceModel;
import io.nop.stream.flow.model.StreamSideOutputModel;
import io.nop.stream.flow.model.StreamSinkModel;
import io.nop.stream.flow.model.StreamSourceModel;
import io.nop.stream.flow.model.StreamUnionModel;
import io.nop.stream.flow.model.StreamTransformModel;
import io.nop.stream.flow.model.StreamWindowModel;
import io.nop.stream.flow.spi.ISqlStreamCompiler;

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

    /**
     * The builder's WORKING model. Final at construction (the caller's parsed model),
     * replaced once per {@link #build()} by a defensive deep copy (CRA 8.45 R4-05
     * W-07 / 1.3-C1-005: build() never mutates the caller-held model — every
     * expansion runs on the copy, so the input model stays pristine and
     * {@code of(model)} is idempotent/re-entrant).
     */
    private StreamModel model;
    private final BeanFunctionResolver beanResolver;

    private StreamSchemaRegistry schemaRegistry;

    private Consumer<StreamExecutionEnvironment> preBuildEnvironmentConfigurer;

    private final Map<String, Object> streamRegistry = new LinkedHashMap<>();

    /**
     * WI11 (plan 2357): per-target-transform xpl bindings — the declared side-input
     * snapshots frozen at build time. Key = target transform id; value = the binding
     * map handed to {@code XplFunctionSupport.newCallScope} ({@code sideInputs} →
     * snapshot Map keyed by sideInput id). Populated by {@link #materializeSideInputs}
     * after validation; consumed by the map/filter/flatMap build paths.
     */
    private final Map<String, Map<String, Object>> sideInputScopes = new LinkedHashMap<>();

    /**
     * WI12 (plan 2358): resolved sink functions by transform id — the bean instances
     * the declared-requirements consistency check inspects (engine-equivalent to what
     * the graph generator's detectRequirements aggregates).
     */
    private final Map<String, io.nop.stream.core.common.functions.SinkFunction<?>> resolvedSinkBeans =
            new LinkedHashMap<>();

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
     * R6 plan 2322: registers a configurer applied to the freshly created
     * environment BEFORE the model walk starts — i.e. before any {@code keyBy}
     * freezes the key-group maxParallelism via the state backend then configured.
     * This is the sanctioned "configure the state backend before keyBy" path the
     * KR-3 drift guard points at: callers that intend to restore under a
     * non-default maxParallelism (for example from a
     * {@code MaxParallelismReshardMigration} product) must inject their backend
     * here. Setting a backend AFTER {@link #build()} still fails loudly in
     * {@code StreamExecutionEnvironment#validateKeyedRoutingParity}.
     */
    public StreamModelDslBuilder withPreBuildEnvironment(
            Consumer<StreamExecutionEnvironment> configurer) {
        if (configurer == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "configurer");
        }
        Consumer<StreamExecutionEnvironment> existing = this.preBuildEnvironmentConfigurer;
        this.preBuildEnvironmentConfigurer =
                existing == null ? configurer : existing.andThen(configurer);
        return this;
    }

    /**
     * WI8b: query the resolved {@code <schemas>} registry. Must be called after
     * {@link #build()} — the registry is populated during the build walk.
     */
    public StreamSchemaRegistry schemaRegistry() {
        if (schemaRegistry == null)
            throw new IllegalStateException("schemaRegistry is only available after build(); call build() first");
        return schemaRegistry;
    }

    /**
     * Build a fresh {@link StreamExecutionEnvironment} populated with an equivalent
     * transformation chain for the parsed {@link StreamModel}.
     *
     * <p>CRA 8.45 R4-05 W-07 (1.3-C1-005): build() does not mutate the parsed model —
     * all expansions run on a defensive deep copy taken at entry (the caller-held
     * model, including its transforms/edges objects, stays pristine and reusable
     * across builders and repeated builds).
     */
    public StreamExecutionEnvironment build() {
        this.model = defensiveCopy(this.model);
        expandSqlModel();
        expandSqlTransforms();
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        if (preBuildEnvironmentConfigurer != null) {
            // R6 plan 2322: runs before applyCheckpointConfig/buildTransforms so a
            // state backend configured here is the one keyBy freezes its key-group
            // maxParallelism from (KR-3 sanctioned pre-build path).
            preBuildEnvironmentConfigurer.accept(env);
        }
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
        // WI12 (plan 2358): the declared environment profile applies to THIS build
        // (needs the live env instance for setDeploymentMode).
        validateEnvironments(env);
        declareComponentRegistries(env);
        buildTransforms(env);
        // WI12 (plan 2358): declared-requirements consistency runs AFTER
        // buildTransforms — the 2PC verdict needs the resolved sink beans.
        validateDeclaredRequirements();
        return env;
    }

    /**
     * CRA 8.45 R4-05 W-07 (1.3-C1-005): entry-time defensive deep copy of the parsed
     * model — a structural clone of the model node graph: the generated
     * {@code cloneInstance()} shell per node plus an independent copy of every
     * collection property (element models cloned recursively, the generated setters
     * re-normalize to {@code KeyedList}). All subsequent expansion passes rewrite
     * only the copy, so the caller-held model is never consumed.
     *
     * <p>Leaf values (strings, enums, numbers and compiled
     * {@code IEvalAction}/{@code IEvalFunction} bodies) are shared by reference: the
     * expansion passes never mutate them. The platform xdef round-trip
     * (model → XNode → model) is deliberately NOT used here: expression-domain
     * properties ({@code keyExpr} etc.) are compiled to {@code IEvalAction} at parse
     * time and do not survive that round-trip (execution-period finding E-2538-1,
     * registered in plan 2538) — the same mechanism {@code expandSqlModel} uses for
     * its compiler products only works because compiler output is a plain XML string
     * with no compiled-expression properties.
     */
    private static StreamModel defensiveCopy(StreamModel model) {
        return (StreamModel) deepCopyComponent(model);
    }

    /**
     * Clone one generated component-model node ({@code cloneInstance()} is declared
     * on every generated model class) and then give it its own copies of all
     * collection-typed properties, discovered via the generated JavaBean
     * {@code getXxx()} accessors (the generated setters rebuild the keyed list
     * wrappers, so the copy keeps the same by-name lookup behavior). Property
     * discovery deliberately does not go through
     * {@code ReflectionManager.getClassModel(...).getFields()}: that registry can
     * come back with an empty field set at runtime (observed live, E-2538-1), while
     * the generated accessor pattern is a hard guarantee of the codegen.
     */
    private static AbstractComponentModel deepCopyComponent(AbstractComponentModel src) {
        AbstractComponentModel copy;
        try {
            copy = (AbstractComponentModel) src.getClass().getMethod("cloneInstance").invoke(src);
        } catch (ReflectiveOperationException e) {
            throw new StreamException(ERR_STREAM_INVALID_ARG, e)
                    .param(ARG_ELEMENT, "<stream>")
                    .param(ARG_DETAIL, "model class does not expose the generated cloneInstance(): "
                            + src.getClass().getName())
                    .loc(src.getLocation());
        }
        Set<String> copiedProps = new HashSet<>();
        for (Class<?> c = src.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getParameterCount() != 0 || m.getReturnType() == void.class
                        || Modifier.isStatic(m.getModifiers())
                        || !m.getName().startsWith("get") || m.getName().equals("getClass")) {
                    continue;
                }
                String propName = Introspector.decapitalize(m.getName().substring(3));
                if (!copiedProps.add(propName)) {
                    continue;
                }
                Object value;
                try {
                    value = m.invoke(src);
                } catch (ReflectiveOperationException e) {
                    throw new StreamException(ERR_STREAM_INVALID_ARG, e)
                            .param(ARG_ELEMENT, "<stream>")
                            .param(ARG_DETAIL, "cannot read model property '" + propName
                                    + "' for the zero-consumption snapshot: " + e.getMessage())
                            .loc(src.getLocation());
                }
                if (!(value instanceof List)) {
                    continue;
                }
                copyListProperty(src, copy, c, propName, (List<?>) value);
            }
        }
        return copy;
    }

    /**
     * Give the copy its own list instance with recursively cloned element models.
     * Leaf elements (compiled {@code IEvalAction}/{@code IEvalFunction} bodies,
     * strings, enums) are shared by reference — the expansion passes never mutate
     * them, only the container and the model-node references.
     */
    private static void copyListProperty(AbstractComponentModel src, AbstractComponentModel copy,
                                         Class<?> declaring, String propName, List<?> value) {
        List<Object> copied = new ArrayList<>(value.size());
        for (Object item : value) {
            copied.add(item instanceof AbstractComponentModel
                    ? deepCopyComponent((AbstractComponentModel) item)
                    : item);
        }
        String setterName = "set" + Character.toUpperCase(propName.charAt(0))
                + propName.substring(1);
        Method setter = null;
        for (Class<?> c = declaring; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(setterName) && m.getParameterCount() == 1
                        && List.class.isAssignableFrom(m.getParameterTypes()[0])) {
                    setter = m;
                    break;
                }
            }
            if (setter != null) {
                break;
            }
        }
        if (setter == null) {
            // no writable setter (computed/read-only list facet) — nothing to detach
            return;
        }
        try {
            setter.invoke(copy, copied);
        } catch (ReflectiveOperationException e) {
            throw new StreamException(ERR_STREAM_INVALID_ARG, e)
                    .param(ARG_ELEMENT, "<stream>")
                    .param(ARG_DETAIL, "cannot write copied list property '" + propName
                            + "' for the zero-consumption snapshot: " + e.getMessage())
                    .loc(src.getLocation());
        }
    }

    /**
     * WI12 (plan 2358): the declared {@code <environments>} profile is a build-time
     * configuration facet. Semantics: zero environments = current behavior (LOCAL
     * default); exactly one environment applies its {@code deploymentMode} directly
     * (attribute omitted = keep the LOCAL default — setDeploymentMode rejects null);
     * MORE than one environment has no selection mechanism → typed reject (collapse
     * to a single profile or switch via a Delta overlay instead).
     *
     * <p>DISTRIBUTED + an ACTIVE checkpoint declaration (mirroring the
     * {@code enableCheckpointing} trigger: enabled && interval&gt;0) is a typed
     * reject: the checkpoint-engine path runs BEFORE the DISTRIBUTED branch in
     * {@code execute()} and does not read deploymentMode, so the declared mode would
     * be silently inert in that combination (restart trigger: the checkpoint engine
     * gains distributed-mode support). {@code enabled="false"} + DISTRIBUTED is legal.
     */
    private void validateEnvironments(StreamExecutionEnvironment env) {
        List<io.nop.stream.flow.model.StreamEnvironmentModel> environments = model.getEnvironments();
        if (environments == null || environments.isEmpty()) {
            return;
        }
        if (environments.size() > 1) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ELEMENT, "<environments>")
                    .param(ARG_DETAIL, environments.size() + " environment profiles declared; the"
                            + " builder applies exactly one — collapse to a single profile"
                            + " (switching profiles is a build-time overlay, not a runtime"
                            + " selection): " + environments.stream()
                            .map(io.nop.stream.flow.model.StreamEnvironmentModel::getName)
                            .collect(java.util.stream.Collectors.toList()))
                    .loc(model.getLocation());
        }
        io.nop.stream.flow.model.StreamEnvironmentModel environment = environments.get(0);
        io.nop.stream.core.execution.DeploymentMode mode = environment.getDeploymentMode();
        boolean activeCheckpoint = model.getCheckpoint() != null
                && model.getCheckpoint().isEnabled()
                && model.getCheckpoint().getInterval() > 0;
        if (mode == io.nop.stream.core.execution.DeploymentMode.DISTRIBUTED && activeCheckpoint) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ELEMENT, elementDesc(environment))
                    .param(ARG_DETAIL, "environment '" + environment.getName()
                            + "' declares deploymentMode=DISTRIBUTED while an active checkpoint"
                            + " declaration (enabled=true, interval>0) is present; the checkpoint"
                            + " engine path runs before the distributed branch and does not read"
                            + " deploymentMode, so the declared mode would be silently inert —"
                            + " drop the checkpoint declaration or use LOCAL until the checkpoint"
                            + " engine supports distributed mode")
                    .loc(environment.getLocation());
        }
        if (mode != null) {
            env.setDeploymentMode(mode);
        }
    }

    /**
     * WI12 (plan 2358): declared-requirements consistency. The declaration semantics
     * is "capabilities this job NEEDS", so only statically decidable requirements are
     * accepted:
     * <ul>
     *   <li>{@code TWO_PHASE_COMMIT_SINK} — checked against the resolved sink beans
     *       (the same instances the graph generator's detectRequirements inspects):
     *       declared but no 2PC sink present = declaration is false → typed reject;
     *       a 2PC sink without the declaration is fine (a requirement is not a
     *       census).</li>
     *   <li>{@code DISTRIBUTED_EXECUTION} — always aggregated by the engine, so the
     *       declaration is always satisfiable.</li>
     *   <li>Every other value has no static verdict at build time → typed reject
     *       (the declarative surface does not accept what it cannot check — no silent
     *       pass-through).</li>
     * </ul>
     */
    private void validateDeclaredRequirements() {
        List<io.nop.stream.flow.model.StreamRequirementModel> requirements = model.getRequirements();
        if (requirements == null || requirements.isEmpty()) {
            return;
        }
        boolean twoPhaseDeclared = false;
        for (io.nop.stream.flow.model.StreamRequirementModel req : requirements) {
            io.nop.stream.core.model.StreamRequirement value = req.getValue();
            if (value == io.nop.stream.core.model.StreamRequirement.TWO_PHASE_COMMIT_SINK) {
                twoPhaseDeclared = true;
            } else if (value != io.nop.stream.core.model.StreamRequirement.DISTRIBUTED_EXECUTION) {
                throw new StreamException(ERR_STREAM_INVALID_ARG)
                        .param(ARG_ELEMENT, "<requirements>")
                        .param(ARG_DETAIL, "requirement '" + value + "' has no build-time static"
                                + " verdict — the declarative surface only accepts"
                                + " TWO_PHASE_COMMIT_SINK (checked against the resolved sinks)"
                                + " and DISTRIBUTED_EXECUTION (always aggregated by the engine)")
                        .loc(req.getLocation());
            }
        }
        if (twoPhaseDeclared) {
            boolean present = resolvedSinkBeans.values().stream()
                    .anyMatch(fn -> fn instanceof io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction);
            if (!present) {
                throw new StreamException(ERR_STREAM_INVALID_ARG)
                        .param(ARG_ELEMENT, "<requirements>")
                        .param(ARG_DETAIL, "TWO_PHASE_COMMIT_SINK is declared but no sink in this"
                                + " job resolves to a TwoPhaseCommitSinkFunction — the requirement"
                                + " declaration is false (a requirement is not a census: leaving"
                                + " it out is always legal)")
                        .loc(requirements.get(0).getLocation());
            }
        }
    }

    /**
     * WI12 (plan 2358): declared {@code <checkpointParticipants>} — an explicit
     * contract pin on which transforms participate in checkpointing. Participation
     * is decided by the FUNCTION BEAN implementing {@code CheckpointParticipant}
     * (the runtime tests operator/UDF instances, never DSL types), so:
     * <ul>
     *   <li>the participant id must reference an existing transform
     *       (ERR_STREAM_REF_UNKNOWN — typo protection),</li>
     *   <li>the target must be a function-carrying transform
     *       (source/sink/map/filter/flatMap/process/custom/aggregate/reduce) —
     *       keyBy/union/sideOutput/window/cep/timestampsAndWatermarks have no
     *       function the runtime could ask (ERR_STREAM_INVALID_ARG),</li>
     *   <li>xpl-form targets are rejected outright — the generated wrappers
     *       (XplSinkFunction etc.) never implement CheckpointParticipant, so the
     *       declaration could never hold (ERR_STREAM_INVALID_ARG),</li>
     *   <li>bean-form targets are resolved at build time and must implement
     *       {@code CheckpointParticipant} (ERR_STREAM_INVALID_ARG) — same-instance
     *       verification with what the runtime will test.</li>
     * </ul>
     */
    private void validateCheckpointParticipants(Map<String, StreamTransformModel> byId) {
        List<io.nop.stream.flow.model.CheckpointParticipantRefModel> participants =
                model.getCheckpointParticipants();
        if (participants == null || participants.isEmpty()) {
            return;
        }
        for (io.nop.stream.flow.model.CheckpointParticipantRefModel p : participants) {
            String id = p.getValue();
            StreamTransformModel target = byId.get(id);
            if (target == null) {
                throw new StreamException(ERR_STREAM_REF_UNKNOWN)
                        .param(ARG_ELEMENT, "<checkpointParticipants>")
                        .param(ARG_REF_TYPE, "transform").param(ARG_REF_NAME, id)
                        .param(ARG_DETAIL, "checkpointParticipant '" + id
                                + "' does not reference an existing transform")
                        .loc(p.getLocation());
            }
            if (!isParticipantCapable(target)) {
                throw new StreamException(ERR_STREAM_INVALID_ARG)
                        .param(ARG_ELEMENT, "<checkpointParticipants>")
                        .param(ARG_DETAIL, "checkpointParticipant '" + id + "' targets transform of"
                                + " type " + target.getClass().getSimpleName()
                                + " which carries no function the runtime could ask —"
                                + " participation is decided by the function bean implementing"
                                + " CheckpointParticipant")
                        .loc(p.getLocation());
            }
            if (target.getBean() == null) {
                throw new StreamException(ERR_STREAM_INVALID_ARG)
                        .param(ARG_ELEMENT, "<checkpointParticipants>")
                        .param(ARG_DETAIL, "checkpointParticipant '" + id + "' targets an xpl-fn"
                                + " transform; the generated wrappers never implement"
                                + " CheckpointParticipant, so the declaration could never hold —"
                                + " use a bean-form function that implements it")
                        .loc(p.getLocation());
            }
            Object bean = beanResolver().resolve(target.getBean(), Object.class);
            if (!(bean instanceof io.nop.stream.core.checkpoint.participant.CheckpointParticipant)) {
                throw new StreamException(ERR_STREAM_INVALID_ARG)
                        .param(ARG_ELEMENT, "<checkpointParticipants>")
                        .param(ARG_DETAIL, "checkpointParticipant '" + id + "': bean '"
                                + target.getBean() + "' ("
                                + bean.getClass().getName()
                                + ") does not implement CheckpointParticipant — the runtime asks"
                                + " the function instance, not the declaration")
                        .loc(p.getLocation());
            }
        }
    }

    /**
     * WI12: transforms whose builder resolves a function bean the runtime can
     * interrogate (the resolveFunctionOrXpl call sites). Virtual/structural
     * transforms carry no such function.
     */
    private static boolean isParticipantCapable(StreamTransformModel t) {
        return t instanceof io.nop.stream.flow.model.StreamSourceModel
                || t instanceof io.nop.stream.flow.model.StreamSinkModel
                || t instanceof io.nop.stream.flow.model.StreamMapModel
                || t instanceof io.nop.stream.flow.model.StreamFilterModel
                || t instanceof io.nop.stream.flow.model.StreamFlatMapModel
                || t instanceof io.nop.stream.flow.model.StreamProcessModel
                || t instanceof io.nop.stream.flow.model.StreamCustomModel
                || t instanceof io.nop.stream.flow.model.StreamAggregateModel
                || t instanceof io.nop.stream.flow.model.StreamReduceModel;
    }

    /**
     * WI21 (§八 11): normalizes the DSL registry declarations (aggregators /
     * joins / schemas) into flow-free {@link StreamComponentEntry} carriers and
     * declares them on the environment, so the core StreamComponents of every
     * materialized model carries them. Descriptive attribute values only — the
     * executable semantics stay with the builder-time validation and the
     * transform assembly.
     */
    private void declareComponentRegistries(StreamExecutionEnvironment env) {
        if (model.getAggregators() != null) {
            for (io.nop.stream.flow.model.StreamAggregatorModel a : model.getAggregators()) {
                Map<String, String> attrs = new LinkedHashMap<>();
                attrs.put("fnId", a.getFnId());
                attrs.put("expr", a.getExpr());
                attrs.put("schemaId", a.getSchemaId());
                env.declareRegistry("aggregators",
                        Collections.singletonMap(a.getAggregatorId(), new StreamComponentEntry("aggregators", a.getAggregatorId(), attrs)));
            }
        }
        if (model.getJoins() != null) {
            for (StreamJoinSpecModel j : model.getJoins()) {
                Map<String, String> attrs = new LinkedHashMap<>();
                attrs.put("joinType", j.getJoinType() == null ? null : j.getJoinType().name());
                attrs.put("leftKeyExprs", j.getLeftKeyExprs());
                attrs.put("rightKeyExprs", j.getRightKeyExprs());
                attrs.put("windowStrategyRef", j.getWindowStrategyRef());
                attrs.put("timeout", j.getTimeout());
                env.declareRegistry("joins",
                        Collections.singletonMap(j.getJoinId(), new StreamComponentEntry("joins", j.getJoinId(), attrs)));
            }
        }
        if (model.getSchemas() != null) {
            for (io.nop.stream.flow.model.StreamSchemaModel sc : model.getSchemas()) {
                StringBuilder fields = new StringBuilder();
                if (sc.getFields() != null) {
                    for (io.nop.stream.flow.model.StreamSchemaFieldModel f : sc.getFields()) {
                        if (fields.length() > 0) {
                            fields.append(',');
                        }
                        fields.append(f.getName()).append(':').append(f.getType());
                    }
                }
                Map<String, String> attrs = new LinkedHashMap<>();
                attrs.put("fields", fields.toString());
                env.declareRegistry("schemas",
                        Collections.singletonMap(sc.getId(), new StreamComponentEntry("schemas", sc.getId(), attrs)));
            }
        }
    }

    // ----------------------------------------------------------------
    // Checkpoint + top-level registry handling
    // ----------------------------------------------------------------

    /**
     * WI17 (plan 25 r2 B4): {@code <sql>} is a model-level GENERATOR — the compilation
     * happens here, before buildTransforms. The provider is looked up by type in the
     * BeanContainer (the IAggregatorFunctionResolver pattern); its XML product is
     * parsed back through the platform DSL parser and REPLACES the parent model's
     * transforms/edges/registries. {@code <sql>} must be the model's only content
     * (coexistence with other transforms/edges/registries fails fast); no provider
     * (including an uninitialized container) fails fast naming the missing
     * nop-stream-sql dependency.
     */
    private void expandSqlModel() {
        io.nop.stream.flow.model.StreamSqlModel sql = model.getSql();
        if (sql == null) {
            return;
        }
        if (model.hasTransforms() || model.hasEdges() || model.hasAggregators()
                || model.hasJoins() || model.hasSchemas() || !model.getWindowingStrategies().isEmpty()) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ELEMENT, "<sql>")
                    .param(ARG_DETAIL, "<sql> must be the model's only content: it compiles to "
                            + "the complete transform/edge/registry set, so coexisting "
                            + "transforms, edges, <aggregators>, <joins>, <schemas> or "
                            + "<windowingStrategies> are rejected")
                    .loc(sql.getLocation());
        }
        if (!io.nop.api.core.ioc.BeanContainer.isInitialized()) {
            throw noSqlProvider(sql);
        }
        ISqlStreamCompiler compiler =
                io.nop.api.core.ioc.BeanContainer.instance()
                        .tryGetBeanByType(ISqlStreamCompiler.class);
        if (compiler == null) {
            throw noSqlProvider(sql);
        }
        Map<String, String> schema = new LinkedHashMap<>();
        Map<String, Long> fieldLateness = new LinkedHashMap<>();
        if (sql.getSchemas() != null) {
            for (io.nop.stream.flow.model.StreamSqlFieldModel f : sql.getSchemas()) {
                schema.put(f.getName(), f.getType());
                // WI25/Q1: time-column lateness annotation (millis); null/<=0 means no
                // declaration — the compiler fails fast on non-time-column declarations.
                java.lang.Long lateness = f.getLateness();
                if (lateness != null && lateness > 0) {
                    fieldLateness.put(f.getName(), lateness);
                }
            }
        }
        // WI37 (D11 release): carry the declared two-tier emit mode into the product.
        // Default-provider compatibility: the no-emit overload delegates to CLOSE.
        String xml = compiler.compileSql(sql.getSource(), schema, sql.getSinkBean(),
                sql.getEmit(), fieldLateness);
        io.nop.core.lang.xml.XNode node;
        try {
            node = io.nop.core.lang.xml.XNode.parse(xml);
        } catch (Exception e) {
            throw new StreamException(ERR_STREAM_INVALID_ARG, e)
                    .param(ARG_ELEMENT, "<sql>")
                    .param(ARG_DETAIL, "SQL compiler returned invalid model XML: " + e.getMessage())
                    .loc(sql.getLocation());
        }
        StreamModel compiled;
        try {
            compiled = (StreamModel) new io.nop.xlang.xdsl.DslModelParser().parseFromNode(node);
        } catch (NopException e) {
            throw new StreamException(ERR_STREAM_INVALID_ARG, e)
                    .param(ARG_ELEMENT, "<sql>")
                    .param(ARG_DETAIL, "SQL compiler product failed stream.xdef validation: "
                            + e.getMessage())
                    .loc(sql.getLocation());
        }
        // 替换父模型内容（B4）：transforms/edges/registries 全量来自编译产物
        model.setTransforms(compiled.getTransforms());
        model.setEdges(compiled.getEdges());
        model.setSchemas(compiled.getSchemas());
        model.setAggregators(compiled.getAggregators());
        model.setJoins(compiled.getJoins());
        model.setWindowingStrategies(compiled.getWindowingStrategies());
        model.setSql(null);
    }

    private static StreamException noSqlProvider(io.nop.stream.flow.model.StreamSqlModel sql) {
        return (StreamException) new StreamException(ERR_STREAM_INVALID_ARG)
                .param(ARG_ELEMENT, "<sql>")
                .param(ARG_DETAIL, "<sql sinkBean='" + sql.getSinkBean()
                        + "'> declares a SQL model generator but no "
                        + ISqlStreamCompiler.class.getName()
                        + " provider is registered; add io.github.entropy-cloud:nop-stream-sql "
                        + "to the classpath")
                .loc(sql.getLocation());
    }

    /**
     * U2 (deferred-unlock): expand every {@code <sqlTransform>} vertex into the
     * operator-chain fragment produced by {@link ISqlStreamCompiler#compileTransform}.
     * Runs after {@link #expandSqlModel()} (the top-level generator keeps priority)
     * and before registry/transform validation, so downstream passes only ever see
     * ordinary transforms/edges. Splice contract (plan 2375, review conditions M1-M4):
     * the fragment is a complete xdef-valid stream model whose FROM-table source
     * vertex carries the reserved id {@link ISqlStreamCompiler#TRANSFORM_ENTRY_ID} —
     * it is stripped and replaced by the declaring vertex's single upstream edge
     * (plain FORWARD, no outputTag); fragment ids (transforms/edges/registries and
     * their refs) are determinically renamed with the vertex-id prefix; the fragment
     * terminal feeds every downstream edge (fan-out allowed).
     */
    private void expandSqlTransforms() {
        if (model.getTransforms() == null || model.getTransforms().isEmpty()) {
            return;
        }
        for (io.nop.stream.flow.model.StreamTransformModel t : new ArrayList<>(model.getTransforms())) {
            if (t instanceof io.nop.stream.flow.model.StreamSqlTransformModel) {
                expandSqlTransformVertex((io.nop.stream.flow.model.StreamSqlTransformModel) t);
            }
        }
    }

    private void expandSqlTransformVertex(io.nop.stream.flow.model.StreamSqlTransformModel v) {
        String vid = v.getId();
        if (!io.nop.api.core.ioc.BeanContainer.isInitialized()) {
            throw noSqlTransformProvider(v);
        }
        ISqlStreamCompiler compiler =
                io.nop.api.core.ioc.BeanContainer.instance()
                        .tryGetBeanByType(ISqlStreamCompiler.class);
        if (compiler == null) {
            throw noSqlTransformProvider(v);
        }

        // input contract: in-degree exactly 1, plain FORWARD edge without outputTag —
        // a deleted edge's HASH keyBy effect or tag channel would otherwise be
        // silently dropped (P1-XDSL-5: every declaration is consumed or rejected)
        StreamEdgeModel inEdge = null;
        int inDegree = 0;
        int outDegree = 0;
        for (StreamEdgeModel e : model.getEdges()) {
            if (vid.equals(e.getTo())) {
                inDegree++;
                inEdge = e;
            }
            if (vid.equals(e.getFrom())) {
                outDegree++;
            }
        }
        if (inDegree == 0) {
            throw invalidSqlTransform(v, "has no incoming edge: a SQL transform consumes "
                    + "exactly one upstream stream");
        }
        if (inDegree > 1) {
            throw invalidSqlTransform(v, "has " + inDegree + " incoming edges: a SQL "
                    + "transform consumes exactly one upstream stream (multi-entry fusion "
                    + "is not supported — use <union> upstream)");
        }
        if (inEdge.getPartition() != null && inEdge.getPartition() != PartitionPolicy.FORWARD) {
            throw invalidSqlTransform(v, "input edge must be partition=FORWARD (a HASH "
                    + "edge's keyExpr would be silently dropped by the splice)");
        }
        if (inEdge.getOutputTag() != null) {
            throw invalidSqlTransform(v, "input edge must not declare outputTag (the tag "
                    + "channel would be silently dropped by the splice)");
        }
        if (outDegree == 0) {
            throw invalidSqlTransform(v, "has no outgoing edge: the fragment result would "
                    + "be unreachable");
        }
        String upstream = inEdge.getFrom();

        Map<String, String> schema = new LinkedHashMap<>();
        Map<String, Long> fieldLateness = new LinkedHashMap<>();
        if (v.getSchemas() != null) {
            for (io.nop.stream.flow.model.StreamSqlFieldModel f : v.getSchemas()) {
                schema.put(f.getName(), f.getType());
                java.lang.Long lateness = f.getLateness();
                if (lateness != null && lateness > 0) {
                    fieldLateness.put(f.getName(), lateness);
                }
            }
        }

        String xml = compiler.compileTransform(v.getSource(), schema, v.getEmit(), fieldLateness);
        io.nop.core.lang.xml.XNode node;
        try {
            node = io.nop.core.lang.xml.XNode.parse(xml);
        } catch (Exception e) {
            throw invalidSqlTransform(v, "SQL compiler returned invalid fragment XML: "
                    + e.getMessage(), e);
        }
        String prefix = vid + "_";
        stripAndRenameFragment(node, upstream, prefix);
        StreamModel fragment;
        try {
            fragment = (StreamModel) new io.nop.xlang.xdsl.DslModelParser().parseFromNode(node);
        } catch (NopException e) {
            throw invalidSqlTransform(v, "SQL fragment failed stream.xdef validation: "
                    + e.getMessage(), e);
        }

        // exit = the unique fragment terminal (it is the source of no internal edge;
        // the stripped entry has already left the parsed fragment)
        java.util.Set<String> sources = new java.util.HashSet<>();
        for (StreamEdgeModel e : fragment.getEdges()) {
            sources.add(e.getFrom());
        }
        String exit = null;
        for (io.nop.stream.flow.model.StreamTransformModel t : fragment.getTransforms()) {
            if (!sources.contains(t.getId())) {
                if (exit != null) {
                    throw invalidSqlTransform(v, "fragment has multiple terminal transforms ("
                            + exit + ", " + t.getId() + "): the product must be a single chain");
                }
                exit = t.getId();
            }
        }
        if (exit == null) {
            throw invalidSqlTransform(v, "fragment has no terminal transform");
        }

        // splice transforms (id-collision check is a safety net — the rename makes
        // collisions impossible for unique vertex ids); the fragment takes the
        // declaring vertex's position to keep the model readable
        java.util.List<io.nop.stream.flow.model.StreamTransformModel> newTransforms =
                new ArrayList<>(model.getTransforms());
        int vIndex = newTransforms.indexOf(v);
        newTransforms.remove(vIndex);
        java.util.Set<String> existingIds = new java.util.HashSet<>();
        for (io.nop.stream.flow.model.StreamTransformModel t : newTransforms) {
            existingIds.add(t.getId());
        }
        for (io.nop.stream.flow.model.StreamTransformModel t : fragment.getTransforms()) {
            if (!existingIds.add(t.getId())) {
                throw invalidSqlTransform(v, "fragment transform id collision: " + t.getId());
            }
        }
        newTransforms.addAll(vIndex, fragment.getTransforms());
        model.setTransforms(newTransforms);

        // splice edges: the replaced input edge's position carries the fragment's
        // internal edges; every downstream edge retargets to the fragment terminal
        java.util.List<StreamEdgeModel> newEdges = new ArrayList<>();
        for (StreamEdgeModel e : model.getEdges()) {
            if (vid.equals(e.getTo())) {
                newEdges.addAll(fragment.getEdges());
            } else if (vid.equals(e.getFrom())) {
                e.setFrom(exit);
                newEdges.add(e);
            } else {
                newEdges.add(e);
            }
        }
        model.setEdges(newEdges);

        // merge fragment registries (ids already renamed with the vertex prefix)
        if (fragment.getSchemas() != null) {
            fragment.getSchemas().forEach(model::addSchema);
        }
        if (fragment.getAggregators() != null) {
            fragment.getAggregators().forEach(model::addAggregator);
        }
        if (fragment.getJoins() != null) {
            fragment.getJoins().forEach(model::addJoinSpec);
        }
        if (fragment.getWindowingStrategies() != null
                && !fragment.getWindowingStrategies().isEmpty()) {
            java.util.List<io.nop.stream.flow.model.WindowingStrategyModel> ws =
                    new ArrayList<>(model.getWindowingStrategies());
            ws.addAll(fragment.getWindowingStrategies());
            model.setWindowingStrategies(ws);
        }
    }

    /**
     * Node-tree level strip + rename, run before the DSL parse so the typed merge
     * never has to touch id bookkeeping: removes the reserved entry source vertex,
     * renames every id/ref attr with the vertex prefix, and rewires the fragment's
     * entry edges to the upstream vertex id.
     */
    private static void stripAndRenameFragment(io.nop.core.lang.xml.XNode streamNode,
                                               String upstream, String prefix) {
        io.nop.core.lang.xml.XNode transforms = childByTag(streamNode, "transforms");
        if (transforms == null || transforms.getChildren() == null) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ELEMENT, "<sqlTransform>")
                    .param(ARG_DETAIL, "SQL fragment product has no <transforms> section");
        }
        io.nop.core.lang.xml.XNode entry = null;
        for (io.nop.core.lang.xml.XNode t : transforms.getChildren()) {
            Object id = t.getAttr("id");
            if (id != null && ISqlStreamCompiler.TRANSFORM_ENTRY_ID.equals(id)) {
                if (entry != null) {
                    throw duplicateEntry();
                }
                entry = t;
            }
        }
        if (entry == null) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ELEMENT, "<sqlTransform>")
                    .param(ARG_DETAIL, "SQL fragment product has no source vertex with the "
                            + "reserved entry id '" + ISqlStreamCompiler.TRANSFORM_ENTRY_ID
                            + "' (SPI contract violation)");
        }
        transforms.removeChild(entry);

        renameIds(streamNode, prefix, upstream);
    }

    private static StreamException duplicateEntry() {
        return (StreamException) new StreamException(ERR_STREAM_INVALID_ARG)
                .param(ARG_ELEMENT, "<sqlTransform>")
                .param(ARG_DETAIL, "SQL fragment product declares multiple entry sources "
                        + "(id='" + ISqlStreamCompiler.TRANSFORM_ENTRY_ID + "'): a fragment "
                        + "has exactly one entry");
    }

    private static io.nop.core.lang.xml.XNode childByTag(io.nop.core.lang.xml.XNode parent,
                                                         String tag) {
        if (parent.getChildren() == null) {
            return null;
        }
        for (io.nop.core.lang.xml.XNode c : parent.getChildren()) {
            if (tag.equals(c.getTagName())) {
                return c;
            }
        }
        return null;
    }

    private static final java.util.Set<String> FRAGMENT_ID_ATTRS = new java.util.HashSet<>(
            java.util.Arrays.asList("id", "strategyId", "aggregatorId", "joinId",
                    "strategyRef", "aggregatorRef", "joinRef", "schemaId",
                    "windowStrategyRef"));

    /** Recursive rename of every fragment-local id/ref attr; edge endpoints are
     * special-cased (entry → upstream, everything else prefixed). */
    private static void renameIds(io.nop.core.lang.xml.XNode node, String prefix,
                                  String upstream) {
        if (node.getChildren() != null) {
            for (io.nop.core.lang.xml.XNode child : node.getChildren()) {
                renameIds(child, prefix, upstream);
            }
        }
        if ("edge".equals(node.getTagName())) {
            Object from = node.getAttr("from");
            if (from != null) {
                node.setAttr("from", ISqlStreamCompiler.TRANSFORM_ENTRY_ID.equals(from)
                        ? upstream : prefix + from);
            }
            Object to = node.getAttr("to");
            if (to != null) {
                node.setAttr("to", prefix + to);
            }
            Object eid = node.getAttr("id");
            if (eid != null) {
                node.setAttr("id", prefix + eid);
            }
            return;
        }
        for (String attr : FRAGMENT_ID_ATTRS) {
            Object value = node.getAttr(attr);
            if (value != null) {
                node.setAttr(attr, prefix + value);
            }
        }
    }

    private static StreamException invalidSqlTransform(
            io.nop.stream.flow.model.StreamSqlTransformModel v, String detail) {
        return invalidSqlTransform(v, detail, null);
    }

    private static StreamException invalidSqlTransform(
            io.nop.stream.flow.model.StreamSqlTransformModel v, String detail, Throwable cause) {
        StreamException e = (StreamException) new StreamException(ERR_STREAM_INVALID_ARG, cause)
                .param(ARG_ELEMENT, "<sqlTransform>")
                .param(ARG_DETAIL, "<sqlTransform id='" + v.getId() + "'> " + detail);
        e.loc(v.getLocation());
        return e;
    }

    private static StreamException noSqlTransformProvider(
            io.nop.stream.flow.model.StreamSqlTransformModel v) {
        return (StreamException) new StreamException(ERR_STREAM_INVALID_ARG)
                .param(ARG_ELEMENT, "<sqlTransform>")
                .param(ARG_DETAIL, "<sqlTransform id='" + v.getId()
                        + "'> declares a SQL transform but no "
                        + ISqlStreamCompiler.class.getName()
                        + " provider is registered; add io.github.entropy-cloud:nop-stream-sql "
                        + "to the classpath")
                .loc(v.getLocation());
    }

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
        // WI12 (plan 2358): the <streams> registry was REMOVED from the schema — the
        // "named stream cross-operator reference" face never had a consumer (the
        // transforms+edges graph expresses the dataflow completely); a leftover
        // declaration now fails at the xdef parse layer.
        // WI11 (plan 2357): <sideInputs> released — validated and materialized by
        // validateSideInputs/materializeSideInputs, injected into the xpl-fn call
        // scopes of its target transforms. The NOT_IMPLEMENTED gap is gone.
        // WI12 (plan 2358): <environments>/<requirements>/<checkpointParticipants>
        // released — validated by validateEnvironments (build-time, needs env) and
        // validateDeclaredRequirements/validateCheckpointParticipants (after
        // buildTransforms, when function beans are resolved). The <coders> registry
        // STAYS a Java-only surface (see the message below).
        if (model.getOnStart() != null || model.getOnEnd() != null || model.getOnError() != null) {
            // WI13 (plan 2359): lifecycle callbacks are a Java-only surface for now.
            // The runtime already has a job event bus (StreamJobEvent
            // JOB_STARTED/JOB_FINISHED/JOB_FAILED), but the declarative wiring lacks
            // the streamCtx context type, the onStart timing design, a bus on the
            // local execute() path, and an owner authorization — so the DSL surface
            // stays fail-fast until those four gaps close.
            throw new StreamException(ERR_STREAM_NOT_IMPLEMENTED)
                    .param(ARG_DETAIL, "lifecycle callbacks are a Java-only surface: the"
                            + " runtime job event bus (StreamJobEventBus) is the designated"
                            + " wiring seam, but the streamCtx context type, the onStart timing"
                            + " contract, the local-execute bus coverage and the owner"
                            + " authorization are not in place yet")
                    .loc(model.getLocation());
        }
        // WI8b: <schemas> declaration surface now has a consumer — fields resolve to
        // BasicTypeInfo at build time (managed names only, first error fails fast);
        // the field coder is the built-in SimpleTypeSerializer. The <coders> registry
        // stays a Java-only surface: wire formats attach at the connector layer
        // (multi-engine WI18's AvroWireFormat), so the declarative registry has no
        // consumer. The error code is pinned to NOT_IMPLEMENTED — the WI1 contract
        // ledger and the nop-lint invalid fixtures depend on it.
        this.schemaRegistry = StreamSchemaRegistry.resolveSchemas(model);
        if (model.hasCoders()) {
            throw new StreamException(ERR_STREAM_NOT_IMPLEMENTED)
                    .param(ARG_DETAIL, "<coders> is a Java-only surface: wire formats attach at"
                            + " the connector layer and the declarative registry has no consumer;"
                            + " declare serializers on the Java side instead")
                    .loc(model.getLocation());
        }
    }

    // ----------------------------------------------------------------
    // Transform DAG walk
    // ----------------------------------------------------------------

    private void buildTransforms(StreamExecutionEnvironment env) {
        Map<String, StreamTransformModel> byId = new LinkedHashMap<>();
        Map<String, Set<String>> upstreams = new HashMap<>();
        List<StreamEdgeModel> edges = validateDag(byId, upstreams);

        // WI11 (plan 2357): structural side-input checks first (validateDag stage, all
        // typed rejects), then the build-time snapshot materialization — each declared
        // sideInput's Supplier bean is resolved and get()-invoked exactly once here,
        // freezing the read-only snapshot into the xpl call scopes of its target.
        validateSideInputs(byId);
        materializeSideInputs();
        // WI12 (plan 2358): checkpoint-participant contract pins (id spelling,
        // function-carrying shape, bean instanceof) — same validateDag-stage pattern.
        validateCheckpointParticipants(byId);

        List<StreamTransformModel> ordered = topologicalOrderTransforms(byId, upstreams, edges);

        // R6 FLOW-02 (plan 2326): an edge INTO a source was previously accepted
        // and then silently ignored by buildSource (which never consumes
        // upstreamIds) — the declared dependency vanished. Typed reject, run
        // AFTER the cycle check so a cyclic graph still reports its dedicated
        // ERR_STREAM_CYCLIC_JOB_GRAPH (the cycle tests pin that code).
        for (StreamEdgeModel e : edges) {
            if (byId.get(e.getTo()) instanceof StreamSourceModel) {
                throw new StreamException(ERR_STREAM_INVALID_STATE)
                        .param(ARG_ELEMENT, "edge '" + e.getId() + "'")
                        .param(ARG_DETAIL, "edge '" + e.getId() + "' targets source transform '"
                                + e.getTo() + "'; sources consume no upstream input — declare the "
                                + "edge from the source instead (R6 FLOW-02)")
                        .loc(e.getLocation());
            }
        }

        for (StreamTransformModel t : ordered) {
            Object built = buildTransform(env, t, upstreams.get(t.getId()));
            streamRegistry.put(t.getId(), built);
            // WI8 Phase 2 (plan 2332): stamp a declared <edge outputTag> onto the built
            // transformation so the graph layer marks its incoming edge as a tag edge.
            applyTagEdgeDeclaration(t, built);
        }
    }

    /**
     * Validates the declared transform/edge graph: every transform carries a non-null
     * unique id, every edge carries a unique id and references known transforms, and
     * every declared edge attribute is consumed or rejected (via
     * {@link #validateEdgeDeclarations}). Fills {@code byId} with the id → transform
     * mapping and {@code upstreams} with the per-transform upstream id sets; returns the
     * validated edge list (never {@code null}).
     */
    private List<StreamEdgeModel> validateDag(Map<String, StreamTransformModel> byId,
                                              Map<String, Set<String>> upstreams) {
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
        validateSideOutputDeclarations(edges, byId);
        validateJoinDeclarations(edges, upstreams);
        validateSinkReachability(edges, byId);
        return edges;
    }

    /**
     * WI8 Phase 2 (plan 2332): build-time pairing validation between {@code <sideOutput>}
     * declarations and their consuming {@code <edge ... outputTag>} edges. A tag id is a
     * GLOBAL identity (OutputTag equality is id-based) and its runtime registration is
     * replace-by-id semantics — so every failure mode here is a typed reject, never a
     * silent topology change:
     * <ol>
     *   <li>a tag id declared by two {@code <sideOutput>} nodes (ambiguous producer),</li>
     *   <li>a consuming edge whose outputTag has no declared sideOutput node at its
     *       {@code from} id (consumes an undeclared tag),</li>
     *   <li>a declared tag with NO consuming edge (the tap would emit into the void —
     *       the consumption side fails fast at runtime instead, but the pairing error
     *       is a declaration typo and belongs at build time),</li>
     *   <li>the same tag consumed by multiple edges (second registration would silently
     *       take over the first),</li>
     *   <li>{@code partition} (other than the FORWARD default) or {@code keyExpr} on a
     *       tag edge — a tag edge follows the producer's partitioning,</li>
     *   <li>a tag-edge target that is not a single-input one-input transform
     *       (map/filter/flatmap/process/custom): keyed/windowed/union/join targets
     *       cannot take a side-output input, and sink targets consume side outputs via
     *       the Java API {@code getSideOutput(tag)} view instead.</li>
     * </ol>
     *
     * <p>WI10 (plan 2356): {@code <window lateData="tag">} declarations register a
     * SECOND class of tag producer — the window operator's late-data channel. The
     * window itself is a virtual build point (no vertex of its own), so the consuming
     * tag edge starts at the window's HOST vertex: the single aggregate/reduce
     * transform that directly consumes the window node. Checks (in order, so the
     * error for the minimal broken model is stable):
     * <ol>
     *   <li>a blank {@code lateData} value (ERR_STREAM_REQUIRED_ATTR),</li>
     *   <li>a lateData tag colliding with a {@code <sideOutput>} tag or another
     *       window's lateData tag (ERR_STREAM_DUPLICATE_ID),</li>
     *   <li>the window's host vertex count is not exactly one — zero hosts (no
     *       aggregate/reduce downstream) or multiple hosts both reject; the first
     *       host is never silently picked (ERR_STREAM_INVALID_ARG),</li>
     *   <li>the lateData tag has no consuming edge, or more than one
     *       (ERR_STREAM_INVALID_ARG),</li>
     *   <li>the consuming edge's {@code from} is not the resolved host vertex
     *       (ERR_STREAM_REF_UNKNOWN),</li>
     *   <li>the same edge constraints as a tap edge apply: target type, non-FORWARD
     *       partition and keyExpr all reject (same codes as the tap path).</li>
     * </ol>
     */
    private void validateSideOutputDeclarations(List<StreamEdgeModel> edges,
                                                Map<String, StreamTransformModel> byId) {
        Map<String, StreamSideOutputModel> nodeByTag = new LinkedHashMap<>();
        for (StreamTransformModel t : model.getTransforms()) {
            if (!(t instanceof StreamSideOutputModel)) {
                continue;
            }
            StreamSideOutputModel so = (StreamSideOutputModel) t;
            StreamSideOutputModel prev = nodeByTag.put(so.getTag(), so);
            if (prev != null) {
                throw new StreamException(ERR_STREAM_DUPLICATE_ID)
                        .param(ARG_ELEMENT, "sideOutput tag")
                        .param(ARG_ID, so.getTag())
                        .param(ARG_DETAIL, "sideOutput tag '" + so.getTag() + "' is declared by two"
                                + " transforms ('" + prev.getId() + "' and '" + so.getId()
                                + "'); a tag id is a global identity — split the producers"
                                + " onto distinct tags")
                        .loc(so.getLocation());
            }
        }

        // WI10 (plan 2356): lateData tag registry. Blank value and tag collisions
        // reject here (checks 1-2) before any edge or host resolution.
        Map<String, StreamWindowModel> lateDataByTag = new LinkedHashMap<>();
        for (StreamTransformModel t : model.getTransforms()) {
            if (!(t instanceof StreamWindowModel)) {
                continue;
            }
            StreamWindowModel w = (StreamWindowModel) t;
            String lateTag = w.getLateData();
            if (lateTag == null) {
                continue;
            }
            if (lateTag.isEmpty()) {
                throw new StreamException(ERR_STREAM_REQUIRED_ATTR)
                        .param(ARG_ELEMENT, elementDesc(w))
                        .param(ARG_ATTR_NAME, "lateData")
                        .loc(w.getLocation());
            }
            StreamSideOutputModel tapOwner = nodeByTag.get(lateTag);
            if (tapOwner != null) {
                throw new StreamException(ERR_STREAM_DUPLICATE_ID)
                        .param(ARG_ELEMENT, elementDesc(w))
                        .param(ARG_ID, lateTag)
                        .param(ARG_OUTPUT_TAG, lateTag)
                        .param(ARG_DETAIL, "window '" + w.getId() + "' declares lateData tag '"
                                + lateTag + "' which is already declared by <sideOutput> '"
                                + tapOwner.getId() + "'; a tag id is a global identity — pick a"
                                + " distinct tag for the late-data channel")
                        .loc(w.getLocation());
            }
            StreamWindowModel prevW = lateDataByTag.put(lateTag, w);
            if (prevW != null) {
                throw new StreamException(ERR_STREAM_DUPLICATE_ID)
                        .param(ARG_ELEMENT, elementDesc(w))
                        .param(ARG_ID, lateTag)
                        .param(ARG_OUTPUT_TAG, lateTag)
                        .param(ARG_DETAIL, "lateData tag '" + lateTag + "' is declared by two"
                                + " windows ('" + prevW.getId() + "' and '" + w.getId()
                                + "'); a tag id is a global identity — split the producers"
                                + " onto distinct tags")
                        .loc(w.getLocation());
            }
        }

        Map<String, List<StreamEdgeModel>> consumerEdgesByTag = new LinkedHashMap<>();
        for (StreamEdgeModel e : edges) {
            if (e.getOutputTag() == null) {
                continue;
            }
            // WI10: a lateData tag edge is validated by the per-window loop below —
            // it must not trip the tap pairing checks (its from is the window's HOST
            // vertex, not a <sideOutput> node).
            if (lateDataByTag.containsKey(e.getOutputTag())) {
                continue;
            }
            StreamSideOutputModel producer = nodeByTag.get(e.getOutputTag());
            if (producer == null || !producer.getId().equals(e.getFrom())) {
                throw new StreamException(ERR_STREAM_REF_UNKNOWN)
                        .param(ARG_ELEMENT, "edge '" + e.getId() + "'")
                        .param(ARG_REF_TYPE, "<sideOutput> declaration")
                        .param(ARG_REF_NAME, e.getOutputTag())
                        .param(ARG_OUTPUT_TAG, e.getOutputTag())
                        .param(ARG_DETAIL, "edge '" + e.getId() + "' consumes outputTag '"
                                + e.getOutputTag() + "' but its from transform '" + e.getFrom()
                                + "' does not declare a <sideOutput> node with that tag; a tag"
                                + " edge must start at the declaring <sideOutput> node")
                        .loc(e.getLocation());
            }
            if (!isTagTargetSupported(byId.get(e.getTo()))) {
                throw new StreamException(ERR_STREAM_INVALID_ARG)
                        .param(ARG_ELEMENT, "edge '" + e.getId() + "'")
                        .param(ARG_OUTPUT_TAG, e.getOutputTag())
                        .param(ARG_DETAIL, "tag edge '" + e.getId() + "' targets transform '"
                                + e.getTo() + "' of type "
                                + byId.get(e.getTo()).getClass().getSimpleName()
                                + "; a tag channel can only feed a single-input transform"
                                + " (map/filter/flatMap/process/custom) — sinks consume side"
                                + " outputs via the Java API getSideOutput(tag) view")
                        .loc(e.getLocation());
            }
            if (e.getPartition() != null && e.getPartition() != PartitionPolicy.FORWARD) {
                throw new StreamException(ERR_STREAM_EDGE_ATTR_UNSUPPORTED)
                        .param(ARG_EDGE_ID, e.getId())
                        .param(ARG_ATTR_NAME, "partition")
                        .param(ARG_DETAIL, "tag edge '" + e.getId() + "' declares partition="
                                + e.getPartition() + "; a tag edge follows the producer's"
                                + " partitioning and cannot re-partition the tag channel")
                        .loc(e.getLocation());
            }
            if (e.getKeyExpr() != null) {
                throw new StreamException(ERR_STREAM_EDGE_ATTR_UNSUPPORTED)
                        .param(ARG_EDGE_ID, e.getId())
                        .param(ARG_ATTR_NAME, "keyExpr")
                        .param(ARG_DETAIL, "tag edge '" + e.getId() + "' declares keyExpr; a tag"
                                + " edge follows the producer's partitioning and cannot"
                                + " re-key the tag channel")
                        .loc(e.getLocation());
            }
            consumerEdgesByTag.computeIfAbsent(e.getOutputTag(), k -> new ArrayList<>()).add(e);
        }

        for (Map.Entry<String, StreamSideOutputModel> entry : nodeByTag.entrySet()) {
            List<StreamEdgeModel> consumers = consumerEdgesByTag.get(entry.getKey());
            StreamSideOutputModel so = entry.getValue();
            if (consumers == null || consumers.isEmpty()) {
                throw new StreamException(ERR_STREAM_INVALID_ARG)
                        .param(ARG_ELEMENT, elementDesc(so))
                        .param(ARG_OUTPUT_TAG, so.getTag())
                        .param(ARG_DETAIL, "sideOutput '" + so.getId() + "' declares tag '"
                                + so.getTag() + "' but no consuming tag edge exists; declare"
                                + " <edge id=\"...\" from=\"" + so.getId() + "\" to=\"<transform>\""
                                + " outputTag=\"" + so.getTag() + "\"/>")
                        .loc(so.getLocation());
            }
            if (consumers.size() > 1) {
                List<String> consumerEdgeIds = new ArrayList<>();
                for (StreamEdgeModel e : consumers) {
                    consumerEdgeIds.add(e.getId());
                }
                throw new StreamException(ERR_STREAM_INVALID_ARG)
                        .param(ARG_ELEMENT, elementDesc(so))
                        .param(ARG_OUTPUT_TAG, so.getTag())
                        .param(ARG_DETAIL, "tag '" + so.getTag() + "' is consumed by multiple"
                                + " edges " + consumerEdgeIds + "; the tag registration is"
                                + " replace-by-id semantics — multiple consumers would"
                                + " silently take each other over. Route additional consumers"
                                + " behind the first tag-fed transform instead")
                        .loc(consumers.get(1).getLocation());
            }
        }

        // WI10 (plan 2356): per-window lateData pairing — host resolution (check 3)
        // then consumer-edge checks (checks 4-6). The window is a virtual build point:
        // its late-data channel leaves the HOST vertex (the single aggregate/reduce
        // transform that directly consumes the window node), so the consuming tag edge
        // must start there, exactly like a tap edge starts at its <sideOutput> node.
        for (Map.Entry<String, StreamWindowModel> entry : lateDataByTag.entrySet()) {
            String lateTag = entry.getKey();
            StreamWindowModel w = entry.getValue();

            Set<String> hostIds = new LinkedHashSet<>();
            for (StreamEdgeModel e : edges) {
                if (!w.getId().equals(e.getFrom())) {
                    continue;
                }
                StreamTransformModel target = byId.get(e.getTo());
                if (target instanceof StreamAggregateModel || target instanceof StreamReduceModel) {
                    hostIds.add(e.getTo());
                }
            }
            if (hostIds.isEmpty()) {
                throw new StreamException(ERR_STREAM_INVALID_ARG)
                        .param(ARG_ELEMENT, elementDesc(w))
                        .param(ARG_OUTPUT_TAG, lateTag)
                        .param(ARG_DETAIL, "window '" + w.getId() + "' declares lateData tag '"
                                + lateTag + "' but has no aggregate/reduce downstream — the"
                                + " late-data channel has no host operator to emit from; add"
                                + " <edge from=\"" + w.getId() + "\" to=\"<aggregate|reduce>\"/>"
                                + " or drop the lateData declaration")
                        .loc(w.getLocation());
            }
            if (hostIds.size() > 1) {
                throw new StreamException(ERR_STREAM_INVALID_ARG)
                        .param(ARG_ELEMENT, elementDesc(w))
                        .param(ARG_OUTPUT_TAG, lateTag)
                        .param(ARG_DETAIL, "window '" + w.getId() + "' declares lateData tag '"
                                + lateTag + "' but feeds multiple aggregate/reduce hosts "
                                + hostIds + "; the late-data channel has no single host vertex"
                                + " — declare the lateData tag on a window with exactly one"
                                + " aggregate/reduce consumer")
                        .loc(w.getLocation());
            }
            String hostId = hostIds.iterator().next();

            List<StreamEdgeModel> consumers = new ArrayList<>();
            for (StreamEdgeModel e : edges) {
                if (lateTag.equals(e.getOutputTag())) {
                    consumers.add(e);
                }
            }
            if (consumers.isEmpty()) {
                throw new StreamException(ERR_STREAM_INVALID_ARG)
                        .param(ARG_ELEMENT, elementDesc(w))
                        .param(ARG_OUTPUT_TAG, lateTag)
                        .param(ARG_DETAIL, "window '" + w.getId() + "' declares lateData tag '"
                                + lateTag + "' but no consuming tag edge exists; declare"
                                + " <edge id=\"...\" from=\"" + hostId + "\" to=\"<transform>\""
                                + " outputTag=\"" + lateTag + "\"/>")
                        .loc(w.getLocation());
            }
            if (consumers.size() > 1) {
                List<String> consumerEdgeIds = new ArrayList<>();
                for (StreamEdgeModel e : consumers) {
                    consumerEdgeIds.add(e.getId());
                }
                throw new StreamException(ERR_STREAM_INVALID_ARG)
                        .param(ARG_ELEMENT, elementDesc(w))
                        .param(ARG_OUTPUT_TAG, lateTag)
                        .param(ARG_DETAIL, "lateData tag '" + lateTag + "' is consumed by"
                                + " multiple edges " + consumerEdgeIds + "; the tag"
                                + " registration is replace-by-id semantics — route"
                                + " additional consumers behind the first tag-fed transform"
                                + " instead")
                        .loc(consumers.get(1).getLocation());
            }
            StreamEdgeModel consumer = consumers.get(0);
            if (!hostId.equals(consumer.getFrom())) {
                throw new StreamException(ERR_STREAM_REF_UNKNOWN)
                        .param(ARG_ELEMENT, "edge '" + consumer.getId() + "'")
                        .param(ARG_REF_TYPE, "<window> lateData declaration")
                        .param(ARG_REF_NAME, lateTag)
                        .param(ARG_OUTPUT_TAG, lateTag)
                        .param(ARG_DETAIL, "edge '" + consumer.getId() + "' consumes lateData"
                                + " tag '" + lateTag + "' but its from transform '"
                                + consumer.getFrom() + "' is not the window's host vertex '"
                                + hostId + "'; the window is a virtual build point — a"
                                + " late-data tag edge must start at the aggregate/reduce"
                                + " transform that consumes window '" + w.getId() + "'")
                        .loc(consumer.getLocation());
            }
            if (!isTagTargetSupported(byId.get(consumer.getTo()))) {
                throw new StreamException(ERR_STREAM_INVALID_ARG)
                        .param(ARG_ELEMENT, "edge '" + consumer.getId() + "'")
                        .param(ARG_OUTPUT_TAG, lateTag)
                        .param(ARG_DETAIL, "lateData tag edge '" + consumer.getId()
                                + "' targets transform '" + consumer.getTo() + "' of type "
                                + byId.get(consumer.getTo()).getClass().getSimpleName()
                                + "; a tag channel can only feed a single-input transform"
                                + " (map/filter/flatMap/process/custom)")
                        .loc(consumer.getLocation());
            }
            if (consumer.getPartition() != null
                    && consumer.getPartition() != PartitionPolicy.FORWARD) {
                throw new StreamException(ERR_STREAM_EDGE_ATTR_UNSUPPORTED)
                        .param(ARG_EDGE_ID, consumer.getId())
                        .param(ARG_ATTR_NAME, "partition")
                        .param(ARG_DETAIL, "lateData tag edge '" + consumer.getId()
                                + "' declares partition=" + consumer.getPartition()
                                + "; a tag edge follows the producer's partitioning and"
                                + " cannot re-partition the tag channel")
                        .loc(consumer.getLocation());
            }
            if (consumer.getKeyExpr() != null) {
                throw new StreamException(ERR_STREAM_EDGE_ATTR_UNSUPPORTED)
                        .param(ARG_EDGE_ID, consumer.getId())
                        .param(ARG_ATTR_NAME, "keyExpr")
                        .param(ARG_DETAIL, "lateData tag edge '" + consumer.getId()
                                + "' declares keyExpr; a tag edge follows the producer's"
                                + " partitioning and cannot re-key the tag channel")
                        .loc(consumer.getLocation());
            }
        }
    }

    /**
     * WI11 (plan 2357): structural checks for {@code <sideInputs>} declarations.
     * Runs in validateDag — BEFORE any build-loop work — so every failure is a typed
     * reject with the declaring element's location. Seven checks:
     * <ol>
     *   <li>duplicate sideInput id (ERR_STREAM_DUPLICATE_ID),</li>
     *   <li>{@code broadcast="false"} — including the shape where the attribute is
     *       omitted and the xdef legacy default (false) is back-filled. Only the
     *       broadcast=true injection style (snapshot copied to every parallel
     *       instance at build time) is supported; no streaming broadcast exists
     *       (vision §七 #G36) (ERR_STREAM_INVALID_ARG),</li>
     *   <li>the {@code to} transform does not exist (ERR_STREAM_REF_UNKNOWN),</li>
     *   <li>the {@code to} transform is not a map/filter/flatMap (ERR_STREAM_INVALID_ARG),</li>
     *   <li>the {@code to} transform is a bean-form map/filter/flatMap — the sideInput
     *       binding can only reach an xpl-fn body; a bean-form function would silently
     *       never see the snapshot, so the detectable shape is rejected instead of
     *       degrading (ERR_STREAM_INVALID_ARG),</li>
     *   <li>the {@code from} bean does not exist (ERR_STREAM_BEAN_NOT_FOUND),</li>
     *   <li>the {@code from} bean is not a {@code Supplier} (ERR_STREAM_BEAN_TYPE_MISMATCH).</li>
     * </ol>
     */
    private void validateSideInputs(Map<String, StreamTransformModel> byId) {
        if (!model.hasSideInputs()) {
            return;
        }
        Set<String> ids = new HashSet<>();
        for (StreamSideInputModel si : model.getSideInputs()) {
            if (!ids.add(si.getId())) {
                throw new StreamException(ERR_STREAM_DUPLICATE_ID)
                        .param(ARG_ELEMENT, elementDesc(si))
                        .param(ARG_ID, si.getId())
                        .param(ARG_DETAIL, "sideInput id '" + si.getId()
                                + "' is declared twice; a sideInput id is the binding key"
                                + " consumers use to reach the snapshot")
                        .loc(si.getLocation());
            }
            if (!si.isBroadcast()) {
                throw new StreamException(ERR_STREAM_INVALID_ARG)
                        .param(ARG_ELEMENT, elementDesc(si))
                        .param(ARG_DETAIL, "sideInput '" + si.getId() + "' declares broadcast=false"
                                + " (or omits the attribute and inherits the legacy xdef default"
                                + " false); only broadcast=true is supported — the snapshot is"
                                + " materialized at build time and copied to every parallel"
                                + " instance; no streaming broadcast exists (vision §七 #G36)")
                        .loc(si.getLocation());
            }
            StreamTransformModel target = byId.get(si.getTo());
            if (target == null) {
                throw new StreamException(ERR_STREAM_REF_UNKNOWN)
                        .param(ARG_ELEMENT, elementDesc(si))
                        .param(ARG_REF_TYPE, "transform").param(ARG_REF_NAME, si.getTo())
                        .param(ARG_DETAIL, "sideInput '" + si.getId() + "' targets transform '"
                                + si.getTo() + "' which does not exist")
                        .loc(si.getLocation());
            }
            if (!(target instanceof StreamMapModel || target instanceof StreamFilterModel
                    || target instanceof StreamFlatMapModel)) {
                throw new StreamException(ERR_STREAM_INVALID_ARG)
                        .param(ARG_ELEMENT, elementDesc(si))
                        .param(ARG_DETAIL, "sideInput '" + si.getId() + "' targets transform '"
                                + si.getTo() + "' of type "
                                + target.getClass().getSimpleName()
                                + "; a side-input snapshot can only be injected into the xpl-fn"
                                + " body of a map/filter/flatMap transform")
                        .loc(si.getLocation());
            }
            if (target.getBean() != null) {
                throw new StreamException(ERR_STREAM_INVALID_ARG)
                        .param(ARG_ELEMENT, elementDesc(si))
                        .param(ARG_DETAIL, "sideInput '" + si.getId() + "' targets transform '"
                                + si.getTo() + "' which declares bean=\"" + target.getBean()
                                + "\"; a bean-form function is assembled by IoC and can never"
                                + " see the side-input binding — switch the target to an xpl-fn"
                                + " body or read the snapshot in the bean's own wiring")
                        .loc(si.getLocation());
            }
            String from = si.getFrom();
            if (!beanResolver().contains(from)) {
                throw new StreamException(ERR_STREAM_BEAN_NOT_FOUND)
                        .param(ARG_ELEMENT, elementDesc(si))
                        .param(ARG_BEAN_NAME, from)
                        .loc(si.getLocation());
            }
            try {
                beanResolver().resolve(from, Supplier.class);
            } catch (StreamException e) {
                // Re-anchor the resolver's typed error to the declaring element so the
                // diagnosis points at the offending <sideInput> line.
                throw e.loc(si.getLocation());
            }
        }
    }

    /**
     * WI11 (plan 2357): build-time snapshot materialization. Every declared
     * sideInput's {@code Supplier} bean is invoked exactly once here — the returned
     * map is the frozen read-only snapshot (low-frequency refresh = restart the job).
     * Targets that collect multiple sideInputs see the union under the single
     * {@code sideInputs} binding. A null snapshot is a typed error, never an empty
     * map: a silently-empty dictionary would look exactly like missing data.
     *
     * <p>CRA 8.45 R4-05 W-01 (1.3-MA-02): the "frozen read-only snapshot" contract is
     * enforced mechanically — the supplier's map is structurally copied and wrapped
     * read-only (recursively through nested Map/List values), so an xpl body write
     * ({@code sideInputs['dict'].put(...)} or a nested value write) fails typed with
     * {@link UnsupportedOperationException} instead of polluting every subsequent
     * record, and the snapshot is detached from the application's live reference
     * (post-build application writes cannot tear through).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void materializeSideInputs() {
        if (!model.hasSideInputs()) {
            return;
        }
        Map<String, Map<String, Object>> snapshotsByTarget = new LinkedHashMap<>();
        for (StreamSideInputModel si : model.getSideInputs()) {
            Supplier<Map<String, Object>> supplier =
                    beanResolver().resolve(si.getFrom(), Supplier.class);
            Map<String, Object> snapshot = supplier.get();
            if (snapshot == null) {
                throw new StreamException(ERR_STREAM_NULL_ARG)
                        .param(ARG_ELEMENT, elementDesc(si))
                        .param(ARG_ARG_NAME, "sideInput snapshot bean '" + si.getFrom() + "'")
                        .param(ARG_DETAIL, "the Supplier returned null — declare an empty map"
                                + " explicitly if the table is genuinely empty")
                        .loc(si.getLocation());
            }
            snapshotsByTarget.computeIfAbsent(si.getTo(), k -> new LinkedHashMap<>())
                    .put(si.getId(), freezeSnapshot(snapshot));
        }
        for (Map.Entry<String, Map<String, Object>> entry : snapshotsByTarget.entrySet()) {
            // the union map bound under "sideInputs" is itself part of the frozen
            // snapshot surface: a top-level write (sideInputs.put(...)) must fail
            // typed exactly like a nested snapshot write (W-01 failure mode ②)
            sideInputScopes.put(entry.getKey(),
                    Collections.singletonMap("sideInputs",
                            Collections.unmodifiableMap(entry.getValue())));
        }
    }

    /**
     * CRA 8.45 R4-05 W-01: structured freeze of a side-input snapshot — defensive
     * top-level copy (detaches the snapshot from the application's live reference),
     * recursive copy + read-only wrapping for nested Map/List values (xpl body writes
     * fail typed with {@link UnsupportedOperationException}), scalar values shared
     * as-is. The value contract is data-shaped structures (Map/List/scalars); custom
     * mutable POJO values are shared by reference after the copy — writing into them
     * is an unsupported usage (declared boundary, not typed-enforced).
     */
    private static Map<String, Object> freezeSnapshot(Map<String, Object> snapshot) {
        Map<String, Object> copy = new LinkedHashMap<>(snapshot);
        for (Map.Entry<String, Object> entry : copy.entrySet()) {
            entry.setValue(freezeValue(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    @SuppressWarnings("unchecked")
    private static Object freezeValue(Object value) {
        if (value instanceof Map) {
            return freezeSnapshot((Map<String, Object>) value);
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(freezeValue(item));
            }
            return Collections.unmodifiableList(copy);
        }
        return value;
    }

    /**
     * WI8 Phase 2 (plan 2332): the transform types that can be fed from a side-output
     * tag channel — single-input, unkeyed-input one-input transforms.
     */
    private static boolean isTagTargetSupported(StreamTransformModel t) {
        return t instanceof StreamMapModel || t instanceof StreamFilterModel
                || t instanceof StreamFlatMapModel || t instanceof StreamProcessModel
                || t instanceof StreamCustomModel;
    }

    /**
     * WI8 Phase 2 (plan 2332): when the transform's declared incoming edge carries an
     * {@code outputTag}, stamps the tag onto the built transformation so the graph
     * layer marks the incoming StreamEdge/JobEdge as a tag edge. The target keeps its
     * regular build wiring (its operator is built on the tap's stream object) — the TAG
     * on the edge is what stops the main-path double delivery and what makes the
     * deployment register the tag consumer feeding this operator.
     */
    private void applyTagEdgeDeclaration(StreamTransformModel t, Object built) {
        StreamEdgeModel tagEdge = null;
        for (StreamEdgeModel e : model.getEdges()) {
            if (!t.getId().equals(e.getTo()) || e.getOutputTag() == null) {
                continue;
            }
            if (tagEdge != null) {
                throw new StreamException(ERR_STREAM_INVALID_ARG)
                        .param(ARG_ELEMENT, elementDesc(t))
                        .param(ARG_DETAIL, "transform '" + t.getId() + "' is the target of"
                                + " multiple outputTag edges ('" + tagEdge.getId() + "', '"
                                + e.getId() + "'); a single-input transform consumes exactly"
                                + " one tag channel")
                        .loc(e.getLocation());
            }
            tagEdge = e;
        }
        if (tagEdge == null) {
            return;
        }
        if (!(built instanceof DataStreamImpl)) {
            throw new StreamException(ERR_STREAM_INVALID_STATE)
                    .param(ARG_ELEMENT, elementDesc(t))
                    .param(ARG_DETAIL, "tag-fed transform '" + t.getId()
                            + "' did not produce a stream object; cannot stamp outputTag '"
                            + tagEdge.getOutputTag() + "' onto its input edge")
                    .loc(t.getLocation());
        }
        io.nop.stream.core.transformation.Transformation<?> transformation =
                ((DataStreamImpl<?>) built).getTransformation();
        if (!(transformation instanceof OneInputTransformation)) {
            throw new StreamException(ERR_STREAM_INVALID_STATE)
                    .param(ARG_ELEMENT, elementDesc(t))
                    .param(ARG_DETAIL, "tag-fed transform '" + t.getId()
                            + "' produced transformation "
                            + transformation.getClass().getSimpleName()
                            + " which cannot carry an input outputTag")
                    .loc(t.getLocation());
        }
        ((OneInputTransformation<?, ?>) transformation)
                .setInputOutputTag(new OutputTag<>(tagEdge.getOutputTag(),
                        UnknownTypeInformation.INSTANCE));
    }

    /**
     * R5 FLOW-2: reverse reachability — every transform must be able to reach a
     * sink. The DAG compile only takes the sink-reachable subgraph, so a
     * misspelled id silently drops the disconnected subtree while build()
     * reports success (zero-output pipeline). Fail fast at build time with the
     * full list of unreachable transforms.
     */
    private void validateSinkReachability(List<StreamEdgeModel> edges,
            Map<String, StreamTransformModel> byId) {
        // Gate: only models that declare at least one sink are checked. Models
        // without any sink are partial-wiring shapes used by contract tests and
        // validators (the empty-compiled-pipeline hazard is covered by the
        // existing build path, not this guard).
        boolean hasSink = false;
        for (StreamTransformModel t : model.getTransforms()) {
            if (t instanceof io.nop.stream.flow.model.StreamSinkModel) {
                hasSink = true;
                break;
            }
        }
        if (!hasSink) {
            return;
        }
        Set<String> reachable = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        for (StreamTransformModel t : model.getTransforms()) {
            if (t instanceof io.nop.stream.flow.model.StreamSinkModel) {
                if (reachable.add(t.getId())) {
                    queue.add(t.getId());
                }
            }
        }
        Map<String, Set<String>> incoming = new HashMap<>();
        for (String id : byId.keySet()) {
            incoming.put(id, new HashSet<>());
        }
        for (StreamEdgeModel e : edges) {
            incoming.get(e.getTo()).add(e.getFrom());
        }
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            for (String prev : incoming.getOrDefault(cur, Collections.emptySet())) {
                if (reachable.add(prev)) {
                    queue.add(prev);
                }
            }
        }
        List<String> unreachable = new ArrayList<>();
        for (String id : byId.keySet()) {
            if (!reachable.contains(id)) {
                unreachable.add(id);
            }
        }
        if (!unreachable.isEmpty()) {
            throw new StreamException(ERR_STREAM_INVALID_STATE)
                    .param(ARG_DETAIL, "transforms not reachable from any sink: " + unreachable
                            + " — a misspelled edge id silently drops this subtree from the"
                            + " compiled pipeline (R5 FLOW-2)");
        }
    }

    /**
     * Orders the validated transforms topologically (Kahn's algorithm) so that every
     * transform is built only after all transforms it consumes have been ordered. Throws
     * {@code ERR_STREAM_CYCLIC_JOB_GRAPH} when the declared graph contains a cycle or an
     * unreachable node.
     */
    private List<StreamTransformModel> topologicalOrderTransforms(
            Map<String, StreamTransformModel> byId,
            Map<String, Set<String>> upstreams,
            List<StreamEdgeModel> edges) {
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
        return ordered;
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
                // WI6: a HASH edge into <union> has no consumer — union merges raw
                // records and cannot carry a per-input keyBy. The supported shape is
                // keyBy AFTER the union (roadmap §3.1).
                if (byId.get(e.getTo()) instanceof StreamUnionModel) {
                    throw new StreamException(ERR_STREAM_EDGE_HASH_REDUNDANT)
                            .param(ARG_EDGE_ID, e.getId()).param(ARG_TRANSFORM_ID, e.getTo())
                            .param(ARG_DETAIL, "HASH edge into <union id='" + e.getTo()
                                    + "'> is not supported: declare keyBy on the transform "
                                    + "AFTER the union instead")
                            .loc(e.getLocation());
                }
                // WI8d: same for <join> — the joinKey set comes from the join spec's
                // leftKeyExprs/rightKeyExprs; a per-input keyBy would double-key.
                if (byId.get(e.getTo()) instanceof StreamJoinModel) {
                    throw new StreamException(ERR_STREAM_EDGE_HASH_REDUNDANT)
                            .param(ARG_EDGE_ID, e.getId()).param(ARG_TRANSFORM_ID, e.getTo())
                            .param(ARG_DETAIL, "HASH edge into <join id='" + e.getTo()
                                    + "'> is not supported: the join keys are declared by the "
                                    + "join spec's leftKeyExprs/rightKeyExprs")
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
                // WI14 (plan 2360): adjudicated keep-reject. The declarative wiring is
                // blocked by three engine-level prerequisites (an edge-declaration
                // carrier reachable from the plan layer, a model-id→vertex-id mapping
                // across the chained graph, and chain-internal edge semantics) plus
                // the missing consumers for receiveWindow/packetSize — an engine-level
                // successor, registered on the roadmap WI14 row.
                throw new StreamException(ERR_STREAM_EDGE_ATTR_UNSUPPORTED)
                        .param(ARG_EDGE_ID, e.getId()).param(ARG_ATTR_NAME, unsupportedAttr)
                        .param(ARG_DETAIL, "edge flow-control attribute '" + unsupportedAttr
                                + "' has no execution carrier on this edge: the declarative"
                                + " wiring is deferred to an engine-level successor (edge"
                                + " config channel, id mapping and chain semantics are the"
                                + " recorded prerequisites; see roadmap WI14)")
                        .loc(e.getLocation());
            }
        }
    }

    /**
     * WI8d: build-time validation of {@code <join>} declarations. Runs inside
     * validateDag (after validateEdgeDeclarations) so every failure surfaces before
     * any transform is built. Eight checks, each fail-fast:
     * unknown joinRef, missing joinType, missing/unequal key sets, exactly-two
     * upstream edges (left/right), windowStrategyRef resolution, window-join
     * joinType restriction (INNER/LEFT — FULL completion is a WI13 item), timeout
     * requiring a window strategy, and timeout format.
     */
    private void validateJoinDeclarations(List<StreamEdgeModel> edges,
                                          Map<String, Set<String>> upstreams) {
        List<StreamJoinSpecModel> specs = model.getJoins() == null
                ? Collections.emptyList() : model.getJoins();
        for (StreamTransformModel t : model.getTransforms()) {
            if (!(t instanceof StreamJoinModel)) {
                continue;
            }
            StreamJoinModel jm = (StreamJoinModel) t;
            StreamJoinSpecModel spec = model.getJoinSpec(jm.getJoinRef());
            if (spec == null) {
                throw new StreamException(ERR_STREAM_REF_UNKNOWN)
                        .param(ARG_ELEMENT, elementDesc(t))
                        .param(ARG_REF_TYPE, "<joins>/<joinSpec>")
                        .param(ARG_REF_NAME, jm.getJoinRef())
                        .loc(t.getLocation());
            }
            if (spec.getJoinType() == null) {
                throw new StreamException(ERR_STREAM_INVALID_ARG)
                        .param(ARG_ELEMENT, elementDesc(t))
                        .param(ARG_DETAIL, "join spec '" + spec.getJoinId()
                                + "' must declare joinType (INNER/LEFT/RIGHT/FULL)")
                        .loc(t.getLocation());
            }
            int leftKeys = countKeyExprs(spec.getLeftKeyExprs());
            int rightKeys = countKeyExprs(spec.getRightKeyExprs());
            if (leftKeys == 0 || rightKeys == 0) {
                throw new StreamException(ERR_STREAM_INVALID_ARG)
                        .param(ARG_ELEMENT, elementDesc(t))
                        .param(ARG_DETAIL, "join spec '" + spec.getJoinId()
                                + "' requires non-empty leftKeyExprs and rightKeyExprs (equi-join)")
                        .loc(t.getLocation());
            }
            if (leftKeys != rightKeys) {
                throw new StreamException(ERR_STREAM_INVALID_ARG)
                        .param(ARG_ELEMENT, elementDesc(t))
                        .param(ARG_DETAIL, "join spec '" + spec.getJoinId()
                                + "' key count mismatch: leftKeyExprs has " + leftKeys
                                + ", rightKeyExprs has " + rightKeys
                                + " (equi-join requires equal arity)")
                        .loc(t.getLocation());
            }
            // count DECLARED EDGES (not the deduped upstream-id set): a self-join
            // declares two edges from the same source, and upstreams collapses them
            long inEdgeCount = edges.stream()
                    .filter(e -> t.getId().equals(e.getTo())).count();
            if (inEdgeCount != 2) {
                throw new StreamException(ERR_STREAM_INVALID_ARG)
                        .param(ARG_ELEMENT, elementDesc(t))
                        .param(ARG_DETAIL, "<join id='" + t.getId() + "'> requires exactly two "
                                + "upstream edges (left/right), found " + inEdgeCount)
                        .loc(t.getLocation());
            }
            Set<String> ups = upstreams.getOrDefault(t.getId(), Collections.emptySet());
            if (ups.isEmpty()) {
                throw new StreamException(ERR_STREAM_INVALID_ARG)
                        .param(ARG_ELEMENT, elementDesc(t))
                        .param(ARG_DETAIL, "<join id='" + t.getId() + "'> has no resolvable upstream")
                        .loc(t.getLocation());
            }
            if (spec.getWindowStrategyRef() != null) {
                WindowingStrategyModel strategy = model.getStrategy(spec.getWindowStrategyRef());
                if (strategy == null) {
                    throw new StreamException(ERR_STREAM_REF_UNKNOWN)
                            .param(ARG_ELEMENT, elementDesc(t))
                            .param(ARG_REF_TYPE, "<windowingStrategies>/<strategy>")
                            .param(ARG_REF_NAME, spec.getWindowStrategyRef())
                            .loc(t.getLocation());
                }
                if (spec.getJoinType() != JoinType.INNER && spec.getJoinType() != JoinType.LEFT) {
                    throw new StreamException(ERR_STREAM_INVALID_ARG)
                            .param(ARG_ELEMENT, elementDesc(t))
                            .param(ARG_DETAIL, "window join '" + spec.getJoinId()
                                    + "' supports joinType INNER/LEFT only; " + spec.getJoinType()
                                    + " window completion is a WI13 evaluation item")
                            .loc(t.getLocation());
                }
                // WI27: the symmetric timeout and the per-side leftTimeout/rightTimeout
                // are mutually exclusive (dual-source precedence would complicate the
                // validation contract); 0/1/2 per-side declarations are all legal —
                // "exactly one or both" is the defaulting semantics (undeclared side
                // defaults to 0 = complete at window end), not a counting rule.
                boolean symmetric = spec.getTimeout() != null;
                boolean asymmetric = spec.getLeftTimeout() != null
                        || spec.getRightTimeout() != null;
                if (symmetric && asymmetric) {
                    throw new StreamException(ERR_STREAM_INVALID_ARG)
                            .param(ARG_ELEMENT, elementDesc(t))
                            .param(ARG_DETAIL, "join spec '" + spec.getJoinId()
                                    + "' declares timeout together with leftTimeout/"
                                    + "rightTimeout; use the symmetric timeout or the "
                                    + "per-side pair, not both")
                            .loc(t.getLocation());
                }
                if (symmetric) {
                    AdvancedTransforms.parseDurationMillis(spec.getTimeout());
                }
                if (spec.getLeftTimeout() != null) {
                    AdvancedTransforms.parseDurationMillis(spec.getLeftTimeout());
                }
                if (spec.getRightTimeout() != null) {
                    AdvancedTransforms.parseDurationMillis(spec.getRightTimeout());
                }
            } else if (spec.getTimeout() != null || spec.getLeftTimeout() != null
                    || spec.getRightTimeout() != null) {
                throw new StreamException(ERR_STREAM_INVALID_ARG)
                        .param(ARG_ELEMENT, elementDesc(t))
                        .param(ARG_DETAIL, "join spec '" + spec.getJoinId()
                                + "' declares a timeout but no windowStrategyRef; timeouts "
                                + "only apply to window joins")
                        .loc(t.getLocation());
            }
        }
    }

    private static int countKeyExprs(String exprs) {
        if (exprs == null || exprs.trim().isEmpty()) {
            return 0;
        }
        return exprs.split(",").length;
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
        // G8: shared bean-vs-xpl resolution (same decisions and the same
        // ERR_STREAM_REQUIRED_BODY anchoring as the previous inline tree).
        SourceFunction<T> fn = resolveFunctionOrXpl(t, t.getBean(), t.getSource(),
                SourceFunction.class, XplSourceFunction::new);
        // WI15 (plan 2361): declared-capability fidelity check — the declaration must
        // match what the resolved bean actually provides (the engine validates
        // connector consistency against the BEAN's capability, so a diverging
        // declaration would be inert). Value-exemption: AT_LEAST_ONCE (the xdef
        // default, force-injected at parse time) is always exempt.
        if (t.getConsistencyCapability() != null
                && t.getConsistencyCapability() != SourceConsistencyCapability.AT_LEAST_ONCE
                && fn.getSourceConsistency() != t.getConsistencyCapability()) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ELEMENT, elementDesc(t)).param(ARG_ATTR_NAME, "consistencyCapability")
                    .param(ARG_DETAIL, "declared source consistencyCapability=" + t.getConsistencyCapability()
                            + " but the resolved bean provides " + fn.getSourceConsistency()
                            + "; the engine validates connector consistency against the bean, so the"
                            + " declaration would be inert — align the declaration with the bean or"
                            + " switch to a connector bean providing the declared capability"
                            + " (xpl-form sources always provide the default)")
                    .loc(t.getLocation());
        }
        // U3 (deferred-unlock, plan 2376): declared rate limiting wraps ANY resolved
        // source (bean or xpl) with the engine-side token bucket — wrapping happens
        // AFTER the fidelity check so consistency validation sees the delegate.
        fn = applyRateLimitDeclaration(fn, t);
        String name = t.getName() == null ? "Source:" + t.getId() : t.getName();
        DataStream<T> source = (DataStream<T>) env.addSource(fn, name);
        // U4 (deferred-unlock, plan 2377): the declared tableId rides the registered
        // SourceTransformation — the graph generator passes it to the source operator,
        // which stamps (INSERT, tableId) at the emission points. Null/blank declaration
        // = undeclared (the setter normalizes; union/parallelism keep the same instance).
        applyDeclaredTableId(source, t);
        // Item 29: per-transform parallelism consumes the DataStreamSource entry
        // (covers the source vertex; undeclared inherits the stream-level value).
        return applyDeclaredParallelism(source, t);
    }

    /**
     * U4 (deferred-unlock, plan 2377): the {@code <source tableId>} declaration is
     * copied onto the registered {@link io.nop.stream.core.transformation.SourceTransformation}
     * so it reaches the source operator through the graph generator. Package-private
     * static for direct wiring pinning; the null/blank → undeclared normalization lives
     * in the transformation setter.
     */
    static <T> void applyDeclaredTableId(DataStream<T> source, StreamSourceModel t) {
        if (t.getTableId() == null) {
            return;
        }
        io.nop.stream.core.transformation.Transformation<T> transformation =
                ((io.nop.stream.core.datastream.DataStreamImpl<T>) source).getTransformation();
        if (!(transformation instanceof io.nop.stream.core.transformation.SourceTransformation)) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ELEMENT, elementDesc(t)).param(ARG_ATTR_NAME, "tableId")
                    .param(ARG_DETAIL, "the declared tableId must land on a SourceTransformation, got "
                            + transformation.getClass().getName())
                    .loc(t.getLocation());
        }
        ((io.nop.stream.core.transformation.SourceTransformation<T>) transformation)
                .setDeclaredTableId(t.getTableId());
    }

    /**
     * U3 (deferred-unlock, plan 2376): the {@code <source>} rate-limit declaration
     * ({@code rateLimitRowsPerSecond}/{@code rateLimitBytesPerSecond}, xdef default 0
     * = unlimited) wraps the resolved {@link SourceFunction} with
     * {@link RateLimitedSourceFunction}. Negative declarations typed-reject; the
     * all-zero declaration returns the delegate unwrapped (no wrapper overhead).
     * Package-private static for direct wiring pinning.
     */
    static <T> SourceFunction<T> applyRateLimitDeclaration(SourceFunction<T> fn,
                                                           StreamSourceModel t) {
        long rows = t.getRateLimitRowsPerSecond();
        long bytes = t.getRateLimitBytesPerSecond();
        if (rows < 0 || bytes < 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ELEMENT, elementDesc(t))
                    .param(ARG_ATTR_NAME, rows < 0 ? "rateLimitRowsPerSecond" : "rateLimitBytesPerSecond")
                    .param(ARG_DETAIL, "rate-limit declaration must not be negative, got "
                            + "rateLimitRowsPerSecond=" + rows + ", rateLimitBytesPerSecond="
                            + bytes + " (0 = unlimited; rate limiting IS backpressure — "
                            + "the source thread blocks on token acquisition)")
                    .loc(t.getLocation());
        }
        if (rows == 0 && bytes == 0) {
            return fn;
        }
        return new RateLimitedSourceFunction<>(fn, new SourceRateLimiter(rows, bytes));
    }

    /**
     * Item 11 FL-1 (WI15 revision): source-side connector configuration declarations
     * that the current execution chain cannot honor must fail fast instead of being
     * silently dropped. {@code <params>}, {@code outputType} and {@code maxParallelism}
     * have no consumer (connector beans validate their own constructor args, see the
     * item 10 audit §2.2 table). {@code consistencyCapability} LEFT this fail-fast in
     * WI15 (plan 2361): a non-default declaration is now validated against the
     * resolved bean's actual capability in buildSource (declared-fidelity dimension —
     * the engine's validateConnectorConsistency remains the guarantee-sufficiency gate).
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
        // WI15 (plan 2361): the consistencyCapability branch LEFT this fail-fast —
        // a non-default declaration is validated against the resolved bean's actual
        // capability in buildSource (declared-fidelity dimension).
    }

    /**
     * Shared body-resolution template for the function-carrying base transforms:
     * {@code bean="..."} attribute first, then the inline xpl {@code <source>} body
     * wrapped by the matching adapter, otherwise fail fast with
     * {@code ERR_STREAM_REQUIRED_BODY} anchored on the declaring transform.
     */
    private <F> F resolveFunctionOrXpl(StreamTransformModel t, String beanAttr, IEvalFunction xplBody,
                                       Class<F> targetType, Function<IEvalFunction, F> xplWrapper) {
        if (beanAttr != null) {
            return resolveBean(t, beanAttr, targetType);
        }
        if (xplBody != null) {
            return xplWrapper.apply(xplBody);
        }
        throw new StreamException(ERR_STREAM_REQUIRED_BODY)
                .param(ARG_ELEMENT, elementDesc(t))
                .loc(t.getLocation());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T, R> SingleOutputStreamOperator<R> buildMap(DataStream<?> in, StreamMapModel t) {
        MapFunction<T, R> fn = resolveFunctionOrXpl(t, t.getBean(), t.getSource(),
                MapFunction.class,
                // WI11: the xpl branch injects the frozen side-input snapshots under
                // the `sideInputs` binding (null when the transform is not a declared
                // sideInput target — identical behavior to the plain wrapper).
                body -> new XplMapFunction<>(body, sideInputScopes.get(t.getId())));
        return applyDeclaredParallelism(((DataStream<T>) in).map((MapFunction) fn), t);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> SingleOutputStreamOperator<T> buildFilter(DataStream<?> in, StreamFilterModel t) {
        FilterFunction<T> fn = resolveFunctionOrXpl(t, t.getBean(), t.getSource(),
                FilterFunction.class,
                body -> new XplFilterFunction<>(body, sideInputScopes.get(t.getId())));
        return applyDeclaredParallelism(((DataStream<T>) in).filter((FilterFunction) fn), t);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T, R> SingleOutputStreamOperator<R> buildFlatMap(DataStream<?> in, StreamFlatMapModel t) {
        FlatMapFunction<T, R> fn = resolveFunctionOrXpl(t, t.getBean(), t.getSource(),
                FlatMapFunction.class,
                body -> new XplFlatMapFunction<>(body, sideInputScopes.get(t.getId())));
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
        // G8: shared bean-vs-xpl resolution (mirrors buildSource).
        SinkFunction<T> fn = resolveFunctionOrXpl(t, t.getBean(), t.getSource(),
                SinkFunction.class, XplSinkFunction::new);
        // WI12 (plan 2358): record the resolved sink function for the declared-requirements
        // consistency check (the same bean instance the graph generator's
        // detectRequirements will see — builder-side instanceof is engine-equivalent).
        resolvedSinkBeans.put(t.getId(), fn);
        // WI15 (plan 2361): declared-capability fidelity check — see the buildSource
        // counterpart. Value-exemption: AT_LEAST_ONCE (the xdef default, force-injected
        // at parse time) is always exempt.
        if (t.getConsistencyCapability() != null
                && t.getConsistencyCapability() != SinkConsistencyCapability.AT_LEAST_ONCE
                && fn.getSinkConsistency() != t.getConsistencyCapability()) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ELEMENT, elementDesc(t)).param(ARG_ATTR_NAME, "consistencyCapability")
                    .param(ARG_DETAIL, "declared sink consistencyCapability=" + t.getConsistencyCapability()
                            + " but the resolved bean provides " + fn.getSinkConsistency()
                            + "; the engine validates connector consistency against the bean, so the"
                            + " declaration would be inert — align the declaration with the bean or"
                            + " switch to a connector bean providing the declared capability"
                            + " (xpl-form sinks always provide the default)")
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
     * Item 11 FL-1 (WI15 revision, mirrors {@link #failFastOnUnsupportedSourceConfig}):
     * {@code <params>}/{@code inputType}/{@code maxParallelism} have no consumer and
     * fail fast instead of being silently dropped. {@code consistencyCapability} LEFT
     * this fail-fast in WI15 (plan 2361): a non-default declaration is validated
     * against the resolved bean's actual capability in buildSink (declared-fidelity
     * dimension — the engine's validateConnectorConsistency remains the
     * guarantee-sufficiency gate).
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
        // WI15 (plan 2361): the consistencyCapability branch LEFT this fail-fast —
        // a non-default declaration is validated against the resolved bean's actual
        // capability in buildSink (declared-fidelity dimension).
    }

    // ----------------------------------------------------------------
    // Helpers used by both base and advanced transform builders.
    // ----------------------------------------------------------------

    BeanFunctionResolver beanResolver() {
        return beanResolver;
    }

    /**
     * WI11 (plan 2357): test-visible view of the sideInput consumer registration —
     * the transform ids whose xpl-fn bodies will receive the {@code sideInputs}
     * binding. Empty when the model declares no sideInputs.
     */
    java.util.Set<String> sideInputScopeTargetIds() {
        return sideInputScopes.keySet();
    }

    /**
     * Item 29 (Phase 3): bean resolution wrapped so a missing/mistyped bean error
     * carries the declaring transform element's source location (the resolvers
     * themselves have no model object to anchor on). An existing location is kept.
     */
    <F> F resolveBean(StreamTransformModel t, String beanName, Class<F> targetType) {
        try {
            return beanResolver.resolve(beanName, targetType);
        } catch (NopException e) {
            if (e.getErrorLocation() == null) {
                e.loc(t.getLocation());
            }
            throw e;
        }
    }

    StreamModel model() {
        return model;
    }

    /**
     * WI8c: column-key → type lookup for parameterized aggregates, built from the
     * model's declared schemas (WI8b registry). A null/unknown schemaId or unknown
     * column yields null — the resolver then skips type validation instead of
     * failing on schemas the declaration never promised.
     */
    java.util.function.Function<String, io.nop.stream.core.common.typeinfo.BasicTypeInfo<?>> columnTypeLookup(
            String schemaId) {
        if (schemaId == null || !StreamSchemaRegistry.resolveSchemas(model).contains(schemaId)) {
            return column -> null;
        }
        java.util.Map<String, io.nop.stream.core.common.typeinfo.BasicTypeInfo<?>> columns =
                new java.util.HashMap<>();
        for (StreamSchemaRegistry.FieldSpec spec : StreamSchemaRegistry.resolveSchemas(model).resolveSchema(schemaId)) {
            columns.put(spec.getName(), spec.getType());
        }
        return columns::get;
    }

    /**
     * Inspection accessor: the DataStream produced for the transform with the given id
     * (null when the transform produced no registered stream). Public so acceptance
     * tests in other modules (e.g. runtime scenario suites) can assert declaration
     * consumption without reaching into builder internals.
     */
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
    PartitionPolicy resolveEdgePartition(String from, String to) {
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
    DataStream<?> applyEdgePartition(DataStream<?> in, String fromId, String toId,
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
            // R6 plan 2322 (supersedes the R5 FLOW-5 reject here): a HASH edge is a
            // partition boundary, so the keyed stage may be wider or narrower than
            // its upstream. The implicit partition vertex takes the target's value
            // (per this method's contract above) and the edge's HashPartitionRouter
            // spreads records across every target partition, so no channel-0
            // concentration is possible on this branch. The reject previously thrown
            // here contradicted the design contract, the "or a HASH edge" advice in
            // its own message, and the user guide — and blocked the per-transform
            // parallelism E2E scenarios. The FORWARD-side mismatch guard lives in
            // {@link #applyDeclaredParallelism} and stays fail-fast.
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
            // R5 FLOW-5: same guard as applyEdgePartition — a keyed stage may
            // not be resized away from its upstream routing parallelism.
            if (stream instanceof DataStreamImpl
                    && stream instanceof KeyedStream
                    && t.getParallelism() != ((DataStreamImpl) stream).getTransformation().getParallelism()) {
                throw new StreamException(ERR_STREAM_EDGE_PARTITION_UNSUPPORTED)
                        .param(ARG_TRANSFORM_ID, t.getId())
                        .param(ARG_DETAIL, "keyed-stage parallelism " + t.getParallelism()
                                + " differs from the upstream routing parallelism "
                                + ((DataStreamImpl) stream).getTransformation().getParallelism()
                                + " — FORWARD routing would concentrate all records on subtask 0"
                                + " and rescale would split state from traffic (R5 FLOW-5;"
                                + " declare equal parallelism)");
            }
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

    /**
     * Renders a human-friendly element description for error messages, e.g.
     * {@code window 'w'} for a {@code StreamWindowModel} with id {@code w}.
     */
    static String elementDesc(StreamTransformModel t) {
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

    /**
     * WI11 (plan 2357): same shape as {@link #elementDesc(StreamTransformModel)} for a
     * registry declaration that is not a graph transform (sideInput).
     */
    static String elementDesc(StreamSideInputModel si) {
        return "sideInput '" + si.getId() + "'";
    }

    /**
     * WI12 (plan 2358): registry-declaration element descriptor (environment).
     */
    static String elementDesc(io.nop.stream.flow.model.StreamEnvironmentModel e) {
        return "environment '" + e.getName() + "'";
    }

    static NopException beanNotFound(String beanName) {
        return new StreamException(ERR_STREAM_BEAN_NOT_FOUND).param(ARG_BEAN_NAME, beanName);
    }

    static NopException beanTypeMismatch(String beanName, Class<?> targetType,
                                                Class<?> actualType) {
        return new StreamException(ERR_STREAM_BEAN_TYPE_MISMATCH)
                .param(ARG_BEAN_NAME, beanName)
                .param(ARG_EXPECTED_TYPE, targetType.getName())
                .param(ARG_ACTUAL_TYPE, actualType.getName());
    }
}
