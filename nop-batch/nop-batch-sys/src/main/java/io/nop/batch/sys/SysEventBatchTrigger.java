package io.nop.batch.sys;

import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.batch.core.BatchDispatchConfig;
import io.nop.batch.core.BatchTaskBuilder;
import io.nop.batch.core.BatchTransactionScope;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.batch.core.IBatchProcessorProvider;
import io.nop.batch.core.IBatchTask;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import io.nop.commons.functional.IFunctionInvoker;
import io.nop.sys.dao.entity.NopSysEvent;
import io.nop.sys.dao.message.SysDaoMessageService;

import java.util.Collections;
import java.util.List;

public class SysEventBatchTrigger {
    private final SysDaoMessageService messageService;

    public SysEventBatchTrigger(SysDaoMessageService messageService) {
        this.messageService = messageService;
    }

    public void processNonBroadcastEvent() {
        buildTask().execute(new BatchTaskContextImpl());
    }

    protected IBatchTask buildTask() {
        BatchTaskBuilder<NopSysEvent, NopSysEvent> builder = new BatchTaskBuilder<>();
        builder.taskName("sysEvent.processNonBroadcastEvent");
        builder.batchSize(messageService.getFetchSize());
        builder.concurrency(1);
        builder.transactionScope(BatchTransactionScope.consume);
        builder.transactionalInvoker(newRequiresNewInvoker());
        builder.dispatchConfig(buildDispatchConfig());
        builder.loader(newLoaderProvider());
        builder.processor(newProcessorProvider());
        builder.consumer(ctx -> (items, chunkCtx) -> {
            for (NopSysEvent event : items) {
                messageService.processClaimedNonBroadcastEvent(event);
            }
        });
        return builder.buildTask();
    }

    protected BatchDispatchConfig<NopSysEvent> buildDispatchConfig() {
        BatchDispatchConfig<NopSysEvent> config = new BatchDispatchConfig<>();
        config.setLoadBatchSize(Math.max(messageService.getFetchSize() * 4, messageService.getFetchSize()));
        config.setPartitionFn((event, ctx) -> event.getPartitionIndex());
        return config;
    }

    protected IBatchLoaderProvider<NopSysEvent> newLoaderProvider() {
        return taskCtx -> (batchSize, chunkCtx) -> messageService.fetchExecutableNonBroadcastEvents(
                Math.max(batchSize, messageService.getFetchSize() * 4));
    }

    protected IBatchProcessorProvider<NopSysEvent, NopSysEvent> newProcessorProvider() {
        return taskCtx -> (event, consumer, chunkCtx) -> {
            List<NopSysEvent> claimed = messageService.claimNonBroadcastEvents(Collections.singletonList(event));
            if (!claimed.isEmpty()) {
                consumer.accept(claimed.get(0));
            }
        };
    }

    protected IFunctionInvoker newRequiresNewInvoker() {
        return new IFunctionInvoker() {
            @Override
            public <R, T> T invoke(java.util.function.Function<R, T> fn, R request) {
                return messageService.runInNewTransaction(fn, request, TransactionPropagation.REQUIRES_NEW);
            }
        };
    }
}
