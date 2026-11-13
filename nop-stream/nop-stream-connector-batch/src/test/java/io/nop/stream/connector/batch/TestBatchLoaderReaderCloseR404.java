/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.connector.batch;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.44 R4-04 (plan 2537) red⑤ face③ acceptance pin — W-05 (finding
 * 1.2-C3-012; adjudication C.5 / seam S5): the reader's close() is the terminal
 * cleanup face; a loader whose {@code close()} fails must surface TYPED
 * ({@code StreamException(ERR_STREAM_STATE_ERROR)} naming the leak risk) instead
 * of being WARN-swallowed (the pre-fix reader closed "normally" while the
 * loader's resources leaked silently — the family's typed discipline applies,
 * same shape as {@code BatchConsumerSinkFunction.close} flush failures).
 *
 * <p>Also pinned: the loader close is attempted exactly once on the failure path,
 * and a healthy loader close still completes without throwing (zero regression
 * on the happy path).
 */
class TestBatchLoaderReaderCloseR404 {

    /** Loader that explodes on close (AutoCloseable, like a JDBC-shaped loader). */
    private static final class FailingLoader implements IBatchLoaderProvider.IBatchLoader<String>,
            AutoCloseable {
        final AtomicBoolean closeAttempted;

        FailingLoader(AtomicBoolean closeAttempted) {
            this.closeAttempted = closeAttempted;
        }

        @Override
        public List<String> load(int batchSize, IBatchChunkContext chunkContext) {
            return List.of();
        }

        @Override
        public void close() {
            closeAttempted.set(true);
            throw new IllegalStateException("loader native handle exploded");
        }
    }

    private static final class HealthyLoader implements IBatchLoaderProvider.IBatchLoader<String>,
            AutoCloseable {
        final AtomicBoolean closed;

        HealthyLoader(AtomicBoolean closed) {
            this.closed = closed;
        }

        @Override
        public List<String> load(int batchSize, IBatchChunkContext chunkContext) {
            return List.of();
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }

    @Test
    void loaderCloseFailureSurfacesTyped() throws Exception {
        AtomicBoolean closeAttempted = new AtomicBoolean(false);
        BatchLoaderSourceReader<String> reader = new BatchLoaderSourceReader<>(
                ctx -> new FailingLoader(closeAttempted), 1);
        reader.addSplits(List.of(new BatchLoaderSplit(0)));
        reader.start();

        StreamException failure = assertThrows(StreamException.class, reader::close,
                "a loader close failure on the terminal cleanup face must fail typed — "
                        + "pre-fix it was WARN-swallowed and the leak stayed invisible "
                        + "(CRA 8.44 R4-04 W-05 face③)");
        assertTrue(closeAttempted.get(), "the loader close must have been attempted");
        assertTrue(String.valueOf(failure).contains("Failed to close the batch loader"),
                "the typed failure must name the loader-close leak, got: " + failure);
        assertEquals(NopStreamErrors.ERR_STREAM_STATE_ERROR.getErrorCode(), failure.getErrorCode(),
                "the typed failure must carry the state-error code");
    }

    @Test
    void healthyLoaderCloseStaysSilent() throws Exception {
        AtomicBoolean closed = new AtomicBoolean(false);
        BatchLoaderSourceReader<String> reader = new BatchLoaderSourceReader<>(
                ctx -> new HealthyLoader(closed), 1);
        reader.addSplits(List.of(new BatchLoaderSplit(0)));
        reader.start();
        reader.close();
        assertTrue(closed.get(), "the healthy loader must still be closed");
        assertTrue(reader.isFinished(), "the reader must be finished after close");
    }
}
