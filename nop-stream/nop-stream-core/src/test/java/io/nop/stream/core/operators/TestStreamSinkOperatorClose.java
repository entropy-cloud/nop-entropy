package io.nop.stream.core.operators;

import io.nop.stream.core.common.functions.SinkFunction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestStreamSinkOperatorClose {

    static class AutoCloseableSink implements SinkFunction<String>, AutoCloseable {
        boolean closed = false;

        @Override
        public void consume(String value) {}

        @Override
        public void close() {
            closed = true;
        }
    }

    @Test
    void testClose_callsAutoCloseable() throws Exception {
        AutoCloseableSink sink = new AutoCloseableSink();
        StreamSinkOperator<String> op = new StreamSinkOperator<>(sink);
        op.close();
        assertTrue(sink.closed, "Sink close() should be called via AutoCloseable path");
    }

    @Test
    void testClose_plainSink_noError() throws Exception {
        SinkFunction<String> sink = value -> {};
        StreamSinkOperator<String> op = new StreamSinkOperator<>(sink);
        assertDoesNotThrow(() -> op.close());
    }
}
