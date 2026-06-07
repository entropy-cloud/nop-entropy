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
    void testCloseWithFlushFailureThrowsStreamException() {
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
        StreamException ex = assertThrows(StreamException.class, sink::close);
        assertTrue(ex.getMessage().contains("chaining-output-flush-failed")
                || ex.getMessage().contains("Flush failed"));
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
