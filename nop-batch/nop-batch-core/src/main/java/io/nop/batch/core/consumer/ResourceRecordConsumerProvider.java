/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.consumer;

import io.nop.api.core.exceptions.NopException;
import io.nop.batch.core.IBatchAggregator;
import io.nop.batch.core.IBatchConsumerProvider;
import io.nop.batch.core.IBatchMetaProvider;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.common.AbstractBatchResourceHandler;
import io.nop.batch.core.exceptions.BatchCancelException;
import io.nop.commons.util.IoHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.record.IResourceRecordIO;
import io.nop.dataset.record.IRecordOutput;

import java.util.List;
import java.util.Map;

import static io.nop.batch.core.BatchErrors.ARG_RESOURCE_PATH;
import static io.nop.batch.core.BatchErrors.ERR_BATCH_WRITE_FILE_FAIL;

/**
 * 将记录写入文件。支持写入header和trailer
 *
 * @param <R> 记录类型
 */
public class ResourceRecordConsumerProvider<R> extends AbstractBatchResourceHandler
        implements IBatchConsumerProvider<R> {
    private IResourceRecordIO<R> recordIO;
    private String encoding;

    /**
     * 写入文件时如果发生异常，则抛出BatchCancelException，取消整个Batch任务处理
     */
    private boolean cancelTaskWhenWriteError;

    /**
     * 用于汇总trailer信息。在文件关闭前会写入trailer信息
     */
    IBatchAggregator<R, Object, Map<String, Object>> aggregator;

    IBatchMetaProvider metaProvider;

    static class ConsumerState<R> {
        IRecordOutput<R> output;

        Object combinedValue;
    }


    public IResourceRecordIO<R> getRecordIO() {
        return recordIO;
    }

    public void setRecordIO(IResourceRecordIO<R> recordIO) {
        this.recordIO = recordIO;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public void setAggregator(IBatchAggregator<R, Object, Map<String, Object>> aggregator) {
        this.aggregator = aggregator;
    }

    public void setMetaProvider(IBatchMetaProvider metaProvider) {
        this.metaProvider = metaProvider;
    }

    public boolean isCancelTaskWhenWriteError() {
        return cancelTaskWhenWriteError;
    }

    public void setCancelTaskWhenWriteError(boolean cancelTaskWhenWriteError) {
        this.cancelTaskWhenWriteError = cancelTaskWhenWriteError;
    }

    @Override
    public IBatchConsumer<R> setup(IBatchTaskContext context) {
        ConsumerState<R> state = newConsumerState(context);
        return (items, ctx) -> consume(items, state);
    }

    ConsumerState<R> newConsumerState(IBatchTaskContext context) {
        ConsumerState<R> state = new ConsumerState<>();
        IResource resource = getResource(context);
        state.output = recordIO.openOutput(resource, encoding);
        Map<String, Object> header = null;
        if (metaProvider != null) {
            // 写入header
            header = metaProvider.getMeta(context);
            state.output.beginWrite(header);
        }

        // 用于汇总计算trailer
        if (aggregator != null) {
            state.combinedValue = aggregator.createCombinedValue(header, context);

            context.onBeforeComplete(() -> {
                Map<String, Object> trailer = aggregator.complete(null, state.combinedValue);
                state.output.endWrite(trailer);
                state.output.flush();
            });

            context.onAfterComplete(err -> {
                IoHelper.safeCloseObject(state.output);
            });
        }
        return state;
    }

    void consume(List<R> items, ConsumerState<R> state) {
        if (items.isEmpty())
            return;

        try {
            if (aggregator != null) {
                items.forEach(item -> {
                    aggregator.aggregate(item, state.combinedValue);
                });
            }

            state.output.writeBatch(items);
            state.output.flush();
        } catch (Exception e) {
            if (cancelTaskWhenWriteError)
                throw new BatchCancelException(ERR_BATCH_WRITE_FILE_FAIL, e).param(ARG_RESOURCE_PATH,
                        getResourcePath());

            throw NopException.adapt(e);
        }
    }
}