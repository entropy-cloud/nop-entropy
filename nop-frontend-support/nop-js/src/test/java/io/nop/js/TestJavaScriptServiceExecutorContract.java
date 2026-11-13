/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.js;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.concurrent.executor.DefaultThreadPoolExecutor;
import io.nop.commons.concurrent.executor.ThreadPoolConfig;
import org.junit.jupiter.api.Test;

import static io.nop.commons.CommonConfigs.CFG_CONCURRENT_CACHED_POOL_MAX_POOL_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 工单 2.1-C4-003 缺陷面调用方回归（nop-js）：JavaScriptService.doStart 的执行器构造以修复后
 * 参数（配置重载显式 cached 形态，maxPoolSize<0）构造成功，语义与池工厂校验契约（设计文档
 * pool-facility-hardening.md §3.3 兼容矩阵 #3）一致；旧的 newExecutor(name,-1,-1) 负值传参
 * 已被工厂校验封死。真实服务构造链由 TestJavaScriptService.start() 覆盖。
 */
public class TestJavaScriptServiceExecutorContract {

    @Test
    public void testExplicitCachedFormConstructsWithBoundAndNegativePathClosed() {
        // 与 JavaScriptService.doStart 相同的修复后构造形态
        ThreadPoolConfig config = new ThreadPoolConfig();
        config.setName("javascript-engine");
        config.setCorePoolSize(0);
        config.setMaxPoolSize(-1);
        DefaultThreadPoolExecutor executor = DefaultThreadPoolExecutor.newExecutor(config);
        try {
            assertEquals(0, executor.stats().getCorePoolSize(), "0-core cached semantics preserved");
            assertEquals(CFG_CONCURRENT_CACHED_POOL_MAX_POOL_SIZE.get().intValue(),
                    executor.stats().getMaxPoolSize(), "cached form must be bounded by platform config");
        } finally {
            executor.destroy();
        }

        // 旧的负值传参不再可用（不再静默变 0-core 无界 cached 池）
        NopException ex = assertThrows(NopException.class,
                () -> DefaultThreadPoolExecutor.newExecutor("javascript-engine", -1, -1));
        assertEquals("nop.err.commons.concurrent.invalid-thread-pool-param", ex.getErrorCode());
    }
}
