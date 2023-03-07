/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.batch.core;

import io.nop.api.core.util.Guard;
import io.nop.batch.core.consumer.BatchConsumerWithListener;
import io.nop.batch.core.consumer.BatchProcessorConsumer;
import io.nop.batch.core.consumer.EmptyBatchConsumer;
import io.nop.batch.core.consumer.InvokerBatchConsumer;
import io.nop.batch.core.consumer.RateLimitConsumer;
import io.nop.batch.core.consumer.RetryBatchConsumer;
import io.nop.batch.core.consumer.SkipBatchConsumer;
import io.nop.batch.core.impl.BatchTask;
import io.nop.batch.core.listener.MetricsRetryConsumeListener;
import io.nop.batch.core.listener.MultiBatchChunkListener;
import io.nop.batch.core.listener.MultiBatchConsumerListener;
import io.nop.batch.core.listener.MultiBatchLoadListener;
import io.nop.batch.core.listener.MultiBatchProcessListener;
import io.nop.batch.core.listener.MultiBatchTaskListener;
import io.nop.batch.core.loader.BatchLoaderWithListener;
import io.nop.batch.core.processor.BatchChunkProcessor;
import io.nop.batch.core.processor.BatchProcessorWithListener;
import io.nop.batch.core.processor.FilterBatchProcessor;
import io.nop.batch.core.processor.InvokerBatchChunkProcessor;
import io.nop.commons.concurrent.executor.ExecutorHelper;
import io.nop.commons.concurrent.ratelimit.DefaultRateLimiter;
import io.nop.commons.functional.IBuilder;
import io.nop.commons.functional.IFunctionInvoker;
import io.nop.commons.util.retry.IRetryPolicy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

/**
 * 负责创建{@link IBatchTask}的工厂类。它负责组织skip/retry/transaction/process/listener的处理顺序
 */
public class BatchTaskBuilder implements IBuilder<IBatchTask> {
    private IBatchLoader loader;
    private IBatchConsumer consumer;
    private IBatchProcessor processor;
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
    private IRetryPolicy retryPolicy;

    private BatchSkipPolicy skipPolicy;

    private List<IBatchTaskListener> taskListeners = new ArrayList<>();
    private List<IBatchLoadListener> loadListeners = new ArrayList<>();
    private List<IBatchConsumeListener> consumeListeners = new ArrayList<>();
    private List<IBatchProcessListener> processListeners = new ArrayList<>();
    private List<IBatchChunkListener> chunkListeners = new ArrayList<>();

    private IBatchChunkProcessor chunkProcessor;

    private boolean singleSession;
    private BatchTransactionScope batchTransactionScope = BatchTransactionScope.consume;
    private Executor executor = ExecutorHelper.syncExecutor();

    private IBatchStateStore stateStore;

    private IFunctionInvoker transactionalInvoker;

    private IFunctionInvoker singleSessionInvoker;

    /**
     * 每秒最多处理多少条记录
     */
    private double rateLimit;

    /**
     * 多线程执行时，如果每个线程处理的batchSize都相同，则可能导致同时读取数据库和同时写数据库，产生资源征用。 通过设置一个随机比例，将每个线程处理的batchSize动态调整为originalBatchSize * (1
     * + jitterRatio * random)， 使得每个线程的每个批次的负载随机化，从而破坏潜在的同步效应。
     */
    private double jitterRatio;

    public static BatchTaskBuilder create() {
        return new BatchTaskBuilder();
    }

    public BatchTaskBuilder stateStore(IBatchStateStore stateStore) {
        this.stateStore = stateStore;
        return this;
    }

    public BatchTaskBuilder skipPolicy(BatchSkipPolicy skipPolicy) {
        this.skipPolicy = skipPolicy;
        return this;
    }

    public BatchTaskBuilder retryPolicy(IRetryPolicy retryPolicy) {
        this.retryPolicy = retryPolicy;
        return this;
    }

    public BatchTaskBuilder retryOneByOne(boolean retryOneByOne) {
        this.retryOneByOne = retryOneByOne;
        return this;
    }

    public BatchTaskBuilder singleMode(boolean singleMode) {
        this.singleMode = singleMode;
        return this;
    }

    public BatchTaskBuilder rateLimit(double rateLimit) {
        this.rateLimit = rateLimit;
        return this;
    }

    public BatchTaskBuilder jitterRatio(double jitterRatio) {
        this.jitterRatio = jitterRatio;
        return this;
    }

    public BatchTaskBuilder transactionScope(BatchTransactionScope scope) {
        this.batchTransactionScope = batchTransactionScope;
        return this;
    }

    public BatchTaskBuilder singleSession(boolean singleSession) {
        this.singleSession = singleSession;
        return this;
    }

    public BatchTaskBuilder concurrency(int concurrency) {
        this.concurrency = concurrency;
        return this;
    }

    public BatchTaskBuilder executor(Executor executor) {
        this.executor = executor;
        return this;
    }

    public BatchTaskBuilder batchSize(int batchSize) {
        this.batchSize = batchSize;
        return this;
    }

    public BatchTaskBuilder loader(IBatchLoader loader) {
        Guard.checkState(this.loader == null, "loader is already set");
        this.loader = loader;
        addListener(loader);
        return this;
    }

    public BatchTaskBuilder consumer(IBatchConsumer<?, IBatchChunkContext> consumer) {
        Guard.checkState(this.consumer == null, "consumer is already set");
        this.consumer = consumer;
        addListener(consumer);
        return this;
    }

    public BatchTaskBuilder chunkProcessor(IBatchChunkProcessor chunkProcessor) {
        Guard.checkState(this.chunkProcessor == null, "chunkProcessor is already set");
        this.chunkProcessor = chunkProcessor;
        addListener(chunkProcessor);
        return this;
    }

    public BatchTaskBuilder transactionalInvoker(IFunctionInvoker invoker) {
        this.transactionalInvoker = invoker;
        return this;
    }

    public BatchTaskBuilder singleSessionInvoker(IFunctionInvoker invoker) {
        this.singleSessionInvoker = invoker;
        return this;
    }

    public BatchTaskBuilder addListener(Object listener) {
        if (listener instanceof IBatchTaskListener) {
            taskListeners.add((IBatchTaskListener) listener);
        }

        if (listener instanceof IBatchLoadListener) {
            loadListeners.add((IBatchLoadListener) listener);
        }

        if (listener instanceof IBatchConsumeListener) {
            consumeListeners.add((IBatchConsumeListener) listener);
        }

        if (listener instanceof IBatchChunkListener) {
            chunkListeners.add((IBatchChunkListener) listener);
        }

        if (listener instanceof IBatchProcessListener)
            processListeners.add((IBatchProcessListener) listener);

        return this;
    }

    public BatchTaskBuilder listeners(Collection<?> listeners) {
        for (Object listener : listeners) {
            addListener(listener);
        }
        return this;
    }

    public BatchTaskBuilder processor(IBatchProcessor<?, ?, IBatchChunkContext> processor) {
        Guard.checkState(this.processor == null, "processor is already set");

        addListener(processor);

        this.processor = processor;
        return this;
    }

    public BatchTaskBuilder processors(List<IBatchProcessor<?, ?, IBatchChunkContext>> processors) {
        for (IBatchProcessor<?, ?, IBatchChunkContext> processor : processors) {
            addProcessor(processor);
        }
        return this;
    }

    public BatchTaskBuilder addProcessor(IBatchProcessor<?, ?, IBatchChunkContext> processor) {
        addListener(processor);
        if (this.processor != null) {
            this.processor = this.processor.then(processor);
        } else {
            this.processor = processor;
        }
        return this;
    }

    public BatchTaskBuilder addFilter(Predicate<?> filter) {
        return addProcessor(new FilterBatchProcessor<>(filter));
    }

    public IBatchTask build() {
        IBatchTaskListener taskListener = null;
        if (!taskListeners.isEmpty()) {
            taskListener = new MultiBatchTaskListener(taskListeners);
        }

        IBatchChunkListener chunkListener = null;
        if (!chunkListeners.isEmpty()) {
            chunkListener = new MultiBatchChunkListener(chunkListeners);
        }

        IBatchChunkProcessor chunkProcessor = buildChunkProcessor();

        return new BatchTask(executor, concurrency, chunkProcessor, chunkListener, taskListener, stateStore);
    }

    protected IBatchChunkProcessor buildChunkProcessor() {
        IBatchLoader loader = this.loader;

        IBatchLoadListener loadListener = null;
        if (!loadListeners.isEmpty()) {
            loadListener = new MultiBatchLoadListener(loadListeners);
            loader = new BatchLoaderWithListener(loader, loadListener);
        }

        IBatchConsumer consumer = this.consumer;
        if (consumer == null)
            consumer = EmptyBatchConsumer.instance();

        if (!consumeListeners.isEmpty()) {
            IBatchConsumeListener consumeListener = new MultiBatchConsumerListener(this.consumeListeners);
            consumer = new BatchConsumerWithListener(consumer, consumeListener);
        }

        if (batchTransactionScope == BatchTransactionScope.consume && consumer != EmptyBatchConsumer.instance()
                && transactionalInvoker != null) {
            // 仅在consume阶段打开事务。process可以是纯逻辑处理过程，不涉及到修改数据库，而读数据一般不需要打开事务。
            consumer = new InvokerBatchConsumer(transactionalInvoker, consumer);
        }

        if (this.processor != null) {
            // 如果设置了processor,则先执行processor再调用consumer，否则直接调用consumer
            IBatchProcessor processor = this.processor;
            if (!this.processListeners.isEmpty()) {
                IBatchProcessListener processListener = new MultiBatchProcessListener(this.processListeners);
                processor = new BatchProcessorWithListener<>(processor, processListener);
            }
            consumer = new BatchProcessorConsumer(processor, this.consumer);

            // 在process和consume阶段打开事务
            if (batchTransactionScope == BatchTransactionScope.process && transactionalInvoker != null) {
                consumer = new InvokerBatchConsumer(transactionalInvoker, consumer);
            }
        }

        // 限制消费速度
        if (rateLimit > 0)
            consumer = new RateLimitConsumer(consumer, new DefaultRateLimiter(rateLimit));

        // 一般情况下事务scope为process或者consume，因此retry是在事务之外执行
        if (retryPolicy != null) {
            consumer = new RetryBatchConsumer(consumer, retryPolicy, retryOneByOne, singleMode,
                    new MetricsRetryConsumeListener());
        }

        // retry失败后，如果错误记录数在一定范围之内，则可以忽略异常继续处理。
        if (skipPolicy != null) {
            consumer = new SkipBatchConsumer(consumer, skipPolicy);
        }

        // 一般只会使用缺省的BatchChunkProcessor，它负责核心的load/process/consume过程
        IBatchChunkProcessor chunkProcessor = this.chunkProcessor;
        if (chunkProcessor == null) {
            chunkProcessor = new BatchChunkProcessor(loader, batchSize, jitterRatio, consumer);
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