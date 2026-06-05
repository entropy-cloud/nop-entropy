package io.nop.stream.connector;

import io.nop.batch.core.IBatchConsumerProvider;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.stream.core.exceptions.StreamException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static org.junit.jupiter.api.Assertions.*;

class TestBatchConsumerSinkFunctionCloseLogging {

    @Test
    void testCloseWithFlushFailureDoesNotThrow() {
        IBatchConsumerProvider<String> failingProvider = new IBatchConsumerProvider<String>() {
            @Override
            public IBatchConsumer<String> setup(IBatchTaskContext context) {
                return (items, chunkContext) -> {
                    throw new StreamException(ARG_DETAIL).param(ARG_DETAIL, "Flush failure simulation");
                };
            }
        };

        BatchConsumerSinkFunction<String> sink = new BatchConsumerSinkFunction<>(failingProvider, 1);
        try {
            sink.consume("item1");
        } catch (StreamException e) {
            // expected: flush fails because provider always throws
        }
        assertDoesNotThrow(sink::close);
    }

    @Test
    void testCloseWithEmptyBufferSucceeds() {
        IBatchConsumerProvider<String> provider = new IBatchConsumerProvider<String>() {
            @Override
            public IBatchConsumer<String> setup(IBatchTaskContext context) {
                return (items, chunkContext) -> {};
            }
        };

        BatchConsumerSinkFunction<String> sink = new BatchConsumerSinkFunction<>(provider, 10);
        assertDoesNotThrow(sink::close);
    }
}
