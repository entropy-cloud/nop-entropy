package io.nop.batch.dsl.manager;

import io.nop.api.core.ioc.IBeanProvider;
import io.nop.batch.core.IBatchAggregator;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.batch.core.IBatchRecordFilter;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.consumer.ResourceRecordConsumerProvider;
import io.nop.batch.core.filter.EvalBatchRecordFilter;
import io.nop.batch.core.loader.ListBatchLoader;
import io.nop.batch.core.loader.ResourceRecordLoaderProvider;
import io.nop.batch.dsl.BatchDslConstants;
import io.nop.batch.dsl.model.BatchExcelReaderModel;
import io.nop.batch.dsl.model.BatchExcelWriterModel;
import io.nop.batch.dsl.model.BatchFileReaderModel;
import io.nop.batch.dsl.model.BatchFileWriterModel;
import io.nop.batch.dsl.model.IBatchExcelIOModel;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.IResourceLoader;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.record.IResourceRecordInputProvider;
import io.nop.core.resource.record.IResourceRecordOutputProvider;
import io.nop.core.resource.record.csv.CsvResourceRecordIO;
import io.nop.record.resource.ModelBasedResourceRecordIO;
import io.nop.report.core.record.ExcelIOConfig;
import io.nop.report.core.record.ExcelResourceIO;
import io.nop.xlang.api.XLang;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class FileBatchSupport {


    public static IBatchLoaderProvider<Object> newFileReader(BatchFileReaderModel loaderModel,
                                                             IBeanProvider beanContainer, boolean saveState,
                                                             IBatchAggregator<Object, Object, Map<String, Object>> aggregator) {
        IResourceRecordInputProvider<Object> recordIO = newRecordInputProvider(loaderModel, beanContainer);
        IResourceLoader resourceLoader = loadResourceLoader(loaderModel.getResourceLoader(), beanContainer);

        ResourceRecordLoaderProvider<Object> loader = new ResourceRecordLoaderProvider<>();
        loader.setRecordIO(recordIO);
        loader.setResourceLoader(resourceLoader);
        loader.setSaveState(Boolean.TRUE.equals(saveState));

        if (loaderModel.getMaxCountExpr() != null)
            loader.setMaxCountExpr(loaderModel.getMaxCountExpr());
        loader.setPathExpr(loaderModel.getFilePath());
        loader.setEncodingExpr(loaderModel.getEncoding());
        loader.setAggregator(aggregator);
        if (loaderModel.getFilter() != null) {
            IBatchRecordFilter<Object, IBatchTaskContext> filter = new EvalBatchRecordFilter<>(loaderModel.getFilter());
            loader.setFilter(filter);
        }

        return loader;
    }

    private static IResourceRecordInputProvider<Object> newRecordInputProvider(BatchFileReaderModel readerModel, IBeanProvider beanContainer) {
        String beanName = readerModel.getResourceIO();
        if (beanName != null)
            return (IResourceRecordInputProvider<Object>) beanContainer.getBean(beanName);

        if (readerModel.getNewRecordInputProvider() != null) {
            IEvalScope scope = XLang.newEvalScope();
            scope.setLocalValue(BatchDslConstants.VAR_READER_MODEL, readerModel);

            return (IResourceRecordInputProvider<Object>) readerModel.getNewRecordInputProvider().invoke(scope);
        }

        if (readerModel.getFileModelPath() != null) {
            ModelBasedResourceRecordIO<Object> io = new ModelBasedResourceRecordIO<>();
            io.setModelFilePath(readerModel.getFileModelPath());
            return io;
        }

        CsvResourceRecordIO<Object> io = new CsvResourceRecordIO<>();
        io.setHeaders(readerModel.getHeaders());
        io.setHeaderLabels(readerModel.getHeaderLabels());
        if (readerModel.getCsvFormat() != null) {
            io.setFormat(readerModel.getCsvFormat());
        }
        if (readerModel.getHeadersNormalizer() != null) {
            io.setHeadersNormalizer(headers -> (List<String>) readerModel.getHeadersNormalizer().call1(null, headers, XLang.newEvalScope()));
        }
        return io;
    }


    public static ResourceRecordConsumerProvider<Object> newFileWriter(BatchFileWriterModel consumerModel,
                                                                       IBeanProvider beanContainer) {
        IResourceRecordOutputProvider<Object> recordIO = newRecordOutputProvider(consumerModel, beanContainer);
        IResourceLoader resourceLoader = loadResourceLoader(consumerModel.getResourceLoader(), beanContainer);

        ResourceRecordConsumerProvider<Object> writer = new ResourceRecordConsumerProvider<>();
        writer.setEncodingExpr(consumerModel.getEncoding());
        writer.setPathExpr(consumerModel.getFilePath());
        writer.setRecordIO(recordIO);
        writer.setResourceLoader(resourceLoader);
        return writer;
    }

    private static IResourceLoader loadResourceLoader(String loaderBean, IBeanProvider beanContainer) {
        if (loaderBean != null)
            return (IResourceLoader) beanContainer.getBean(loaderBean);
        return VirtualFileSystem.instance();
    }

    private static IResourceRecordOutputProvider<Object> newRecordOutputProvider(BatchFileWriterModel writerModel, IBeanProvider beanContainer) {
        String beanName = writerModel.getResourceIO();
        if (beanName != null)
            return (IResourceRecordOutputProvider<Object>) beanContainer.getBean(beanName);

        if (writerModel.getNewRecordOutputProvider() != null) {
            IEvalScope scope = XLang.newEvalScope();
            scope.setLocalValue(BatchDslConstants.VAR_WRITER_MODEL, writerModel);
            return (IResourceRecordOutputProvider<Object>) writerModel.getNewRecordOutputProvider().invoke(scope);
        }

        if (writerModel.getFileModelPath() != null) {
            ModelBasedResourceRecordIO<Object> io = new ModelBasedResourceRecordIO<>();
            io.setModelFilePath(writerModel.getFileModelPath());
            return io;
        }

        CsvResourceRecordIO<Object> io = new CsvResourceRecordIO<>();
        io.setHeaders(writerModel.getHeaders());
        io.setHeaderLabels(writerModel.getHeaderLabels());
        if (writerModel.getCsvFormat() != null) {
            io.setFormat(writerModel.getCsvFormat());
        }
        return io;
    }

    public static ResourceRecordLoaderProvider<Object> newExcelReader(BatchExcelReaderModel loaderModel,
                                                                      IBeanProvider beanContainer, boolean saveState,
                                                                      IBatchAggregator<Object, Object, Map<String, Object>> aggregator) {
        IResourceRecordInputProvider<Object> recordIO = newExcelIO(loaderModel);

        ResourceRecordLoaderProvider<Object> loader = new ResourceRecordLoaderProvider<>();
        loader.setRecordIO(recordIO);
        loader.setResourceLoader(VirtualFileSystem.instance());
        loader.setSaveState(Boolean.TRUE.equals(saveState));

//        if (loaderModel.getMaxCountExpr() != null)
//            loader.setMaxCountExpr(loaderModel.getMaxCountExpr());
        loader.setPathExpr(loaderModel.getFilePath());
        loader.setAggregator(aggregator);
        if (loaderModel.getFilter() != null) {
            IBatchRecordFilter<Object, IBatchTaskContext> filter = new EvalBatchRecordFilter<>(loaderModel.getFilter());
            loader.setFilter(filter);
        }

        return loader;
    }

    public static ResourceRecordConsumerProvider<Object> newExcelWriter(BatchExcelWriterModel consumerModel,
                                                                        IBeanProvider beanContainer) {
        IResourceRecordOutputProvider<Object> recordIO = newExcelIO(consumerModel);
        //IResourceLoader resourceLoader = loadResourceLoader(consumerModel.getResourceLoader(), beanContainer);

        ResourceRecordConsumerProvider<Object> writer = new ResourceRecordConsumerProvider<>();
        writer.setPathExpr(consumerModel.getFilePath());
        writer.setRecordIO(recordIO);
        //writer.setResourceLoader(resourceLoader);
        return writer;
    }

    private static ExcelResourceIO<Object> newExcelIO(IBatchExcelIOModel ioModel) {
        ExcelResourceIO<Object> io = new ExcelResourceIO<>();
        ExcelIOConfig config = new ExcelIOConfig();
        config.setDataSheetName(ioModel.getDataSheetName());
        config.setHeaderSheetName(ioModel.getHeaderSheetName());
        config.setTrailerSheetName(ioModel.getTrailerSheetName());
        config.setTemplatePath(ioModel.getTemplatePath());
        if (ioModel.getHeaderRowCount() != null) {
            config.setHeaderRowCount(ioModel.getHeaderRowCount());
        }

        io.setIOConfig(config);
        io.setHeaders(ioModel.getHeaders());
        io.setHeaderLabels(ioModel.getHeaderLabels());
        return io;
    }

    static final String KEY_LOADER = "batchLoader";

    public static <T> List<T> batchLoad(int batchSize, IBatchChunkContext chunkCtx, IBatchLoaderProvider<T> provider) {
        IBatchTaskContext taskCtx = chunkCtx.getTaskContext();
        synchronized (chunkCtx) {
            IBatchLoaderProvider.IBatchLoader loader = (IBatchLoaderProvider.IBatchLoader) taskCtx.getAttribute(KEY_LOADER);
            if (loader == null) {
                loader = provider.setup(taskCtx);
                taskCtx.setAttribute(KEY_LOADER, loader);
            }
            return loader.load(batchSize, chunkCtx);
        }
    }

    public static <T> List<T> batchLoadList(int batchSize, IBatchChunkContext chunkCtx,
                                            Function<IBatchTaskContext, List<T>> listProvider) {
        return batchLoad(batchSize, chunkCtx, taskCtx -> {
            List<T> list = listProvider.apply(taskCtx);
            if (list == null)
                list = Collections.emptyList();
            return new ListBatchLoader<>(list);
        });
    }
}