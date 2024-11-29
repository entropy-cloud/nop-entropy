package io.nop.batch.dsl.manager;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.IBeanProvider;
import io.nop.batch.core.BatchTaskBuilder;
import io.nop.batch.core.IBatchAggregator;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchChunkProcessorBuilder;
import io.nop.batch.core.IBatchConsumerProvider;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.batch.core.IBatchMetaProvider;
import io.nop.batch.core.IBatchProcessorProvider;
import io.nop.batch.core.IBatchRecordFilter;
import io.nop.batch.core.IBatchStateStore;
import io.nop.batch.core.IBatchTaskBuilder;
import io.nop.batch.core.consumer.EmptyBatchConsumer;
import io.nop.batch.core.consumer.MultiBatchConsumerProvider;
import io.nop.batch.core.consumer.ResourceRecordConsumerProvider;
import io.nop.batch.core.consumer.SplitBatchConsumer;
import io.nop.batch.core.filter.EvalBatchRecordFilter;
import io.nop.batch.core.processor.FilterBatchProcessor;
import io.nop.batch.core.processor.MultiBatchProcessorProvider;
import io.nop.batch.dsl.model.BatchChunkProcessorBuilderModel;
import io.nop.batch.dsl.model.BatchConsumerModel;
import io.nop.batch.dsl.model.BatchListenersModel;
import io.nop.batch.dsl.model.BatchLoaderModel;
import io.nop.batch.dsl.model.BatchOrmWriterModel;
import io.nop.batch.dsl.model.BatchProcessorModel;
import io.nop.batch.dsl.model.BatchTaggerModel;
import io.nop.batch.dsl.model.BatchTaskModel;
import io.nop.commons.collections.OrderByComparator;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.retry.IRetryPolicy;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.INamedSqlBuilder;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.dao.utils.TransactionalFunctionInvoker;
import io.nop.dataset.record.IRecordSplitter;
import io.nop.dataset.record.IRecordTagger;
import io.nop.dataset.record.support.RecordTagSplitter;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.utils.SingleSessionFunctionInvoker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static io.nop.batch.dsl.BatchDslErrors.ARG_BATCH_TASK_NAME;
import static io.nop.batch.dsl.BatchDslErrors.ARG_PROCESSOR_NAME;
import static io.nop.batch.dsl.BatchDslErrors.ERR_BATCH_TASK_NO_LOADER;
import static io.nop.batch.dsl.BatchDslErrors.ERR_BATCH_TASK_PROCESSOR_IS_NULL;
import static io.nop.batch.dsl.manager.FileBatchSupport.newFileReader;
import static io.nop.batch.dsl.manager.FileBatchSupport.newFileWriter;
import static io.nop.batch.dsl.manager.JdbcBatchSupport.newJdbcReader;
import static io.nop.batch.dsl.manager.JdbcBatchSupport.newJdbcWriter;
import static io.nop.batch.dsl.manager.OrmBatchSupport.newOrmReader;

public class ModelBasedBatchTaskBuilderFactory {
    private final String batchTaskName;
    private final BatchTaskModel batchTaskModel;
    private final ITransactionTemplate transactionTemplate;
    private final IOrmTemplate ormTemplate;

    private final IJdbcTemplate jdbcTemplate;
    private final IDaoProvider daoProvider;
    private final INamedSqlBuilder sqlLibManager;
    private final IBatchStateStore stateStore;

    public ModelBasedBatchTaskBuilderFactory(BatchTaskModel batchTaskModel,
                                             IBatchStateStore stateStore,
                                             ITransactionTemplate transactionTemplate,
                                             IOrmTemplate ormTemplate, IJdbcTemplate jdbcTemplate,
                                             IDaoProvider daoProvider, INamedSqlBuilder sqlLibManager) {
        this.batchTaskName = batchTaskModel.getTaskName();
        this.stateStore = stateStore;
        this.batchTaskModel = batchTaskModel;
        this.transactionTemplate = transactionTemplate;
        this.ormTemplate = ormTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.daoProvider = daoProvider;
        this.sqlLibManager = sqlLibManager;
    }

    public IBatchTaskBuilder newTaskBuilder(IBeanProvider beanContainer) {
        BatchTaskBuilder<Object, Object> builder = new BatchTaskBuilder<>();
        builder.taskName(batchTaskModel.getTaskName());
        builder.taskVersion(batchTaskModel.getTaskVersion());
        builder.taskKeyExpr(batchTaskModel.getTaskKeyExpr());
        builder.allowStartIfComplete(batchTaskModel.getAllowStartIfComplete());
        builder.startLimit(batchTaskModel.getStartLimit());

        builder.batchSize(batchTaskModel.getBatchSize());
        if (batchTaskModel.getJitterRatio() != null)
            builder.jitterRatio(batchTaskModel.getJitterRatio());
        if (batchTaskModel.getSingleMode() != null) {
            builder.singleMode(batchTaskModel.getSingleMode());
        }
        if (batchTaskModel.getSingleSession() != null) {
            builder.singleSession(batchTaskModel.getSingleSession());
        }

        if (batchTaskModel.getRateLimit() != null)
            builder.rateLimit(batchTaskModel.getRateLimit());

        if (batchTaskModel.getTransactionScope() != null)
            builder.transactionScope(batchTaskModel.getTransactionScope());
        if (batchTaskModel.getRetryOneByOne() != null)
            builder.retryOneByOne(batchTaskModel.getRetryOneByOne());
        if (batchTaskModel.getConcurrency() > 0)
            builder.concurrency(batchTaskModel.getConcurrency());
        if (batchTaskModel.getExecutor() != null) {
            builder.executor((Executor) beanContainer.getBean(batchTaskModel.getExecutor()));
        }

        if (batchTaskModel.getRetryPolicy() != null) {
            builder.retryPolicy((IRetryPolicy<IBatchChunkContext>) batchTaskModel.getRetryPolicy().buildRetryPolicy());
        }

        if (batchTaskModel.getSkipPolicy() != null) {
            builder.skipPolicy(batchTaskModel.getSkipPolicy().buildSkipPolicy());
        }

        if (batchTaskModel.getInputSorter() != null) {
            builder.inputComparator(new OrderByComparator<>(batchTaskModel.getInputSorter(),
                    BeanTool::getComplexProperty));
        }

        if (transactionTemplate != null)
            builder.transactionalInvoker(new TransactionalFunctionInvoker(transactionTemplate));

        if (ormTemplate != null) {
            builder.singleSessionInvoker(new SingleSessionFunctionInvoker(ormTemplate));
        }

        if (Boolean.TRUE.equals(batchTaskModel.getSaveState()))
            builder.stateStore(stateStore);

        if (batchTaskModel.getLoadRetryPolicy() != null) {
            builder.loadRetryPolicy((IRetryPolicy<IBatchChunkContext>) this.batchTaskModel.getLoadRetryPolicy().buildRetryPolicy());
        }

        buildTask(builder, beanContainer);

        return builder;
    }

    private void buildTask(BatchTaskBuilder<Object, Object> builder, IBeanProvider beanContainer) {
        IBatchLoaderProvider<Object> loader = buildLoader(builder, beanContainer);
        if (loader == null)
            throw new NopException(ERR_BATCH_TASK_NO_LOADER)
                    .source(batchTaskModel)
                    .param(ARG_BATCH_TASK_NAME, batchTaskName);
        builder.loader(loader);

        if (batchTaskModel.getProcessors() != null) {
            List<IBatchProcessorProvider<?, ?>> list = new ArrayList<>(batchTaskModel.getProcessors().size());

            for (BatchProcessorModel processorModel : batchTaskModel.getProcessors()) {
                IBatchProcessorProvider<Object, Object> processor = buildProcessor(processorModel, builder, beanContainer);
                if (processorModel.getFilter() != null) {
                    list.add(newFilterProcessor(processorModel.getFilter()));
                }
                list.add(processor);
            }
            builder.processor(MultiBatchProcessorProvider.fromList(list));
        }

        IBatchChunkProcessorBuilder<Object> chunkProcessor = buildChunkProcessorBuilder(builder, beanContainer);
        if (chunkProcessor != null)
            builder.chunkProcessorBuilder(chunkProcessor);

        IRecordTagger<Object, IBatchChunkContext> tagger = getTagger(beanContainer);
        IRecordSplitter<Object, Object, IBatchChunkContext> splitter = tagger == null ? null : new RecordTagSplitter<>(tagger);

        if (batchTaskModel.getConsumers().size() == 1) {
            IBatchConsumerProvider<Object> writer = getWriter(batchTaskModel.getConsumers().get(0), beanContainer);
            builder.consumer(writer);
        } else {
            Map<String, List<IBatchConsumerProvider<Object>>> map = new HashMap<>();
            for (BatchConsumerModel consumerModel : batchTaskModel.getConsumers()) {
                IBatchConsumerProvider<Object> writer = getWriter(consumerModel, beanContainer);
                map.computeIfAbsent(consumerModel.getForTag(), k -> new ArrayList<>()).add(writer);
            }

            List<IBatchConsumerProvider<Object>> list = map.remove(null);
            if (map.isEmpty()) {
                if (list != null) {
                    builder.consumer(MultiBatchConsumerProvider.fromList(list));
                }
            } else {
                List<IBatchConsumerProvider<Object>> writers = new ArrayList<>();

                if (splitter != null) {
                    Map<String, IBatchConsumerProvider<Object>> consumerMap = new HashMap<>();
                    map.forEach((name, consumers) -> {
                        IBatchConsumerProvider<Object> writer = MultiBatchConsumerProvider.fromList(consumers);
                        consumerMap.put(name, writer);
                    });

                    SplitBatchConsumer<Object, Object> writer = new SplitBatchConsumer<>(splitter,
                            (tag, ctx) -> consumerMap.get(tag).setup(ctx.getTaskContext()), false);
                    writers.add(writer);
                }
                if (list != null) {
                    writers.add(MultiBatchConsumerProvider.fromList(list));
                }
                builder.consumer(MultiBatchConsumerProvider.fromList(writers));
            }
        }
    }

    private IBatchProcessorProvider<Object, Object> newFilterProcessor(IEvalFunction func) {
        return new FilterBatchProcessor<>(new EvalBatchRecordFilter<>(func));
    }

    @SuppressWarnings("unchecked")
    private IBatchLoaderProvider<Object> buildLoader(BatchTaskBuilder<Object, Object> builder, IBeanProvider beanContainer) {
        if (batchTaskModel.getLoader() == null) {
            return null;
        }

        addListeners(builder, batchTaskModel.getLoader());

        BatchLoaderModel reader = batchTaskModel.getLoader();
        return buildLoader(reader, beanContainer);
    }

    private IBatchLoaderProvider<Object> buildLoader(BatchLoaderModel loaderModel, IBeanProvider beanProvider) {
        IBatchLoaderProvider<Object> provider = buildLoader0(loaderModel, beanProvider);
        if (loaderModel.getAdapter() == null || provider == null)
            return provider;

        return context -> {
            IBatchLoaderProvider.IBatchLoader<Object> loader = provider.setup(context);
            return (IBatchLoaderProvider.IBatchLoader<Object>) loaderModel.getAdapter().call1(null, loader, context.getEvalScope());
        };
    }


    @SuppressWarnings("unchecked")
    private IBatchLoaderProvider<Object> buildLoader0(BatchLoaderModel loaderModel, IBeanProvider beanProvider) {
        if (loaderModel.getBean() != null) {
            return (IBatchLoaderProvider<Object>) beanProvider.getBean(loaderModel.getBean());
        }

        IBatchAggregator<Object, Object, Map<String, Object>> aggregator = loadAggregator(loaderModel.getAggregator(), beanProvider);
        boolean saveState = Boolean.TRUE.equals(loaderModel.getSaveState());

        if (loaderModel.getFileReader() != null) {
            return newFileReader(loaderModel.getFileReader(), beanProvider, saveState, aggregator);
        } else if (loaderModel.getJdbcReader() != null) {
            return newJdbcReader(loaderModel.getJdbcReader(), beanProvider, jdbcTemplate, sqlLibManager);
        } else if (loaderModel.getOrmReader() != null) {
            return newOrmReader(loaderModel.getOrmReader(), daoProvider);
        } else if (loaderModel.getSource() != null) {
            return context -> (batchSize, ctx) -> (List<Object>) loaderModel.getSource().call2(null,
                    batchSize, ctx, ctx.getEvalScope());
        } else {
            return null;
        }
    }

    private IBatchAggregator<Object, Object, Map<String, Object>> loadAggregator(String beanName, IBeanProvider beanContainer) {
        if (beanName == null)
            return null;
        return (IBatchAggregator) beanContainer.getBean(beanName);
    }


    @SuppressWarnings("unchecked")
    private IBatchProcessorProvider<Object, Object> buildProcessor(BatchProcessorModel processorModel,
                                                                   BatchTaskBuilder<Object, Object> builder, IBeanProvider beanContainer) {
        addListeners(builder, processorModel);

        IBatchProcessorProvider<Object, Object> provider = buildProcessor0(processorModel, beanContainer);
        if (provider == null)
            throw new NopException(ERR_BATCH_TASK_PROCESSOR_IS_NULL).param(ARG_PROCESSOR_NAME, processorModel.getName());

        if (processorModel.getAdapter() == null)
            return provider;
        return context -> {
            IBatchProcessorProvider.IBatchProcessor<Object, Object> processor = provider.setup(context);
            return (IBatchProcessorProvider.IBatchProcessor<Object, Object>) processorModel.getAdapter().call1(null, processor, context.getEvalScope());
        };
    }

    private IBatchProcessorProvider<Object, Object> buildProcessor0(BatchProcessorModel processorModel, IBeanProvider beanContainer) {
        if (processorModel.getBean() != null)
            return (IBatchProcessorProvider) beanContainer.getBean(processorModel.getBean());

        if (processorModel.getSource() != null) {
            return context -> (item, consumer, ctx) -> {
                processorModel.getSource().call3(null, item, consumer, ctx, ctx.getEvalScope());
            };
        } else {
            return null;
        }
    }

    private IBatchChunkProcessorBuilder<Object> buildChunkProcessorBuilder(BatchTaskBuilder<Object, Object> builder, IBeanProvider beanContainer) {
        if (batchTaskModel.getChunkProcessorBuilder() == null)
            return null;

        addListeners(builder, batchTaskModel.getChunkProcessorBuilder());

        BatchChunkProcessorBuilderModel processorModel = batchTaskModel.getChunkProcessorBuilder();
        if (processorModel.getBean() != null)
            return (IBatchChunkProcessorBuilder<Object>) beanContainer.getBean(processorModel.getBean());

        return null;
    }

    private void addListeners(BatchTaskBuilder<Object, Object> builder, BatchListenersModel listenersModel) {
        if (listenersModel.getOnTaskBegin() != null)
            builder.addTaskInitializer(context -> {
                context.onTaskBegin(() -> {
                    listenersModel.getOnTaskBegin().call1(null, context, context.getEvalScope());
                });
            });

        if (listenersModel.getOnTaskEnd() != null)
            builder.addTaskInitializer(context -> {
                context.onAfterComplete(err -> {
                    listenersModel.getOnTaskEnd().call2(null, context, err, context.getEvalScope());
                });
            });

        if (listenersModel.getOnChunkBegin() != null) {
            builder.addTaskInitializer(context -> {
                context.onChunkBegin(ctx -> {
                    listenersModel.getOnChunkBegin().call1(null, ctx, ctx.getEvalScope());
                });
            });
        }

        if (listenersModel.getOnChunkEnd() != null) {
            builder.addTaskInitializer(context -> {
                context.onChunkEnd((ctx, err) -> {
                    listenersModel.getOnChunkEnd().call2(null, ctx, err, ctx.getEvalScope());
                });
            });
        }
    }

    private IRecordTagger<Object, IBatchChunkContext> getTagger(IBeanProvider beanContainer) {
        if (batchTaskModel.getTagger() == null)
            return null;

        BatchTaggerModel taggerModel = batchTaskModel.getTagger();
        if (taggerModel.getBean() != null)
            return (IRecordTagger) beanContainer.getBean(taggerModel.getBean());

        if (taggerModel.getSource() != null)
            return (record, ctx) ->
                    CollectionHelper.toCollection(taggerModel.getSource().call2(null,
                            record, ctx, ctx.getEvalScope()), true);
        return null;
    }

    private IBatchConsumerProvider<Object> getWriter(BatchConsumerModel consumerModel, IBeanProvider beanContainer) {
        IBatchConsumerProvider<Object> provider = getWriter0(consumerModel, beanContainer);
        if (provider == null)
            throw new IllegalArgumentException("nop.err.batch.null-writer:" + consumerModel.getLocation());

        if (consumerModel.getAdapter() == null)
            return provider;
        return context -> {
            IBatchConsumerProvider.IBatchConsumer<Object> writer = provider.setup(context);
            return (IBatchConsumerProvider.IBatchConsumer<Object>) consumerModel.getAdapter().call1(null, writer, context.getEvalScope());
        };
    }

    private IBatchConsumerProvider<Object> getWriter0(BatchConsumerModel consumerModel, IBeanProvider beanContainer) {
        IBatchAggregator<Object, Object, Map<String, Object>> aggregator = loadAggregator(consumerModel.getAggregator(), beanContainer);
        IBatchMetaProvider metaProvider = loadMetaProvider(consumerModel.getMetaProvider(), beanContainer);

        IBatchConsumerProvider<Object> ret;
        if (consumerModel.getFileWriter() != null) {
            ResourceRecordConsumerProvider<Object> writer = newFileWriter(consumerModel.getFileWriter(), beanContainer);
            writer.setAggregator(aggregator);
            writer.setMetaProvider(metaProvider);
            ret = writer;
        } else if (consumerModel.getOrmWriter() != null) {
            ret = newOrmWriter(consumerModel.getOrmWriter(), beanContainer);
        } else if (consumerModel.getJdbcWriter() != null) {
            ret = newJdbcWriter(consumerModel.getJdbcWriter(), jdbcTemplate);
        } else {
            ret = null;
        }
        return addFilterForWriter(consumerModel, ret);
    }

    private IBatchConsumerProvider<Object> addFilterForWriter(BatchConsumerModel consumerModel, IBatchConsumerProvider<Object> consumer) {
        if (consumerModel.getFilter() == null)
            return consumer;

        if (consumer == null)
            consumer = EmptyBatchConsumer.instance();

        IBatchRecordFilter<Object, IBatchChunkContext> filter = new EvalBatchRecordFilter<>(consumerModel.getFilter());
        return consumer.withFilter(filter);
    }

    private IBatchMetaProvider loadMetaProvider(String beanName, IBeanProvider beanContainer) {
        if (beanName == null)
            return null;
        return (IBatchMetaProvider) beanContainer.getBean(beanName);
    }


    private IBatchConsumerProvider<Object> newOrmWriter(BatchOrmWriterModel consumerModel, IBeanProvider beanContainer) {
        return null;
    }
}
