/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import io.nop.batch.core.loader.AsyncFetchPartitionDispatchLoaderProvider;
import io.nop.commons.metrics.GlobalMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2546 R4-12（C8-008 / W-09）**生产注销路径**钉（closure audit M-3）：
 * Gauge 由 {@code setup()} 注册、由 {@code context.onAfterComplete(...)} 注销——
 * 两者都必须经**生产代码**触发，不得由测试自行复现。
 */
public class TestCraR412DispatchGaugeLifecycle {

    @Test
    public void gaugeRegisteredOnSetupAndRemovedOnComplete() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GlobalMeterRegistry.registerInstance(registry);
        try {
            IBatchLoaderProvider<String> source = ctx -> (batchSize, chunkCtx) -> new ArrayList<>();
            Executor inline = Runnable::run;

            AsyncFetchPartitionDispatchLoaderProvider<String> provider =
                    new AsyncFetchPartitionDispatchLoaderProvider<>(source, inline, 1, 2, (item, ctx) -> 0);

            BatchTaskContextImpl ctx = new BatchTaskContextImpl();
            ctx.setTaskName("gauge-lifecycle");
            ctx.setTaskKey("exec-key-1");

            // 生产路径注册：provider.setup(context) 内部注册 Gauge
            provider.setup(ctx);

            assertNotNull(registry.find("nop.batch.dispatch.backlog").tag("exec.id", "exec-key-1").gauge(),
                    "setup() 必须注册 backlog Gauge（生产路径）");
            assertNotNull(registry.find("nop.batch.dispatch.available-permits")
                            .tag("exec.id", "exec-key-1").gauge(),
                    "setup() 必须注册 available-permits Gauge");

            // 生产路径注销：context.complete() 触发 onAfterComplete 闭包
            ctx.complete();

            assertNull(registry.find("nop.batch.dispatch.backlog").tag("exec.id", "exec-key-1").gauge(),
                    "onAfterComplete 必须注销 Gauge（否则 meter-id 复用陈旧读数 + 逐任务累积）");
            assertNull(registry.find("nop.batch.dispatch.available-permits")
                            .tag("exec.id", "exec-key-1").gauge(),
                    "许可 Gauge 同样必须注销");
        } finally {
            GlobalMeterRegistry.registerInstance(new SimpleMeterRegistry());
        }
    }
}
