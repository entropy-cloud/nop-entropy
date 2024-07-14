package io.nop.batch.dsl.manager;

import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.IBeanProvider;
import io.nop.api.core.util.ProcessResult;
import io.nop.batch.core.BatchTaskBuilder;
import io.nop.batch.core.IBatchAggregator;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchChunkProcessor;
import io.nop.batch.core.IBatchConsumer;
import io.nop.batch.core.IBatchLoader;
import io.nop.batch.core.IBatchMetaProvider;
import io.nop.batch.core.IBatchProcessor;
import io.nop.batch.core.IBatchStateStore;
import io.nop.batch.core.IBatchTask;
import io.nop.batch.core.consumer.MultiBatchConsumer;
import io.nop.batch.core.consumer.ResourceRecordConsumer;
import io.nop.batch.core.consumer.SplitBatchConsumer;
import io.nop.batch.core.listener.EvalBatchChunkListener;
import io.nop.batch.core.listener.EvalBatchConsumeListener;
import io.nop.batch.core.listener.EvalBatchLoadListener;
import io.nop.batch.core.listener.EvalBatchTaskListener;
import io.nop.batch.core.loader.ResourceRecordLoader;
import io.nop.batch.dsl.model.BatchChunkProcessorModel;
import io.nop.batch.dsl.model.BatchFileReaderModel;
import io.nop.batch.dsl.model.BatchFileWriterModel;
import io.nop.batch.dsl.model.BatchJdbcReaderModel;
import io.nop.batch.dsl.model.BatchListenersModel;
import io.nop.batch.dsl.model.BatchOrmReaderModel;
import io.nop.batch.dsl.model.BatchProcessorModel;
import io.nop.batch.dsl.model.BatchReaderModel;
import io.nop.batch.dsl.model.BatchTaggerModel;
import io.nop.batch.dsl.model.BatchTaskModel;
import io.nop.batch.dsl.model.BatchWriterModel;
import io.nop.batch.orm.loader.OrmQueryBatchLoader;
import io.nop.commons.collections.OrderByComparator;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.resource.IResourceLoader;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.record.IResourceRecordIO;
import io.nop.core.resource.record.csv.CsvResourceRecordIO;
import io.nop.dao.api.IDaoProvider;
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

public class ModelBasedBatchTaskFactory implements IBatchTaskFactory {
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

    @Override
    public IBatchTask newTask(IBeanProvider beanContainer) {
        BatchTaskBuilder builder = new BatchTaskBuilder();
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
            builder.retryPolicy(batchTaskModel.getRetryPolicy().buildRetryPolicy());
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

        return builder.build();
    }

    private void buildTask(BatchTaskBuilder builder, IBeanProvider beanContainer) {
        IBatchLoader<?, ? extends IEvalContext> loader = buildLoader(builder, beanContainer);
        if (loader == null)
            throw new NopException(ERR_BATCH_TASK_NO_LOADER)
                    .source(batchTaskModel)
                    .param(ARG_BATCH_TASK_NAME, batchTaskName);
        builder.loader(loader);

        if (batchTaskModel.getProcessors() != null) {
            List<IBatchProcessor<?, ?, IBatchChunkContext>> list = new ArrayList<>(batchTaskModel.getProcessors().size());

            for (BatchProcessorModel processorModel : batchTaskModel.getProcessors()) {
                IBatchProcessor<?, ?, IBatchChunkContext> processor = buildProcessor(processorModel, builder, beanContainer);
                list.add(processor);
            }
            builder.processors(list);
        }

        IBatchChunkProcessor chunkProcessor = buildChunkProcessor(builder, beanContainer);
        if (chunkProcessor != null)
            builder.chunkProcessor(chunkProcessor);

        IRecordTagger<Object, IBatchChunkContext> tagger = getTagger(beanContainer);
        IRecordSplitter<?, ?, IBatchChunkContext> splitter = tagger == null ? null : new RecordTagSplitter<Object, IBatchChunkContext>(tagger);

        if (batchTaskModel.getWriters().size() == 1) {
            IBatchConsumer<Object, IBatchChunkContext> writer = getWriter(batchTaskModel.getWriters().get(0), beanContainer);
            builder.consumer(writer);
        } else {
            Map<String, List<IBatchConsumer<Object, IBatchChunkContext>>> map = new HashMap<>();
            for (BatchWriterModel writerModel : batchTaskModel.getWriters()) {
                IBatchConsumer<Object, IBatchChunkContext> writer = getWriter(writerModel, beanContainer);
                map.computeIfAbsent(writerModel.getForTag(), k -> new ArrayList<>()).add(writer);
            }

            List<IBatchConsumer<Object, IBatchChunkContext>> list = map.remove(null);
            if (map.isEmpty()) {
                if (list != null) {
                    builder.consumer(buildWriter(list));
                }
            } else {
                List<IBatchConsumer<?, IBatchChunkContext>> writers = new ArrayList<>();

                if (splitter != null) {
                    Map<String, IBatchConsumer<Object, IBatchChunkContext>> consumerMap = new HashMap<>();
                    map.forEach((name, consumers) -> {
                        IBatchConsumer<Object, IBatchChunkContext> writer = buildWriter(consumers);
                        builder.addListener(writer);
                        consumerMap.put(name, writer);
                    });

                    SplitBatchConsumer writer = new SplitBatchConsumer(splitter, consumerMap::get, false);
                    writers.add(writer);
                }
                if (list != null) {
                    writers.add(buildWriter(list));
                }
                builder.consumers(writers);
            }
        }
    }

    private IBatchConsumer<Object, IBatchChunkContext> buildWriter(List<IBatchConsumer<Object, IBatchChunkContext>> list) {
        if (list == null || list.isEmpty())
            return null;
        if (list.size() == 1)
            return list.get(0);
        return new MultiBatchConsumer<>(list);
    }

    @SuppressWarnings("unchecked")
    private IBatchLoader<?, ? extends IEvalContext> buildLoader(BatchTaskBuilder builder, IBeanProvider beanContainer) {
        if (batchTaskModel.getReader() == null) {
            return null;
        }

        addListeners(builder, batchTaskModel.getReader());

        BatchReaderModel reader = batchTaskModel.getReader();
        if (reader.getBean() != null) {
            return (IBatchLoader<Object, IEvalContext>) beanContainer.getBean(reader.getBean());
        }

        return buildReader(reader, beanContainer);
    }

    @SuppressWarnings("unchecked")
    private IBatchLoader<?, ? extends IEvalContext> buildReader(BatchReaderModel readerModel, IBeanProvider beanProvider) {
        IBatchAggregator aggregator = loadAggregator(readerModel.getAggregator(), beanProvider);

        if (readerModel.getFileReader() != null) {
            return buildFileReader(readerModel.getFileReader(), beanProvider, aggregator);
        } else if (readerModel.getJdbcReader() != null) {
            return buildJdbcReader(readerModel.getJdbcReader());
        } else if (readerModel.getOrmReader() != null) {
            return buildOrmReader(readerModel.getOrmReader());
        } else if (readerModel.getSource() != null) {
            return (batchSize, ctx) -> (List<Object>) readerModel.getSource().call2(null,
                    batchSize, ctx, ctx.getEvalScope());
        } else {
            return null;
        }
    }

    private IBatchAggregator loadAggregator(String beanName, IBeanProvider beanContainer) {
        if (beanName != null)
            return null;
        return (IBatchAggregator) beanContainer.getBean(beanName);
    }

    private IBatchLoader<?, IBatchChunkContext> buildOrmReader(BatchOrmReaderModel readerModel) {
        QueryBean query = readerModel.getQuery();
        List<String> batchLoadProps = readerModel.getBatchLoadProps();

        OrmQueryBatchLoader<IOrmEntity> loader = new OrmQueryBatchLoader<>();
        loader.setBatchLoadProps(batchLoadProps);
        loader.setDaoProvider(daoProvider);
        if (query != null)
            loader.setQuery(query);
        //loader.setSqlGenerator(readerModel.getEql());

        return loader;
    }

    private IBatchLoader<Object, IEvalContext> buildJdbcReader(BatchJdbcReaderModel readerModel) {
        return null;
    }

    private IBatchLoader<Object, IEvalContext> buildFileReader(BatchFileReaderModel readerModel,
                                                               IBeanProvider beanContainer,
                                                               IBatchAggregator aggregator) {
        IResourceRecordIO<Object> recordIO = loadRecordIO(readerModel.getResourceIO(), beanContainer);
        IResourceLoader resourceLoader = loadResourceLoader(readerModel.getResourceLoader(), beanContainer);

        ResourceRecordLoader<Object, IEvalContext> loader = new ResourceRecordLoader<>();
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
    private IBatchProcessor<?, ?, IBatchChunkContext> buildProcessor(BatchProcessorModel processorModel,
                                                                     BatchTaskBuilder builder, IBeanProvider beanContainer) {
        addListeners(builder, processorModel);

        if (processorModel.getBean() != null)
            return (IBatchProcessor<?, ?, IBatchChunkContext>) beanContainer.getBean(processorModel.getBean());

        return (item, consumer, ctx) -> {
            processorModel.getSource().call3(null, item, consumer, ctx, ctx.getEvalScope());
        };
    }

    private IBatchChunkProcessor buildChunkProcessor(BatchTaskBuilder builder, IBeanProvider beanContainer) {
        if (batchTaskModel.getChunkProcessor() == null)
            return null;

        addListeners(builder, batchTaskModel.getChunkProcessor());

        BatchChunkProcessorModel processorModel = batchTaskModel.getChunkProcessor();
        if (processorModel.getBean() != null)
            return (IBatchChunkProcessor) beanContainer.getBean(processorModel.getBean());

        return ctx -> {
            Object value = processorModel.getSource().call1(null, ctx, ctx.getEvalScope());
            return ProcessResult.STOP == value ? ProcessResult.STOP : ProcessResult.CONTINUE;
        };
    }

    private void addListeners(BatchTaskBuilder builder, BatchListenersModel listenersModel) {
        if (listenersModel.getOnTaskBegin() != null || listenersModel.getOnTaskEnd() != null) {
            builder.addListener(new EvalBatchTaskListener(listenersModel.getOnTaskBegin(), listenersModel.getOnTaskEnd()));
        }

        if (listenersModel.getOnLoadBegin() != null || listenersModel.getOnLoadEnd() != null) {
            builder.addListener(new EvalBatchLoadListener<>(listenersModel.getOnLoadBegin(), listenersModel.getOnLoadEnd()));
        }

        if (listenersModel.getOnChunkBegin() != null || listenersModel.getOnChunkEnd() != null) {
            builder.addListener(new EvalBatchChunkListener(listenersModel.getOnChunkBegin(), listenersModel.getOnChunkEnd()));
        }

        if (listenersModel.getOnConsumeBegin() != null || listenersModel.getOnConsumeEnd() != null) {
            builder.addListener(new EvalBatchConsumeListener<>(listenersModel.getOnConsumeBegin(), listenersModel.getOnConsumeEnd()));
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

    private IBatchConsumer<Object, IBatchChunkContext> getWriter(BatchWriterModel writerModel, IBeanProvider beanContainer) {
        IBatchAggregator aggregator = loadAggregator(writerModel.getAggregator(), beanContainer);
        IBatchMetaProvider metaProvider = loadMetaProvider(writerModel.getMetaProvider(), beanContainer);

        if (writerModel.getFileWriter() != null) {
            ResourceRecordConsumer<Object, IBatchChunkContext> writer = newFileWriter(writerModel.getFileWriter(), beanContainer);
            writer.setName(writerModel.getName());
            writer.setAggregator(aggregator);
            writer.setMetaProvider(metaProvider);
            return writer;
        }
        return null;
    }

    private IBatchMetaProvider loadMetaProvider(String beanName, IBeanProvider beanContainer) {
        if (beanName == null)
            return null;
        return (IBatchMetaProvider) beanContainer.getBean(beanName);
    }

    private ResourceRecordConsumer<Object, IBatchChunkContext> newFileWriter(BatchFileWriterModel writerModel,
                                                                             IBeanProvider beanContainer) {
        IResourceRecordIO<Object> recordIO = loadRecordIO(writerModel.getResourceIO(), beanContainer);
        IResourceLoader resourceLoader = loadResourceLoader(writerModel.getResourceLoader(), beanContainer);

        ResourceRecordConsumer<Object, IBatchChunkContext> writer = new ResourceRecordConsumer<>();
        writer.setEncoding(writerModel.getEncoding());
        writer.setPathExpr(writerModel.getPathExpr());
        writer.setRecordIO(recordIO);
        writer.setResourceLoader(resourceLoader);
        return writer;
    }
}
