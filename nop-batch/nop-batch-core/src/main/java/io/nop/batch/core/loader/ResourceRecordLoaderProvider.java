/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.loader;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.time.CoreMetrics;
import io.nop.batch.core.IBatchAggregator;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.batch.core.IBatchRecordFilter;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.common.AbstractBatchResourceHandler;
import io.nop.batch.core.utils.BatchTaskHelper;
import io.nop.commons.util.IoHelper;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.resource.IResource;
import io.nop.core.resource.record.IResourceRecordInputProvider;
import io.nop.dataset.record.IRecordInput;
import io.nop.dataset.record.IRowNumberRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static io.nop.batch.core.BatchErrors.ARG_PROCESSING_ITEMS;
import static io.nop.batch.core.BatchErrors.ARG_READ_COUNT;
import static io.nop.batch.core.BatchErrors.ARG_RESOURCE_PATH;
import static io.nop.batch.core.BatchErrors.ERR_BATCH_TOO_MANY_PROCESSING_ITEMS;

/**
 * 读取数据文件。支持设置aggregator，在读取的过程中计算一些汇总信息
 *
 * @param <S> 数据文件中的记录类型
 */
public class ResourceRecordLoaderProvider<S> extends AbstractBatchResourceHandler
        implements IBatchLoaderProvider<S> {
    static final Logger LOG = LoggerFactory.getLogger(ResourceRecordLoaderProvider.class);

    private IResourceRecordInputProvider<S> recordIO;
    private IEvalAction encodingExpr;

    // 对读取的数据进行汇总处理，例如统计读入的总行数等，最后在complete时写入到数据库中
    private IBatchAggregator<S, Object, ?> aggregator;

    private IBatchRecordFilter<S, IBatchTaskContext> filter;

    /**
     * 最多读取多少行数据（包含跳过的记录）
     */
    IEvalAction maxCountExpr;

    /**
     * 跳过起始的多少行数据
     */
    long skipCount;

    int maxProcessingItems = 50000;

    /**
     * 当 processingItems 超过最大限制时等待的时间（毫秒）。等待结束后若仍超过则抛错。
     */
    long overLimitWaitMillis = 60000L;

    /**
     * 是否确保返回的记录实现{@link IRowNumberRecord}接口，并设置rowNumber为当前读取记录条目数，从1开始
     */
    boolean recordRowNumber = false;

    /**
     * 是否记录处理状态。如果是，则打开文件的时候会检查此前保存的处理条目数，跳过相应的数据行
     */
    boolean saveState;

    static class LoaderState<S> {

        IRecordInput<S> input;

        /**
         * 从行号映射到对应记录的处理状况。false表示正在处理，true表示处理完毕
         */
        TreeMap<Long, Boolean> processingItems;

        Object combinedValue;

        IBatchTaskContext context;
    }

    public void setEncodingExpr(IEvalAction encodingExpr) {
        this.encodingExpr = encodingExpr;
    }

    public void setAggregator(IBatchAggregator<S, Object, ?> aggregator) {
        this.aggregator = aggregator;
    }

    public void setFilter(IBatchRecordFilter<S, IBatchTaskContext> filter) {
        this.filter = filter;
    }

    public IResourceRecordInputProvider<S> getRecordIO() {
        return recordIO;
    }

    public void setRecordIO(IResourceRecordInputProvider<S> recordIO) {
        this.recordIO = recordIO;
    }

    public void setSaveState(boolean saveState) {
        this.saveState = saveState;
    }

    public boolean isSaveState() {
        return saveState;
    }

    public void setMaxCountExpr(IEvalAction maxCountExpr) {
        this.maxCountExpr = maxCountExpr;
    }

    public long getSkipCount() {
        return skipCount;
    }

    public void setSkipCount(long skipCount) {
        this.skipCount = skipCount;
    }

    public boolean isRecordRowNumber() {
        return recordRowNumber;
    }

    public void setRecordRowNumber(boolean recordRowNumber) {
        this.recordRowNumber = recordRowNumber;
    }

    public void setMaxProcessingItems(int maxProcessingItems) {
        this.maxProcessingItems = maxProcessingItems;
    }

    /**
     * 设置当超过最大处理条目限制时等待的毫秒数。
     *
     * @param overLimitWaitMillis 等待毫秒数，<=0 则不等待，直接按当前逻辑处理
     */
    public void setOverLimitWaitMillis(long overLimitWaitMillis) {
        this.overLimitWaitMillis = overLimitWaitMillis;
    }

    @Override
    public IBatchLoader<S> setup(IBatchTaskContext context) {
        LoaderState<S> state = newLoaderState(context);
        return (batchSize, ctx) -> {
            ctx.onAfterComplete(err -> onChunkEnd(ctx, err, state));
            ctx.getTaskContext().onBeforeComplete(() -> {
                if (state.processingItems != null && !state.processingItems.isEmpty())
                    throw new IllegalStateException("processingItems must be empty");
            });
            return load(batchSize, state, ctx);
        };
    }

    LoaderState<S> newLoaderState(IBatchTaskContext context) {
        LoaderState<S> state = new LoaderState<>();
        state.context = context;
        IResource resource = getResource(context);
        String encoding = this.encodingExpr == null ? null : ConvertHelper.toString(this.encodingExpr.invoke(context));
        IRecordInput<S> input = recordIO.openInput(resource, encoding);

        input.beforeRead(context.getAttributes());

        long skipCount = getSkipCount(context);

        if (aggregator != null) {
            state.combinedValue = aggregator.createCombinedValue(input.getHeaderMeta(), context);
            context.onBeforeComplete(() -> {
                aggregator.complete(state.input.getTrailerMeta(), state.combinedValue);
            });
        }

        if (skipCount > 0) {
            skip(input, skipCount, state);
        }

        if (maxCountExpr != null) {
            Long maxCount = ConvertHelper.toLong(maxCountExpr.invoke(context));
            if (maxCount != null)
                input = input.limit(maxCount);
        }

        state.input = input;

        context.onAfterComplete(err -> {
            IoHelper.safeCloseObject(state.input);
        });

        if (saveState)
            state.processingItems = new TreeMap<>();
        return state;
    }

    private long getSkipCount(IBatchTaskContext context) {
        long processedRowNumber = context.getCompletedIndex();

        long skipCount = this.skipCount;
        if (processedRowNumber > skipCount) {
            skipCount = processedRowNumber;
        }
        return skipCount;
    }

    private void skip(IRecordInput<S> input, long skipCount, LoaderState<S> state) {
        if (aggregator != null) {
            // 如果设置了aggregator，则需要从头开始遍历所有记录，否则断点重提的时候结果可能不正确。
            for (long i = 0; i < skipCount; i++) {
                if (!input.hasNext())
                    break;
                S item = input.next();
                if (filter != null && filter.accept(item, state.context))
                    continue;

                aggregator.aggregate(input.next(), state.combinedValue);
            }
        } else {
            input.skip(skipCount);
        }
    }

    public synchronized void onChunkEnd(IBatchChunkContext context, Throwable exception, LoaderState<S> state) {
        if (state.processingItems != null) {
            // 多个chunk有可能被并行处理，所以可能会乱序完成
            long maxRowNumber = -1;
            long minRowNumber = -1;
            if (context.getChunkItems() != null) {
                for (Object item : context.getChunkItems()) {
                    long rowNumber = getRowNumber(item, context);
                    if (rowNumber > 0) {
                        if (rowNumber > maxRowNumber)
                            maxRowNumber = rowNumber;
                        if (rowNumber < minRowNumber || minRowNumber < 0)
                            minRowNumber = rowNumber;
                        state.processingItems.put(rowNumber, true);
                    }
                }
            }

            // 如果最小的rowNumber已经完成，则记录处理历史。
            // 如果有线程处理的过慢，这里会出现积压，实际已经处理过的item因为前面的item尚未结束无法被标记为结束
            Iterator<Map.Entry<Long, Boolean>> it = state.processingItems.entrySet().iterator();
            long completedRow = -1L;
            while (it.hasNext()) {
                Map.Entry<Long, Boolean> entry = it.next();
                if (entry.getValue()) {
                    it.remove();
                    completedRow = entry.getKey();
                } else {
                    break;
                }
            }

            LOG.info("nop.batch.loader.chunk-end:taskKey={},chunkMinRowNumber={},chunkMaxRowNumber={}," +
                            "chunkCompletedRow={},completedIndex={},processingItems={}",
                    state.context.getTaskKey(), minRowNumber, maxRowNumber, completedRow,
                    state.context.getCompletedIndex(), state.processingItems.size());

            // 如果处理阶段异常，则不会保存到状态变量中，这样下次处理的时候仍然会处理到这些记录
            if (completedRow > 0 && exception == null) {
                context.getTaskContext().setCompletedIndex(completedRow);
            }

            // 通知可能在load中等待容量恢复的线程
            this.notifyAll();
        }
    }

    synchronized List<S> load(int batchSize, LoaderState<S> state, IBatchChunkContext chunkCtx) {
        // 进入时如果发现处理中的条目已经超过限制，则等待一段时间；若等待后仍超过，则不读取，直接抛错
        if (!waitUntilUnderLimit(state)) {
            throw BatchTaskHelper.newTaskError(state.context, ERR_BATCH_TOO_MANY_PROCESSING_ITEMS)
                    .param(ARG_PROCESSING_ITEMS, state.processingItems == null ? 0 : state.processingItems.size())
                    .param(ARG_READ_COUNT, state.input.getReadCount())
                    .param(ARG_RESOURCE_PATH, getResourcePath());
        }

        long readCount = state.input.getReadCount();

        List<S> items = loadItems(batchSize, state);
        if (saveState || recordRowNumber) {
            for (S item : items) {
                long rowNumber = ++readCount;
                if (item instanceof IRowNumberRecord) {
                    IRowNumberRecord record = (IRowNumberRecord) item;
                    if (record.getRecordRowNumber() > 0) {
                        rowNumber = record.getRecordRowNumber();
                    } else {
                        ((IRowNumberRecord) item).setRecordRowNumber(rowNumber);
                    }
                } else {
                    chunkCtx.setRowNumber(item, rowNumber);
                }

                if (state.processingItems != null && rowNumber > 0)
                    state.processingItems.put(rowNumber, false);
            }

            if (state.processingItems != null && state.processingItems.size() > maxProcessingItems) {
                // 放入processingItems后如果超过最大限制，则先等待一段时间，再决定是否报错
                if (!waitUntilUnderLimit(state)) {
                    throw BatchTaskHelper.newTaskError(state.context, ERR_BATCH_TOO_MANY_PROCESSING_ITEMS)
                            .param(ARG_PROCESSING_ITEMS, state.processingItems.size())
                            .param(ARG_READ_COUNT, state.input.getReadCount())
                            .param(ARG_RESOURCE_PATH, getResourcePath());
                }
            }
        }

        if (aggregator != null) {
            for (S item : items) {
                aggregator.aggregate(item, state.combinedValue);
            }
        }
        return items;
    }

    /**
     * 如果当前 processingItems 超过最大限制，则在 overLimitWaitMillis 时间窗口内循环等待直到恢复到限制以内或超时。
     * 返回 true 表示当前已不超过限制，可以继续；false 表示超时后仍然超过限制。
     */
    private boolean waitUntilUnderLimit(LoaderState<S> state) {
        if (state.processingItems == null)
            return true; // 未启用processingItems，无需限制

        if (state.processingItems.size() <= maxProcessingItems)
            return true;

        long waitMs = this.overLimitWaitMillis;
        if (waitMs <= 0)
            return false; // 不等待则立即认为超时

        final long deadline = CoreMetrics.currentTimeMillis() + waitMs;
        long remaining;
        do {
            remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0)
                break;
            try {
                LOG.warn("nop.batch.loader.over-limit:taskName={},taskId={},taskKey={}, processingItems={} > max={}, wait={}ms",
                        state.context.getTaskName(), state.context.getTaskId(), state.context.getTaskKey(),
                        state.processingItems.size(), maxProcessingItems, remaining);
                this.wait(remaining);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warn("nop.batch.loader.wait-interrupted");
                break;
            }
        } while (state.processingItems.size() > maxProcessingItems);

        return state.processingItems.size() <= maxProcessingItems;
    }

    private List<S> loadItems(int batchSize, LoaderState<S> state) {
        if (filter == null)
            return state.input.readBatch(batchSize);

        return state.input.readFiltered(batchSize, item -> filter.accept(item, state.context));
    }

    private long getRowNumber(Object item, IBatchChunkContext chunkCtx) {
        if (item instanceof IRowNumberRecord) {
            return ((IRowNumberRecord) item).getRecordRowNumber();
        }
        return chunkCtx.getRowNumber(item);
    }
}