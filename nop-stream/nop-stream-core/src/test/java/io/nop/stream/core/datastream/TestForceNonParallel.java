package io.nop.stream.core.datastream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestForceNonParallel {

    @Test
    void forceNonParallelThrowsUnsupported() {
        SingleOutputStreamOperatorImpl<String> operator = new SingleOutputStreamOperatorImpl<>(null, null);
        assertThrows(UnsupportedOperationException.class, operator::forceNonParallel);
    }
}
