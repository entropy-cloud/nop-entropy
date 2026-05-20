/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector;

import io.nop.api.core.util.ICancellable;
import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.DebeziumConfig;
import io.nop.message.debezium.DebeziumMessageSource;
import io.nop.stream.core.common.functions.source.SourceFunction;

/**
 * Adapts nop-message-debezium's {@link DebeziumMessageSource} to nop-stream's {@link SourceFunction}.
 * <p>
 * Captures database change events (CDC) and emits them as {@link ChangeEvent} records into the stream.
 * Blocks until cancelled.
 */
public class DebeziumCdcSourceFunction implements SourceFunction<ChangeEvent> {

    private static final long serialVersionUID = 1L;

    private final DebeziumConfig config;

    private volatile boolean running = true;
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
        source = new DebeziumMessageSource(config);
        subscription = source.subscribe(ctx::collect);

        while (running) {
            Thread.sleep(1000);
        }
    }

    @Override
    public void cancel() {
        running = false;
        if (subscription != null) {
            subscription.cancel();
        }
        if (source != null) {
            source.stop();
        }
    }
}
