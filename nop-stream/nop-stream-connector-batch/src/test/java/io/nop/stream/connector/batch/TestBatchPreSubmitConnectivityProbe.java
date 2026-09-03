/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.batch;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchConsumerProvider;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.stream.core.connector.ConnectivityProbeOutcome;
import io.nop.stream.core.connector.StreamConnectivityProber;
import io.nop.stream.core.exceptions.StreamException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 20 (P-REQ-13, D3) batch family probe tests: the loader source probe runs
 * {@code loaderProvider.setup()} and closes the loader (cleanup semantics); the
 * consumer sink probe verifies the construction-level setup result (the audit's
 * natural probe point) and fails fast when setup produced nothing.
 */
public class TestBatchPreSubmitConnectivityProbe {

    // ------------------------------------------------------------------
    // H-3: batch-loader source
    // ------------------------------------------------------------------

    @Test
    public void loaderProbeRunsSetupAndClosesLoader() {
        RecordingLoaderProvider provider = new RecordingLoaderProvider();
        BatchLoaderSourceFunction<String> source = new BatchLoaderSourceFunction<>(provider);
        ConnectivityProbeOutcome outcome = StreamConnectivityProber.probe("batch-loader", source);
        assertEquals(ConnectivityProbeOutcome.Status.PASS, outcome.getStatus(), () -> String.valueOf(outcome));
        assertEquals(1, provider.setupCount.get(), "loaderProvider.setup() must run during the probe");
        assertTrue(provider.loaderClosed.get() > 0, "AutoCloseable loader must be closed (no residue)");
    }

    @Test
    public void loaderProbeFailsWhenProviderSetupThrows() {
        IBatchLoaderProvider<String> failing = new IBatchLoaderProvider<>() {
            @Override
            public IBatchLoader<String> setup(IBatchTaskContext context) {
                throw new IllegalStateException("loader backend unreachable");
            }
        };
        BatchLoaderSourceFunction<String> source = new BatchLoaderSourceFunction<>(failing);
        ConnectivityProbeOutcome outcome = StreamConnectivityProber.probe("batch-loader", source);
        assertEquals(ConnectivityProbeOutcome.Status.FAIL, outcome.getStatus());
        assertEquals("nop.err.stream.connectivity-check-failed", outcome.getErrorCode());
        assertTrue(outcome.getDetail().contains("loader backend unreachable"), () -> outcome.getDetail());
    }

    @Test
    public void loaderProbeFailsWhenSetupReturnsNullLoader() {
        IBatchLoaderProvider<String> nulling = new IBatchLoaderProvider<>() {
            @Override
            public IBatchLoader<String> setup(IBatchTaskContext context) {
                return null;
            }
        };
        BatchLoaderSourceFunction<String> source = new BatchLoaderSourceFunction<>(nulling);
        ConnectivityProbeOutcome outcome = StreamConnectivityProber.probe("batch-loader", source);
        assertEquals(ConnectivityProbeOutcome.Status.FAIL, outcome.getStatus(), () -> String.valueOf(outcome));
        assertTrue(outcome.getDetail().contains("returned null loader"), () -> outcome.getDetail());
    }

    // ------------------------------------------------------------------
    // H-4: batch-consumer sink (construction-level probe point)
    // ------------------------------------------------------------------

    @Test
    public void consumerProbeVerifiesConstructionLevelSetup() {
        RecordingConsumerProvider provider = new RecordingConsumerProvider();
        BatchConsumerSinkFunction<String> sink = new BatchConsumerSinkFunction<>(provider);
        assertEquals(1, provider.setupCount.get(), "construction must run consumerProvider.setup()");
        ConnectivityProbeOutcome outcome = StreamConnectivityProber.probe("batch-consumer", sink);
        assertEquals(ConnectivityProbeOutcome.Status.PASS, outcome.getStatus(), () -> String.valueOf(outcome));
        assertEquals(1, provider.setupCount.get(),
                "probe must NOT re-run setup on the constructed instance (D3: construction-level)");
    }

    @Test
    public void consumerProbeFailsWhenConstructionSetupProducedNothing() {
        IBatchConsumerProvider<String> nulling = new IBatchConsumerProvider<>() {
            @Override
            public IBatchConsumer<String> setup(IBatchTaskContext context) {
                return null;
            }
        };
        BatchConsumerSinkFunction<String> sink = new BatchConsumerSinkFunction<>(nulling);
        StreamException ex = assertThrows(StreamException.class, sink::checkConnection);
        assertTrue(ex.getMessage().contains("consumer is null"), ex.getMessage());
        ConnectivityProbeOutcome outcome = StreamConnectivityProber.probe("batch-consumer", sink);
        assertEquals(ConnectivityProbeOutcome.Status.FAIL, outcome.getStatus());
        assertTrue(outcome.getDetail().contains("consumer is null"), () -> outcome.getDetail());
    }

    // ------------------------------------------------------------------
    // fixtures
    // ------------------------------------------------------------------

    private static final class RecordingLoaderProvider implements IBatchLoaderProvider<String> {
        final AtomicInteger setupCount = new AtomicInteger();
        final AtomicInteger loaderClosed = new AtomicInteger();

        @Override
        public IBatchLoader<String> setup(IBatchTaskContext context) {
            setupCount.incrementAndGet();
            return new RecordingLoader(loaderClosed);
        }
    }

    private static final class RecordingLoader
            implements IBatchLoaderProvider.IBatchLoader<String>, AutoCloseable {
        private final AtomicInteger loaderClosed;

        RecordingLoader(AtomicInteger loaderClosed) {
            this.loaderClosed = loaderClosed;
        }

        @Override
        public List<String> load(int batchSize, IBatchChunkContext chunkContext) {
            throw new IllegalStateException("probe must not consume any record");
        }

        @Override
        public void close() {
            loaderClosed.incrementAndGet();
        }
    }

    private static final class RecordingConsumerProvider implements IBatchConsumerProvider<String> {
        final AtomicInteger setupCount = new AtomicInteger();

        @Override
        public IBatchConsumer<String> setup(IBatchTaskContext context) {
            setupCount.incrementAndGet();
            return (records, chunkContext) -> {
            };
        }
    }
}
