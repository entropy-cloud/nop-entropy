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
import io.nop.batch.core.IBatchConsumer;
import io.nop.batch.core.IBatchMetaProvider;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.IBatchTaskListener;
import io.nop.batch.core.common.AbstractBatchResourceHandler;
import io.nop.batch.core.exceptions.BatchCancelException;
import io.nop.dataset.record.IRecordOutput;
import io.nop.commons.util.IoHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.record.IResourceRecordIO;

import java.util.List;
import java.util.Map;

import static io.nop.batch.core.BatchErrors.ARG_RESOURCE_PATH;
import static io.nop.batch.core.BatchErrors.ERR_BATCH_WRITE_FILE_FAIL;

/**
 * 将记录写入文件。支持写入header和trailer
 *
 * @param <R> 记录类型
 * @param <C> ChunkContext
 */
public class ResourceRecordConsumer<R, C> extends AbstractBatchResourceHandler
        implements IBatchConsumer<R, C>, IBatchTaskListener {
    private IResourceRecordIO<R> recordIO;
    private String encoding;

    /**
     * 写入文件时如果发生异常，则抛出BatchCancelException，取消整个Batch任务处理
     */
    private boolean cancelTaskWhenWriteError;

    private IRecordOutput<R> output;

    private IBatchMetaProvider metaProvider;

    /**
     * 用于汇总trailer信息。在文件关闭前会写入trailer信息
     */
    private IBatchAggregator<R, Object, Map<String, Object>> aggregator;

    private Object combinedValue;

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
    public synchronized void onTaskBegin(IBatchTaskContext context) {
        IResource resource = getResource();
        output = recordIO.openOutput(resource, encoding);
        Map<String, Object> header = null;
        if (metaProvider != null) {
            // 写入header
            header = metaProvider.getMeta(context);
            output.setHeaderMeta(header);
        }

        // 用于汇总计算trailer
        if (aggregator != null) {
            combinedValue = aggregator.createCombinedValue(header, context);
        }
    }

    @Override
    public synchronized void onTaskEnd(Throwable exception, IBatchTaskContext context) {
        try {
            if (aggregator != null && output != null) {
                Map<String, Object> trailer = aggregator.complete(null, combinedValue);
                output.setTrailerMeta(trailer);
                combinedValue = null;
                output.flush();
            }
        } finally {
            if (output != null) {
                IoHelper.safeCloseObject(output);
                output = null;
            }
        }
    }

    @Override
    public void consume(List<R> items, C context) {
        if (items.isEmpty())
            return;

        try {
            if (aggregator != null) {
                items.forEach(item -> {
                    aggregator.aggregate(item, combinedValue);
                });
            }

            output.writeBatch(items);
            output.flush();
        } catch (Exception e) {
            if (cancelTaskWhenWriteError)
                throw new BatchCancelException(ERR_BATCH_WRITE_FILE_FAIL, e).param(ARG_RESOURCE_PATH,
                        getResource().getPath());

            throw NopException.adapt(e);
        }
    }
}