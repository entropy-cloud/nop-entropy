/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.jobgraph;

import io.nop.stream.core.graph.StreamEdge;
import io.nop.stream.core.graph.StreamGraph;
import io.nop.stream.core.graph.StreamNode;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.operator.StreamOperator;
import io.nop.stream.core.operator.StreamOperatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for JobGraphGenerator class.
 */
public class TestJobGraphGenerator {

    private JobGraphGenerator generator;

    @BeforeEach
    public void setUp() {
        generator = new JobGraphGenerator();
    }

    @Test
    public void testGenerateNullStreamGraph() {
        assertThrows(IllegalArgumentException.class, () -> generator.generate(null));
    }

    @Test
    public void testSimpleChain() {
        StreamGraph streamGraph = new StreamGraph();
        
        StreamNode sourceNode = createStreamNode(1, "Source", 2);
        StreamNode mapNode = createStreamNode(2, "Map", 2);
        StreamNode filterNode = createStreamNode(3, "Filter", 2);
        
        streamGraph.addStreamNode(sourceNode);
        streamGraph.addStreamNode(mapNode);
        streamGraph.addStreamNode(filterNode);
        
        streamGraph.addSourceID(1);
        
        StreamEdge edge1 = new StreamEdge(1, 2);
        StreamEdge edge2 = new StreamEdge(2, 3);
        
        streamGraph.addStreamEdge(edge1);
        streamGraph.addStreamEdge(edge2);
        
        JobGraph jobGraph = generator.generate(streamGraph);
        
        assertNotNull(jobGraph);
        assertEquals(1, jobGraph.getNumberOfVertices());
        assertEquals(0, jobGraph.getNumberOfEdges());
        
        JobVertex vertex = jobGraph.getVertex("vertex-1");
        assertNotNull(vertex);
        assertEquals("Source -> Map -> Filter", vertex.getName());
        assertEquals(2, vertex.getParallelism());
        
        assertFalse(vertex.getOperatorChains().isEmpty());
    }

    @Test
    public void testNonChainableDifferentParallelism() {
        StreamGraph streamGraph = new StreamGraph();
        
        StreamNode sourceNode = createStreamNode(1, "Source", 2);
        StreamNode mapNode = createStreamNode(2, "Map", 4);
        
        streamGraph.addStreamNode(sourceNode);
        streamGraph.addStreamNode(mapNode);
        
        streamGraph.addSourceID(1);
        
        StreamEdge edge = new StreamEdge(1, 2);
        streamGraph.addStreamEdge(edge);
        
        JobGraph jobGraph = generator.generate(streamGraph);
        
        assertNotNull(jobGraph);
        assertEquals(2, jobGraph.getNumberOfVertices());
        assertEquals(1, jobGraph.getNumberOfEdges());
        
        assertNotNull(jobGraph.getVertex("vertex-1"));
        assertNotNull(jobGraph.getVertex("vertex-2"));
        
        assertEquals(2, jobGraph.getVertex("vertex-1").getParallelism());
        assertEquals(4, jobGraph.getVertex("vertex-2").getParallelism());
    }

    @Test
    public void testMultipleSources() {
        StreamGraph streamGraph = new StreamGraph();
        
        StreamNode source1 = createStreamNode(1, "Source1", 2);
        StreamNode source2 = createStreamNode(2, "Source2", 2);
        StreamNode map = createStreamNode(3, "Map", 2);
        StreamNode sink = createStreamNode(4, "Sink", 2);
        
        streamGraph.addStreamNode(source1);
        streamGraph.addStreamNode(source2);
        streamGraph.addStreamNode(map);
        streamGraph.addStreamNode(sink);
        
        streamGraph.addSourceID(1);
        streamGraph.addSourceID(2);
        streamGraph.addSinkID(4);
        
        streamGraph.addStreamEdge(new StreamEdge(1, 3));
        streamGraph.addStreamEdge(new StreamEdge(2, 3));
        streamGraph.addStreamEdge(new StreamEdge(3, 4));
        
        JobGraph jobGraph = generator.generate(streamGraph);
        
        assertNotNull(jobGraph);
        assertTrue(jobGraph.getNumberOfVertices() >= 2);
        
        assertTrue(jobGraph.containsVertex("vertex-1"));
        assertTrue(jobGraph.containsVertex("vertex-2"));
    }

    @Test
    public void testEdgeCreation() {
        StreamGraph streamGraph = new StreamGraph();
        
        StreamNode source = createStreamNode(1, "Source", 2);
        StreamNode map = createStreamNode(2, "Map", 3);
        StreamNode sink = createStreamNode(3, "Sink", 4);

        
        streamGraph.addStreamNode(source);
        streamGraph.addStreamNode(map);
        streamGraph.addStreamNode(sink);
        
        streamGraph.addSourceID(1);
        streamGraph.addSinkID(3);
        
        streamGraph.addStreamEdge(new StreamEdge(1, 2));
        streamGraph.addStreamEdge(new StreamEdge(2, 3));
        
        JobGraph jobGraph = generator.generate(streamGraph);
        
        assertNotNull(jobGraph);
        assertEquals(3, jobGraph.getNumberOfVertices());
        assertEquals(2, jobGraph.getNumberOfEdges());
        
        List<JobEdge> edges = jobGraph.getEdges();
        assertEquals(2, edges.size());
        
        assertEquals("vertex-1", edges.get(0).getSourceVertex());
        assertEquals("vertex-2", edges.get(0).getTargetVertex());
        assertEquals(ResultPartitionType.PIPELINED, edges.get(0).getPartitionType());
    }

    @Test
    public void testIdMapping() {
        StreamGraph streamGraph = new StreamGraph();
        
        StreamNode node1 = createStreamNode(1, "Op1", 2);
        StreamNode node2 = createStreamNode(2, "Op2", 3);
        StreamNode node3 = createStreamNode(3, "Op3", 4);
        
        streamGraph.addStreamNode(node1);
        streamGraph.addStreamNode(node2);
        streamGraph.addStreamNode(node3);
        
        streamGraph.addSourceID(1);
        
        streamGraph.addStreamEdge(new StreamEdge(1, 2));
        streamGraph.addStreamEdge(new StreamEdge(2, 3));
        
        JobGraph jobGraph = generator.generate(streamGraph);
        
        assertNotNull(jobGraph);
        
        assertTrue(jobGraph.containsVertex("vertex-1"));
        assertTrue(jobGraph.containsVertex("vertex-2"));
        assertTrue(jobGraph.containsVertex("vertex-3"));
    }

    private StreamNode createStreamNode(int id, String name, int parallelism) {
        TypeInformation<?> outputType = Types.STRING;
        StreamOperatorFactory<?> operatorFactory = new TestOperatorFactory(name);
        
        return new StreamNode(id, name, operatorFactory, outputType, parallelism);
    }
    
    private static class TestOperatorFactory implements StreamOperatorFactory<Object> {
        private final String name;
        
        TestOperatorFactory(String name) {
            this.name = name;
        }
        
        @Override
        public StreamOperator<Object> createStreamOperator(TypeInformation<Object> outputType) {
            return new TestOperator(name);
        }
        
        @Override
        public String getName() {
            return name;
        }
        
        @Override
        public int getParallelism() {
            return 1;
        }
    }
    
    private static class TestOperator implements StreamOperator<Object> {
        private final String name;
        
        TestOperator(String name) {
            this.name = name;
        }
        
        @Override
        public TypeInformation<Object> getOutputType() {
            return null;
        }
        
        @Override
        public String getName() {
            return name;
        }
        
        @Override
        public void initialize() {
        }
        
        @Override
        public void open() {
        }
        
        @Override
        public void close() {
        }
        
        @Override
        public ChainingStrategy getChainingStrategy() {
            return ChainingStrategy.ALWAYS;
        }
    }
    
    /**
     * Type information helper for testing
     */
    private static class Types {
        static final TypeInformation<String> STRING = new TypeInformation<String>() {
            @Override
            public Class<String> getTypeClass() {
                return String.class;
            }
        };
    }
}
