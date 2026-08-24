/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.dao.history;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.history.IBatchHistoryStoreModel;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import io.nop.batch.dao.entity.NopBatchRecordResult;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.reflect.hook.IPropGetMissingHook;
import io.nop.dao.api.IEntityDao;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * saveProcessed必须在处理成功时把记录落库（resultStatus=0），
 * 否则filterProcessed的查询永远返回空，基于DAO的处理历史去重完全失效
 */
public class TestDaoBatchRecordHistoryStore {

    @SuppressWarnings("unchecked")
    private static IEntityDao<NopBatchRecordResult> newRecordingDao(List<NopBatchRecordResult> saved) {
        return (IEntityDao<NopBatchRecordResult>) Proxy.newProxyInstance(
                TestDaoBatchRecordHistoryStore.class.getClassLoader(),
                new Class[]{IEntityDao.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "newEntity":
                            return new NopBatchRecordResult();
                        case "saveEntity":
                            saved.add((NopBatchRecordResult) args[0]);
                            return null;
                        default:
                            return null;
                    }
                });
    }

    private static IBatchHistoryStoreModel newModel(IEvalFunction keyFn, IEvalFunction infoFn) {
        return new IBatchHistoryStoreModel() {
            @Override
            public IEvalFunction getRecordKeyExpr() {
                return keyFn;
            }

            @Override
            public IEvalFunction getRecordInfoExpr() {
                return infoFn;
            }

            @Override
            public boolean isOnlySaveLastError() {
                return false;
            }

            @Override
            public Object prop_get(String propName) {
                return null;
            }

            @Override
            public boolean prop_has(String propName) {
                return false;
            }
        };
    }

    private static IBatchChunkContext newChunkContext(String taskId) {
        BatchTaskContextImpl taskCtx = new BatchTaskContextImpl();
        taskCtx.setTaskId(taskId);
        IBatchChunkContext chunkCtx = taskCtx.newChunkContext();
        chunkCtx.setConcurrency(1);
        chunkCtx.setThreadIndex(0);
        return chunkCtx;
    }

    @Test
    public void testSaveProcessedOnSuccess() {
        List<NopBatchRecordResult> saved = new ArrayList<>();
        DaoBatchRecordHistoryStore<String> store = new DaoBatchRecordHistoryStore<>(
                newRecordingDao(saved), newModel((t, args, scope) -> String.valueOf(args[0]), null));

        store.saveProcessed(Arrays.asList("a", "b", "c"), null, newChunkContext("task-1"));

        assertEquals(3, saved.size());
        for (NopBatchRecordResult result : saved) {
            assertEquals("task-1", result.getBatchTaskId());
            assertEquals(0, result.getResultStatus());
        }
        assertEquals("a", saved.get(0).getRecordKey());
        assertEquals("b", saved.get(1).getRecordKey());
        assertEquals("c", saved.get(2).getRecordKey());
    }

    @Test
    public void testSaveProcessedSkippedOnFailure() {
        List<NopBatchRecordResult> saved = new ArrayList<>();
        DaoBatchRecordHistoryStore<String> store = new DaoBatchRecordHistoryStore<>(
                newRecordingDao(saved), newModel((t, args, scope) -> String.valueOf(args[0]), null));

        // 处理失败时不写入历史，重启后这些记录会被重新处理
        store.saveProcessed(Arrays.asList("a", "b"), new RuntimeException("consume-fail"),
                newChunkContext("task-1"));
        assertTrue(saved.isEmpty());
    }

    @Test
    public void testSaveProcessedRecordInfo() {
        List<NopBatchRecordResult> saved = new ArrayList<>();
        DaoBatchRecordHistoryStore<String> store = new DaoBatchRecordHistoryStore<>(
                newRecordingDao(saved), newModel((t, args, scope) -> "key-" + args[0],
                        (t, args, scope) -> "info-" + args[0]));

        store.saveProcessed(Arrays.asList("a"), null, newChunkContext("task-1"));

        assertEquals(1, saved.size());
        assertEquals("key-a", saved.get(0).getRecordKey());
        assertEquals("info-a", saved.get(0).getRecordInfo());
    }
}
