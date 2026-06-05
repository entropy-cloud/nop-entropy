/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.nop.api.core.util.ICancellable;
import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.DebeziumConfig;
import io.nop.message.debezium.DebeziumMessageSource;

import io.nop.stream.core.common.functions.source.SourceConsistencyCapability;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.connector.DrainableSource;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.*;

public class DebeziumCdcSourceFunction implements DrainableSource<ChangeEvent> {

    private static final long serialVersionUID = 1L;

    private transient DebeziumConfig config;

    private volatile boolean running = true;
    private volatile boolean draining = false;
    private final AtomicBoolean runEntered = new AtomicBoolean(false);
    private transient volatile CountDownLatch completionLatch;
    private volatile DebeziumMessageSource source;
    private volatile ICancellable subscription;

    public DebeziumCdcSourceFunction(DebeziumConfig config) {
        if (config == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "config");
        }
        this.config = config;
        this.completionLatch = new CountDownLatch(1);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (config == null) {
            config = new DebeziumConfig();
        }
        completionLatch = new CountDownLatch(1);
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
        if (!runEntered.compareAndSet(false, true)) {
            return;
        }
        this.draining = false;
        initCompletionLatch();

        try {
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
        } finally {
            if (subscription != null) {
                try {
                    subscription.cancel();
                } catch (Exception e) {
                    // ignore cleanup errors
                }
                subscription = null;
            }
            if (source != null) {
                try {
                    source.stop();
                } catch (Exception e) {
                    // ignore cleanup errors
                }
                source = null;
            }
            runEntered.set(false);
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

    public boolean isDraining() {
        return draining;
    }
}
