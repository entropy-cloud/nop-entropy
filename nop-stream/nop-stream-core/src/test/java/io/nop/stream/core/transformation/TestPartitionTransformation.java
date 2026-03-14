/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.transformation;

import io.nop.commons.partition.IPartitioner;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for PartitionTransformation class.
 */
public class TestPartitionTransformation {

    // ========== Construction Tests ==========

    @Test
    public void testBasicConstruction() {
        TypeInformation<String> outputType = createStringTypeInformation();
        Transformation<String> input = createMockTransformation("Input", 2);
        IPartitioner<String> partitioner = new HashPartitioner<>();

        PartitionTransformation<String> transformation = new PartitionTransformation<>(
                input, "TestPartition", partitioner, outputType, 4);

        assertNotNull(transformation);
        assertEquals("TestPartition", transformation.getName());
        assertEquals(4, transformation.getParallelism());
        assertEquals(outputType, transformation.getOutputType());
        assertEquals(input, transformation.getInput());
        assertEquals(partitioner, transformation.getPartitioner());
    }

    @Test
    public void testGetInputs() {
        TypeInformation<String> outputType = createStringTypeInformation();
        Transformation<String> input = createMockTransformation("Input", 2);
        IPartitioner<String> partitioner = new HashPartitioner<>();

        PartitionTransformation<String> transformation = new PartitionTransformation<>(
                input, "TestPartition", partitioner, outputType, 4);

        List<Transformation<?>> inputs = transformation.getInputs();
        assertNotNull(inputs);
        assertEquals(1, inputs.size());
        assertEquals(input, inputs.get(0));
    }

    @Test
    public void testGetInputsReturnsSingletonList() {
        TypeInformation<String> outputType = createStringTypeInformation();
        Transformation<String> input = createMockTransformation("Input", 2);
        IPartitioner<String> partitioner = new HashPartitioner<>();

        PartitionTransformation<String> transformation = new PartitionTransformation<>(
                input, "TestPartition", partitioner, outputType, 4);

        // Verify the returned list is unmodifiable/singleton
        List<Transformation<?>> inputs = transformation.getInputs();
        assertThrows(UnsupportedOperationException.class, () -> inputs.add(null));
    }

    // ========== Parallelism Tests ==========

    @Test
    public void testDifferentParallelismValues() {
        TypeInformation<String> outputType = createStringTypeInformation();
        Transformation<String> input = createMockTransformation("Input", 1);
        IPartitioner<String> partitioner = new HashPartitioner<>();

        // Test parallelism = 1
        PartitionTransformation<String> t1 = new PartitionTransformation<>(
                input, "P1", partitioner, outputType, 1);
        assertEquals(1, t1.getParallelism());

        // Test parallelism = 2
        PartitionTransformation<String> t2 = new PartitionTransformation<>(
                input, "P2", partitioner, outputType, 2);
        assertEquals(2, t2.getParallelism());

        // Test parallelism = 16
        PartitionTransformation<String> t16 = new PartitionTransformation<>(
                input, "P16", partitioner, outputType, 16);
        assertEquals(16, t16.getParallelism());
    }

    // ========== ID Generation Tests ==========

    @Test
    public void testUniqueIdGeneration() {
        TypeInformation<String> outputType = createStringTypeInformation();
        Transformation<String> input = createMockTransformation("Input", 2);
        IPartitioner<String> partitioner = new HashPartitioner<>();

        PartitionTransformation<String> t1 = new PartitionTransformation<>(
                input, "T1", partitioner, outputType, 2);
        PartitionTransformation<String> t2 = new PartitionTransformation<>(
                input, "T2", partitioner, outputType, 2);

        assertNotEquals(t1.getId(), t2.getId());
        assertTrue(t1.getId() > 0);
        assertTrue(t2.getId() > 0);
    }

    // ========== Partitioner Tests ==========

    @Test
    public void testPartitionerIsPreserved() {
        TypeInformation<String> outputType = createStringTypeInformation();
        Transformation<String> input = createMockTransformation("Input", 2);
        
        // Test with different partitioners
        IPartitioner<String> hashPartitioner = new HashPartitioner<>();
        IPartitioner<String> roundRobinPartitioner = new RoundRobinPartitioner<>();

        PartitionTransformation<String> t1 = new PartitionTransformation<>(
                input, "HashPartition", hashPartitioner, outputType, 4);
        PartitionTransformation<String> t2 = new PartitionTransformation<>(
                input, "RoundRobinPartition", roundRobinPartitioner, outputType, 4);

        assertEquals(hashPartitioner, t1.getPartitioner());
        assertEquals(roundRobinPartitioner, t2.getPartitioner());
        assertNotEquals(t1.getPartitioner(), t2.getPartitioner());
    }

    @Test
    public void testPartitionerWithCustomLogic() {
        TypeInformation<Integer> outputType = createIntegerTypeInformation();
        Transformation<Integer> input = createMockTransformation("Input", 2);
        
        // Custom partitioner that partitions based on modulo
        IPartitioner<Integer> moduloPartitioner = (key, numPartitions) -> key % numPartitions;

        PartitionTransformation<Integer> transformation = new PartitionTransformation<>(
                input, "ModuloPartition", moduloPartitioner, outputType, 4);

        assertNotNull(transformation.getPartitioner());
        assertEquals(0, transformation.getPartitioner().partition(4, 4));
        assertEquals(1, transformation.getPartitioner().partition(5, 4));
        assertEquals(2, transformation.getPartitioner().partition(6, 4));
        assertEquals(3, transformation.getPartitioner().partition(7, 4));
    }

    // ========== Type Information Tests ==========

    @Test
    public void testOutputTypeIsPreserved() {
        TypeInformation<String> stringType = createStringTypeInformation();
        TypeInformation<Integer> integerType = createIntegerTypeInformation();
        
        Transformation<String> stringInput = createMockTransformation("StringInput", 2);
        Transformation<Integer> intInput = createMockTransformation("IntInput", 2);
        
        IPartitioner<String> stringPartitioner = new HashPartitioner<>();
        IPartitioner<Integer> intPartitioner = new HashPartitioner<>();

        PartitionTransformation<String> stringTransform = new PartitionTransformation<>(
                stringInput, "StringPartition", stringPartitioner, stringType, 2);
        PartitionTransformation<Integer> intTransform = new PartitionTransformation<>(
                intInput, "IntPartition", intPartitioner, integerType, 2);

        assertEquals(stringType, stringTransform.getOutputType());
        assertEquals(String.class, stringTransform.getOutputType().getTypeClass());
        
        assertEquals(integerType, intTransform.getOutputType());
        assertEquals(Integer.class, intTransform.getOutputType().getTypeClass());
    }

    // ========== Chained Transformation Tests ==========

    @Test
    public void testPartitionInChain() {
        // Create a chain: Source -> Partition -> Map
        TypeInformation<String> stringType = createStringTypeInformation();
        Transformation<String> source = createMockTransformation("Source", 1);
        IPartitioner<String> partitioner = new HashPartitioner<>();

        PartitionTransformation<String> partition = new PartitionTransformation<>(
                source, "Partition", partitioner, stringType, 4);

        // Verify the chain structure
        assertEquals(source, partition.getInput());
        assertEquals(1, partition.getInputs().size());
    }

    // ========== Edge Cases ==========

    @Test
    public void testPartitionWithNullPartitionerResult() {
        TypeInformation<String> outputType = createStringTypeInformation();
        Transformation<String> input = createMockTransformation("Input", 2);
        
        // Partitioner that returns 0 for null keys
        IPartitioner<String> nullSafePartitioner = (key, numPartitions) -> {
            if (key == null) {
                return 0;
            }
            return Math.abs(key.hashCode()) % numPartitions;
        };

        PartitionTransformation<String> transformation = new PartitionTransformation<>(
                input, "NullSafePartition", nullSafePartitioner, outputType, 4);

        assertNotNull(transformation.getPartitioner());
        assertEquals(0, transformation.getPartitioner().partition(null, 4));
    }

    @Test
    public void testPartitionWithHighParallelism() {
        TypeInformation<String> outputType = createStringTypeInformation();
        Transformation<String> input = createMockTransformation("Input", 1);
        IPartitioner<String> partitioner = new HashPartitioner<>();

        PartitionTransformation<String> transformation = new PartitionTransformation<>(
                input, "HighParallelism", partitioner, outputType, 1024);

        assertEquals(1024, transformation.getParallelism());
    }

    @Test
    public void testPartitionWithSameInputAndOutputParallelism() {
        TypeInformation<String> outputType = createStringTypeInformation();
        Transformation<String> input = createMockTransformation("Input", 4);
        IPartitioner<String> partitioner = new HashPartitioner<>();

        PartitionTransformation<String> transformation = new PartitionTransformation<>(
                input, "SameParallelism", partitioner, outputType, 4);

        assertEquals(4, transformation.getParallelism());
        assertEquals(input.getParallelism(), transformation.getParallelism());
    }

    // ========== Serialization Tests ==========

    @Test
    public void testSerialization() {
        TypeInformation<String> outputType = createStringTypeInformation();
        Transformation<String> input = createMockTransformation("Input", 2);
        IPartitioner<String> partitioner = new HashPartitioner<>();

        PartitionTransformation<String> original = new PartitionTransformation<>(
                input, "TestPartition", partitioner, outputType, 4);

        // Verify the class implements Serializable
        assertTrue(original instanceof java.io.Serializable);
    }

    // ========== Helper Methods ==========

    private <T> Transformation<T> createMockTransformation(String name, int parallelism) {
        return new Transformation<T>(name, null, parallelism) {
            private static final long serialVersionUID = 1L;

            @Override
            public List<Transformation<?>> getInputs() {
                return java.util.Collections.emptyList();
            }
        };
    }

    private TypeInformation<String> createStringTypeInformation() {
        return new TypeInformation<String>() {
            @Override
            public Class<String> getTypeClass() {
                return String.class;
            }
        };
    }

    private TypeInformation<Integer> createIntegerTypeInformation() {
        return new TypeInformation<Integer>() {
            @Override
            public Class<Integer> getTypeClass() {
                return Integer.class;
            }
        };
    }

    // ========== Test Helper Classes ==========

    private static class HashPartitioner<T> implements IPartitioner<T> {
        @Override
        public int partition(T key, int numPartitions) {
            if (key == null) {
                return 0;
            }
            return Math.abs(key.hashCode()) % numPartitions;
        }
    }

    private static class RoundRobinPartitioner<T> implements IPartitioner<T> {
        private int counter = 0;

        @Override
        public int partition(T key, int numPartitions) {
            int partition = counter % numPartitions;
            counter++;
            return partition;
        }
    }
}
