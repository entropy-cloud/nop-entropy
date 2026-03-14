/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.jobgraph;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.nop.stream.core.common.typeinfo.TypeInformation;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for JobGraph class.
 */
public class TestJobGraph {

    private JobGraph jobGraph;
    private JobVertex sourceVertex;
    private JobVertex mapVertex;
    private JobVertex sinkVertex;

    @BeforeEach
    public void setUp() {
        jobGraph = new JobGraph("test-job");
        sourceVertex = createTestVertex("vertex-1", "Source", 2);
        mapVertex = createTestVertex("vertex-2", "Map", 2);
        sinkVertex = createTestVertex("vertex-3", "Sink", 2);
    }

    @Test
    public void testJobGraphConstruction() {
        assertNotNull(jobGraph);
        assertEquals("test-job", jobGraph.getJobName());
        assertEquals(0, jobGraph.getNumberOfVertices());
        assertEquals(0, jobGraph.getNumberOfEdges());
    }

    @Test
    public void testJobGraphNullName() {
        assertThrows(IllegalArgumentException.class, () -> new JobGraph(null));
    }

    @Test
    public void testAddVertex() {
        jobGraph.addVertex(sourceVertex);
        assertEquals(1, jobGraph.getNumberOfVertices());
        assertTrue(jobGraph.containsVertex("vertex-1"));
        assertEquals(sourceVertex, jobGraph.getVertex("vertex-1"));
    }

    @Test
    public void testAddNullVertex() {
        assertThrows(IllegalArgumentException.class, () -> jobGraph.addVertex(null));
    }

    @Test
    public void testAddDuplicateVertex() {
        jobGraph.addVertex(sourceVertex);
        assertThrows(IllegalArgumentException.class, () -> jobGraph.addVertex(sourceVertex));
    }

    @Test
    public void testGetVertices() {
        jobGraph.addVertex(sourceVertex);
        jobGraph.addVertex(mapVertex);
        
        Map<String, JobVertex> vertices = jobGraph.getVertices();
        assertEquals(2, vertices.size());
        assertTrue(vertices.containsKey("vertex-1"));
        assertTrue(vertices.containsKey("vertex-2"));
        
        assertThrows(UnsupportedOperationException.class, () -> vertices.put("vertex-3", sinkVertex));
    }

    @Test
    public void testAddEdgeWithNonexistentSource() {
        JobEdge edge = new JobEdge("vertex-1", "vertex-2", ResultPartitionType.PIPELINED);
        assertThrows(IllegalArgumentException.class, () -> jobGraph.addEdge(edge));
    }

    @Test
    public void testAddEdgeWithNonexistentTarget() {
        jobGraph.addVertex(sourceVertex);
        JobEdge edge = new JobEdge("vertex-1", "vertex-2", ResultPartitionType.PIPELINED);
        assertThrows(IllegalArgumentException.class, () -> jobGraph.addEdge(edge));
    }

    @Test
    public void testAddMultipleEdges() {
        jobGraph.addVertex(sourceVertex);
        jobGraph.addVertex(mapVertex);
        jobGraph.addVertex(sinkVertex);
        
        JobEdge edge1 = new JobEdge("vertex-1", "vertex-2", ResultPartitionType.PIPELINED);
        JobEdge edge2 = new JobEdge("vertex-2", "vertex-3", ResultPartitionType.PIPELINED_BOUNDED);
        
        jobGraph.addEdge(edge1);
        jobGraph.addEdge(edge2);
        
        assertEquals(2, jobGraph.getNumberOfEdges());
    }

    @Test
    public void testGetEdges() {
        jobGraph.addVertex(sourceVertex);
        jobGraph.addVertex(mapVertex);
        
        JobEdge edge = new JobEdge("vertex-1", "vertex-2", ResultPartitionType.PIPELINED);
        jobGraph.addEdge(edge);
        
        List<JobEdge> edges = jobGraph.getEdges();
        assertEquals(1, edges.size());
        assertEquals(edge, edges.get(0));
        
        assertThrows(UnsupportedOperationException.class, () -> 
            edges.add(new JobEdge("vertex-2", "vertex-3", ResultPartitionType.PIPELINED)));
    }

    @Test
    public void testContainsVertex() {
        assertFalse(jobGraph.containsVertex("vertex-1"));
        jobGraph.addVertex(sourceVertex);
        assertTrue(jobGraph.containsVertex("vertex-1"));
        assertFalse(jobGraph.containsVertex("vertex-2"));
    }

    @Test
    public void testGetVertex() {
        assertNull(jobGraph.getVertex("vertex-1"));
        jobGraph.addVertex(sourceVertex);
        JobVertex retrieved = jobGraph.getVertex("vertex-1");
        assertNotNull(retrieved);
        assertEquals(sourceVertex, retrieved);
    }

    @Test
    public void testClear() {
        jobGraph.addVertex(sourceVertex);
        jobGraph.addVertex(mapVertex);
        
        JobEdge edge = new JobEdge("vertex-1", "vertex-2", ResultPartitionType.PIPELINED);
        jobGraph.addEdge(edge);
        
        assertEquals(2, jobGraph.getNumberOfVertices());
        assertEquals(1, jobGraph.getNumberOfEdges());
        
        jobGraph.clear();
        
        assertEquals(0, jobGraph.getNumberOfVertices());
        assertEquals(0, jobGraph.getNumberOfEdges());
        assertTrue(jobGraph.getVertices().isEmpty());
        assertTrue(jobGraph.getEdges().isEmpty());
    }

    @Test
    public void testSerialization() throws Exception {
        jobGraph.addVertex(sourceVertex);
        jobGraph.addVertex(mapVertex);
        jobGraph.addVertex(sinkVertex);
        
        JobEdge edge1 = new JobEdge("vertex-1", "vertex-2", ResultPartitionType.PIPELINED);
        JobEdge edge2 = new JobEdge("vertex-2", "vertex-3", ResultPartitionType.PIPELINED_BOUNDED);
        
        jobGraph.addEdge(edge1);
        jobGraph.addEdge(edge2);
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(jobGraph);
        oos.close();
        
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        JobGraph deserialized = (JobGraph) ois.readObject();
        
        assertNotNull(deserialized);
        assertEquals("test-job", deserialized.getJobName());
        assertEquals(3, deserialized.getNumberOfVertices());
        assertEquals(2, deserialized.getNumberOfEdges());
    }

    @Test
    public void testDifferentParallelismVertices() {
        JobVertex vertex1 = createTestVertex("v1", "Op1", 1);
        JobVertex vertex2 = createTestVertex("v2", "Op2", 4);
        JobVertex vertex3 = createTestVertex("v3", "Op3", 8);
        
        jobGraph.addVertex(vertex1);
        jobGraph.addVertex(vertex2);
        jobGraph.addVertex(vertex3);
        
        jobGraph.addEdge(new JobEdge("v1", "v2", ResultPartitionType.PIPELINED));
        jobGraph.addEdge(new JobEdge("v2", "v3", ResultPartitionType.PIPELINED));
        
        assertEquals(3, jobGraph.getNumberOfVertices());
        assertEquals(2, jobGraph.getNumberOfEdges());
        
        assertEquals(1, jobGraph.getVertex("v1").getParallelism());
        assertEquals(4, jobGraph.getVertex("v2").getParallelism());
        assertEquals(8, jobGraph.getVertex("v3").getParallelism());
    }

    @Test
    public void testDifferentPartitionTypes() {
        jobGraph.addVertex(sourceVertex);
        jobGraph.addVertex(mapVertex);
        jobGraph.addVertex(sinkVertex);
        
        JobEdge pipelinedEdge = new JobEdge("vertex-1", "vertex-2", ResultPartitionType.PIPELINED);
        JobEdge boundedEdge = new JobEdge("vertex-2", "vertex-3", ResultPartitionType.PIPELINED_BOUNDED);
        
        jobGraph.addEdge(pipelinedEdge);
        jobGraph.addEdge(boundedEdge);
        
        List<JobEdge> edges = jobGraph.getEdges();
        assertEquals(2, edges.size());
        assertEquals(ResultPartitionType.PIPELINED, edges.get(0).getPartitionType());
        assertEquals(ResultPartitionType.PIPELINED_BOUNDED, edges.get(1).getPartitionType());
    }

    @Test
    public void testToString() {
        jobGraph.addVertex(sourceVertex);
        String str = jobGraph.toString();
        assertNotNull(str);
        assertTrue(str.contains("test-job"));
        assertTrue(str.contains("1"));
    }

    private JobVertex createTestVertex(String id, String name, int parallelism) {
        Invokable<Void> invokable = () -> {};
        
        List<io.nop.stream.core.operator.StreamOperator<?>> operators = new ArrayList<>();
        operators.add(new TestOperator(name));
        
        OperatorChain chain = new OperatorChain(operators);
        
        return new JobVertex(id, name, parallelism, Collections.singletonList(chain), invokable);
    }
    
    private static class TestOperator implements io.nop.stream.core.operator.StreamOperator<Object>, java.io.Serializable {
        private final String name;
        
        TestOperator(String name) {
            this.name = name;
        }
        
        @Override
        public io.nop.stream.core.common.typeinfo.TypeInformation<Object> getOutputType() {
            return new TypeInformation<Object>() {
                @Override
                public Class<Object> getTypeClass() {
                    return Object.class;
                }
            };
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
        public io.nop.stream.core.operator.StreamOperator.ChainingStrategy getChainingStrategy() {
            return io.nop.stream.core.operator.StreamOperator.ChainingStrategy.ALWAYS;
        }

    }
}
