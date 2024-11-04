/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.processor;

import io.nop.api.core.util.ProcessResult;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchChunkProcessor;
import io.nop.batch.core.IBatchConsumerProvider.IBatchConsumer;
import io.nop.batch.core.IBatchLoaderProvider.IBatchLoader;
import io.nop.batch.core.IBatchProcessorProvider.IBatchProcessor;
import io.nop.batch.core.IBatchTaskMetrics;
import io.nop.commons.util.MathHelper;

import java.util.Collections;
import java.util.List;

/**
 * 对chunk的缺省处理过程。它读取一个数据列表，然后调用consumer去处理该列表。 一般consumer的实现类为{@link io.nop.batch.core.consumer.BatchProcessorConsumer}，
 * 在这个类中会对输入记录列表中的每天记录逐条应用{@link IBatchProcessor}。
 *
 * @param <S> 输入数据类型
 */
public class BatchChunkProcessor<S> implements IBatchChunkProcessor {
    private final IBatchLoader<S> loader;
    private final int batchSize;
    private final double jitterRatio;
    private final IBatchConsumer<S> consumer;

    public BatchChunkProcessor(IBatchLoader<S> loader, int batchSize, double jitterRatio,
                               IBatchConsumer<S> consumer) {
        this.loader = loader;
        this.consumer = consumer;
        this.batchSize = batchSize;
        this.jitterRatio = jitterRatio;
    }

    @Override
    public ProcessResult process(IBatchChunkContext chunkContext) {
        chunkContext.setChunkItems(Collections.emptyList());

        // 随机调整batchSize大小，避免多线程处理时出现资源争用
        int size = adjustBatchSize(batchSize);

        List<S> items = loadItems(size, chunkContext);

        // 没有数据返回时表示全局数据都已经读取完毕
        if (items == null || items.isEmpty()) {
            return ProcessResult.STOP;
        }

        chunkContext.setChunkItems(items);

        consumer.consume(items, chunkContext);

        return ProcessResult.CONTINUE;
    }

    protected List<S> loadItems(int batchSize, IBatchChunkContext chunkContext) {
        IBatchTaskMetrics metrics = chunkContext.getTaskContext().getMetrics();
        Object meter = metrics == null ? null : metrics.beginLoad();

        boolean success = false;
        try {
            List<S> items = loader.load(batchSize, chunkContext);
            success = true;
            return items;
        } finally {
            if (metrics != null)
                metrics.endLoad(meter, chunkContext.getChunkItems().size(), success);
        }
    }

    protected int adjustBatchSize(int batchSize) {
        int ret = batchSize;
        if (jitterRatio > 0) {
            int range = (int) (batchSize * jitterRatio);
            ret += MathHelper.random().nextInt(-8, 8) * range / 8;
        }
        if (ret <= 0)
            ret = 1;
        return ret;
    }
}