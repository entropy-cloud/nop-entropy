/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core;

import io.nop.api.core.exceptions.NopException;
import io.nop.batch.core.consumer.ResourceRecordConsumerProvider;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import io.nop.core.resource.impl.InMemoryTextResource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 断点续传（completedIndex > 0）时如果输出文件已存在且非空，继续执行会截断重建输出文件，
 * 读侧跳过的已完成记录的产出将永久丢失。此时必须显式报错而不是静默截断。
 */
public class TestResourceRecordConsumerRecovery {

    private ResourceRecordConsumerProvider<String> newProvider(InMemoryTextResource resource) {
        ResourceRecordConsumerProvider<String> provider = new ResourceRecordConsumerProvider<>();
        provider.setRecordIO(new DebugResourceRecordIO(0));
        provider.setResource(resource);
        return provider;
    }

    @Test
    public void testRecoveryWithExistingOutputFails() {
        ResourceRecordConsumerProvider<String> provider =
                newProvider(new InMemoryTextResource("/out/result.txt", "previous partial output"));

        BatchTaskContextImpl context = new BatchTaskContextImpl();
        context.setCompletedIndex(50);

        NopException ex = assertThrows(NopException.class, () -> provider.setup(context));
        assertEquals(BatchErrors.ERR_BATCH_OUTPUT_FILE_EXISTS_ON_RECOVERY.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testFreshStartWithExistingOutputOverwrites() {
        // 非续传场景（completedIndex=0，尚未确认任何记录）保持原有的覆盖写语义
        ResourceRecordConsumerProvider<String> provider =
                newProvider(new InMemoryTextResource("/out/result.txt", "stale output"));

        BatchTaskContextImpl context = new BatchTaskContextImpl();
        context.setCompletedIndex(0);

        assertNotNull(provider.setup(context));
    }

    @Test
    public void testRecoveryWithMissingOutputAllowed() {
        // 续传但输出文件尚不存在（如按次动态命名），不构成冲突
        ResourceRecordConsumerProvider<String> provider =
                newProvider(new InMemoryTextResource("/out/result.txt", null));

        BatchTaskContextImpl context = new BatchTaskContextImpl();
        context.setCompletedIndex(50);

        assertNotNull(provider.setup(context));
    }
}
