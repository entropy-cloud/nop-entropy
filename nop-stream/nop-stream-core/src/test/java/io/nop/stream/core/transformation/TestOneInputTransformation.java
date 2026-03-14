/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.transformation;

import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.operator.StreamOperator;
import io.nop.stream.core.operator.StreamOperatorFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for OneInputTransformation class.
 */
public class TestOneInputTransformation {

    // ========== Construction Tests (Without KeySelector) ==========

    @Test
    public void testBasicConstructionWithoutKeySelector() {
        TypeInformation<String> inputType = createStringTypeInformation();
        TypeInformation<Integer> outputType = createIntegerTypeInformation();
        Transformation<String> input = createMockTransformation("Input", inputType, 2);
        StreamOperatorFactory<Integer> operatorFactory = new TestOperatorFactory<>("MapOperator");

        OneInputTransformation<String, Integer> transformation = new OneInputTransformation<>(
                input, "Map", operatorFactory, outputType, 4);

        assertNotNull(transformation);
        assertEquals("Map", transformation.getName());
        assertEquals(4, transformation.getParallelism());
        assertEquals(outputType, transformation.getOutputType());
        assertEquals(input, transformation.getInput());
        assertEquals(operatorFactory, transformation.getOperatorFactory());
        assertNull(transformation.getKeySelector());
    }

    // ========== Construction Tests (With KeySelector) ==========

    @Test
    public void testConstructionWithKeySelector() {
        TypeInformation<String> inputType = createStringTypeInformation();
        TypeInformation<Integer> outputType = createIntegerTypeInformation();
        Transformation<String> input = createMockTransformation("Input", inputType, 2);
        StreamOperatorFactory<Integer> operatorFactory = new TestOperatorFactory<>("KeyedMapOperator");
        KeySelector<String, String> keySelector = new TestKeySelector();

        OneInputTransformation<String, Integer> transformation = new OneInputTransformation<>(
                input, "KeyedMap", operatorFactory, outputType, 4, keySelector);

        assertNotNull(transformation);
        assertEquals("KeyedMap", transformation.getName());
        assertEquals(4, transformation.getParallelism());
        assertEquals(outputType, transformation.getOutputType());
        assertEquals(input, transformation.getInput());
        assertEquals(operatorFactory, transformation.getOperatorFactory());
        assertEquals(keySelector, transformation.getKeySelector());
    }

    // ========== GetInputs Tests ==========

    @Test
    public void testGetInputs() {
        TypeInformation<String> inputType = createStringTypeInformation();
        TypeInformation<Integer> outputType = createIntegerTypeInformation();
        Transformation<String> input = createMockTransformation("Input", inputType, 2);
        StreamOperatorFactory<Integer> operatorFactory = new TestOperatorFactory<>("MapOperator");

        OneInputTransformation<String, Integer> transformation = new OneInputTransformation<>(
                input, "Map", operatorFactory, outputType, 4);

        List<Transformation<?>> inputs = transformation.getInputs();
        assertNotNull(inputs);
        assertEquals(1, inputs.size());
        assertEquals(input, inputs.get(0));
    }

    @Test
    public void testGetInputsReturnsSingletonList() {
        TypeInformation<String> inputType = createStringTypeInformation();
        TypeInformation<Integer> outputType = createIntegerTypeInformation();
        Transformation<String> input = createMockTransformation("Input", inputType, 2);
        StreamOperatorFactory<Integer> operatorFactory = new TestOperatorFactory<>("MapOperator");

        OneInputTransformation<String, Integer> transformation = new OneInputTransformation<>(
                input, "Map", operatorFactory, outputType, 4);

        List<Transformation<?>> inputs = transformation.getInputs();
        assertThrows(UnsupportedOperationException.class, () -> inputs.add(null));
    }

    // ========== Parallelism Tests ==========

    @Test
    public void testDifferentParallelismValues() {
        TypeInformation<String> inputType = createStringTypeInformation();
        TypeInformation<Integer> outputType = createIntegerTypeInformation();
        Transformation<String> input = createMockTransformation("Input", inputType, 1);
        StreamOperatorFactory<Integer> operatorFactory = new TestOperatorFactory<>("MapOperator");

        OneInputTransformation<String, Integer> t1 = new OneInputTransformation<>(
                input, "Map1", operatorFactory, outputType, 1);
        assertEquals(1, t1.getParallelism());

        OneInputTransformation<String, Integer> t2 = new OneInputTransformation<>(
                input, "Map2", operatorFactory, outputType, 2);
        assertEquals(2, t2.getParallelism());

        OneInputTransformation<String, Integer> t16 = new OneInputTransformation<>(
                input, "Map16", operatorFactory, outputType, 16);
        assertEquals(16, t16.getParallelism());
    }

    // ========== ID Generation Tests ==========

    @Test
    public void testUniqueIdGeneration() {
        TypeInformation<String> inputType = createStringTypeInformation();
        TypeInformation<Integer> outputType = createIntegerTypeInformation();
        Transformation<String> input = createMockTransformation("Input", inputType, 2);
        StreamOperatorFactory<Integer> operatorFactory = new TestOperatorFactory<>("MapOperator");

        OneInputTransformation<String, Integer> t1 = new OneInputTransformation<>(
                input, "Map1", operatorFactory, outputType, 2);
        OneInputTransformation<String, Integer> t2 = new OneInputTransformation<>(
                input, "Map2", operatorFactory, outputType, 2);

        assertNotEquals(t1.getId(), t2.getId());
        assertTrue(t1.getId() > 0);
        assertTrue(t2.getId() > 0);
    }

    // ========== OperatorFactory Tests ==========

    @Test
    public void testOperatorFactoryIsPreserved() {
        TypeInformation<String> inputType = createStringTypeInformation();
        TypeInformation<Integer> outputType = createIntegerTypeInformation();
        Transformation<String> input = createMockTransformation("Input", inputType, 2);

        StreamOperatorFactory<Integer> factory1 = new TestOperatorFactory<>("Operator1");
        StreamOperatorFactory<Integer> factory2 = new TestOperatorFactory<>("Operator2");

        OneInputTransformation<String, Integer> t1 = new OneInputTransformation<>(
                input, "Map1", factory1, outputType, 2);
        OneInputTransformation<String, Integer> t2 = new OneInputTransformation<>(
                input, "Map2", factory2, outputType, 2);

        assertEquals(factory1, t1.getOperatorFactory());
        assertEquals(factory2, t2.getOperatorFactory());
    }

    @Test
    public void testOperatorFactoryCreatesOperator() {
        TypeInformation<String> inputType = createStringTypeInformation();
        TypeInformation<Integer> outputType = createIntegerTypeInformation();
        Transformation<String> input = createMockTransformation("Input", inputType, 2);
        StreamOperatorFactory<Integer> operatorFactory = new TestOperatorFactory<>("TestOperator");

        OneInputTransformation<String, Integer> transformation = new OneInputTransformation<>(
                input, "Map", operatorFactory, outputType, 4);

        StreamOperator<Integer> operator = transformation.getOperatorFactory().createStreamOperator(outputType);
        assertNotNull(operator);
        assertEquals("TestOperator", operator.getName());
    }

    // ========== KeySelector Tests ==========

    @Test
    public void testKeySelectorNullWhenNotProvided() {
        TypeInformation<String> inputType = createStringTypeInformation();
        TypeInformation<Integer> outputType = createIntegerTypeInformation();
        Transformation<String> input = createMockTransformation("Input", inputType, 2);
        StreamOperatorFactory<Integer> operatorFactory = new TestOperatorFactory<>("MapOperator");

        OneInputTransformation<String, Integer> transformation = new OneInputTransformation<>(
                input, "Map", operatorFactory, outputType, 4);

        assertNull(transformation.getKeySelector());
    }

    @Test
    public void testKeySelectorReturnsCorrectKey() throws Exception {
        TypeInformation<String> inputType = createStringTypeInformation();
        TypeInformation<Integer> outputType = createIntegerTypeInformation();
        Transformation<String> input = createMockTransformation("Input", inputType, 2);
        StreamOperatorFactory<Integer> operatorFactory = new TestOperatorFactory<>("KeyedOperator");
        KeySelector<String, String> keySelector = new TestKeySelector();

        OneInputTransformation<String, Integer> transformation = new OneInputTransformation<>(
                input, "KeyedMap", operatorFactory, outputType, 4, keySelector);

        assertNotNull(transformation.getKeySelector());
        assertEquals("key-test", transformation.getKeySelector().getKey("test"));
    }

    // ========== Type Information Tests ==========

    @Test
    public void testInputAndOutputTypesDiffer() {
        TypeInformation<String> inputType = createStringTypeInformation();
        TypeInformation<Integer> outputType = createIntegerTypeInformation();
        Transformation<String> input = createMockTransformation("Input", inputType, 2);
        StreamOperatorFactory<Integer> operatorFactory = new TestOperatorFactory<>("MapOperator");

        OneInputTransformation<String, Integer> transformation = new OneInputTransformation<>(
                input, "Map", operatorFactory, outputType, 4);

        assertEquals(String.class, input.getOutputType().getTypeClass());
        assertEquals(Integer.class, transformation.getOutputType().getTypeClass());
    }

    @Test
    public void testInputAndOutputTypesSame() {
        TypeInformation<String> stringType = createStringTypeInformation();
        Transformation<String> input = createMockTransformation("Input", stringType, 2);
        StreamOperatorFactory<String> operatorFactory = new TestOperatorFactory<>("FilterOperator");

        OneInputTransformation<String, String> transformation = new OneInputTransformation<>(
                input, "Filter", operatorFactory, stringType, 4);

        assertEquals(String.class, transformation.getOutputType().getTypeClass());
        assertEquals(input.getOutputType(), transformation.getOutputType());
    }

    // ========== PhysicalTransformation Inheritance Tests ==========

    @Test
    public void testExtendsPhysicalTransformation() {
        TypeInformation<String> inputType = createStringTypeInformation();
        TypeInformation<Integer> outputType = createIntegerTypeInformation();
        Transformation<String> input = createMockTransformation("Input", inputType, 2);
        StreamOperatorFactory<Integer> operatorFactory = new TestOperatorFactory<>("MapOperator");

        OneInputTransformation<String, Integer> transformation = new OneInputTransformation<>(
                input, "Map", operatorFactory, outputType, 4);

        assertTrue(transformation instanceof PhysicalTransformation);
    }

    @Test
    public void testExtendsTransformation() {
        TypeInformation<String> inputType = createStringTypeInformation();
        TypeInformation<Integer> outputType = createIntegerTypeInformation();
        Transformation<String> input = createMockTransformation("Input", inputType, 2);
        StreamOperatorFactory<Integer> operatorFactory = new TestOperatorFactory<>("MapOperator");

        OneInputTransformation<String, Integer> transformation = new OneInputTransformation<>(
                input, "Map", operatorFactory, outputType, 4);

        assertTrue(transformation instanceof Transformation);
    }

    // ========== Chained Transformation Tests ==========

    @Test
    public void testTransformationChain() {
        // Create: Source -> Map -> Filter
        TypeInformation<String> stringType = createStringTypeInformation();
        TypeInformation<Integer> intType = createIntegerTypeInformation();

        Transformation<String> source = createMockTransformation("Source", stringType, 1);
        StreamOperatorFactory<Integer> mapFactory = new TestOperatorFactory<>("MapOperator");

        OneInputTransformation<String, Integer> map = new OneInputTransformation<>(
                source, "Map", mapFactory, intType, 2);

        StreamOperatorFactory<Integer> filterFactory = new TestOperatorFactory<>("FilterOperator");
        OneInputTransformation<Integer, Integer> filter = new OneInputTransformation<>(
                map, "Filter", filterFactory, intType, 2);

        // Verify chain structure
        assertEquals(source, map.getInput());
        assertEquals(map, filter.getInput());
    }

    // ========== Edge Cases ==========

    @Test
    public void testTransformationWithHighParallelism() {
        TypeInformation<String> inputType = createStringTypeInformation();
        TypeInformation<Integer> outputType = createIntegerTypeInformation();
        Transformation<String> input = createMockTransformation("Input", inputType, 1);
        StreamOperatorFactory<Integer> operatorFactory = new TestOperatorFactory<>("MapOperator");

        OneInputTransformation<String, Integer> transformation = new OneInputTransformation<>(
                input, "HighParallelism", operatorFactory, outputType, 1024);

        assertEquals(1024, transformation.getParallelism());
    }

    @Test
    public void testTransformationParallelismDifferentFromInput() {
        TypeInformation<String> inputType = createStringTypeInformation();
        TypeInformation<Integer> outputType = createIntegerTypeInformation();
        Transformation<String> input = createMockTransformation("Input", inputType, 2);
        StreamOperatorFactory<Integer> operatorFactory = new TestOperatorFactory<>("MapOperator");

        OneInputTransformation<String, Integer> transformation = new OneInputTransformation<>(
                input, "Map", operatorFactory, outputType, 8);

        assertEquals(2, input.getParallelism());
        assertEquals(8, transformation.getParallelism());
        assertNotEquals(input.getParallelism(), transformation.getParallelism());
    }

    // ========== Serialization Tests ==========

    @Test
    public void testSerialization() {
        TypeInformation<String> inputType = createStringTypeInformation();
        TypeInformation<Integer> outputType = createIntegerTypeInformation();
        Transformation<String> input = createMockTransformation("Input", inputType, 2);
        StreamOperatorFactory<Integer> operatorFactory = new TestOperatorFactory<>("MapOperator");

        OneInputTransformation<String, Integer> transformation = new OneInputTransformation<>(
                input, "Map", operatorFactory, outputType, 4);

        assertTrue(transformation instanceof java.io.Serializable);
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

    // ========== Test Helper Classes ==========

    private static class TestOperatorFactory<OUT> implements StreamOperatorFactory<OUT> {
        private final String name;

        TestOperatorFactory(String name) {
            this.name = name;
        }

        @Override
        public StreamOperator<OUT> createStreamOperator(TypeInformation<OUT> outputType) {
            return new TestOperator<>(name, outputType);
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

    private static class TestOperator<OUT> implements StreamOperator<OUT> {
        private final String name;
        private final TypeInformation<OUT> outputType;

        TestOperator(String name, TypeInformation<OUT> outputType) {
            this.name = name;
            this.outputType = outputType;
        }

        @Override
        public TypeInformation<OUT> getOutputType() {
            return outputType;
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

    private static class TestKeySelector implements KeySelector<String, String> {
        @Override
        public String getKey(String value) throws Exception {
            return "key-" + value;
        }
    }
}
