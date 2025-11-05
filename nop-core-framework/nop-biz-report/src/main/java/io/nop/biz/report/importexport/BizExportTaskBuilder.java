package io.nop.biz.report.importexport;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.batch.core.BatchTaskBuilder;
import io.nop.batch.core.IBatchConsumerProvider;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.batch.core.consumer.ResourceRecordConsumerProvider;
import io.nop.batch.orm.loader.OrmQueryBatchLoaderProvider;
import io.nop.biz.report.BizReportConstants;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.record.csv.CsvResourceRecordIO;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.orm.IOrmEntity;
import io.nop.report.core.record.ExcelIOConfig;
import io.nop.report.core.record.ExcelResourceIO;

import java.util.concurrent.Executor;

import static io.nop.biz.report.BizReportConstants.VAR_EXPORT_FILE_PATH;

public class BizExportTaskBuilder {

    private final IGraphQLEngine graphQLEngine;
    private final IDaoProvider daoProvider;
    private final Executor executor;

    public BizExportTaskBuilder(IGraphQLEngine graphQLEngine,
                                IDaoProvider daoProvider,
                                Executor executor) {
        this.graphQLEngine = graphQLEngine;
        this.daoProvider = daoProvider;
        this.executor = executor;
    }

    public BatchTaskBuilder<Object, Object> newTaskBuilder(
            String bizObjName, IEntityDao<IOrmEntity> dao,
            BizEntityExportConfig config) {
        BatchTaskBuilder<Object, Object> builder = new BatchTaskBuilder<>();
        builder.loader(buildLoader(bizObjName, dao, config));
        builder.concurrency(config.getConcurrency()).batchSize(config.getBatchSize());
        builder.executor(executor);
        builder.singleSession(true);
        builder.consumer(buildConsumer(config));
        return builder;
    }

    protected IBatchLoaderProvider<Object> buildLoader(String bizObjName,
                                                       IEntityDao<IOrmEntity> dao, BizEntityExportConfig config) {
        OrmQueryBatchLoaderProvider<IOrmEntity> provider = new OrmQueryBatchLoaderProvider<>();
        provider.setEntityName(dao.getEntityName());
        provider.setDaoProvider(daoProvider);
        QueryBean query = new QueryBean();
        query.setFilter(config.getFilter());
        query.setOrderBy(config.getOrderBy());
        provider.setQuery(query);
        return provider.withHook(loader -> {
            FieldSelectionBean selection = FieldSelectionBean.fromProps(config.getExportFieldNames());
            return new GraphQLFetchResultBatchLoader(loader, graphQLEngine, bizObjName, selection);
        });
    }

    protected IBatchConsumerProvider<Object> buildConsumer(BizEntityExportConfig config) {
        String exportFormat = config.getExportFormat();
        if (StringHelper.isEmpty(exportFormat))
            exportFormat = BizReportConstants.EXPORT_FORMAT_XLSX;

        if (BizReportConstants.EXPORT_FORMAT_XLSX.equals(exportFormat)) {
            return newExcelWriter(config);
        } else if (BizReportConstants.EXPORT_FORMAT_CSV.equals(exportFormat)) {
            return newCsvWriter(config);
        } else {
            throw new IllegalArgumentException("nop.err.biz.report.unsupported-export-format:" + exportFormat);
        }
    }

    protected IBatchConsumerProvider<Object> newExcelWriter(BizEntityExportConfig config) {
        ExcelResourceIO<Object> recordIO = newExcelIO(config);
        ResourceRecordConsumerProvider<Object> writer = new ResourceRecordConsumerProvider<>();
        writer.setPathExpr(getPathExpr());
        writer.setRecordIO(recordIO);
        addMeta(writer, config);
        return writer;
    }

    protected ExcelResourceIO<Object> newExcelIO(BizEntityExportConfig exportConfig) {
        ExcelResourceIO<Object> io = new ExcelResourceIO<>();
        ExcelIOConfig config = new ExcelIOConfig();
        io.setIOConfig(config);
        io.setHeaders(exportConfig.getExportFieldNames());
        if (exportConfig.isUseFieldLabels())
            io.setHeaderLabels(exportConfig.getExportFieldLabels());
        return io;
    }

    protected IEvalAction getPathExpr() {
        return ctx -> ctx.getEvalScope().getValue(VAR_EXPORT_FILE_PATH);
    }

    protected IBatchConsumerProvider<Object> newCsvWriter(BizEntityExportConfig config) {
        CsvResourceRecordIO<Object> io = new CsvResourceRecordIO<>();
        io.setHeaders(config.getExportFieldNames());
        if (config.isUseFieldLabels())
            io.setHeaderLabels(config.getExportFieldLabels());
        if (config.getCsvFormat() != null) {
            io.setFormat(config.getCsvFormat());
        }

        ResourceRecordConsumerProvider<Object> writer = new ResourceRecordConsumerProvider<>();
        writer.setPathExpr(getPathExpr());
        writer.setRecordIO(io);
        writer.setResourceLocator(VirtualFileSystem.instance());
        addMeta(writer, config);
        return writer;
    }

    void addMeta(ResourceRecordConsumerProvider<Object> provider, BizEntityExportConfig config) {
        if (config.getMetaData() != null) {
            provider.setMetaProvider(taskCtx -> config.getMetaData());
        }
    }
}
