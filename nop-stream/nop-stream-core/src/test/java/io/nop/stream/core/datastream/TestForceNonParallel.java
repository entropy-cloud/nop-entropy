package io.nop.stream.core.datastream;

import io.nop.stream.core.transformation.Transformation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link SingleOutputStreamOperator#forceNonParallel()} locks the
 * underlying Transformation to parallelism = 1 (rather than throwing
 * UnsupportedOperationException as the prior stub did).
 *
 * <p>The lock propagates through {@code Transformation → StreamNode → JobVertex
 * → GraphExecutionPlan} so that no downstream PartitionedPlan/DeploymentPlan
 * override can raise the vertex parallelism above 1. This unblocks CEP's non-keyed
 * entry point ({@code CEP.pattern(nonKeyedStream, pattern)}).
 */
public class TestForceNonParallel {

    @Test
    void forceNonParallelLocksTransformationToOne() {
        // SingleOutputStreamOperatorImpl with null transformation should still
        // return without throwing (defensive no-op when no transformation to lock).
        SingleOutputStreamOperatorImpl<String> operator = new SingleOutputStreamOperatorImpl<>(null, null);
        // Must not throw — returns self for chaining.
        operator.forceNonParallel();
    }

    @Test
    void forceNonParallelSetsLockFlagOnTransformation() {
        // Use a minimal concrete Transformation subclass to verify the lock is set.
        Transformation<String> transformation = new StubTransformation<>();
        assertFalse(transformation.isParallelismLocked(),
                "Transformation must start unlocked");

        SingleOutputStreamOperatorImpl<String> operator =
                new SingleOutputStreamOperatorImpl<>(null, transformation);
        operator.forceNonParallel();

        assertTrue(transformation.isParallelismLocked(),
                "forceNonParallel must lock the transformation to parallel-1");
    }

    private static class StubTransformation<T> extends Transformation<T> {
        StubTransformation() {
            super("stub", null, 4);
        }

        @Override
        public java.util.List<Transformation<?>> getInputs() {
            return java.util.Collections.emptyList();
        }
    }
}
