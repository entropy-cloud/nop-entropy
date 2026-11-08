/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core;

import io.nop.api.core.annotations.core.PropertySetter;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.batch.core.IBatchConsumerProvider.IBatchConsumer;
import io.nop.batch.core.IBatchLoaderProvider.IBatchLoader;
import io.nop.batch.core.IBatchProcessorProvider.IBatchProcessor;
import io.nop.batch.core.consumer.AddCompletedBatchConsumer;
import io.nop.batch.core.consumer.BatchProcessorConsumer;
import io.nop.batch.core.consumer.EmptyBatchConsumer;
import io.nop.batch.core.consumer.InvokerBatchConsumer;
import io.nop.batch.core.consumer.RateLimitConsumer;
import io.nop.batch.core.consumer.RetryAllBatchConsumer;
import io.nop.batch.core.consumer.RetryOneByOneBatchConsumer;
import io.nop.batch.core.consumer.SingleModeBatchConsumer;
import io.nop.batch.core.consumer.SkipBatchConsumer;
import io.nop.batch.core.consumer.WithHistoryBatchConsumer;
import io.nop.batch.core.impl.BatchTask;
import io.nop.batch.core.loader.AsyncFetchPartitionDispatchLoaderProvider;
import io.nop.batch.core.loader.ChunkSortBatchLoader;
import io.nop.batch.core.loader.InvokerBatchLoader;
import io.nop.batch.core.loader.PartitionDispatchLoaderProvider;
import io.nop.batch.core.loader.RetryBatchLoader;
import io.nop.batch.core.processor.BatchChunkProcessor;
import io.nop.batch.core.processor.BatchSequentialProcessor;
import io.nop.batch.core.processor.InvokerBatchChunkProcessor;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.ratelimit.DefaultRateLimiter;
import io.nop.commons.functional.IFunctionInvoker;
import io.nop.commons.util.retry.IRetryPolicy;
import io.nop.core.lang.eval.IEvalFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import static io.nop.batch.core.BatchErrors.ARG_FETCH_THREAD_COUNT;
import static io.nop.batch.core.BatchErrors.ARG_LOAD_BATCH_SIZE;
import static io.nop.batch.core.BatchErrors.ERR_BATCH_DISPATCH_LOAD_BATCH_SIZE_INVALID;
import static io.nop.batch.core.BatchErrors.ERR_BATCH_SAVE_STATE_WITH_DISPATCHER_UNSUPPORTED;

/**
 * 负责创建{@link IBatchTask}的工厂类。它负责组织skip/retry/transaction/process/listener的处理顺序
 */
public class BatchTaskBuilder<S, R> implements IBatchTaskBuilder {
    static final Logger LOG = LoggerFactory.getLogger(BatchTaskBuilder.class);

    private String taskName;
    private Long taskVersion;
    private IBatchLoaderProvider<S> loader;
    private IBatchConsumerProvider<R> consumer;
    private boolean useBatchRequestGenerator;
    private IBatchProcessorProvider<S, R> processor;
    private boolean asyncProcessor;
    private long asyncProcessTimeout;
    private int batchSize = 100;

    /**
     * 同时启动多少个线程去并行处理
     */
    private int concurrency = 0;
    private boolean retryOneByOne;

    /**
     * singleMode表示批量读取数据，然后逐条处理、消费，而不是批量消费。
     */
    private boolean singleMode;
    private IRetryPolicy<IBatchChunkContext> retryPolicy;
    private IRetryPolicy<IBatchChunkContext> loadRetryPolicy;

    private BatchSkipPolicy skipPolicy;

    private boolean singleSession;
    private BatchTransactionScope batchTransactionScope = BatchTransactionScope.consume;
    private Executor executor;

    private IBatchStateStore stateStore;
    private Boolean allowStartIfComplete;
    private int startLimit;

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

    private IEvalFunction taskKeyExpr;

    private BatchDispatchConfig<S> dispatchConfig;

    public static <S, R> BatchTaskBuilder<S, R> create() {
        return new BatchTaskBuilder<>();
    }

    @PropertySetter
    public BatchTaskBuilder<S, R> startLimit(int startLimit) {
        this.startLimit = startLimit;
        return this;
    }

    @PropertySetter
    public BatchTaskBuilder<S, R> allowStartIfComplete(Boolean allowStartIfComplete) {
        this.allowStartIfComplete = allowStartIfComplete;
        return this;
    }

    @PropertySetter
    public BatchTaskBuilder<S, R> dispatchConfig(BatchDispatchConfig<S> dispatchConfig) {
        this.dispatchConfig = dispatchConfig;
        return this;
    }

    @PropertySetter
    public BatchTaskBuilder<S, R> taskName(String taskName) {
        this.taskName = taskName;
        return this;
    }

    @PropertySetter
    public BatchTaskBuilder<S, R> taskVersion(Long taskVersion) {
        this.taskVersion = taskVersion;
        return this;
    }

    @PropertySetter
    public BatchTaskBuilder<S, R> useBatchRequestGenerator(boolean b) {
        this.useBatchRequestGenerator = b;
        return this;
    }

    @PropertySetter
    public BatchTaskBuilder<S, R> taskKeyExpr(IEvalFunction expr) {
        this.taskKeyExpr = expr;
        return this;
    }

    @PropertySetter
    public BatchTaskBuilder<S, R> asyncProcessor(boolean asyncProcessor) {
        this.asyncProcessor = asyncProcessor;
        return this;
    }

    @PropertySetter
    public BatchTaskBuilder<S, R> asyncProcessTimeout(long asyncProcessTimeout) {
        this.asyncProcessTimeout = asyncProcessTimeout;
        return this;
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
    public BatchTaskBuilder<S, R> loadRetryPolicy(IRetryPolicy<IBatchChunkContext> retryPolicy) {
        this.loadRetryPolicy = retryPolicy;
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
    public IBatchTask buildTask() {
        return new BatchTask<S>(taskName, taskVersion == null ? 0 : taskVersion, taskKeyExpr,
                executor, concurrency, taskInitializers, this::buildLoader, this::buildChunkProcessor, stateStore,
                allowStartIfComplete, startLimit);
    }

    protected IBatchLoader<S> buildLoader(IBatchTaskContext context) {
        IBatchLoader<S> loader;
        if (dispatchConfig != null) {
            Executor executor = dispatchConfig.getExecutor();
            if (executor == null)
                executor = GlobalExecutors.cachedThreadPool();

            int fetchThreadCount = dispatchConfig.getFetchThreadCount();
            int loadBatchSize = dispatchConfig.getLoadBatchSize();

            // C8-001（plan 2516 裁定②，←C5-005）：dispatch 容量装配期 fail-fast。buildLoader 为全部 dispatch
            // 路径（AsyncFetch/同步两 provider、DSL 与程序化装配）的必经点，单点覆盖五层装配链；
            // 队列构造器 Guard 与 xdef min 为备选不启用（错误上下文丢失、拦不住 Java API 装配路径）。
            if (loadBatchSize <= 0)
                throw new NopException(ERR_BATCH_DISPATCH_LOAD_BATCH_SIZE_INVALID)
                        .param(ARG_LOAD_BATCH_SIZE, loadBatchSize)
                        .param(ARG_FETCH_THREAD_COUNT, fetchThreadCount);

            // 1.7-C6-008（plan 2545 W-07；closure audit R1 B-1 + R2 BL-1/MJ-4 修正）：**chunk 压实 + 异步 fetch
            // 派发**是不支持的组合。fetch 线程与消费线程在时间/线程上解耦，reader 的 chunk 级压实回调
            // （标记 processingItems 完成、推进 completedIndex）只能附着在 fetch 线程自建的一次性 ctx 上，
            // 而该 ctx 的 chunkItems 恒为 null —— 压实必然空转，completedIndex 永不推进，任务 100% 以
            // ERR_BATCH_PROCESSING_ITEMS_NOT_EMPTY 失败。装配期响亮失败，不给运维留下"跑 60 秒后报一条
            // 指向错误根因的异常"的排障陷阱。
            // <p>
            // 判定轴 = loader 的 **chunk 压实能力**（{@link IBatchLoaderProvider#hasChunkCompaction()}），
            // 不是 task 级 stateStore：saveState 有两条独立轴——任务级（接 stateStore）与
            // loader 级（DSL &lt;loader saveState="true"&gt; → ResourceRecordLoaderProvider.saveState）。
            // 只判 stateStore 会漏掉 loader 级轴（R2 BL-1）；反过来，jdbc/orm 的 task 级 saveState 只有
            // 「任务结束关连接/存计数」语义、无 chunk 压实，本可正常工作，不应被拒（R2 MJ-4）。
            // 同步派发（fetchThreadCount<=0）的 fetcher 闭包捕获消费线程 ctx，压实正常，不受本守卫影响。
            if (fetchThreadCount > 0 && this.loader != null && this.loader.hasChunkCompaction())
                throw new NopException(ERR_BATCH_SAVE_STATE_WITH_DISPATCHER_UNSUPPORTED)
                        .param(ARG_FETCH_THREAD_COUNT, fetchThreadCount);

            if (fetchThreadCount > 0) {
                loader = new AsyncFetchPartitionDispatchLoaderProvider<>(this::buildLoader1, executor, fetchThreadCount, loadBatchSize,
                        dispatchConfig.getPartitionFn()).setup(context);
            } else {
                loader = new PartitionDispatchLoaderProvider<>(this::buildLoader1, loadBatchSize, dispatchConfig.getPartitionFn()).setup(context);
            }
        } else {
            loader = buildLoader0(context);
        }

        if (inputComparator != null)
            loader = new ChunkSortBatchLoader<>(inputComparator, loader);
        return loader;
    }

    private IBatchLoader<S> buildLoader1(IBatchTaskContext context) {
        IBatchLoader<S> loader = this.loader.setup(context);
        if (singleSession && singleSessionInvoker != null)
            loader = new InvokerBatchLoader<>(singleSessionInvoker, loader);
        // dispatch模式下同样要包装loadRetryPolicy，否则该配置会被静默忽略，
        // 与无dispatcher时的行为不一致
        if (loadRetryPolicy != null)
            loader = new RetryBatchLoader<>(loader, loadRetryPolicy);
        return loader;
    }

    protected IBatchLoader<S> buildLoader0(IBatchTaskContext context) {
        IBatchLoader<S> loader = this.loader.setup(context);
        if (loadRetryPolicy != null)
            loader = new RetryBatchLoader<>(loader, loadRetryPolicy);
        return loader;
    }

    @SuppressWarnings("rawtypes")
    protected IBatchChunkProcessorProvider.IBatchChunkProcessor<S> buildChunkProcessor(IBatchLoader<S> loader, IBatchTaskContext context) {
        IBatchConsumer<S> consumer = this.consumer == null ? null : (IBatchConsumer) this.consumer.setup(context);
        if (consumer == null)
            consumer = EmptyBatchConsumer.instance();

        // historyStore的saveProcessed必须与业务consume同事务提交，否则崩溃窗口会导致重启后重复处理。
        // consume scope的Invoker包装在WithHistory内侧（先包装=内层），无法覆盖saveProcessed，
        // 因此有historyStore时自动把consumer链的事务包装位置提升到process级（WithHistory外侧）。
        BatchTransactionScope scope = batchTransactionScope;
        if (scope == BatchTransactionScope.consume && historyStore != null && transactionalInvoker != null) {
            LOG.info("nop.batch.history-txn-promoted:taskName={},auto-promote transactionScope from consume to process for history atomicity",
                    taskName);
            scope = BatchTransactionScope.process;
        }

        if (scope == BatchTransactionScope.consume && transactionalInvoker != null) {
            // 仅在consume阶段打开事务。process可以是纯逻辑处理过程，不涉及到修改数据库，而读数据一般不需要打开事务。
            consumer = new InvokerBatchConsumer<>(transactionalInvoker, consumer);
        }

        if (this.processor != null) {
            // 如果设置了processor,则先执行processor再调用consumer，否则直接调用consumer
            IBatchProcessor<S, R> processor = this.processor.setup(context);
            if (useBatchRequestGenerator) {
                processor = new BatchSequentialProcessor(processor);
            }
            BatchProcessorConsumer<S, R> processorConsumer = new BatchProcessorConsumer<>(processor,
                    (IBatchConsumer<R>) consumer, asyncProcessor, asyncProcessTimeout);
            // 1.7-C5-003（plan 2545 W-05；closure audit B-2 修正）：输出集上界接线——原实现只提供
            // setter 而无生产调用方，导致上界恒为 0（不限制），护栏在生产路径上不可达。
            // 本处是主装配链的接线点；OrmBatchConsumerProvider.setup 自建 BatchProcessorConsumer 为旁路，
            // 已独立接线同一配置键（closure audit R2 MJ-3）。
            processorConsumer.setMaxOutputItemsPerChunk(BatchConfigs.CFG_BATCH_MAX_OUTPUT_ITEMS_PER_CHUNK.get());
            consumer = processorConsumer;
        }

        // 保存处理历史，避免重复处理
        if (historyStore != null)
            consumer = new WithHistoryBatchConsumer<>(historyStore, consumer, historyConsumer == null ? null : historyConsumer.setup(context));

        // 在process（含history自动提升后的consume）阶段打开事务，保证saveProcessed在事务内
        if (scope == BatchTransactionScope.process && transactionalInvoker != null) {
            consumer = new InvokerBatchConsumer<>(transactionalInvoker, consumer);
        }

        // 事务提交后就可以标记记录为完成，调用context.addCompletedItems(items)
        if (batchTransactionScope != BatchTransactionScope.chunk)
            consumer = new AddCompletedBatchConsumer<>(consumer);

        // 限制消费速度
        if (rateLimit > 0)
            consumer = new RateLimitConsumer<>(consumer, new DefaultRateLimiter(rateLimit));

        // singleMode为true，则针对单个记录执行consumer
        if (singleMode)
            consumer = new SingleModeBatchConsumer<>(consumer);

        // 一般情况下事务scope为process或者consume，因此retry是在事务之外执行.
        if (retryPolicy != null) {
            if (retryOneByOne) {
                // skip检查在retry之后执行
                consumer = new RetryOneByOneBatchConsumer<>(consumer, retryPolicy, snapshotBuilder, skipPolicy);
            } else {
                consumer = new RetryAllBatchConsumer<>(consumer, retryPolicy, snapshotBuilder);
            }
        }

        // retry失败后，如果错误记录数在一定范围之内，则可以忽略异常继续处理。retryOneByOne的时候已经执行过skipPolicy这里就不再需要处理
        if (skipPolicy != null && !retryOneByOne) {
            consumer = new SkipBatchConsumer<>(consumer, skipPolicy);
        }

        IBatchChunkProcessorProvider.IBatchChunkProcessor chunkProcessor;
        chunkProcessor = new BatchChunkProcessor<>(loader, batchSize, jitterRatio, consumer);

        if (batchTransactionScope == BatchTransactionScope.chunk && transactionalInvoker != null) {
            // chunk处理的整个过程（包括load/process/consume）都打开事务
            chunkProcessor = new InvokerBatchChunkProcessor<>(transactionalInvoker, chunkProcessor);
        }

        // 整个chunk处理过程共享一个ORM session，因此需要注意抛出异常时可能会导致数据相互影响的问题。
        // 缺省情况下异常导致事务回滚时会自动调用session.clear()
        if (singleSession && singleSessionInvoker != null) {
            chunkProcessor = new InvokerBatchChunkProcessor<>(singleSessionInvoker, chunkProcessor);
        }

        return chunkProcessor;
    }
}