/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.jobgraph;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a vertex (node) in the streaming job execution graph.
 *
 * <p>A JobVertex encapsulates a unit of execution in the optimized job graph.
 * It represents a set of parallel task instances that execute the same operation.
 * Unlike StreamNode which represents individual operators, JobVertex represents
 * a fused chain of operators that are executed together for efficiency.
 *
 * <p>Each JobVertex contains:
 * <ul>
 *   <li>Unique ID: String identifier within the JobGraph</li>
 *   <li>Name: Human-readable name for the vertex</li>
 *   <li>Parallelism: Number of parallel task instances</li>
 *   <li>Operator chains: The chain of operators to execute</li>
 *   <li>Invokable: The executable task that runs the operator logic</li>
 * </ul>
 *
 * <p>JobVertex is the result of graph optimization where multiple operators
 * are chained together to reduce serialization and network overhead.
 * All fields are final as the vertex structure is immutable after construction.
 *
 * <p>Key differences from StreamNode:
 * <ul>
 *   <li>Uses String IDs instead of Integer IDs</li>
 *   <li>Represents optimized execution units, not individual operators</li>
 *   <li>Contains operator chains rather than single operator factories</li>
 *   <li>Has Invokable for actual execution rather than operator factory</li>
 * </ul>
 *
 * @see JobGraph
 * @see JobEdge
 * @see OperatorChain
 * @see Invokable
 */
public class JobVertex implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Unique identifier for this vertex within the job graph.
     */
    private final String id;

    /**
     * Human-readable name for this vertex (e.g., "Source: Kafka", "Map -> Filter").
     */
    private final String name;

    /**
     * The parallelism (number of parallel task instances) for this vertex.
     * Must be greater than 0.
     */
    private final int parallelism;

    /**
     * The list of operator chains that will be executed by this vertex.
     * Each chain represents a fused sequence of operators.
     */
    private final List<OperatorChain> operatorChains;

    /**
     * The invokable task that will execute the operator logic for this vertex.
     * This is the actual executable unit deployed to task managers.
     */
    private final Invokable<?> invokable;

    /**
     * Constructs a new JobVertex with all required parameters.
     *
     * <p>All parameters are validated to ensure they are not null (or invalid for primitives).
     * The operatorChains list must contain at least one chain.
     *
     * @param id Unique identifier for this vertex (must not be null)
     * @param name Human-readable name for this vertex (must not be null)
     * @param parallelism Number of parallel task instances (must be > 0)
     * @param operatorChains List of operator chains (must not be null or empty)
     * @param invokable Executable task for this vertex (must not be null)
     * @throws IllegalArgumentException if any parameter is null or invalid
     */
    public JobVertex(String id, String name, int parallelism,
                      List<OperatorChain> operatorChains, Invokable<?> invokable) {
        if (id == null) {
            throw new IllegalArgumentException("Vertex ID cannot be null");
        }
        if (name == null) {
            throw new IllegalArgumentException("Vertex name cannot be null");
        }
        if (parallelism <= 0) {
            throw new IllegalArgumentException("Parallelism must be greater than 0, got: " + parallelism);
        }
        if (operatorChains == null || operatorChains.isEmpty()) {
            throw new IllegalArgumentException("Operator chains cannot be null or empty");
        }
        if (invokable == null) {
            throw new IllegalArgumentException("Invokable cannot be null");
        }

        this.id = id;
        this.name = name;
        this.parallelism = parallelism;
        this.operatorChains = new ArrayList<>(operatorChains); // Defensive copy
        this.invokable = invokable;
    }

    /**
     * Returns the unique identifier for this job vertex.
     *
     * @return The vertex ID
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the human-readable name of this job vertex.
     *
     * @return The vertex name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the parallelism (number of parallel task instances) for this vertex.
     *
     * @return The parallelism value
     */
    public int getParallelism() {
        return parallelism;
    }

    /**
     * Returns the list of operator chains for this vertex.
     *
     * <p>The returned list is an unmodifiable view to prevent external modification.
     *
     * @return Unmodifiable list of operator chains
     */
    public List<OperatorChain> getOperatorChains() {
        return Collections.unmodifiableList(operatorChains);
    }

    /**
     * Returns the invokable task for this vertex.
     *
     * @return The invokable that executes this vertex's logic
     */
    public Invokable<?> getInvokable() {
        return invokable;
    }
}
