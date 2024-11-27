package io.nop.batch.dsl.manager;

import io.nop.api.core.ioc.IBeanProvider;
import io.nop.batch.core.IBatchAggregator;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.batch.core.IBatchRecordFilter;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.consumer.ResourceRecordConsumerProvider;
import io.nop.batch.core.filter.EvalBatchRecordFilter;
import io.nop.batch.core.loader.ResourceRecordLoaderProvider;
import io.nop.batch.dsl.model.BatchFileReaderModel;
import io.nop.batch.dsl.model.BatchFileWriterModel;
import io.nop.core.resource.IResourceLoader;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.record.IResourceRecordInputProvider;
import io.nop.core.resource.record.IResourceRecordOutputProvider;
import io.nop.core.resource.record.csv.CsvResourceRecordIO;
import io.nop.record.resource.ModelBasedResourceRecordIO;
import io.nop.xlang.api.XLang;

import java.util.Map;

public class FileBatchSupport {


    public static IBatchLoaderProvider<Object> buildFileReader(BatchFileReaderModel loaderModel,
                                                               IBeanProvider beanContainer, boolean saveState,
                                                               IBatchAggregator<Object, Object, Map<String, Object>> aggregator) {
        IResourceRecordInputProvider<Object> recordIO = newRecordInputProvider(loaderModel, beanContainer);
        IResourceLoader resourceLoader = loadResourceLoader(loaderModel.getResourceLoader(), beanContainer);

        ResourceRecordLoaderProvider<Object> loader = new ResourceRecordLoaderProvider<>();
        loader.setRecordIO(recordIO);
        loader.setResourceLoader(resourceLoader);
        loader.setSaveState(Boolean.TRUE.equals(saveState));

        if (loaderModel.getMaxCount() != null)
            loader.setMaxCount(loaderModel.getMaxCount());
        loader.setPathExpr(loaderModel.getFilePath());
        loader.setEncoding(loaderModel.getEncoding());
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

        if (readerModel.getNewRecordInputProvider() != null)
            return (IResourceRecordInputProvider<Object>) readerModel.getNewRecordInputProvider().invoke(XLang.newEvalScope());

        if (readerModel.getFileModelPath() != null) {
            ModelBasedResourceRecordIO<Object> io = new ModelBasedResourceRecordIO<>();
            io.setModelFilePath(readerModel.getFileModelPath());
            return io;
        }
        return new CsvResourceRecordIO<>();
    }


    public static ResourceRecordConsumerProvider<Object> newFileWriter(BatchFileWriterModel consumerModel,
                                                                       IBeanProvider beanContainer) {
        IResourceRecordOutputProvider<Object> recordIO = newRecordOutputProvider(consumerModel, beanContainer);
        IResourceLoader resourceLoader = loadResourceLoader(consumerModel.getResourceLoader(), beanContainer);

        ResourceRecordConsumerProvider<Object> writer = new ResourceRecordConsumerProvider<>();
        writer.setEncoding(consumerModel.getEncoding());
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

    private static IResourceRecordOutputProvider<Object> newRecordOutputProvider(BatchFileWriterModel readerModel, IBeanProvider beanContainer) {
        String beanName = readerModel.getResourceIO();
        if (beanName != null)
            return (IResourceRecordOutputProvider<Object>) beanContainer.getBean(beanName);

        if (readerModel.getNewRecordOutputProvider() != null)
            return (IResourceRecordOutputProvider<Object>) readerModel.getNewRecordOutputProvider().invoke(XLang.newEvalScope());

        if (readerModel.getFileModelPath() != null) {
            ModelBasedResourceRecordIO<Object> io = new ModelBasedResourceRecordIO<>();
            io.setModelFilePath(readerModel.getFileModelPath());
            return io;
        }

        return new CsvResourceRecordIO<>();
    }


}
