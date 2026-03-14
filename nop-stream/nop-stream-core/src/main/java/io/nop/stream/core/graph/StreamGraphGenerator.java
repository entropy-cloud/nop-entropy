/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.graph;

import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.operator.StreamOperator;
import io.nop.stream.core.operator.StreamOperatorFactory;
import io.nop.stream.core.transformation.OneInputTransformation;
import io.nop.stream.core.transformation.PartitionTransformation;
import io.nop.stream.core.transformation.SinkTransformation;
import io.nop.stream.core.transformation.SourceTransformation;
import io.nop.stream.core.transformation.Transformation;

import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Converts a chain of Transformation objects into a StreamGraph representation.
 * 
 * <p>The StreamGraphGenerator traverses the transformation DAG and creates the corresponding
 * StreamNode and StreamEdge objects that form the executable streaming topology. This class
 * implements a visitor pattern using instanceof checks to dispatch to the appropriate
 * transformation handler methods.
 * 
 * <p>The generator processes transformations recursively, ensuring that input transformations
 * are processed before their dependents, building the graph from sources to sinks.
 * 
 * <p>Supported transformation types:
 * <ul>
 *   <li>{@link SourceTransformation}: Creates source nodes with no inputs</li>
 *   <li>{@link OneInputTransformation}: Creates operator nodes with one input edge</li>
 *   <li>{@link SinkTransformation}: Creates sink nodes with one input edge</li>
 *   <li>{@link PartitionTransformation}: Creates partitioning nodes with partitioner edges</li>
 * </ul>
 * 
 * <p>The generator tracks which transformations have already been processed to avoid
 * duplicating nodes in the graph when the same transformation is referenced multiple times.
 * 
 * @see StreamGraph
 * @see StreamNode
 * @see StreamEdge
 * @see Transformation
 */
public class StreamGraphGenerator {
    
    /**
     * The stream graph being constructed.
     */
    private final StreamGraph streamGraph;
    
    /**
     * Set of transformation IDs that have already been processed.
     * Used to avoid processing the same transformation multiple times.
     */
    private final Set<Integer> processedTransformations;
    
    /**
     * Creates a new StreamGraphGenerator with an empty graph.
     */
    public StreamGraphGenerator() {
        this.streamGraph = new StreamGraph();
        this.processedTransformations = new HashSet<>();
    }
    
    /**
     * Generates a StreamGraph from a list of sink transformations.
     * 
     * <p>This method processes each transformation in the list, which typically represents
     * the sink nodes of a streaming topology. The processing is recursive, so all upstream
     * transformations will be processed as well.
     * 
     * @param transformations the list of transformations to process (typically sink transformations)
     * @return the constructed StreamGraph
     * @throws IllegalArgumentException if transformations is null
     */
    public StreamGraph generate(List<Transformation<?>> transformations) {
        if (transformations == null) {
            throw new IllegalArgumentException("Transformations list cannot be null");
        }
        
        for (Transformation<?> transformation : transformations) {
            transform(transformation);
        }
        
        return streamGraph;
    }
    
    /**
     * Transforms a single transformation into its graph representation.
     * 
     * <p>This method dispatches to the appropriate handler based on the transformation type.
     * It also ensures that each transformation is only processed once by tracking processed IDs.
     * 
     * @param transformation the transformation to process
     */
    private void transform(Transformation<?> transformation) {
        if (transformation == null) {
            return;
        }
        
        // Skip if already processed
        if (processedTransformations.contains(transformation.getId())) {
            return;
        }
        processedTransformations.add(transformation.getId());
        
        // Dispatch to appropriate handler based on transformation type
        if (transformation instanceof SourceTransformation) {
            transformSource((SourceTransformation<?>) transformation);
        } else if (transformation instanceof OneInputTransformation) {
            transformOneInput((OneInputTransformation<?, ?>) transformation);
        } else if (transformation instanceof SinkTransformation) {
            transformSink((SinkTransformation<?>) transformation);
        } else if (transformation instanceof PartitionTransformation) {
            transformPartition((PartitionTransformation<?>) transformation);
        } else {
            throw new IllegalArgumentException(
                "Unknown transformation type: " + transformation.getClass().getName()
            );
        }
    }
    
    /**
     * Transforms a SourceTransformation into a StreamNode.
     * 
     * <p>Source transformations are leaf nodes in the DAG with no inputs. They represent
     * data sources like Kafka topics, files, or socket connections. The created node
     * uses a SourceOperatorFactory to wrap the SourceFunction.
     * 
     * @param transformation the source transformation to process
     * @param <OUT> the output type of the source
     */
    private <OUT> void transformSource(SourceTransformation<OUT> transformation) {
        // Create operator factory wrapper for the source function
        StreamOperatorFactory<OUT> operatorFactory = 
            new SourceOperatorFactory<>(transformation.getSourceFunction());
        
        // Create the stream node for this source
        StreamNode node = new StreamNode(
            transformation.getId(),
            transformation.getName(),
            operatorFactory,
            transformation.getOutputType(),
            transformation.getParallelism()
        );
        
        // Add node to graph and mark as source
        streamGraph.addStreamNode(node);
        streamGraph.addSourceID(node.getId());
    }
    
    /**
     * Transforms a OneInputTransformation into a StreamNode and StreamEdge.
     * 
     * <p>One-input transformations represent operations like map, filter, flatMap, etc.
     * They have exactly one input transformation and produce one output stream.
     * 
     * <p>The method:
     * <ol>
     *   <li>Recursively processes the input transformation</li>
     *   <li>Creates a StreamNode for this transformation</li>
     *   <li>Creates a StreamEdge connecting the input to this node</li>
     * </ol>
     * 
     * @param transformation the one-input transformation to process
     * @param <IN> the input type
     * @param <OUT> the output type
     */
    private <IN, OUT> void transformOneInput(OneInputTransformation<IN, OUT> transformation) {
        // 1. Recursively process the input transformation
        transform(transformation.getInput());
        
        // 2. Create the StreamNode for this transformation
        StreamNode node = new StreamNode(
            transformation.getId(),
            transformation.getName(),
            transformation.getOperatorFactory(),
            transformation.getOutputType(),
            transformation.getParallelism()
        );
        
        // Set key selector if present
        if (transformation.getKeySelector() != null) {
            node.setKeySelector(transformation.getKeySelector());
        }
        
        // Add node to graph
        streamGraph.addStreamNode(node);
        
        // 3. Create the StreamEdge connecting input to this node
        StreamEdge edge = new StreamEdge(
            transformation.getInput().getId(),
            node.getId()
        );
        // Use null partitioner for forward partitioning (same parallel instance)
        
        streamGraph.addStreamEdge(edge);
    }
    
    /**
     * Transforms a SinkTransformation into a StreamNode and StreamEdge.
     * 
     * <p>Sink transformations are terminal operations that consume elements from a stream
     * and send them to an external system. They have one input and no outputs.
     * 
     * <p>The method:
     * <ol>
     *   <li>Recursively processes the input transformation</li>
     *   <li>Creates a StreamNode for this sink using a SinkOperatorFactory</li>
     *   <li>Creates a StreamEdge connecting the input to this sink node</li>
     *   <li>Registers the node as a sink in the graph</li>
     * </ol>
     * 
     * @param transformation the sink transformation to process
     * @param <T> the input type consumed by the sink
     */
    private <T> void transformSink(SinkTransformation<T> transformation) {
        // 1. Recursively process the input transformation
        transform(transformation.getInput());
        
        // 2. Create operator factory wrapper for the sink function
        StreamOperatorFactory<Void> operatorFactory = 
            new SinkOperatorFactory<>(transformation.getSinkFunction());
        
        // Create the stream node for this sink
        StreamNode node = new StreamNode(
            transformation.getId(),
            transformation.getName(),
            operatorFactory,
            transformation.getOutputType(),
            transformation.getParallelism()
        );
        
        // Add node to graph
        streamGraph.addStreamNode(node);
        
        // 3. Create the StreamEdge connecting input to this sink
        StreamEdge edge = new StreamEdge(
            transformation.getInput().getId(),
            node.getId()
        );
        
        streamGraph.addStreamEdge(edge);
        
        // 4. Register as sink
        streamGraph.addSinkID(node.getId());
    }
    
    /**
     * Transforms a PartitionTransformation into a StreamNode and StreamEdge with partitioner.
     * 
     * <p>Partition transformations are logical transformations that define how data should
     * be distributed across parallel instances. They are typically created by operations
     * like keyBy, rebalance, or rescale.
     * 
     * <p>The method:
     * <ol>
     *   <li>Recursively processes the input transformation</li>
     *   <li>Creates a StreamNode for this partition transformation</li>
     *   <li>Creates a StreamEdge with the partitioner from the transformation</li>
     * </ol>
     * 
     * @param transformation the partition transformation to process
     * @param <T> the element type
     */
    private <T> void transformPartition(PartitionTransformation<T> transformation) {
        // 1. Recursively process the input transformation
        transform(transformation.getInput());
        
        // 2. Create the StreamNode for this partition transformation
        // Partition nodes don't have an operator factory - they're logical
        // We use a placeholder factory that returns null
        StreamNode node = new StreamNode(
            transformation.getId(),
            transformation.getName(),
            new PartitionOperatorFactory<>(),
            transformation.getOutputType(),
            transformation.getParallelism()
        );
        
        // Add node to graph
        streamGraph.addStreamNode(node);
        
        // 3. Create the StreamEdge with partitioner
        StreamEdge edge = new StreamEdge(
            transformation.getInput().getId(),
            node.getId()
        );
        edge.setPartitioner(transformation.getPartitioner());
        
        streamGraph.addStreamEdge(edge);
    }
    
    // ===== Inner classes for operator factory wrappers =====
    
    /**
     * Operator factory wrapper for SourceFunction.
     * 
     * <p>This factory creates operators that wrap source functions for execution.
     * The actual execution logic is handled by the streaming runtime.
     * 
     * @param <OUT> the output type of the source
     */
    private static class SourceOperatorFactory<OUT> implements StreamOperatorFactory<OUT>, Serializable {
        
        private static final long serialVersionUID = 1L;
        
        private final SourceFunction<OUT> sourceFunction;
        
        public SourceOperatorFactory(SourceFunction<OUT> sourceFunction) {
            this.sourceFunction = sourceFunction;
        }
        
        public SourceFunction<OUT> getSourceFunction() {
            return sourceFunction;
        }
        
        @Override
        public StreamOperator<OUT> createStreamOperator(TypeInformation<OUT> outputType) {
            // Source operators are handled specially by the runtime
            // This returns null as the actual source execution is through SourceFunction
            return null;
        }
        
        @Override
        public int getParallelism() {
            return 1;
        }
        
        @Override
        public String getName() {
            return "Source";
        }
    }
    
    /**
     * Operator factory wrapper for SinkFunction.
     * 
     * <p>This factory creates operators that wrap sink functions for execution.
     * The actual sink execution is handled by the streaming runtime.
     * 
     * @param <T> the input type consumed by the sink
     */
    private static class SinkOperatorFactory<T> implements StreamOperatorFactory<Void>, Serializable {
        
        private static final long serialVersionUID = 1L;
        
        private final SinkFunction<T> sinkFunction;
        
        public SinkOperatorFactory(SinkFunction<T> sinkFunction) {
            this.sinkFunction = sinkFunction;
        }
        
        public SinkFunction<T> getSinkFunction() {
            return sinkFunction;
        }
        
        @Override
        public StreamOperator<Void> createStreamOperator(TypeInformation<Void> outputType) {
            // Sink operators are handled specially by the runtime
            // This returns null as the actual sink execution is through SinkFunction
            return null;
        }
        
        @Override
        public int getParallelism() {
            return 1;
        }
        
        @Override
        public String getName() {
            return "Sink";
        }
    }
    
    /**
     * Placeholder operator factory for partition transformations.
     * 
     * <p>Partition transformations are logical and don't execute any operator.
     * They only affect how data is distributed between nodes.
     * 
     * @param <T> the element type
     */
    private static class PartitionOperatorFactory<T> implements StreamOperatorFactory<T>, Serializable {
        
        private static final long serialVersionUID = 1L;
        
        @Override
        public StreamOperator<T> createStreamOperator(TypeInformation<T> outputType) {
            // Partition transformations are logical - no actual operator
            return null;
        }
        
        @Override
        public int getParallelism() {
            return 1;
        }
        
        @Override
        public String getName() {
            return "Partition";
        }
    }
}
