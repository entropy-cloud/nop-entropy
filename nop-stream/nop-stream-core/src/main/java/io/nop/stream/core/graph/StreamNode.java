/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.graph;

import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.operator.StreamOperatorFactory;
import io.nop.stream.core.windowing.assigners.WindowAssigner;
import io.nop.stream.core.windowing.triggers.Trigger;

import java.io.Serializable;

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
 *   <li>Optional window assigner: For windowed operations</li>
 *   <li>Optional trigger: For custom window firing logic</li>
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
     * Optional key selector for keyed stream operations.
     * May be null for non-keyed operations.
     */
    private KeySelector<?, ?> keySelector;
    
    /**
     * Optional window assigner for windowed operations.
     * May be null for non-windowed operations.
     */
    private WindowAssigner<?, ?> windowAssigner;
    
    /**
     * Optional trigger for custom window firing logic.
     * May be null to use the default trigger for the window assigner.
     */
    private Trigger<?, ?> trigger;
    
    /**
     * Constructs a new StreamNode with the required parameters.
     * 
     * <p>Optional parameters (keySelector, windowAssigner, trigger) are initially null
     * and can be set using their respective setter methods.
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
     * Returns the window assigner for windowed operations.
     * 
     * @return The window assigner, or null if this is not a windowed operation
     */
    public WindowAssigner<?, ?> getWindowAssigner() {
        return windowAssigner;
    }
    
    /**
     * Sets the window assigner for windowed operations.
     * 
     * @param windowAssigner The window assigner to use
     */
    public void setWindowAssigner(WindowAssigner<?, ?> windowAssigner) {
        this.windowAssigner = windowAssigner;
    }
    
    /**
     * Returns the trigger for custom window firing logic.
     * 
     * @return The trigger, or null to use the default trigger
     */
    public Trigger<?, ?> getTrigger() {
        return trigger;
    }
    
    /**
     * Sets the trigger for custom window firing logic.
     * 
     * @param trigger The trigger to use
     */
    public void setTrigger(Trigger<?, ?> trigger) {
        this.trigger = trigger;
    }
}
