/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core;

import io.nop.batch.core.IBatchLoaderProvider.IBatchLoader;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import io.nop.batch.core.loader.ListBatchLoader;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestListBatchLoader {

    private List<String> newList(int size) {
        List<String> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add("item-" + i);
        }
        return list;
    }

    /**
     * 模拟 BatchChunkProcessor 的消费循环：load 返回空列表表示数据读取完毕
     */
    private List<String> loadAll(IBatchLoader<String> loader, int batchSize, List<Integer> chunkSizes) {
        IBatchTaskContext taskContext = new BatchTaskContextImpl();
        List<String> all = new ArrayList<>();
        while (true) {
            IBatchChunkContext chunkContext = taskContext.newChunkContext();
            List<String> batch = loader.load(batchSize, chunkContext);
            assertTrue(batch.size() <= batchSize, "batch size must not exceed batchSize");
            if (batch.isEmpty()) {
                chunkSizes.add(0);
                break;
            }
            chunkSizes.add(batch.size());
            all.addAll(batch);
        }
        // EOF 之后继续 load 必须仍然返回空列表
        assertEquals(Collections.emptyList(),
                loader.load(batchSize, taskContext.newChunkContext()));
        return all;
    }

    @Test
    public void testLoadMultipleChunks() {
        List<String> list = newList(250);
        ListBatchLoader<String, Void> loader = new ListBatchLoader<>(list);

        List<Integer> chunkSizes = new ArrayList<>();
        List<String> all = loadAll(loader, 100, chunkSizes);

        assertEquals(List.of(100, 100, 50, 0), chunkSizes);
        // 全部数据按顺序加载，无丢失、无重叠
        assertEquals(list, all);
    }

    @Test
    public void testLoadExactMultipleChunks() {
        List<String> list = newList(200);
        ListBatchLoader<String, Void> loader = new ListBatchLoader<>(list);

        List<Integer> chunkSizes = new ArrayList<>();
        List<String> all = loadAll(loader, 100, chunkSizes);

        assertEquals(List.of(100, 100, 0), chunkSizes);
        assertEquals(list, all);
    }

    @Test
    public void testLoadSinglePartialChunk() {
        List<String> list = newList(50);
        ListBatchLoader<String, Void> loader = new ListBatchLoader<>(list);

        List<Integer> chunkSizes = new ArrayList<>();
        List<String> all = loadAll(loader, 100, chunkSizes);

        assertEquals(List.of(50, 0), chunkSizes);
        assertEquals(list, all);
    }

    @Test
    public void testLoadEmptyList() {
        ListBatchLoader<String, Void> loader = new ListBatchLoader<>(new ArrayList<>());

        List<Integer> chunkSizes = new ArrayList<>();
        List<String> all = loadAll(loader, 100, chunkSizes);

        assertEquals(List.of(0), chunkSizes);
        assertEquals(Collections.emptyList(), all);
    }
}
