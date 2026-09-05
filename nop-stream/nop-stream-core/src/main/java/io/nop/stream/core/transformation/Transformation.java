/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.transformation;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;

import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;

/**
 * Abstract base class representing a transformation operation in the streaming DAG.
 * This is a simplified version of Flink's Transformation class, providing the core
 * functionality for nop-stream processing operations.
 * 
 * @param <T> the output type of the transformation
 */
public abstract class Transformation<T> implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private static final AtomicInteger idCounter = new AtomicInteger(0);
    
    private int id;
    private final String name;
    private int parallelism;
    private final TypeInformation<T> outputType;

    /**
     * Mutable flag set by {@code SingleOutputStreamOperator.forceNonParallel()} to
     * lock this transformation (and any downstream vertex) to parallelism = 1.
     *
     * <p>StreamGraphGenerator reads this flag and forces the corresponding
     * StreamNode's parallelism to 1 with {@code parallelismLocked = true}; the
     * lock propagates through StreamNode → JobVertex → GraphExecutionPlan so
     * that no downstream consumer (PartitionedPlan, DeploymentPlan, runtime)
     * can override the parallel-1 requirement. Used by CEP's non-keyed entry
     * point ({@code CEP.pattern}) which requires a single global NFA.
     */
    private boolean parallelismLocked;
    
    /**
     * Creates a new transformation with the specified name and output type.
     * 
     * @param name the name of the transformation
     * @param outputType the output type information
     * @param parallelism the parallelism for the transformation
     */
    protected Transformation(String name, TypeInformation<T> outputType, int parallelism) {
        this.id = idCounter.incrementAndGet();
        this.name = name;
        this.outputType = outputType;
        this.parallelism = parallelism;
    }

    /**
     * Returns the unique ID of this transformation.
     * 
     * @return the transformation ID
     */
    public int getId() {
        return id;
    }

    /**
     * Reassigns this transformation's id to a deterministic value derived from
     * the pipeline structure (transformation name + occurrence index). Called
     * by {@code StreamExecutionEnvironment.buildStreamModel} before graph
     * generation: the constructor's global static counter drifts across
     * {@code env.execute()} calls, so an identically regenerated job would get
     * different transformation/vertex ids — breaking checkpoint restore
     * (task-location vertex matching) and the stream-model fingerprint. The
     * stable id is identical for identical rebuilds and unique within one
     * build (collision-probed against the build's id set by the caller).
     */
    public void assignStableId(int stableId) {
        this.id = stableId;
    }
    
    /**
     * Returns the name of this transformation.
     * 
     * @return the transformation name
     */
    public String getName() {
        return name;
    }
    
    /**
     * Returns the parallelism for this transformation.
     *
     * @return the parallelism
     */
    public int getParallelism() {
        return parallelism;
    }

    /**
     * Sets the per-operator parallelism for this transformation, overriding the
     * environment-level parallelism it was constructed with (item 29: the
     * {@code transforms/@parallelism} DSL declaration consumes this entry).
     *
     * <p>Controlled mutability adjudication: operator construction happens inside
     * {@code map()}/{@code filter()}/... before the caller ever sees the stream
     * object, so a per-operator parallelism cannot be threaded through the
     * transformation constructors without duplicating every builder method with a
     * parallelism overload. A guarded setter (the same trade the Flink DataStream
     * API makes) is the justified form. Guards:
     * <ul>
     *   <li>{@code parallelism >= 1} (typed {@code ERR_STREAM_INVALID_ARG} otherwise);</li>
     *   <li>rejected with {@code ERR_STREAM_INVALID_STATE} when
     *       {@link #isParallelismLocked()} and the requested value is not 1 — the
     *       {@code forceNonParallel()} lock is not weakened (setting 1 on a
     *       locked-to-1 transformation stays allowed, value-wise a no-op).</li>
     * </ul>
     * The effective vertex parallelism still resolves through
     * {@code StreamGraphGenerator.resolveParallelism}, which forces a locked
     * transformation to 1 regardless.
     *
     * @param parallelism the per-operator parallelism, at least 1
     */
    public void setParallelism(int parallelism) {
        if (parallelism < 1) {
            throw new StreamException(NopStreamErrors.ERR_STREAM_INVALID_ARG)
                    .param(ARG_ARG_NAME, "parallelism")
                    .param(ARG_DETAIL, "must be at least 1, got: " + parallelism);
        }
        if (this.parallelismLocked && parallelism != 1) {
            throw new StreamException(NopStreamErrors.ERR_STREAM_INVALID_STATE)
                    .param(ARG_DETAIL,
                            "transformation '" + name + "' is locked to parallelism 1 via forceNonParallel()"
                                    + "; a non-1 parallelism cannot be set");
        }
        this.parallelism = parallelism;
    }

    /**
     * Returns whether this transformation has been locked to parallelism = 1
     * via {@code forceNonParallel()}.
     *
     * @return true if the transformation is locked to parallel-1
     */
    public boolean isParallelismLocked() {
        return parallelismLocked;
    }

    /**
     * Locks this transformation to parallelism = 1. Once locked, the
     * corresponding execution vertex is forced to parallel-1 regardless of
     * the environment parallelism or any downstream DeploymentPlan override.
     *
     * <p>This is intended for operators that require a single global instance
     * (e.g. CEP's non-keyed entry point with a single global NFA).
     */
    public void lockParallelismToOne() {
        this.parallelismLocked = true;
    }
    
    /**
     * Returns the output type information for this transformation.
     * 
     * @return the output type information
     */
    public TypeInformation<T> getOutputType() {
        return outputType;
    }
    
    /**
     * Returns the input transformations that this transformation depends on.
     * 
     * @return the list of input transformations
     */
    public abstract List<Transformation<?>> getInputs();
}
