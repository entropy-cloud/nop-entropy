package io.nop.batch.dsl.manager;

import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.IBeanProvider;
import io.nop.batch.core.*;
import io.nop.batch.core.consumer.EmptyBatchConsumer;
import io.nop.batch.core.consumer.MultiBatchConsumerProvider;
import io.nop.batch.core.consumer.ResourceRecordConsumerProvider;
import io.nop.batch.core.consumer.SplitBatchConsumer;
import io.nop.batch.core.filter.EvalBatchRecordFilter;
import io.nop.batch.core.loader.ResourceRecordLoaderProvider;
import io.nop.batch.core.processor.FilterBatchProcessor;
import io.nop.batch.core.processor.MultiBatchProcessorProvider;
import io.nop.batch.dsl.model.*;
import io.nop.batch.orm.loader.OrmQueryBatchLoaderProvider;
import io.nop.commons.collections.OrderByComparator;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.retry.IRetryPolicy;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.xml.IXNodeGenerator;
import io.nop.core.lang.xml.XNode;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.resource.IResourceLoader;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.record.IResourceRecordIO;
import io.nop.core.resource.record.csv.CsvResourceRecordIO;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IQueryBuilder;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.dao.utils.TransactionalFunctionInvoker;
import io.nop.dataset.record.IRecordSplitter;
import io.nop.dataset.record.IRecordTagger;
import io.nop.dataset.record.support.RecordTagSplitter;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.utils.SingleSessionFunctionInvoker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static io.nop.batch.dsl.BatchDslErrors.ARG_BATCH_TASK_NAME;
import static io.nop.batch.dsl.BatchDslErrors.ERR_BATCH_TASK_NO_LOADER;

public class ModelBasedBatchTaskFactory {
    private final String batchTaskName;
    private final BatchTaskModel batchTaskModel;
    private final ITransactionTemplate transactionTemplate;
    private final IOrmTemplate ormTemplate;

    private final IJdbcTemplate jdbcTemplate;
    private final IDaoProvider daoProvider;
    private final IBatchStateStore stateStore;

    public ModelBasedBatchTaskFactory(String batchTaskName, BatchTaskModel batchTaskModel,
                                      IBatchStateStore stateStore,
                                      ITransactionTemplate transactionTemplate,
                                      IOrmTemplate ormTemplate, IJdbcTemplate jdbcTemplate,
                                      IDaoProvider daoProvider) {
        this.batchTaskName = batchTaskName;
        this.stateStore = stateStore;
        this.batchTaskModel = batchTaskModel;
        this.transactionTemplate = transactionTemplate;
        this.ormTemplate = ormTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.daoProvider = daoProvider;
    }

    public IBatchTaskBuilder newTaskBuilder(IBeanProvider beanContainer) {
        BatchTaskBuilder<Object, Object> builder = new BatchTaskBuilder<>();
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

        builder.stateStore(stateStore);

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

        if (batchTaskModel.getWriters().size() == 1) {
            IBatchConsumerProvider<Object> writer = getWriter(batchTaskModel.getWriters().get(0), beanContainer);
            builder.consumer(writer);
        } else {
            Map<String, List<IBatchConsumerProvider<Object>>> map = new HashMap<>();
            for (BatchWriterModel writerModel : batchTaskModel.getWriters()) {
                IBatchConsumerProvider<Object> writer = getWriter(writerModel, beanContainer);
                map.computeIfAbsent(writerModel.getForTag(), k -> new ArrayList<>()).add(writer);
            }

            List<IBatchConsumerProvider<Object>> list = map.remove(null);
            if (map.isEmpty()) {
                if (list != null) {
                    builder.consumer(buildWriter(list));
                }
            } else {
                List<IBatchConsumerProvider<Object>> writers = new ArrayList<>();

                if (splitter != null) {
                    Map<String, IBatchConsumerProvider<Object>> consumerMap = new HashMap<>();
                    map.forEach((name, consumers) -> {
                        IBatchConsumerProvider<Object> writer = buildWriter(consumers);
                        consumerMap.put(name, writer);
                    });

                    SplitBatchConsumer<Object, Object> writer = new SplitBatchConsumer<>(splitter,
                            (tag, ctx) -> consumerMap.get(tag).setup(ctx.getTaskContext()), false);
                    writers.add(writer);
                }
                if (list != null) {
                    writers.add(buildWriter(list));
                }
                builder.consumer(MultiBatchConsumerProvider.fromList(writers));
            }
        }
    }

    private IBatchProcessorProvider<Object, Object> newFilterProcessor(IEvalFunction func) {
        return new FilterBatchProcessor<>(new EvalBatchRecordFilter<>(func));
    }

    private IBatchConsumerProvider<Object> buildWriter(List<IBatchConsumerProvider<Object>> list) {
        if (list == null || list.isEmpty())
            return null;
        if (list.size() == 1)
            return list.get(0);
        return new MultiBatchConsumerProvider<>(list);
    }

    @SuppressWarnings("unchecked")
    private IBatchLoaderProvider<Object> buildLoader(BatchTaskBuilder<Object, Object> builder, IBeanProvider beanContainer) {
        if (batchTaskModel.getReader() == null) {
            return null;
        }

        addListeners(builder, batchTaskModel.getReader());

        BatchReaderModel reader = batchTaskModel.getReader();
        if (reader.getBean() != null) {
            return (IBatchLoaderProvider<Object>) beanContainer.getBean(reader.getBean());
        }

        return buildReader(reader, beanContainer);
    }

    @SuppressWarnings("unchecked")
    private IBatchLoaderProvider<Object> buildReader(BatchReaderModel readerModel, IBeanProvider beanProvider) {
        IBatchAggregator<Object, Object, Map<String, Object>> aggregator = loadAggregator(readerModel.getAggregator(), beanProvider);

        if (readerModel.getFileReader() != null) {
            return buildFileReader(readerModel.getFileReader(), beanProvider, aggregator);
        } else if (readerModel.getJdbcReader() != null) {
            return buildJdbcReader(readerModel.getJdbcReader());
        } else if (readerModel.getOrmReader() != null) {
            return buildOrmReader(readerModel.getOrmReader());
        } else if (readerModel.getSource() != null) {
            return context -> (batchSize, ctx) -> (List<Object>) readerModel.getSource().call2(null,
                    batchSize, ctx, ctx.getEvalScope());
        } else {
            return null;
        }
    }

    private IBatchAggregator<Object, Object, Map<String, Object>> loadAggregator(String beanName, IBeanProvider beanContainer) {
        if (beanName != null)
            return null;
        return (IBatchAggregator) beanContainer.getBean(beanName);
    }

    private IBatchLoaderProvider<Object> buildOrmReader(BatchOrmReaderModel readerModel) {
        IXNodeGenerator query = readerModel.getQuery();
        List<String> batchLoadProps = readerModel.getBatchLoadProps();

        OrmQueryBatchLoaderProvider<IOrmEntity> loader = new OrmQueryBatchLoaderProvider<>();
        loader.setBatchLoadProps(batchLoadProps);
        loader.setDaoProvider(daoProvider);
        if (query != null)
            loader.setQueryBuilder(newQueryBuilder(query));
        //loader.setSqlGenerator(readerModel.getEql());

        return (IBatchLoaderProvider) loader;
    }

    private IQueryBuilder newQueryBuilder(IXNodeGenerator generator) {
        return context -> {
            XNode node = generator.generateNode(context);
            return BeanTool.buildBeanFromTreeBean(node, QueryBean.class);
        };
    }

    private IBatchLoaderProvider<Object> buildJdbcReader(BatchJdbcReaderModel readerModel) {
        return null;
    }

    private IBatchLoaderProvider<Object> buildFileReader(BatchFileReaderModel readerModel,
                                                         IBeanProvider beanContainer,
                                                         IBatchAggregator<Object, Object, Map<String, Object>> aggregator) {
        IResourceRecordIO<Object> recordIO = loadRecordIO(readerModel.getResourceIO(), beanContainer);
        IResourceLoader resourceLoader = loadResourceLoader(readerModel.getResourceLoader(), beanContainer);

        ResourceRecordLoaderProvider<Object> loader = new ResourceRecordLoaderProvider<>();
        loader.setName("reader");
        loader.setRecordIO(recordIO);
        loader.setResourceLoader(resourceLoader);
        if (readerModel.getMaxCount() != null)
            loader.setMaxCount(readerModel.getMaxCount());
        loader.setPathExpr(readerModel.getPathExpr());
        loader.setEncoding(readerModel.getEncoding());
        loader.setAggregator(aggregator);

        return loader;
    }

    private IResourceRecordIO<Object> loadRecordIO(String beanName, IBeanProvider beanContainer) {
        if (beanName != null)
            return (IResourceRecordIO<Object>) beanContainer.getBean(beanName);
        return new CsvResourceRecordIO<>();
    }

    private IResourceLoader loadResourceLoader(String loaderBean, IBeanProvider beanContainer) {
        if (loaderBean != null)
            return (IResourceLoader) beanContainer.getBean(loaderBean);
        return VirtualFileSystem.instance();
    }

    @SuppressWarnings("unchecked")
    private IBatchProcessorProvider<Object, Object> buildProcessor(BatchProcessorModel processorModel,
                                                                   BatchTaskBuilder<Object, Object> builder, IBeanProvider beanContainer) {
        addListeners(builder, processorModel);

        if (processorModel.getBean() != null)
            return (IBatchProcessorProvider) beanContainer.getBean(processorModel.getBean());

        return context -> (item, consumer, ctx) -> {
            processorModel.getSource().call3(null, item, consumer, ctx, ctx.getEvalScope());
        };
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
                context.addAfterComplete(err -> {
                    listenersModel.getOnTaskEnd().call2(null, err, context, context.getEvalScope());
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
                context.onChunkEnd((err, ctx) -> {
                    listenersModel.getOnChunkEnd().call2(null, err, ctx, ctx.getEvalScope());
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

    private IBatchConsumerProvider<Object> getWriter(BatchWriterModel writerModel, IBeanProvider beanContainer) {
        IBatchAggregator<Object, Object, Map<String, Object>> aggregator = loadAggregator(writerModel.getAggregator(), beanContainer);
        IBatchMetaProvider metaProvider = loadMetaProvider(writerModel.getMetaProvider(), beanContainer);

        IBatchConsumerProvider<Object> ret;
        if (writerModel.getFileWriter() != null) {
            ResourceRecordConsumerProvider<Object> writer = newFileWriter(writerModel.getFileWriter(), beanContainer);
            writer.setName(writerModel.getName());
            writer.setAggregator(aggregator);
            writer.setMetaProvider(metaProvider);
            ret = writer;
        } else {
            ret = null;
        }
        return addFilterForWriter(writerModel, ret);
    }

    private IBatchConsumerProvider<Object> addFilterForWriter(BatchWriterModel writerModel, IBatchConsumerProvider<Object> consumer) {
        if (writerModel.getFilter() == null)
            return consumer;

        if (consumer == null)
            consumer = EmptyBatchConsumer.instance();

        IBatchRecordFilter<Object> filter = new EvalBatchRecordFilter<>(writerModel.getFilter());
        return consumer.withFilter(filter);
    }

    private IBatchMetaProvider loadMetaProvider(String beanName, IBeanProvider beanContainer) {
        if (beanName == null)
            return null;
        return (IBatchMetaProvider) beanContainer.getBean(beanName);
    }

    private ResourceRecordConsumerProvider<Object> newFileWriter(BatchFileWriterModel writerModel,
                                                                 IBeanProvider beanContainer) {
        IResourceRecordIO<Object> recordIO = loadRecordIO(writerModel.getResourceIO(), beanContainer);
        IResourceLoader resourceLoader = loadResourceLoader(writerModel.getResourceLoader(), beanContainer);

        ResourceRecordConsumerProvider<Object> writer = new ResourceRecordConsumerProvider<>();
        writer.setEncoding(writerModel.getEncoding());
        writer.setPathExpr(writerModel.getPathExpr());
        writer.setRecordIO(recordIO);
        writer.setResourceLoader(resourceLoader);
        return writer;
    }
}
