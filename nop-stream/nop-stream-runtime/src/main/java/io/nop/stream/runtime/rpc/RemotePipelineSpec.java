/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.rpc;

import io.nop.stream.core.exceptions.StreamException;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;
import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Item 14 (composite-scenario distributed): serializable pipeline DECLARATION carried
 * by a {@link TaskDeploymentDescriptor} when the pipeline is expressed as an XDSL
 * model instead of a pre-built {@link io.nop.stream.core.jobgraph.JobGraph}.
 *
 * <p><strong>Why this exists</strong>: an XDSL-built JobGraph embeds compiled
 * expression objects (e.g. {@code ExprEvalAction} behind inline {@code keyExpr})
 * that are NOT Java-serializable, so the graph cannot cross the JVM boundary inside
 * the deployTask RPC. Shipping the declaration instead follows the model-first
 * architecture already used by {@code SubtaskPlanBuilder} ("the TaskManager rebuilds
 * everything locally from serializable model metadata"): every TaskManager
 * re-parses the same VFS model + re-binds the serializable beans and rebuilds an
 * IDENTICAL graph locally. Identity across JVMs is guaranteed by the
 * stable-transformation-id pass in
 * {@code StreamExecutionEnvironment#buildJobGraph(String)} (ids derive from
 * transformation names, not the global counter).
 *
 * <p>The bean bindings must be Java-serializable — the scenario module supplies
 * serializable variants of its source/sink/function beans for exactly this contract.
 */
public final class RemotePipelineSpec implements Serializable {

    private static final long serialVersionUID = 1L;

    /** VFS path of the XDSL stream model (e.g. {@code /nop/stream/demo/fraud-s1-cdc.stream.xml}). */
    private final String streamVfsPath;

    /** Serializable bean bindings (bean id -> UDF/strategy instance) for the DSL builder. */
    private final Map<String, Object> beanBindings;

    public RemotePipelineSpec(String streamVfsPath, Map<String, Object> beanBindings) {
        if (streamVfsPath == null || streamVfsPath.isBlank()) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "streamVfsPath must not be blank");
        }
        if (beanBindings == null) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "beanBindings must not be null (may be empty)");
        }
        this.streamVfsPath = streamVfsPath;
        this.beanBindings = new LinkedHashMap<>(beanBindings);
    }

    public String getStreamVfsPath() {
        return streamVfsPath;
    }

    public Map<String, Object> getBeanBindings() {
        return Collections.unmodifiableMap(beanBindings);
    }

    @Override
    public String toString() {
        return "RemotePipelineSpec{streamVfsPath='" + streamVfsPath
                + "', beans=" + beanBindings.keySet() + '}';
    }
}
