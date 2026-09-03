package io.nop.stream.connector.batch;

import io.nop.batch.core.IBatchConsumerProvider;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.stream.core.exceptions.StreamException;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /**
     * Roadmap item 10 audit fix CN-3: when BOTH the flush and the consumer close fail,
     * the thrown error must prioritize the flush failure (the possible-data-loss
     * signal) with the close failure attached as suppressed — not the other way round.
     */
    @Test
    void testCloseWithFlushAndCloseFailurePrioritizesFlushError() {
        IBatchConsumerProvider<String> failingProvider = new IBatchConsumerProvider<String>() {
            @Override
            public IBatchConsumer<String> setup(IBatchTaskContext context) {
                return new FailingClosableConsumer<>();
            }
        };

        BatchConsumerSinkFunction<String> sink = new BatchConsumerSinkFunction<>(failingProvider, 1);
        try {
            sink.consume("item1");
        } catch (StreamException e) {
            // expected: flush fails because the consumer always throws
        }

        StreamException ex = assertThrows(StreamException.class, sink::close);
        assertTrue(ex.getMessage().contains("chaining-output-flush-failed")
                        || ex.getMessage().contains("Flush failed"),
                "flush (data-loss) error must be the primary signal, was: " + ex.getMessage());
        // flush() wraps the consumer failure, so the cause chain is:
        // FLUSH_FAILED -> flush wrapper (mentions "flush") -> original consumer failure
        Throwable cause = ex.getCause();
        assertTrue(cause instanceof StreamException,
                "primary cause must be the flush failure, was: " + cause);
        boolean flushFailureInChain = false;
        for (Throwable t = cause; t != null; t = t.getCause()) {
            if (String.valueOf(t.getMessage()).contains("Flush failure simulation")) {
                flushFailureInChain = true;
                break;
            }
        }
        assertTrue(flushFailureInChain, "flush failure must appear in the primary cause chain");
        assertTrue(cause.getSuppressed().length > 0
                        && cause.getSuppressed()[0] instanceof IllegalStateException,
                "close failure must ride along as suppressed on the flush error");
    }

    /** Consumer whose consume() and close() both always fail (AutoCloseable on purpose). */
    private static final class FailingClosableConsumer<R>
            implements IBatchConsumerProvider.IBatchConsumer<R>, AutoCloseable {

        @Override
        public void consume(java.util.Collection<R> items, io.nop.batch.core.IBatchChunkContext chunkContext) {
            throw new StreamException(ARG_DETAIL).param(ARG_DETAIL, "Flush failure simulation");
        }

        @Override
        public void close() {
            throw new IllegalStateException("Close failure simulation");
        }
    }
}
