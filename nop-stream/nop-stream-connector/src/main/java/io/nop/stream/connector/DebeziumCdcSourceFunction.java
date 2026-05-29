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
import io.nop.stream.core.exceptions.StreamException;

public class DebeziumCdcSourceFunction implements DrainableSource<ChangeEvent> {

    private static final long serialVersionUID = 1L;

    private final DebeziumConfig config;

    private volatile boolean running = true;
    private volatile boolean draining = false;
    private transient volatile CountDownLatch completionLatch;
    private volatile DebeziumMessageSource source;
    private volatile ICancellable subscription;

    public DebeziumCdcSourceFunction(DebeziumConfig config) {
        if (config == null) {
            throw new StreamException("config must not be null");
        }
        this.config = config;
        this.completionLatch = new CountDownLatch(1);
    }

    private void initCompletionLatch() {
        if (completionLatch == null) {
            synchronized (this) {
                if (completionLatch == null) {
                    completionLatch = new CountDownLatch(1);
                }
            }
        }
    }

    @Override
    public void run(SourceContext<ChangeEvent> ctx) throws Exception {
        initCompletionLatch();

        if (!draining) {
            source = new DebeziumMessageSource(config);
            try {
                subscription = source.subscribe(ctx::collect);
            } catch (Exception e) {
                source.stop();
                throw e;
            }
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
        if (completionLatch != null) {
            completionLatch.countDown();
        }
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
        if (completionLatch != null) {
            completionLatch.countDown();
        }
    }

    /**
     * Returns whether this source is currently in drain mode.
     */
    public boolean isDraining() {
        return draining;
    }
}
