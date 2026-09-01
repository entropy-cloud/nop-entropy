/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.graph;

import java.io.Serializable;

import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.operators.ChainingStrategy;
import io.nop.stream.core.operators.StreamOperatorFactory;

/**
 * Represents a node in the streaming execution graph.
 * 
 * <p>A StreamNode encapsulates all the information needed to execute a single operation
 * in the streaming topology. This includes the operator factory, type information,
 * parallelism settings, and optional windowing configuration.
 * 
 * <p>StreamNodes are the vertices in the StreamGraph DAG and represent operations such as:
 * <ul>
 *   <li>Source operations (data ingestion)</li>
 *   <li>Transformation operations (map, filter, etc.)</li>
 *   <li>Sink operations (data output)</li>
 *   <li>Window operations</li>
 * </ul>
 * 
 * <p>Each StreamNode has a unique ID within the graph and contains:
 * <ul>
 *   <li>Operator factory: Creates the actual operator instance</li>
 *   <li>Output type: Type information for the data produced</li>
 *   <li>Parallelism: Number of parallel instances</li>
 *   <li>Optional key selector: For keyed streams</li>
 * </ul>
 * 
 * <p>This class is designed to be immutable after construction. All fields are either
 * final (required fields) or set only during construction (optional fields).
 * 
 * @see StreamGraph
 * @see StreamEdge
 * @see StreamOperatorFactory
 */
public class StreamNode implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Unique identifier for this node within the stream graph.
     */
    private final int id;
    
    /**
     * Human-readable name for this node (e.g., operation name).
     */
    private final String name;
    
    /**
     * Factory for creating the stream operator that will execute this node's operation.
     */
    private final StreamOperatorFactory<?> operatorFactory;
    
    /**
     * Type information for the output data type of this node.
     */
    private final TypeInformation<?> outputType;
    
    /**
     * The parallelism (number of parallel instances) for this node.
     */
    private final int parallelism;

    /**
     * Lock flag set by StreamGraphGenerator when the source Transformation has
     * been locked to parallelism = 1 via {@code forceNonParallel()}.
     * Propagates to JobVertex.parallelismLocked so GraphExecutionPlan can
     * reject any DeploymentPlan/runtime override that would raise the
     * vertex parallelism above 1.
     */
    private boolean parallelismLocked;
    
    /**
     * Optional key selector for keyed stream operations.
     * May be null for non-keyed operations.
     */
    private KeySelector<?, ?> keySelector;
    
    /**
     * Chaining strategy for this operator.
     * Defaults to ALWAYS if not explicitly set.
     */
    private ChainingStrategy chainingStrategy = ChainingStrategy.ALWAYS;
    
    /**
     * Constructs a new StreamNode with the required parameters.
     * 
     * 
     * @param id Unique identifier for this node
     * @param name Human-readable name for this node
     * @param operatorFactory Factory for creating the stream operator
     * @param outputType Type information for the output data
     * @param parallelism Number of parallel instances
     */
    public StreamNode(int id, String name, StreamOperatorFactory<?> operatorFactory,
                      TypeInformation<?> outputType, int parallelism) {
        this.id = id;
        this.name = name;
        this.operatorFactory = operatorFactory;
        this.outputType = outputType;
        this.parallelism = parallelism;
    }
    
    /**
     * Returns the unique identifier for this stream node.
     * 
     * @return The node ID
     */
    public int getId() {
        return id;
    }
    
    /**
     * Returns the human-readable name of this stream node.
     * 
     * @return The node name
     */
    public String getName() {
        return name;
    }
    
    /**
     * Returns the factory for creating the stream operator.
     * 
     * @return The operator factory
     */
    public StreamOperatorFactory<?> getOperatorFactory() {
        return operatorFactory;
    }
    
    /**
     * Returns the type information for the output data.
     * 
     * @return The output type information
     */
    public TypeInformation<?> getOutputType() {
        return outputType;
    }
    
    /**
     * Returns the parallelism (number of parallel instances) for this node.
     * 
     * @return The parallelism value
     */
    public int getParallelism() {
        return parallelism;
    }

    /**
     * Returns whether this node has been locked to parallelism = 1 by
     * {@code forceNonParallel()} on the upstream Transformation.
     *
     * @return true if this node is locked to parallel-1
     */
    public boolean isParallelismLocked() {
        return parallelismLocked;
    }

    /**
     * Marks this node as locked to parallelism = 1. Called by
     * StreamGraphGenerator when the source Transformation has
     * {@code parallelismLocked = true}.
     */
    public void setParallelismLocked(boolean parallelismLocked) {
        this.parallelismLocked = parallelismLocked;
    }
    
    /**
     * Returns the key selector for keyed stream operations.
     * 
     * @return The key selector, or null if this is not a keyed operation
     */
    public KeySelector<?, ?> getKeySelector() {
        return keySelector;
    }
    
    /**
     * Sets the key selector for keyed stream operations.
     * 
     * @param keySelector The key selector to use
     */
    public void setKeySelector(KeySelector<?, ?> keySelector) {
        this.keySelector = keySelector;
    }
    
    /**
     * Returns the chaining strategy for this node.
     *
     * @return the chaining strategy
     */
    public ChainingStrategy getChainingStrategy() {
        return chainingStrategy;
    }

    /**
     * Sets the chaining strategy for this node.
     *
     * @param chainingStrategy the chaining strategy to use
     */
    public void setChainingStrategy(ChainingStrategy chainingStrategy) {
        this.chainingStrategy = chainingStrategy;
    }
}
