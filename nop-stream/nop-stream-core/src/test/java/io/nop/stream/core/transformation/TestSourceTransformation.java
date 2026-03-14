/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.transformation;

import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for SourceTransformation class.
 */
public class TestSourceTransformation {

    // ========== Construction Tests ==========

    @Test
    public void testBasicConstruction() {
        TypeInformation<String> outputType = createStringTypeInformation();
        SourceFunction<String> sourceFunction = new TestSourceFunction<>();

        SourceTransformation<String> transformation = new SourceTransformation<>(
                "TestSource", sourceFunction, outputType, 2);

        assertNotNull(transformation);
        assertEquals("TestSource", transformation.getName());
        assertEquals(2, transformation.getParallelism());
        assertEquals(outputType, transformation.getOutputType());
        assertEquals(sourceFunction, transformation.getSourceFunction());
    }

    // ========== GetInputs Tests ==========

    @Test
    public void testGetInputsReturnsEmptyList() {
        TypeInformation<String> outputType = createStringTypeInformation();
        SourceFunction<String> sourceFunction = new TestSourceFunction<>();

        SourceTransformation<String> transformation = new SourceTransformation<>(
                "TestSource", sourceFunction, outputType, 2);

        List<Transformation<?>> inputs = transformation.getInputs();
        assertNotNull(inputs);
        assertTrue(inputs.isEmpty());
    }

    @Test
    public void testGetInputsReturnsImmutableEmptyList() {
        TypeInformation<String> outputType = createStringTypeInformation();
        SourceFunction<String> sourceFunction = new TestSourceFunction<>();

        SourceTransformation<String> transformation = new SourceTransformation<>(
                "TestSource", sourceFunction, outputType, 2);

        List<Transformation<?>> inputs = transformation.getInputs();
        assertThrows(UnsupportedOperationException.class, () -> inputs.add(null));
    }

    // ========== Parallelism Tests ==========

    @Test
    public void testDifferentParallelismValues() {
        TypeInformation<String> outputType = createStringTypeInformation();
        SourceFunction<String> sourceFunction = new TestSourceFunction<>();

        SourceTransformation<String> t1 = new SourceTransformation<>(
                "Source1", sourceFunction, outputType, 1);
        assertEquals(1, t1.getParallelism());

        SourceTransformation<String> t2 = new SourceTransformation<>(
                "Source2", sourceFunction, outputType, 2);
        assertEquals(2, t2.getParallelism());

        SourceTransformation<String> t16 = new SourceTransformation<>(
                "Source16", sourceFunction, outputType, 16);
        assertEquals(16, t16.getParallelism());
    }

    // ========== ID Generation Tests ==========

    @Test
    public void testUniqueIdGeneration() {
        TypeInformation<String> outputType = createStringTypeInformation();
        SourceFunction<String> sourceFunction = new TestSourceFunction<>();

        SourceTransformation<String> t1 = new SourceTransformation<>(
                "Source1", sourceFunction, outputType, 1);
        SourceTransformation<String> t2 = new SourceTransformation<>(
                "Source2", sourceFunction, outputType, 1);

        assertNotEquals(t1.getId(), t2.getId());
        assertTrue(t1.getId() > 0);
        assertTrue(t2.getId() > 0);
    }

    // ========== SourceFunction Tests ==========

    @Test
    public void testSourceFunctionIsPreserved() {
        TypeInformation<String> outputType = createStringTypeInformation();
        TestSourceFunction<String> sourceFunction1 = new TestSourceFunction<>();
        TestSourceFunction<String> sourceFunction2 = new TestSourceFunction<>();

        SourceTransformation<String> t1 = new SourceTransformation<>(
                "Source1", sourceFunction1, outputType, 1);
        SourceTransformation<String> t2 = new SourceTransformation<>(
                "Source2", sourceFunction2, outputType, 1);

        assertEquals(sourceFunction1, t1.getSourceFunction());
        assertEquals(sourceFunction2, t2.getSourceFunction());
    }

    @Test
    public void testSourceFunctionCanBeRun() throws Exception {
        TypeInformation<String> outputType = createStringTypeInformation();
        CollectingSourceFunction sourceFunction = new CollectingSourceFunction();

        SourceTransformation<String> transformation = new SourceTransformation<>(
                "TestSource", sourceFunction, outputType, 1);

        // Run the source function in a separate thread
        AtomicBoolean completed = new AtomicBoolean(false);
        Thread sourceThread = new Thread(() -> {
            try {
                transformation.getSourceFunction().run(new TestSourceContext<>());
                completed.set(true);
            } catch (Exception e) {
                // Ignore
            }
        });

        sourceThread.start();
        sourceThread.join(1000); // Wait up to 1 second

        // Cancel the source
        transformation.getSourceFunction().cancel();
        sourceThread.join(1000);

        // Verify the source ran and was cancelled
        assertTrue(sourceFunction.wasRun() || completed.get());
    }

    @Test
    public void testSourceFunctionCancellation() {
        TypeInformation<String> outputType = createStringTypeInformation();
        CancellableSourceFunction sourceFunction = new CancellableSourceFunction();

        SourceTransformation<String> transformation = new SourceTransformation<>(
                "TestSource", sourceFunction, outputType, 1);

        assertFalse(sourceFunction.isCancelled());
        transformation.getSourceFunction().cancel();
        assertTrue(sourceFunction.isCancelled());
    }

    // ========== Type Information Tests ==========

    @Test
    public void testOutputTypeIsPreserved() {
        TypeInformation<String> stringType = createStringTypeInformation();
        TypeInformation<Integer> integerType = createIntegerTypeInformation();

        SourceFunction<String> stringSource = new TestSourceFunction<>();
        SourceFunction<Integer> intSource = new TestSourceFunction<>();

        SourceTransformation<String> stringTransform = new SourceTransformation<>(
                "StringSource", stringSource, stringType, 1);
        SourceTransformation<Integer> intTransform = new SourceTransformation<>(
                "IntSource", intSource, integerType, 1);

        assertEquals(String.class, stringTransform.getOutputType().getTypeClass());
        assertEquals(Integer.class, intTransform.getOutputType().getTypeClass());
    }

    // ========== Transformation Inheritance Tests ==========

    @Test
    public void testExtendsTransformation() {
        TypeInformation<String> outputType = createStringTypeInformation();
        SourceFunction<String> sourceFunction = new TestSourceFunction<>();

        SourceTransformation<String> transformation = new SourceTransformation<>(
                "TestSource", sourceFunction, outputType, 2);

        assertTrue(transformation instanceof Transformation);
    }

    // ========== Edge Cases ==========

    @Test
    public void testSourceWithHighParallelism() {
        TypeInformation<String> outputType = createStringTypeInformation();
        SourceFunction<String> sourceFunction = new TestSourceFunction<>();

        SourceTransformation<String> transformation = new SourceTransformation<>(
                "HighParallelismSource", sourceFunction, outputType, 1024);

        assertEquals(1024, transformation.getParallelism());
    }

    @Test
    public void testSourceWithParallelismOne() {
        TypeInformation<String> outputType = createStringTypeInformation();
        SourceFunction<String> sourceFunction = new TestSourceFunction<>();

        SourceTransformation<String> transformation = new SourceTransformation<>(
                "SingleParallelismSource", sourceFunction, outputType, 1);

        assertEquals(1, transformation.getParallelism());
    }

    // ========== Serialization Tests ==========

    @Test
    public void testSerialization() {
        TypeInformation<String> outputType = createStringTypeInformation();
        SourceFunction<String> sourceFunction = new TestSourceFunction<>();

        SourceTransformation<String> transformation = new SourceTransformation<>(
                "TestSource", sourceFunction, outputType, 2);

        assertTrue(transformation instanceof java.io.Serializable);
    }

    // ========== Multiple Sources Tests ==========

    @Test
    public void testMultipleSourcesHaveUniqueIds() {
        TypeInformation<String> outputType = createStringTypeInformation();
        SourceFunction<String> sourceFunction = new TestSourceFunction<>();

        SourceTransformation<String> source1 = new SourceTransformation<>(
                "Source1", sourceFunction, outputType, 1);
        SourceTransformation<String> source2 = new SourceTransformation<>(
                "Source2", sourceFunction, outputType, 1);
        SourceTransformation<String> source3 = new SourceTransformation<>(
                "Source3", sourceFunction, outputType, 1);

        assertNotEquals(source1.getId(), source2.getId());
        assertNotEquals(source2.getId(), source3.getId());
        assertNotEquals(source1.getId(), source3.getId());
    }

    // ========== Helper Methods ==========

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

    private static class TestSourceFunction<T> implements SourceFunction<T> {
        private static final long serialVersionUID = 1L;

        @Override
        public void run(SourceContext<T> ctx) throws Exception {
            // No-op for testing
        }

        @Override
        public void cancel() {
            // No-op for testing
        }
    }

    private static class CollectingSourceFunction implements SourceFunction<String> {
        private static final long serialVersionUID = 1L;
        private boolean run = false;
        private volatile boolean cancelled = false;

        @Override
        public void run(SourceContext<String> ctx) throws Exception {
            run = true;
            while (!cancelled) {
                Thread.sleep(10);
            }
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        public boolean wasRun() {
            return run;
        }
    }

    private static class CancellableSourceFunction implements SourceFunction<String> {
        private static final long serialVersionUID = 1L;
        private volatile boolean cancelled = false;

        @Override
        public void run(SourceContext<String> ctx) throws Exception {
            while (!cancelled) {
                Thread.sleep(10);
            }
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        public boolean isCancelled() {
            return cancelled;
        }
    }

    private static class TestSourceContext<T> implements SourceFunction.SourceContext<T> {
        private static final long serialVersionUID = 1L;

        @Override
        public void collect(T element) {
            // No-op for testing
        }

        @Override
        public void collectWithTimestamp(T element, long timestamp) {
            // No-op for testing
        }

        @Override
        public void emitWatermark(long mark) {
            // No-op for testing
        }

        @Override
        public void markAsTemporarilyIdle() {
            // No-op for testing
        }

        @Override
        public long getProcessingTime() {
            return System.currentTimeMillis();
        }
    }
}
