/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core;

import io.nop.api.core.annotations.core.PropertySetter;
import io.nop.api.core.util.Guard;
import io.nop.batch.core.IBatchConsumerProvider.IBatchConsumer;
import io.nop.batch.core.IBatchLoaderProvider.IBatchLoader;
import io.nop.batch.core.IBatchProcessorProvider.IBatchProcessor;
import io.nop.batch.core.consumer.*;
import io.nop.batch.core.impl.BatchTaskExecution;
import io.nop.batch.core.loader.ChunkSortBatchLoader;
import io.nop.batch.core.processor.BatchChunkProcessor;
import io.nop.batch.core.processor.InvokerBatchChunkProcessor;
import io.nop.commons.concurrent.executor.ExecutorHelper;
import io.nop.commons.concurrent.ratelimit.DefaultRateLimiter;
import io.nop.commons.functional.IFunctionInvoker;
import io.nop.commons.util.retry.IRetryPolicy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * 负责创建{@link IBatchTask}的工厂类。它负责组织skip/retry/transaction/process/listener的处理顺序
 */
public class BatchTaskBuilder<S, R> implements IBatchTaskBuilder {
    private IBatchLoaderProvider<S> loader;
    private IBatchConsumerProvider<R> consumer;
    private IBatchProcessorProvider<S, R> processor;
    private int batchSize = 100;

    /**
     * 同时启动多少个线程去并行处理
     */
    private int concurrency = 1;
    private boolean retryOneByOne;

    /**
     * singleMode表示批量读取数据，然后逐条处理、消费，而不是批量消费。
     */
    private boolean singleMode;
    private IRetryPolicy<IBatchChunkContext> retryPolicy;

    private BatchSkipPolicy skipPolicy;

    private IBatchChunkProcessorBuilder<S> chunkProcessorBuilder;

    private boolean singleSession;
    private BatchTransactionScope batchTransactionScope = BatchTransactionScope.consume;
    private Executor executor = ExecutorHelper.syncExecutor();

    private IBatchStateStore stateStore;

    /**
     * 在事务环境中执行
     */
    private IFunctionInvoker transactionalInvoker;

    private IFunctionInvoker singleSessionInvoker;

    private IBatchRecordHistoryStore<S> historyStore;

    /**
     * 如果发现历史已经被处理过，可能也会需要调用一个consumer来执行额外的逻辑
     */
    private IBatchConsumerProvider<S> historyConsumer;

    private Comparator<S> inputComparator;

    private IBatchRecordSnapshotBuilder<S> snapshotBuilder;

    private List<Consumer<IBatchTaskContext>> taskInitializers;

    /**
     * 每秒最多处理多少条记录
     */
    private double rateLimit;

    /**
     * 多线程执行时，如果每个线程处理的batchSize都相同，则可能导致同时读取数据库和同时写数据库，产生资源征用。 通过设置一个随机比例，将每个线程处理的batchSize动态调整为originalBatchSize * (1
     * + jitterRatio * random)， 使得每个线程的每个批次的负载随机化，从而破坏潜在的同步效应。
     */
    private double jitterRatio;

    public static <S, R> BatchTaskBuilder<S, R> create() {
        return new BatchTaskBuilder<>();
    }

    public void addTaskInitializer(Consumer<IBatchTaskContext> initializer) {
        if (taskInitializers == null)
            taskInitializers = new ArrayList<>();
        taskInitializers.add(initializer);
    }

    @PropertySetter
    public BatchTaskBuilder<S, R> inputComparator(Comparator<S> comparator) {
        this.inputComparator = comparator;
        return this;
    }

    @PropertySetter
    public BatchTaskBuilder<S, R> stateStore(IBatchStateStore stateStore) {
        this.stateStore = stateStore;
        return this;
    }

    @PropertySetter
    public BatchTaskBuilder<S, R> historyStore(IBatchRecordHistoryStore<S> historyStore) {
        this.historyStore = historyStore;
        return this;
    }

    @PropertySetter
    public BatchTaskBuilder<S, R> skipPolicy(BatchSkipPolicy skipPolicy) {
        this.skipPolicy = skipPolicy;
        return this;
    }

    @PropertySetter
    public BatchTaskBuilder<S, R> retryPolicy(IRetryPolicy<IBatchChunkContext> retryPolicy) {
        this.retryPolicy = retryPolicy;
        return this;
    }

    @PropertySetter
    public BatchTaskBuilder<S, R> retryOneByOne(boolean retryOneByOne) {
        this.retryOneByOne = retryOneByOne;
        return this;
    }

    @PropertySetter
    public BatchTaskBuilder<S, R> singleMode(boolean singleMode) {
        this.singleMode = singleMode;
        return this;
    }

    @PropertySetter
    public BatchTaskBuilder<S, R> rateLimit(double rateLimit) {
        this.rateLimit = rateLimit;
        return this;
    }

    @PropertySetter
    public BatchTaskBuilder<S, R> jitterRatio(double jitterRatio) {
        this.jitterRatio = jitterRatio;
        return this;
    }

    @PropertySetter
    public BatchTaskBuilder<S, R> transactionScope(BatchTransactionScope scope) {
        this.batchTransactionScope = scope;
        return this;
    }

    @PropertySetter
    public BatchTaskBuilder<S, R> singleSession(boolean singleSession) {
        this.singleSession = singleSession;
        return this;
    }

    @PropertySetter
    public BatchTaskBuilder<S, R> concurrency(int concurrency) {
        this.concurrency = concurrency;
        return this;
    }

    @PropertySetter
    public BatchTaskBuilder<S, R> executor(Executor executor) {
        this.executor = executor;
        return this;
    }

    @PropertySetter
    public BatchTaskBuilder<S, R> batchSize(int batchSize) {
        this.batchSize = batchSize;
        return this;
    }

    @PropertySetter
    public BatchTaskBuilder<S, R> loader(IBatchLoaderProvider<S> loader) {
        Guard.checkState(this.loader == null, "loader is already set");
        this.loader = loader;
        return this;
    }

    @PropertySetter
    public BatchTaskBuilder<S, R> historyConsumer(IBatchConsumerProvider<S> historyConsumer) {
        Guard.checkState(this.historyConsumer == null, "history consumer is already set");
        this.historyConsumer = historyConsumer;
        return this;
    }


    @PropertySetter
    public BatchTaskBuilder<S, R> consumer(IBatchConsumerProvider<R> consumer) {
        Guard.checkState(this.consumer == null, "consumer is already set");
        this.consumer = consumer;
        return this;
    }

    @PropertySetter
    public BatchTaskBuilder<S, R> chunkProcessorBuilder(IBatchChunkProcessorBuilder<S> chunkProcessorBuilder) {
        Guard.checkState(this.chunkProcessorBuilder == null, "chunkProcessorBuilder is already set");
        this.chunkProcessorBuilder = chunkProcessorBuilder;
        return this;
    }

    @PropertySetter
    public BatchTaskBuilder<S, R> transactionalInvoker(IFunctionInvoker invoker) {
        this.transactionalInvoker = invoker;
        return this;
    }

    @PropertySetter
    public BatchTaskBuilder<S, R> singleSessionInvoker(IFunctionInvoker invoker) {
        this.singleSessionInvoker = invoker;
        return this;
    }

    @PropertySetter
    public BatchTaskBuilder<S, R> batchRecordSnapshotBuilder(IBatchRecordSnapshotBuilder<S> builder) {
        this.snapshotBuilder = builder;
        return this;
    }

    @PropertySetter
    public BatchTaskBuilder<S, R> processor(IBatchProcessorProvider<S, R> processor) {
        Guard.checkState(this.processor == null, "processor is already set");
        this.processor = processor;
        return this;
    }

    @Override
    public IBatchTask buildTask(IBatchTaskContext context) {
        IBatchChunkProcessor chunkProcessor = buildChunkProcessor(context);

        return new BatchTaskExecution(executor, concurrency, taskInitializers, chunkProcessor, stateStore);
    }

    @SuppressWarnings("rawtypes")
    protected IBatchChunkProcessor buildChunkProcessor(IBatchTaskContext context) {
        IBatchLoader<S> loader = this.loader.setup(context);

        if (inputComparator != null)
            loader = new ChunkSortBatchLoader<>(inputComparator, loader);

        IBatchConsumer<S> consumer = this.consumer == null ? null : (IBatchConsumer) this.consumer.setup(context);
        if (consumer == null)
            consumer = EmptyBatchConsumer.instance();

        if (batchTransactionScope == BatchTransactionScope.consume
                && transactionalInvoker != null) {
            // 仅在consume阶段打开事务。process可以是纯逻辑处理过程，不涉及到修改数据库，而读数据一般不需要打开事务。
            consumer = new InvokerBatchConsumer<>(transactionalInvoker, consumer);
        }

        if (this.processor != null) {
            // 如果设置了processor,则先执行processor再调用consumer，否则直接调用consumer
            IBatchProcessor<S, R> processor = this.processor.setup(context);
            consumer = new BatchProcessorConsumer<>(processor, (IBatchConsumer<R>) consumer);
        }

        // 保存处理历史，避免重复处理
        if (historyStore != null)
            consumer = new WithHistoryBatchConsumer<>(historyStore, consumer, historyConsumer.setup(context));

        // 在process和consume阶段打开事务
        if (batchTransactionScope == BatchTransactionScope.process && transactionalInvoker != null) {
            consumer = new InvokerBatchConsumer<>(transactionalInvoker, consumer);
        }

        // 限制消费速度
        if (rateLimit > 0)
            consumer = new RateLimitConsumer<>(consumer, new DefaultRateLimiter(rateLimit));

        // 一般情况下事务scope为process或者consume，因此retry是在事务之外执行
        if (retryPolicy != null) {
            consumer = new RetryBatchConsumer<>(consumer, retryPolicy, retryOneByOne, singleMode, snapshotBuilder);
        }

        // retry失败后，如果错误记录数在一定范围之内，则可以忽略异常继续处理。
        if (skipPolicy != null) {
            consumer = new SkipBatchConsumer<>(consumer, skipPolicy);
        }

        IBatchChunkProcessor chunkProcessor;
        // 一般只会使用缺省的BatchChunkProcessor，它负责核心的load/process/consume过程
        IBatchChunkProcessorBuilder<S> chunkProcessorBuilder = this.chunkProcessorBuilder;
        if (chunkProcessorBuilder == null) {
            chunkProcessor = new BatchChunkProcessor<>(loader, batchSize, jitterRatio, consumer);
        } else {
            chunkProcessor = chunkProcessorBuilder.buildChunkProcessor(loader, batchSize, jitterRatio, consumer);
        }

        if (batchTransactionScope == BatchTransactionScope.chunk && transactionalInvoker != null) {
            // chunk处理的整个过程（包括load/process/consume）都打开事务
            chunkProcessor = new InvokerBatchChunkProcessor(transactionalInvoker, chunkProcessor);
        }

        // 整个chunk处理过程共享一个ORM session，因此需要注意抛出异常时可能会导致数据相互影响的问题。
        // 缺省情况下异常导致事务回滚时会自动调用session.clear()
        if (singleSession && singleSessionInvoker != null) {
            chunkProcessor = new InvokerBatchChunkProcessor(singleSessionInvoker, chunkProcessor);
        }

        return chunkProcessor;
    }
}