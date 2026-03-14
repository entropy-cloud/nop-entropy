/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.transformation;

import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for SinkTransformation class.
 */
public class TestSinkTransformation {

    // ========== Construction Tests ==========

    @Test
    public void testBasicConstruction() {
        TypeInformation<String> inputType = createStringTypeInformation();
        TypeInformation<Void> outputType = createVoidTypeInformation();
        Transformation<String> input = createMockTransformation("Input", inputType, 2);
        SinkFunction<String> sinkFunction = new CollectingSinkFunction<>();

        SinkTransformation<String> transformation = new SinkTransformation<>(
                input, "TestSink", sinkFunction, outputType, 1);

        assertNotNull(transformation);
        assertEquals("TestSink", transformation.getName());
        assertEquals(1, transformation.getParallelism());
        assertEquals(outputType, transformation.getOutputType());
        assertEquals(input, transformation.getInput());
        assertEquals(sinkFunction, transformation.getSinkFunction());
    }

    @Test
    public void testGetInputs() {
        TypeInformation<String> inputType = createStringTypeInformation();
        TypeInformation<Void> outputType = createVoidTypeInformation();
        Transformation<String> input = createMockTransformation("Input", inputType, 2);
        SinkFunction<String> sinkFunction = new CollectingSinkFunction<>();

        SinkTransformation<String> transformation = new SinkTransformation<>(
                input, "TestSink", sinkFunction, outputType, 1);

        List<Transformation<?>> inputs = transformation.getInputs();
        assertNotNull(inputs);
        assertEquals(1, inputs.size());
        assertEquals(input, inputs.get(0));
    }

    @Test
    public void testGetInputsReturnsSingletonList() {
        TypeInformation<String> inputType = createStringTypeInformation();
        TypeInformation<Void> outputType = createVoidTypeInformation();
        Transformation<String> input = createMockTransformation("Input", inputType, 2);
        SinkFunction<String> sinkFunction = new CollectingSinkFunction<>();

        SinkTransformation<String> transformation = new SinkTransformation<>(
                input, "TestSink", sinkFunction, outputType, 1);

        // Verify the returned list is unmodifiable/singleton
        List<Transformation<?>> inputs = transformation.getInputs();
        assertThrows(UnsupportedOperationException.class, () -> inputs.add(null));
    }

    // ========== Parallelism Tests ==========

    @Test
    public void testDifferentParallelismValues() {
        TypeInformation<String> inputType = createStringTypeInformation();
        TypeInformation<Void> outputType = createVoidTypeInformation();
        Transformation<String> input = createMockTransformation("Input", inputType, 1);
        SinkFunction<String> sinkFunction = new CollectingSinkFunction<>();

        // Test parallelism = 1
        SinkTransformation<String> t1 = new SinkTransformation<>(
                input, "Sink1", sinkFunction, outputType, 1);
        assertEquals(1, t1.getParallelism());

        // Test parallelism = 2
        SinkTransformation<String> t2 = new SinkTransformation<>(
                input, "Sink2", sinkFunction, outputType, 2);
        assertEquals(2, t2.getParallelism());

        // Test parallelism = 8
        SinkTransformation<String> t8 = new SinkTransformation<>(
                input, "Sink8", sinkFunction, outputType, 8);
        assertEquals(8, t8.getParallelism());
    }

    // ========== ID Generation Tests ==========

    @Test
    public void testUniqueIdGeneration() {
        TypeInformation<String> inputType = createStringTypeInformation();
        TypeInformation<Void> outputType = createVoidTypeInformation();
        Transformation<String> input = createMockTransformation("Input", inputType, 2);
        SinkFunction<String> sinkFunction = new CollectingSinkFunction<>();

        SinkTransformation<String> t1 = new SinkTransformation<>(
                input, "Sink1", sinkFunction, outputType, 1);
        SinkTransformation<String> t2 = new SinkTransformation<>(
                input, "Sink2", sinkFunction, outputType, 1);

        assertNotEquals(t1.getId(), t2.getId());
        assertTrue(t1.getId() > 0);
        assertTrue(t2.getId() > 0);
    }

    // ========== Sink Function Tests ==========

    @Test
    public void testSinkFunctionIsPreserved() {
        TypeInformation<String> inputType = createStringTypeInformation();
        TypeInformation<Void> outputType = createVoidTypeInformation();
        Transformation<String> input = createMockTransformation("Input", inputType, 2);

        CollectingSinkFunction<String> collectingSink = new CollectingSinkFunction<>();
        NoOpSinkFunction<String> noOpSink = new NoOpSinkFunction<>();

        SinkTransformation<String> t1 = new SinkTransformation<>(
                input, "CollectingSink", collectingSink, outputType, 1);
        SinkTransformation<String> t2 = new SinkTransformation<>(
                input, "NoOpSink", noOpSink, outputType, 1);

        assertEquals(collectingSink, t1.getSinkFunction());
        assertEquals(noOpSink, t2.getSinkFunction());
        assertNotEquals(t1.getSinkFunction(), t2.getSinkFunction());
    }

    @Test
    public void testSinkFunctionConsumesData() throws Exception {
        TypeInformation<String> inputType = createStringTypeInformation();
        TypeInformation<Void> outputType = createVoidTypeInformation();
        Transformation<String> input = createMockTransformation("Input", inputType, 2);

        CollectingSinkFunction<String> collectingSink = new CollectingSinkFunction<>();

        SinkTransformation<String> transformation = new SinkTransformation<>(
                input, "CollectingSink", collectingSink, outputType, 1);

        // Test that the sink function can consume data
        SinkFunction<String> sinkFunc = transformation.getSinkFunction();
        sinkFunc.consume("test1");
        sinkFunc.consume("test2");
        sinkFunc.consume("test3");

        List<String> collected = collectingSink.getCollected();
        assertEquals(3, collected.size());
        assertTrue(collected.contains("test1"));
        assertTrue(collected.contains("test2"));
        assertTrue(collected.contains("test3"));
    }

    // ========== Type Information Tests ==========

    @Test
    public void testOutputTypeIsVoid() {
        TypeInformation<String> inputType = createStringTypeInformation();
        TypeInformation<Void> outputType = createVoidTypeInformation();
        Transformation<String> input = createMockTransformation("Input", inputType, 2);
        SinkFunction<String> sinkFunction = new CollectingSinkFunction<>();

        SinkTransformation<String> transformation = new SinkTransformation<>(
                input, "TestSink", sinkFunction, outputType, 1);

        assertEquals(Void.class, transformation.getOutputType().getTypeClass());
    }

    @Test
    public void testDifferentInputTypes() {
        TypeInformation<Void> outputType = createVoidTypeInformation();
        SinkFunction<String> stringSink = new CollectingSinkFunction<>();
        SinkFunction<Integer> intSink = new CollectingSinkFunction<>();

        Transformation<String> stringInput = createMockTransformation("StringInput", 
                createStringTypeInformation(), 2);
        Transformation<Integer> intInput = createMockTransformation("IntInput", 
                createIntegerTypeInformation(), 2);

        SinkTransformation<String> stringSinkTransform = new SinkTransformation<>(
                stringInput, "StringSink", stringSink, outputType, 1);
        SinkTransformation<Integer> intSinkTransform = new SinkTransformation<>(
                intInput, "IntSink", intSink, outputType, 1);

        assertEquals(String.class, stringSinkTransform.getInput().getOutputType().getTypeClass());
        assertEquals(Integer.class, intSinkTransform.getInput().getOutputType().getTypeClass());
    }

    // ========== PhysicalTransformation Inheritance Tests ==========

    @Test
    public void testExtendsPhysicalTransformation() {
        TypeInformation<String> inputType = createStringTypeInformation();
        TypeInformation<Void> outputType = createVoidTypeInformation();
        Transformation<String> input = createMockTransformation("Input", inputType, 2);
        SinkFunction<String> sinkFunction = new CollectingSinkFunction<>();

        SinkTransformation<String> transformation = new SinkTransformation<>(
                input, "TestSink", sinkFunction, outputType, 1);

        // Verify it extends PhysicalTransformation
        assertTrue(transformation instanceof PhysicalTransformation);
    }

    @Test
    public void testExtendsTransformation() {
        TypeInformation<String> inputType = createStringTypeInformation();
        TypeInformation<Void> outputType = createVoidTypeInformation();
        Transformation<String> input = createMockTransformation("Input", inputType, 2);
        SinkFunction<String> sinkFunction = new CollectingSinkFunction<>();

        SinkTransformation<String> transformation = new SinkTransformation<>(
                input, "TestSink", sinkFunction, outputType, 1);

        // Verify it extends Transformation
        assertTrue(transformation instanceof Transformation);
    }

    // ========== Chained Transformation Tests ==========

    @Test
    public void testSinkAtEndOfChain() {
        // Create a chain: Source -> Map -> Filter -> Sink
        TypeInformation<String> stringType = createStringTypeInformation();
        TypeInformation<Void> voidType = createVoidTypeInformation();

        Transformation<String> source = createMockTransformation("Source", stringType, 1);
        Transformation<String> map = createMockTransformation("Map", stringType, 2);
        Transformation<String> filter = createMockTransformation("Filter", stringType, 2);
        
        SinkFunction<String> sinkFunction = new CollectingSinkFunction<>();

        SinkTransformation<String> sink = new SinkTransformation<>(
                filter, "Sink", sinkFunction, voidType, 2);

        // Verify the chain structure
        assertEquals(filter, sink.getInput());
        assertEquals(1, sink.getInputs().size());
    }

    // ========== Edge Cases ==========

    @Test
    public void testSinkWithHighParallelism() {
        TypeInformation<String> inputType = createStringTypeInformation();
        TypeInformation<Void> outputType = createVoidTypeInformation();
        Transformation<String> input = createMockTransformation("Input", inputType, 1);
        SinkFunction<String> sinkFunction = new CollectingSinkFunction<>();

        SinkTransformation<String> transformation = new SinkTransformation<>(
                input, "HighParallelismSink", sinkFunction, outputType, 1024);

        assertEquals(1024, transformation.getParallelism());
    }

    @Test
    public void testSinkWithSameInputParallelism() {
        TypeInformation<String> inputType = createStringTypeInformation();
        TypeInformation<Void> outputType = createVoidTypeInformation();
        Transformation<String> input = createMockTransformation("Input", inputType, 4);
        SinkFunction<String> sinkFunction = new CollectingSinkFunction<>();

        SinkTransformation<String> transformation = new SinkTransformation<>(
                input, "SameParallelismSink", sinkFunction, outputType, 4);

        assertEquals(4, transformation.getParallelism());
        assertEquals(input.getParallelism(), transformation.getParallelism());
    }

    @Test
    public void testSinkWithDifferentInputParallelism() {
        TypeInformation<String> inputType = createStringTypeInformation();
        TypeInformation<Void> outputType = createVoidTypeInformation();
        Transformation<String> input = createMockTransformation("Input", inputType, 2);
        SinkFunction<String> sinkFunction = new CollectingSinkFunction<>();

        SinkTransformation<String> transformation = new SinkTransformation<>(
                input, "DifferentParallelismSink", sinkFunction, outputType, 8);

        assertEquals(8, transformation.getParallelism());
        assertNotEquals(input.getParallelism(), transformation.getParallelism());
    }

    // ========== Serialization Tests ==========

    @Test
    public void testSerialization() {
        TypeInformation<String> inputType = createStringTypeInformation();
        TypeInformation<Void> outputType = createVoidTypeInformation();
        Transformation<String> input = createMockTransformation("Input", inputType, 2);
        SinkFunction<String> sinkFunction = new CollectingSinkFunction<>();

        SinkTransformation<String> transformation = new SinkTransformation<>(
                input, "TestSink", sinkFunction, outputType, 1);

        // Verify the class implements Serializable
        assertTrue(transformation instanceof java.io.Serializable);
    }

    // ========== Multiple Sinks Tests ==========

    @Test
    public void testMultipleSinksFromSameSource() {
        // Create two sinks from the same source
        TypeInformation<String> inputType = createStringTypeInformation();
        TypeInformation<Void> outputType = createVoidTypeInformation();
        Transformation<String> source = createMockTransformation("Source", inputType, 2);

        SinkFunction<String> sinkFunction1 = new CollectingSinkFunction<>();
        SinkFunction<String> sinkFunction2 = new CollectingSinkFunction<>();

        SinkTransformation<String> sink1 = new SinkTransformation<>(
                source, "Sink1", sinkFunction1, outputType, 1);
        SinkTransformation<String> sink2 = new SinkTransformation<>(
                source, "Sink2", sinkFunction2, outputType, 1);

        // Both sinks should have the same input
        assertEquals(source, sink1.getInput());
        assertEquals(source, sink2.getInput());
        assertEquals(sink1.getInput(), sink2.getInput());

        // But different IDs
        assertNotEquals(sink1.getId(), sink2.getId());
    }

    // ========== Helper Methods ==========

    private <T> Transformation<T> createMockTransformation(String name, TypeInformation<T> outputType, int parallelism) {
        return new Transformation<T>(name, outputType, parallelism) {
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

    private TypeInformation<Void> createVoidTypeInformation() {
        return new TypeInformation<Void>() {
            @Override
            public Class<Void> getTypeClass() {
                return Void.class;
            }
        };
    }

    // ========== Test Helper Classes ==========

    private static class CollectingSinkFunction<T> implements SinkFunction<T> {
        private final List<T> collected = new ArrayList<>();

        @Override
        public void consume(T value) {
            collected.add(value);
        }

        public List<T> getCollected() {
            return collected;
        }
    }

    private static class NoOpSinkFunction<T> implements SinkFunction<T> {
        @Override
        public void consume(T value) {
            // No-op
        }
    }
}
