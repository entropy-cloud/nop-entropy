/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.dsl;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import io.nop.batch.dsl.utils.BatchLoaderHelper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * loader缓存key必须区分provider身份。同一任务上下文中多个provider复用同一key时，
 * 第二个provider会命中第一个provider的缓存loader（游标已耗尽），返回错误数据源的空结果
 */
public class TestBatchLoaderHelper {

    @Test
    public void testMultipleProvidersDoNotInterfere() {
        BatchTaskContextImpl taskCtx = new BatchTaskContextImpl();
        IBatchChunkContext chunkCtx = taskCtx.newChunkContext();
        chunkCtx.setConcurrency(1);
        chunkCtx.setThreadIndex(0);

        List<String> first = BatchLoaderHelper.batchLoadWithFullList(2, chunkCtx,
                tc -> Arrays.asList("a", "b", "c"));
        assertEquals(Arrays.asList("a", "b"), first);

        // 修复前：第二个provider命中固定key"batchLoader"缓存的第一个ListBatchLoader（已读尽），返回空列表
        List<String> second = BatchLoaderHelper.batchLoadWithFullList(2, chunkCtx,
                tc -> Arrays.asList("x", "y", "z"));
        assertEquals(Arrays.asList("x", "y"), second);
    }
}
