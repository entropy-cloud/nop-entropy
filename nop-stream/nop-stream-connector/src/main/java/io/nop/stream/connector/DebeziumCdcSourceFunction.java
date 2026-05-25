/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.nop.api.core.util.ICancellable;
import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.DebeziumConfig;
import io.nop.message.debezium.DebeziumMessageSource;

import io.nop.stream.core.common.functions.source.SourceConsistencyCapability;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.connector.DrainableSource;

/**
 * Adapts nop-message-debezium's {@link DebeziumMessageSource} to nop-stream's {@link SourceFunction}.
 * <p>
 * Captures database change events (CDC) and emits them as {@link ChangeEvent} records into the stream.
 * Blocks until cancelled.
 * <p>
 * Implements {@link DrainableSource} to support DRAIN termination mode: when {@link #truncateForDrain()}
 * is called, the source stops consuming new change events but allows already-in-flight records
 * to be processed before the final checkpoint.
 */
public class DebeziumCdcSourceFunction implements DrainableSource<ChangeEvent> {

    private static final long serialVersionUID = 1L;

    private final DebeziumConfig config;

    private volatile boolean running = true;
    private volatile boolean draining = false;
    private final CountDownLatch completionLatch = new CountDownLatch(1);
    private DebeziumMessageSource source;
    private ICancellable subscription;

    public DebeziumCdcSourceFunction(DebeziumConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        this.config = config;
    }

    @Override
    public void run(SourceContext<ChangeEvent> ctx) throws Exception {
        if (!draining) {
            source = new DebeziumMessageSource(config);
            subscription = source.subscribe(ctx::collect);
        }

        while (running && !draining) {
            if (completionLatch.await(1, TimeUnit.SECONDS)) {
                break;
            }
        }
    }

    @Override
    public void cancel() {
        running = false;
        completionLatch.countDown();
        if (subscription != null) {
            subscription.cancel();
        }
        if (source != null) {
            source.stop();
        }
    }

    @Override
    public SourceConsistencyCapability getSourceConsistency() {
        return SourceConsistencyCapability.REPLAYABLE;
    }

    /**
     * Truncates the source for DRAIN termination mode.
     * Stops consuming new change events by cancelling the subscription to the
     * Debezium message source, but allows the run() loop to exit gracefully
     * so that already-in-flight records can be checkpointed.
     *
     * @throws Exception if stopping the subscription fails
     */
    @Override
    public void truncateForDrain() throws Exception {
        draining = true;
        if (subscription != null) {
            subscription.cancel();
            subscription = null;
        }
        if (source != null) {
            source.stop();
            source = null;
        }
        // Unblock the run() loop so it can exit
        completionLatch.countDown();
    }

    /**
     * Returns whether this source is currently in drain mode.
     */
    public boolean isDraining() {
        return draining;
    }
}
