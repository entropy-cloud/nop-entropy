/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core;

import io.nop.batch.core.IBatchLoaderProvider.IBatchLoader;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import io.nop.batch.core.loader.ResourceRecordLoaderProvider;
import io.nop.core.context.ExecutionContextImpl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * saveState模式下processingItems完成性检查的任务级回调只能在setup时注册一次。
 * 若在每次load时注册，长任务的回调列表会随chunk数线性增长（隐性内存泄漏）。
 */
public class TestResourceRecordLoaderCallbackLeak {

    @SuppressWarnings("unchecked")
    private static int countTaskBeforeCompletes(IBatchTaskContext context) {
        try {
            Field f = ExecutionContextImpl.class.getDeclaredField("beforeCompletes");
            f.setAccessible(true);
            List<Object> list = (List<Object>) f.get(context);
            return list == null ? 0 : list.size();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    public void testBeforeCompleteRegisteredOnceAcrossLoads() {
        ResourceRecordLoaderProvider<String> provider = new ResourceRecordLoaderProvider<>();
        provider.setSaveState(true);
        provider.setResourcePath("/test.txt");
        provider.setResourceLocator(new DebugResourceLocator());
        DebugResourceRecordIO io = new DebugResourceRecordIO(10000);
        provider.setRecordIO(io);

        BatchTaskContextImpl context = new BatchTaskContextImpl();
        IBatchLoader<String> loader = provider.setup(context);

        assertEquals(1, countTaskBeforeCompletes(context));

        for (int i = 0; i < 5; i++) {
            IBatchChunkContext chunkContext = context.newChunkContext();
            loader.load(10, chunkContext);
        }

        // 多次load后任务级回调数量必须保持不变（修复前每次load都会追加一个，会变成6）
        assertEquals(1, countTaskBeforeCompletes(context));
    }
}
